package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.AudioStreamInfo;
import com.metrovoc.phonon.audio.OggPageScanner;
import com.metrovoc.phonon.config.PhononServerConfig;

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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server-side audio file storage.
 * Downloads and stores OGG files for distribution via packets.
 */
public class ServerAudioStorage {

    private static ServerAudioStorage instance;

    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2);
    private final Map<UUID, AudioStreamInfo> streamInfoCache = new ConcurrentHashMap<>();

    private Path storageDir;

    private ServerAudioStorage() {}

    public static ServerAudioStorage getInstance() {
        if (instance == null) {
            instance = new ServerAudioStorage();
        }
        return instance;
    }

    public void initialize(Path worldDir) {
        this.storageDir = worldDir.resolve("phonon_audio");
        try {
            Files.createDirectories(storageDir);
            scanExistingAudioFiles();
            Phonon.LOGGER.info("Server audio storage initialized at {}", storageDir);
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to create audio storage directory", e);
        }
    }

    private void scanExistingAudioFiles() {
        try (var files = Files.list(storageDir)) {
            files.filter(p -> p.toString().endsWith(".ogg"))
                .forEach(this::scanAndCacheFromPath);
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to scan existing audio files", e);
        }
    }

    private void scanAndCacheFromPath(Path audioPath) {
        String fileName = audioPath.getFileName().toString();
        String uuidStr = fileName.substring(0, fileName.length() - 4);
        try {
            UUID resourceId = UUID.fromString(uuidStr);
            scanAndCacheStreamInfo(resourceId, audioPath);
        } catch (IllegalArgumentException e) {
            Phonon.LOGGER.warn("Skipping invalid audio file name: {}", fileName);
        }
    }

    private void scanAndCacheStreamInfo(UUID resourceId, Path audioPath) {
        OggPageScanner.OggScanResult result = OggPageScanner.scan(audioPath);
        if (result != null) {
            streamInfoCache.put(resourceId, new AudioStreamInfo(
                result.headerBytes(),
                result.seekTable(),
                result.sampleRate()
            ));
        }
    }

    public Optional<AudioStreamInfo> getStreamInfo(UUID resourceId) {
        return Optional.ofNullable(streamInfoCache.get(resourceId));
    }

    public CompletableFuture<Boolean> downloadAndStore(UUID resourceId, String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path targetFile = storageDir.resolve(resourceId + ".ogg");

                Phonon.LOGGER.info("Downloading audio from {} to {}", url, targetFile);

                HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(PhononServerConfig.getDownloadConnectTimeoutSeconds()))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(PhononServerConfig.getDownloadReadTimeoutSeconds()))
                    .build();

                HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
                );

                if (response.statusCode() == 200) {
                    Files.copy(response.body(), targetFile, StandardCopyOption.REPLACE_EXISTING);
                    scanAndCacheStreamInfo(resourceId, targetFile);
                    long size = Files.size(targetFile);
                    Phonon.LOGGER.info("Downloaded audio {} ({} bytes)", resourceId, size);
                    return true;
                } else {
                    Phonon.LOGGER.error("Failed to download audio: HTTP {}", response.statusCode());
                    return false;
                }
            } catch (Exception e) {
                Phonon.LOGGER.error("Failed to download audio {}", resourceId, e);
                return false;
            }
        }, downloadExecutor);
    }

    public boolean hasAudio(UUID resourceId) {
        if (storageDir == null) return false;
        return Files.exists(storageDir.resolve(resourceId + ".ogg"));
    }

    public Optional<Path> getAudioPath(UUID resourceId) {
        if (storageDir == null) return Optional.empty();
        Path file = storageDir.resolve(resourceId + ".ogg");
        return Files.exists(file) ? Optional.of(file) : Optional.empty();
    }

    public Optional<byte[]> readAudio(UUID resourceId) {
        return getAudioPath(resourceId).flatMap(path -> {
            try {
                return Optional.of(Files.readAllBytes(path));
            } catch (IOException e) {
                Phonon.LOGGER.error("Failed to read audio file {}", resourceId, e);
                return Optional.empty();
            }
        });
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

    public int calculateChunkCount(UUID resourceId) {
        long size = getAudioSize(resourceId);
        return (int) Math.ceil((double) size / PhononServerConfig.getChunkSize());
    }

    public static int getChunkSize() {
        return PhononServerConfig.getChunkSize();
    }

    public long getDurationMs(UUID resourceId) {
        return getAudioPath(resourceId)
            .flatMap(FFmpegHelper::getDurationMs)
            .orElse(-1L);
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
        downloadExecutor.shutdown();
    }
}
