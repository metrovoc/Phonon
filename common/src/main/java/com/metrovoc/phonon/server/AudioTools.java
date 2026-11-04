package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.config.PhononServerConfig;
import com.metrovoc.phonon.server.AudioToolManifest.Asset;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Finds yt-dlp, FFmpeg and Deno, and installs pinned copies into the game folder when an operator accepts.
 * Installed copies win over the system's, because they exist only if the system's were missing.
 */
public final class AudioTools {
    public static final List<String> TOOLS = List.of("yt-dlp", "ffmpeg", "ffprobe", "deno");
    private static final boolean WINDOWS =
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    public enum Source { INSTALLED, SYSTEM, MISSING }

    public record Status(String tool, Source source) {}

    private static Path toolsDir;

    private AudioTools() {}

    public static void initialize(Path gameDir) {
        toolsDir = gameDir.toAbsolutePath().normalize().resolve("phonon").resolve("tools");
    }

    public static String command(String tool) {
        return installed(tool).map(Path::toString).orElse(tool);
    }

    public static Optional<Path> installed(String tool) {
        if (toolsDir == null) {
            return Optional.empty();
        }
        Path path = toolsDir.resolve(fileName(tool));
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    public static List<Status> status() {
        List<Status> statuses = new ArrayList<>();
        for (String tool : TOOLS) {
            Source source = installed(tool).isPresent() ? Source.INSTALLED
                : onPath(tool) ? Source.SYSTEM
                : Source.MISSING;
            statuses.add(new Status(tool, source));
        }
        return statuses;
    }

    private static boolean onPath(String tool) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String directory : path.split(File.pathSeparator)) {
            if (!directory.isBlank() && Files.isExecutable(Path.of(directory).resolve(fileName(tool)))) {
                return true;
            }
        }
        return false;
    }

    private static String fileName(String tool) {
        return WINDOWS ? tool + ".exe" : tool;
    }

    private static Optional<List<Asset>> assets() {
        return AudioToolManifest.forPlatform(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    public static boolean canInstall() {
        return assets().isPresent();
    }

    /** Download size of everything not yet installed. */
    public static long installBytes() {
        return assets().orElse(List.of()).stream()
            .filter(asset -> installed(asset.tool()).isEmpty())
            .mapToLong(Asset::bytes)
            .sum();
    }

    /** Blocking. Reports each tool's name as it starts. */
    public static void install(Consumer<String> progress) throws IOException {
        List<Asset> assets = assets().orElseThrow(() -> new IOException("No tool builds for this platform"));
        Files.createDirectories(toolsDir);
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        for (Asset asset : assets) {
            if (installed(asset.tool()).isEmpty()) {
                progress.accept(asset.tool());
                installAsset(client, asset);
            }
        }
    }

    private static void installAsset(HttpClient client, Asset asset) throws IOException {
        Path download = toolsDir.resolve(asset.tool() + ".download");
        Path unpacked = toolsDir.resolve(asset.tool() + ".part");
        try {
            fetch(client, asset, download);
            unpack(download, unpacked, asset);
            if (!WINDOWS && !unpacked.toFile().setExecutable(true)) {
                throw new IOException("Could not make " + asset.tool() + " executable");
            }
            Path target = toolsDir.resolve(fileName(asset.tool()));
            try {
                Files.move(unpacked, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(unpacked, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Phonon.LOGGER.info("Installed {} from {}", asset.tool(), asset.url());
        } finally {
            Files.deleteIfExists(download);
            Files.deleteIfExists(unpacked);
        }
    }

    private static void fetch(HttpClient client, Asset asset, Path destination) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(asset.url())).GET().build();
        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + asset.tool(), e);
        }
        MessageDigest digest = sha256();
        try (InputStream input = new DigestInputStream(response.body(), digest)) {
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + asset.url());
            }
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equals(asset.sha256())) {
            throw new IOException("Checksum mismatch for " + asset.tool() + ": " + actual);
        }
    }

    private static void unpack(Path download, Path destination, Asset asset) throws IOException {
        switch (asset.packaging()) {
            case RAW -> Files.copy(download, destination, StandardCopyOption.REPLACE_EXISTING);
            case GZIP -> {
                try (InputStream input = new GZIPInputStream(Files.newInputStream(download))) {
                    Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            case ZIP -> unzipTool(download, destination, fileName(asset.tool()));
        }
    }

    private static void unzipTool(Path zip, Path destination, String entryName) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (entry.getName().equals(entryName)) {
                    try (OutputStream output = Files.newOutputStream(destination)) {
                        input.transferTo(output);
                    }
                    return;
                }
            }
        }
        throw new IOException(entryName + " not found in " + zip.getFileName());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The server's proxy and cookies settings, as yt-dlp arguments. */
    public static List<String> networkArguments() {
        List<String> arguments = new ArrayList<>();
        String proxy = PhononServerConfig.getDownloadProxy().strip();
        if (!proxy.isEmpty()) {
            arguments.addAll(List.of("--proxy", proxy));
        }
        String cookies = PhononServerConfig.getCookiesFile().strip();
        if (!cookies.isEmpty()) {
            // Downloads run in a temporary folder, so a relative path has to be pinned to the server's
            arguments.addAll(List.of("--cookies", Path.of(cookies).toAbsolutePath().toString()));
        }
        return arguments;
    }

    /** Blocking. A command's output, or null if it failed or ran out of time. */
    private static String run(int timeoutSeconds, String... command) throws InterruptedException {
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("phonon_tool_", ".txt");
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                return null;
            }
            String text = Files.readString(output, StandardCharsets.UTF_8);
            return process.exitValue() == 0 ? text : null;
        } catch (IOException e) {
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException e) {
                    Phonon.LOGGER.debug("Could not delete {}", output, e);
                }
            }
        }
    }

    /** Blocking. What a tool reports as its version, or null if it can't be run. */
    public static String version(String tool) {
        String flag = tool.equals("ffmpeg") || tool.equals("ffprobe") ? "-version" : "--version";
        try {
            String output = run(10, command(tool), flag);
            return output == null || output.isBlank() ? null : shortVersion(tool, output.lines().findFirst().orElse(""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** "ffmpeg version 6.1.1 Copyright..." and "deno 2.9.8 (...)" down to the number. */
    static String shortVersion(String tool, String line) {
        String[] words = line.strip().split("\\s+");
        return switch (tool) {
            case "ffmpeg", "ffprobe" -> words.length > 2 ? words[2] : line.strip();
            case "deno" -> words.length > 1 ? words[1] : line.strip();
            default -> words[0];
        };
    }

    /** yt-dlp versions are release dates. Days since this one, or -1 if it isn't one. */
    public static long ageInDays(String ytDlpVersion, LocalDate today) {
        String[] parts = ytDlpVersion == null ? new String[0] : ytDlpVersion.split("\\.");
        if (parts.length < 3) {
            return -1;
        }
        try {
            return ChronoUnit.DAYS.between(LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])), today);
        } catch (RuntimeException notADate) {
            return -1;
        }
    }

    /** Blocking. Installs Phonon's own yt-dlp, which then takes over from any copy on the system. */
    public static void installYtDlp() throws IOException {
        List<Asset> assets = assets().orElseThrow(() -> new IOException("No tool builds for this platform"));
        Files.createDirectories(toolsDir);
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        for (Asset asset : assets) {
            if (asset.tool().equals("yt-dlp")) {
                installAsset(client, asset);
            }
        }
    }

    /** Blocking. Lets the installed yt-dlp update itself, since video sites change faster than mod releases. */
    public static boolean updateYtDlp() throws IOException, InterruptedException {
        if (installed("yt-dlp").isEmpty()) {
            installYtDlp();
        }
        Path ytDlp = installed("yt-dlp").orElseThrow(() -> new IOException("yt-dlp couldn't be installed"));
        String output = run(300, ytDlp.toString(), "--update-to", "stable");
        Phonon.LOGGER.info("yt-dlp update:\n{}", output);
        return output != null;
    }
}
