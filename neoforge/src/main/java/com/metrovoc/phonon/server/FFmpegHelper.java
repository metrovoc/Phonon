package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * FFmpeg/FFprobe wrapper for audio metadata extraction.
 * Relies on system-installed ffmpeg - no bundled binaries.
 */
public class FFmpegHelper {

    private static volatile Boolean available;

    /**
     * Check if ffprobe is available on the system.
     */
    public static boolean isAvailable() {
        if (available == null) {
            available = checkAvailability();
        }
        return available;
    }

    private static boolean checkAvailability() {
        try {
            Process process = new ProcessBuilder("ffprobe", "-version")
                .redirectErrorStream(true)
                .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                Phonon.LOGGER.info("FFmpeg detected on system");
                return true;
            }
        } catch (Exception e) {
            // ffprobe not found
        }
        Phonon.LOGGER.warn("FFmpeg not found. Audio duration will be unavailable. Install ffmpeg: https://ffmpeg.org/download.html");
        return false;
    }

    /**
     * Get audio duration in milliseconds using ffprobe.
     *
     * @param audioFile Path to the audio file
     * @return Duration in milliseconds, or empty if unavailable
     */
    public static Optional<Long> getDurationMs(Path audioFile) {
        if (!isAvailable()) {
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
