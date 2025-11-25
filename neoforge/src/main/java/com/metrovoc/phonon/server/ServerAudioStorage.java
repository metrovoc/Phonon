package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.network.packets.AudioChunkPacket;

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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server-side audio file storage.
 * Downloads and stores OGG files for distribution via packets.
 */
public class ServerAudioStorage {

    private static ServerAudioStorage instance;

    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2);
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

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
            Phonon.LOGGER.info("Server audio storage initialized at {}", storageDir);
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to create audio storage directory", e);
        }
    }

    /**
     * Download audio from URL and store locally.
     * Returns the resource UUID on success.
     */
    public CompletableFuture<Boolean> downloadAndStore(UUID resourceId, String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path targetFile = storageDir.resolve(resourceId + ".ogg");

                Phonon.LOGGER.info("Downloading audio from {} to {}", url, targetFile);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofMinutes(5))
                    .build();

                HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
                );

                if (response.statusCode() == 200) {
                    Files.copy(response.body(), targetFile, StandardCopyOption.REPLACE_EXISTING);
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

    /**
     * Check if audio file exists in storage.
     */
    public boolean hasAudio(UUID resourceId) {
        if (storageDir == null) return false;
        return Files.exists(storageDir.resolve(resourceId + ".ogg"));
    }

    /**
     * Get audio file path.
     */
    public Optional<Path> getAudioPath(UUID resourceId) {
        if (storageDir == null) return Optional.empty();
        Path file = storageDir.resolve(resourceId + ".ogg");
        return Files.exists(file) ? Optional.of(file) : Optional.empty();
    }

    /**
     * Read audio file as bytes.
     */
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

    /**
     * Get audio file size in bytes.
     */
    public long getAudioSize(UUID resourceId) {
        return getAudioPath(resourceId).map(path -> {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return 0L;
            }
        }).orElse(0L);
    }

    /**
     * Calculate number of chunks needed for a file.
     */
    public int calculateChunkCount(UUID resourceId) {
        long size = getAudioSize(resourceId);
        return (int) Math.ceil((double) size / AudioChunkPacket.CHUNK_SIZE);
    }

    /**
     * Delete audio file.
     */
    public boolean deleteAudio(UUID resourceId) {
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
