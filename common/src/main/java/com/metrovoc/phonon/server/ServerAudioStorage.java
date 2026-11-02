package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.AudioStreamInfo;
import com.metrovoc.phonon.audio.OggPageScanner;
import com.metrovoc.phonon.config.PhononServerConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Server-owned OGG storage and seek metadata. */
public final class ServerAudioStorage {
    private static ServerAudioStorage instance;

    private final Map<UUID, AudioStreamInfo> streamInfoCache = new java.util.concurrent.ConcurrentHashMap<>();
    private ExecutorService downloadExecutor;
    private Path storageDir;

    private ServerAudioStorage() {
        downloadExecutor = createDownloadExecutor();
    }

    public static ServerAudioStorage getInstance() {
        if (instance == null) {
            instance = new ServerAudioStorage();
        }
        return instance;
    }

    public static void reset() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    public void initialize(Path worldDir) {
        if (downloadExecutor == null || downloadExecutor.isShutdown()) {
            downloadExecutor = createDownloadExecutor();
        }

        storageDir = worldDir.resolve("phonon_audio");
        streamInfoCache.clear();
        try {
            Files.createDirectories(storageDir);
            scanExistingAudioFiles();
            Phonon.LOGGER.info("Server audio storage initialized at {}", storageDir);
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to initialize audio storage", e);
        }
    }

