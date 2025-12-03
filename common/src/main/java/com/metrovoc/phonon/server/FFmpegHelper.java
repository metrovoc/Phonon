package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * FFmpeg/FFprobe wrapper for audio metadata extraction.
 * Also provides yt-dlp integration for downloading from various sources.
 * Relies on system-installed tools - no bundled binaries.
 */
public class FFmpegHelper {

    public static boolean isAvailable() {
        return isFFmpegAvailable();
    }

    public static boolean isFFmpegAvailable() {
        return checkCommand("ffprobe", "-version");
    }

    public static boolean isYtDlpAvailable() {
        return checkCommand("yt-dlp", "--version");
    }

    private static boolean checkCommand(String cmd, String arg) {
        try {
            Process process = new ProcessBuilder(cmd, arg)
                .redirectErrorStream(true)
                .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Download any URL using yt-dlp and convert to OGG Vorbis.
     * Supports YouTube, Bilibili, and many other video/audio sites,
     * as well as direct audio URLs.
     *
     * @param url        The source URL to download
     * @param targetFile The destination OGG file path
     * @return true if download and conversion succeeded
     */
    public static boolean downloadAndConvert(String url, Path targetFile) {
        if (!isYtDlpAvailable()) {
            return false;
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("phonon_dl_");

            ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "-x",                           // Extract audio only
                "--audio-format", "vorbis",     // Convert to OGG (Vorbis)
                "--audio-quality", "5",         // Quality ~160kbps
                "-o", "%(id)s.%(ext)s",         // Output filename template
                "--no-playlist",                // Download single video only
                "--no-warnings",
                url
            );
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);

            Phonon.LOGGER.info("Running yt-dlp for: {}", url);
            Process process = pb.start();

            // Read output to prevent buffer blocking
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Phonon.LOGGER.debug("[yt-dlp] {}", line);
                }
            }

            boolean finished = process.waitFor(300, TimeUnit.SECONDS); // 5 minute timeout
            if (!finished) {
                process.destroyForcibly();
                Phonon.LOGGER.error("yt-dlp timeout");
                return false;
            }

            if (process.exitValue() == 0) {
                // Find the generated .ogg file
                try (Stream<Path> files = Files.list(tempDir)) {
                    Optional<Path> downloaded = files
                        .filter(p -> p.toString().endsWith(".ogg"))
                        .findFirst();

                    if (downloaded.isPresent()) {
                        Files.createDirectories(targetFile.getParent());
                        Files.move(downloaded.get(), targetFile, StandardCopyOption.REPLACE_EXISTING);
                        Phonon.LOGGER.info("yt-dlp successfully converted to: {}", targetFile);
                        return true;
                    } else {
                        Phonon.LOGGER.error("yt-dlp produced no .ogg file");
                    }
                }
            } else {
                Phonon.LOGGER.error("yt-dlp failed with exit code: {}", process.exitValue());
            }
        } catch (Exception e) {
            Phonon.LOGGER.error("yt-dlp download failed", e);
        } finally {
            // Clean up temp directory
            if (tempDir != null) {
                try (Stream<Path> walk = Files.walk(tempDir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
        return false;
    }

    public static Optional<Long> getDurationMs(Path audioFile) {
        if (!isFFmpegAvailable()) {
            return Optional.empty();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "quiet",
                "-show_entries", "format=duration",
                "-of", "csv=p=0",
                audioFile.toAbsolutePath().toString()
            );

            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.readLine();
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                Phonon.LOGGER.warn("FFprobe timeout for {}", audioFile);
                return Optional.empty();
            }

            if (process.exitValue() != 0 || output == null || output.isBlank()) {
                Phonon.LOGGER.warn("FFprobe failed for {}", audioFile);
                return Optional.empty();
            }

            double seconds = Double.parseDouble(output.trim());
            long ms = (long) (seconds * 1000);
            return Optional.of(ms);

        } catch (Exception e) {
            Phonon.LOGGER.error("Failed to get duration for {}: {}", audioFile, e.getMessage());
            return Optional.empty();
        }
    }
}
