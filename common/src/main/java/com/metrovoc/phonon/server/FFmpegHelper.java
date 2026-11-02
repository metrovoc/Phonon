package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.config.PhononServerConfig;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * FFmpeg/FFprobe wrapper for audio metadata extraction and conversion.
 * Also provides yt-dlp integration for downloading from various sources.
 */
public class FFmpegHelper {
    private static final String TITLE_FILE = "title.txt";

    /**
     * Download any URL using yt-dlp and convert to OGG Vorbis.
     * Supports YouTube, Bilibili, and many other video/audio sites,
     * as well as direct audio URLs.
     *
     * @param url        The source URL to download
     * @param targetFile The destination OGG file path
     */
    public static DownloadResult downloadAndConvert(String url, Path targetFile) {
        Path tempDir = null;
        Process process = null;
        try {
            tempDir = Files.createTempDirectory("phonon_dl_");
            Path log = tempDir.resolve("yt-dlp.log");

            ProcessBuilder pb = new ProcessBuilder(ytDlpArguments(url, tempDir));
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(log.toFile());

            Phonon.LOGGER.info("Running yt-dlp for: {}", url);
            try {
                process = pb.start();
            } catch (IOException e) {
                Phonon.LOGGER.error("yt-dlp could not run (PATH={})", System.getenv("PATH"), e);
                return DownloadResult.failed(AudioFailure.TOOLS_MISSING);
            }

            boolean finished = process.waitFor(
                PhononServerConfig.getDownloadReadTimeoutSeconds(),
                TimeUnit.SECONDS
            );
            if (!finished) {
                process.destroyForcibly();
                Phonon.LOGGER.error("yt-dlp timed out:\n{}", readLog(log));
                return DownloadResult.failed(AudioFailure.TIMEOUT);
            }

            Optional<Path> downloaded = findOgg(tempDir);
            if (process.exitValue() == 0 && downloaded.isPresent()) {
                Files.createDirectories(targetFile.getParent());
                Files.move(downloaded.get(), targetFile, StandardCopyOption.REPLACE_EXISTING);
                Phonon.LOGGER.info("yt-dlp successfully converted to: {}", targetFile);
                return DownloadResult.ok(readTitle(tempDir));
            }

            String output = readLog(log);
            Phonon.LOGGER.error("yt-dlp failed with exit code {}:\n{}", process.exitValue(), output);
            return DownloadResult.failed(AudioFailure.classify(output));
        } catch (Exception e) {
            Phonon.LOGGER.error("yt-dlp failed", e);
            return DownloadResult.failed(AudioFailure.UNKNOWN);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteRecursively(tempDir);
        }
    }

    private static List<String> ytDlpArguments(String url, Path tempDir) {
        List<String> arguments = new ArrayList<>(List.of(
            AudioTools.command("yt-dlp"),
            "-x",                           // Extract audio only
            "--audio-format", "vorbis",     // Convert to OGG (Vorbis)
            "--audio-quality", Integer.toString(10 - PhononServerConfig.getAudioQuality()),
            "-o", "%(id)s.%(ext)s",         // Output filename template
            "--no-playlist",                // Download single video only
            "--max-filesize", PhononServerConfig.getMaxAudioSizeMB() + "M",
            "--no-progress",
            "--encoding", "utf-8",
            "--print-to-file", "after_move:title", tempDir.resolve(TITLE_FILE).toString()
        ));
        AudioTools.installed("ffmpeg").ifPresent(ffmpeg ->
            arguments.addAll(List.of("--ffmpeg-location", ffmpeg.getParent().toString())));
        AudioTools.installed("deno").ifPresent(deno ->
            arguments.addAll(List.of("--js-runtimes", "deno:" + deno)));
        arguments.addAll(List.of("--", url));
        return arguments;
    }

    private static Optional<Path> findOgg(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.toString().endsWith(".ogg")).findFirst();
        }
    }

    private static String readTitle(Path tempDir) throws IOException {
        Path file = tempDir.resolve(TITLE_FILE);
        return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8).strip() : "";
    }

    private static String readLog(Path log) throws IOException {
        return new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    /** Convert a local audio file to OGG Vorbis. Returns the failure, or null on success. */
    public static AudioFailure convertToOgg(Path source, Path targetFile) {
        Path temporary = targetFile.resolveSibling(targetFile.getFileName() + ".part.ogg");
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                AudioTools.command("ffmpeg"),
                "-y", "-v", "error",
                "-i", source.toAbsolutePath().toString(),
                "-vn",
                "-c:a", "libvorbis",
                "-q:a", Integer.toString(PhononServerConfig.getAudioQuality()),
                temporary.toString()
            );
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            try {
                process = pb.start();
            } catch (IOException e) {
                Phonon.LOGGER.error("ffmpeg could not run (PATH={})", System.getenv("PATH"), e);
                return AudioFailure.TOOLS_MISSING;
            }
            if (!process.waitFor(PhononServerConfig.getDownloadReadTimeoutSeconds(), TimeUnit.SECONDS)) {
                return AudioFailure.TIMEOUT;
            }
            if (process.exitValue() != 0) {
                Phonon.LOGGER.error("ffmpeg could not convert {}", source);
                return AudioFailure.INVALID_AUDIO;
            }
            Files.move(temporary, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return null;
        } catch (Exception e) {
            Phonon.LOGGER.error("Failed to convert {}", source, e);
            return AudioFailure.UNKNOWN;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    public static Optional<Long> getDurationMs(Path audioFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                AudioTools.command("ffprobe"),
                "-v", "quiet",
                "-show_entries", "format=duration",
                "-of", "csv=p=0",
                audioFile.toAbsolutePath().toString()
            );

            Process process = pb.start();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                Phonon.LOGGER.warn("FFprobe timeout for {}", audioFile);
                return Optional.empty();
            }

            String output;
            try (var reader = process.inputReader()) {
                output = reader.readLine();
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