    private static ExecutorService createDownloadExecutor() {
        return Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "Phonon-AudioDownload");
            thread.setDaemon(true);
            return thread;
        });
    }

    private void scanExistingAudioFiles() {
        try (var files = Files.list(storageDir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".ogg"))
                .forEach(this::scanAndCacheFromPath);
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to scan existing audio files", e);
        }
    }

    private void scanAndCacheFromPath(Path audioPath) {
        String fileName = audioPath.getFileName().toString();
        try {
            UUID resourceId = UUID.fromString(fileName.substring(0, fileName.length() - 4));
            if (!scanAndCacheStreamInfo(resourceId, audioPath)) {
                Phonon.LOGGER.warn("Ignoring invalid OGG file {}", audioPath);
            }
        } catch (IllegalArgumentException e) {
            Phonon.LOGGER.warn("Skipping invalid audio file name: {}", fileName);
        }
    }

    private boolean scanAndCacheStreamInfo(UUID resourceId, Path audioPath) {
        OggPageScanner.OggScanResult result = OggPageScanner.scan(audioPath);
        if (result == null) {
            streamInfoCache.remove(resourceId);
            return false;
        }

        streamInfoCache.put(resourceId, new AudioStreamInfo(
            result.headerBytes(),
            result.seekTable(),
            result.sampleRate(),
            result.durationMs()
        ));
        return true;
    }

    public Optional<AudioStreamInfo> getStreamInfo(UUID resourceId) {
        return Optional.ofNullable(streamInfoCache.get(resourceId));
    }

    public CompletableFuture<DownloadResult> downloadAndStore(UUID resourceId, String url) {
        return CompletableFuture.supplyAsync(() -> downloadAndStoreBlocking(resourceId, url), downloadExecutor);
    }

    /** Stores a local audio file, leaving the original where it is. */
    public CompletableFuture<DownloadResult> importAndStore(UUID resourceId, Path source) {
        return CompletableFuture.supplyAsync(() -> importBlocking(resourceId, source), downloadExecutor);
    }

    /** Runs slow work on the download threads. */
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, downloadExecutor);
    }

    private DownloadResult downloadAndStoreBlocking(UUID resourceId, String url) {
        Path targetFile = storageDir.resolve(resourceId + ".ogg");
        Phonon.LOGGER.info("Processing audio: {}", url);
        DownloadResult result = FFmpegHelper.downloadAndConvert(url, targetFile);

        if (!result.success() && isDirectOggUrl(url)) {
            Phonon.LOGGER.info("Falling back to direct OGG download");
            AudioFailure failure = directDownload(url, targetFile);
            result = failure == null ? DownloadResult.ok(fileTitle(url)) : DownloadResult.failed(failure);
        }
        return result.success() ? validateStored(resourceId, targetFile, result) : deleteAndReturn(targetFile, result);
    }

    private DownloadResult importBlocking(UUID resourceId, Path source) {
        Path targetFile = storageDir.resolve(resourceId + ".ogg");
        try {
            String fileName = source.getFileName().toString();
            String title = stripExtension(fileName);
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".ogg") && OggPageScanner.scan(source) != null) {
                if (Files.size(source) > getMaxAudioBytes()) {
                    return DownloadResult.failed(AudioFailure.TOO_LARGE);
                }
                Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                AudioFailure failure = FFmpegHelper.convertToOgg(source, targetFile);
                if (failure != null) {
                    return deleteAndReturn(targetFile, DownloadResult.failed(failure));
                }
            }
            return validateStored(resourceId, targetFile, DownloadResult.ok(title));
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to import {}", source, e);
            return deleteAndReturn(targetFile, DownloadResult.failed(AudioFailure.UNKNOWN));
        }
    }

    private DownloadResult validateStored(UUID resourceId, Path targetFile, DownloadResult result) {
        try {
            long size = Files.isRegularFile(targetFile) ? Files.size(targetFile) : 0;
            if (size <= 0) {
                return deleteAndReturn(targetFile, DownloadResult.failed(AudioFailure.INVALID_AUDIO));
            }
            if (size > getMaxAudioBytes()) {
                Phonon.LOGGER.error("Rejected audio {}: {} bytes exceeds the limit", resourceId, size);
                return deleteAndReturn(targetFile, DownloadResult.failed(AudioFailure.TOO_LARGE));
            }
            if (!scanAndCacheStreamInfo(resourceId, targetFile)) {
                Phonon.LOGGER.error("Rejected audio {}: not a valid seekable OGG Vorbis file", resourceId);
                return deleteAndReturn(targetFile, DownloadResult.failed(AudioFailure.INVALID_AUDIO));
            }
            Phonon.LOGGER.info("Stored audio {} ({} bytes)", resourceId, size);
            return result;
        } catch (Exception e) {
            Phonon.LOGGER.error("Failed to store audio {}", resourceId, e);
            return deleteAndReturn(targetFile, DownloadResult.failed(AudioFailure.UNKNOWN));
        }
    }

    private static DownloadResult deleteAndReturn(Path targetFile, DownloadResult result) {
        try {
            Files.deleteIfExists(targetFile);
        } catch (IOException ignored) {
        }
        return result;
    }

    static String fileTitle(String url) {
        String path = URI.create(url).getPath();
        String decoded = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        return stripExtension(decoded);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static boolean isDirectOggUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ogg") || lower.contains(".ogg?");
    }

    /** Returns the failure, or null on success. */
    private AudioFailure directDownload(String url, Path targetFile) {
        Path temporary = targetFile.resolveSibling(targetFile.getFileName() + ".part");
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(PhononServerConfig.getDownloadConnectTimeoutSeconds()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(PhononServerConfig.getDownloadReadTimeoutSeconds()))
            .GET()
            .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                if (response.statusCode() != 200) {
                    Phonon.LOGGER.error("HTTP download failed: {}", response.statusCode());
                    return AudioFailure.fromHttpStatus(response.statusCode());
                }

                long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (declaredLength > getMaxAudioBytes()) {
                    Phonon.LOGGER.error("HTTP audio exceeds configured size limit: {} bytes", declaredLength);
                    return AudioFailure.TOO_LARGE;
                }

                Files.createDirectories(targetFile.getParent());
                copyWithLimit(input, temporary, getMaxAudioBytes());
                moveAtomically(temporary, targetFile);
                return null;
            }
        } catch (AudioTooLargeException e) {
            deleteQuietly(temporary);
            return AudioFailure.TOO_LARGE;
        } catch (IOException e) {
            Phonon.LOGGER.error("Direct download failed", e);
            deleteQuietly(temporary);
            return AudioFailure.NETWORK;
        } catch (Exception e) {
            Phonon.LOGGER.error("Direct download failed", e);
            deleteQuietly(temporary);
            return AudioFailure.UNKNOWN;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static final class AudioTooLargeException extends IOException {
    }

    private static void copyWithLimit(InputStream input, Path output, long maxBytes) throws IOException {
        byte[] transferBuffer = new byte[64 * 1024];
        long written = 0;
        try (OutputStream destination = Files.newOutputStream(output)) {
            int read;
            while ((read = input.read(transferBuffer)) >= 0) {
                written += read;
                if (written > maxBytes) {
                    throw new AudioTooLargeException();
                }
                destination.write(transferBuffer, 0, read);
            }
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long getMaxAudioBytes() {
        return PhononServerConfig.getMaxAudioSizeMB() * 1024L * 1024L;
    }

    public boolean hasAudio(UUID resourceId) {
        return getAudioPath(resourceId).isPresent() && streamInfoCache.containsKey(resourceId);
    }

    public Optional<Path> getAudioPath(UUID resourceId) {
        if (storageDir == null) {
            return Optional.empty();
        }
        Path file = storageDir.resolve(resourceId + ".ogg");
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    public long getAudioSize(UUID resourceId) {
        return getAudioPath(resourceId).map(path -> {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return 0L;
            }
        }).orElse(0L);
    }

    public long getDurationMs(UUID resourceId) {
        AudioStreamInfo info = streamInfoCache.get(resourceId);
        if (info != null && info.durationMs() > 0) {
            return info.durationMs();
        }
        return getAudioPath(resourceId).flatMap(FFmpegHelper::getDurationMs).orElse(-1L);
    }

    public boolean deleteAudio(UUID resourceId) {
        streamInfoCache.remove(resourceId);
        return getAudioPath(resourceId).map(path -> {
            try {
                Files.deleteIfExists(path);
                return true;
            } catch (IOException e) {
                Phonon.LOGGER.error("Failed to delete audio file {}", resourceId, e);
                return false;
            }
        }).orElse(false);
    }

    public void shutdown() {
        if (downloadExecutor != null) {
            downloadExecutor.shutdownNow();
        }
        streamInfoCache.clear();
        storageDir = null;
    }
}
