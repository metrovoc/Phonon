package com.metrovoc.phonon.audio;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and manages ffmpeg/ffprobe binaries.
 * Uses ffbinaries.com prebuilt releases.
 */
public final class FFmpegBinary {
    private static final String VERSION = "6.1";

    private static Path installDir;
    private static volatile boolean initialized;
    private static volatile boolean available;

    private FFmpegBinary() {}

    public static void setInstallDir(Path dir) {
        installDir = dir;
    }

    public static Path getInstallDir() {
        return installDir;
    }

    public static boolean isAvailable() {
        if (!initialized) {
            checkInstalled();
        }
        return available;
    }

    public static Path getFFprobePath() {
        if (installDir == null) return null;
        String exe = isWindows() ? "ffprobe.exe" : "ffprobe";
        return installDir.resolve(exe);
    }

    public static Path getFFmpegPath() {
        if (installDir == null) return null;
        String exe = isWindows() ? "ffmpeg.exe" : "ffmpeg";
        return installDir.resolve(exe);
    }

    private static void checkInstalled() {
        initialized = true;
        Path ffprobe = getFFprobePath();
        if (ffprobe != null && Files.isExecutable(ffprobe)) {
            available = true;
        }
    }

    /**
     * Download ffmpeg and ffprobe for current platform.
     * Progress callback receives values 0.0 to 1.0.
     */
    public static CompletableFuture<Boolean> download(Consumer<Double> progress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (installDir == null) {
                    return false;
                }
                Files.createDirectories(installDir);

                String platform = detectPlatform();
                if (platform == null) {
                    return false;
                }

                // Download URLs for ffmpeg 6.1 from ffbinaries
                String baseUrl = "https://github.com/ffbinaries/ffbinaries-prebuilt/releases/download/v" + VERSION + "/";
                String ffmpegZip = "ffmpeg-" + VERSION + "-" + platform + ".zip";
                String ffprobeZip = "ffprobe-" + VERSION + "-" + platform + ".zip";

                progress.accept(0.0);

                // Download and extract ffprobe (required)
                if (!downloadAndExtract(baseUrl + ffprobeZip, installDir)) {
                    return false;
                }
                progress.accept(0.5);

                // Download and extract ffmpeg (for future transcoding)
                if (!downloadAndExtract(baseUrl + ffmpegZip, installDir)) {
                    return false;
                }
                progress.accept(1.0);

                // Mark executable on Unix
                if (!isWindows()) {
                    getFFprobePath().toFile().setExecutable(true);
                    getFFmpegPath().toFile().setExecutable(true);
                }

                initialized = false; // Force recheck
                return isAvailable();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    private static boolean downloadAndExtract(String url, Path destDir) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(10))
            .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            return false;
        }

        // Extract zip
        try (ZipInputStream zis = new ZipInputStream(response.body())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = entry.getName();
                // Only extract ffmpeg/ffprobe executables
                if (name.contains("ffmpeg") || name.contains("ffprobe")) {
                    // Handle nested paths in zip
                    String fileName = Path.of(name).getFileName().toString();
                    Path target = destDir.resolve(fileName);
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        return true;
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        if (os.contains("win")) {
            return arch.contains("64") ? "windows-64" : "windows-32";
        } else if (os.contains("mac")) {
            // ffbinaries uses "osx-64" for both Intel and ARM (Rosetta compatible)
            return "osx-64";
        } else if (os.contains("linux")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                return "linux-arm-64";
            } else if (arch.contains("arm")) {
                return "linux-armel";
            } else {
                return arch.contains("64") ? "linux-64" : "linux-32";
            }
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
