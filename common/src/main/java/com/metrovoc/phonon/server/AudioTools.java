package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
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

    /** Blocking. Lets the installed yt-dlp update itself, since video sites change faster than mod releases. */
    public static boolean updateYtDlp() throws IOException, InterruptedException {
        Path ytDlp = installed("yt-dlp").orElseThrow(() -> new IOException("yt-dlp was not installed by Phonon"));
        Process process = new ProcessBuilder(ytDlp.toString(), "--update-to", "stable")
            .redirectErrorStream(true)
            .start();
        String output;
        try (InputStream input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
        }
        Phonon.LOGGER.info("yt-dlp update:\n{}", output);
        return finished && process.exitValue() == 0;
    }
}
