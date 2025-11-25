package com.metrovoc.phonon.audio;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around ffprobe for audio metadata.
 * Uses downloaded binary from FFmpegBinary, falls back to system PATH.
 */
public final class FFprobe {
    private static Boolean available;
    private static String executablePath;

    private FFprobe() {}

    public static boolean isAvailable() {
        if (available == null) {
            available = checkAvailable();
        }
        return available;
    }

    public static void resetCache() {
        available = null;
        executablePath = null;
    }

    private static boolean checkAvailable() {
        // First check downloaded binary
        Path downloaded = FFmpegBinary.getFFprobePath();
        if (downloaded != null && Files.isExecutable(downloaded)) {
            executablePath = downloaded.toAbsolutePath().toString();
            return testExecutable(executablePath);
        }

        // Fall back to system PATH
        executablePath = "ffprobe";
        return testExecutable(executablePath);
    }

    private static boolean testExecutable(String path) {
        try {
            Process p = new ProcessBuilder(path, "-version")
                .redirectErrorStream(true)
                .start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get audio duration in milliseconds.
     * Returns empty if ffprobe fails or is unavailable.
     */
    public static OptionalLong getDurationMs(Path file) {
        if (!isAvailable()) {
            return OptionalLong.empty();
        }

        try {
            Process p = new ProcessBuilder(
                executablePath,
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file.toAbsolutePath().toString()
            ).redirectErrorStream(true).start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                output = reader.readLine();
            }

            boolean finished = p.waitFor(30, TimeUnit.SECONDS);
            if (!finished || p.exitValue() != 0 || output == null) {
                return OptionalLong.empty();
            }

            double seconds = Double.parseDouble(output.trim());
            return OptionalLong.of((long) (seconds * 1000));
        } catch (Exception e) {
            return OptionalLong.empty();
        }
    }
}
