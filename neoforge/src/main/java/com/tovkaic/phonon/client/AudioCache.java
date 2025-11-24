package com.tovkaic.phonon.client;

import com.tovkaic.phonon.Phonon;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client-side audio cache.
 * Downloads and stores OGG files permanently (MVP: no cleanup).
 */
public class AudioCache {

    /**
     * Callback interface for async download operations.
     */
    public interface DownloadCallback {
        void onComplete(UUID resourceId, Path file);
        void onError(UUID resourceId, Exception e);
    }

    private static AudioCache instance;
    private final Map<UUID, Path> cache = new ConcurrentHashMap<>();
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private Path cacheDir;

    private AudioCache() {}

    public static AudioCache getInstance() {
        if (instance == null) {
            instance = new AudioCache();
        }
        return instance;
    }

    public void initialize(Path gameDir) {
        this.cacheDir = gameDir.resolve("phonon_cache");
        try {
            Files.createDirectories(cacheDir);
            loadCacheIndex();
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to initialize audio cache", e);
        }
    }

    private void loadCacheIndex() {
        try {
            if (Files.exists(cacheDir)) {
                Files.list(cacheDir)
                    .filter(p -> p.toString().endsWith(".ogg"))
                    .forEach(p -> {
                        String filename = p.getFileName().toString();
                        UUID id = UUID.fromString(filename.replace(".ogg", ""));
                        cache.put(id, p);
                    });
                Phonon.LOGGER.info("Loaded {} cached audio files", cache.size());
            }
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to load cache index", e);
        }
    }

    public Optional<Path> getCachedAudio(UUID resourceId) {
        return Optional.ofNullable(cache.get(resourceId));
    }

    /**
     * Download audio file (legacy method without callback).
     * @deprecated Use {@link #downloadAudio(UUID, String, DownloadCallback)} instead.
     */
    @Deprecated
    public void downloadAudio(UUID resourceId, String url) {
        downloadAudio(resourceId, url, null);
    }

    /**
     * Download audio file with callback.
     * If already cached, callback fires immediately.
     *
     * @param resourceId Audio resource ID
     * @param url Download URL
     * @param callback Completion callback (may be null)
     */
    public void downloadAudio(UUID resourceId, String url, DownloadCallback callback) {
        // Already cached - invoke callback immediately
        if (cache.containsKey(resourceId)) {
            Phonon.LOGGER.info("Audio {} already cached", resourceId);
            if (callback != null) {
                // Callback must run on main thread
                Minecraft.getInstance().tell(() -> callback.onComplete(resourceId, cache.get(resourceId)));
            }
            return;
        }

        // Download in background thread
        downloadExecutor.submit(() -> {
            try {
                Path targetFile = cacheDir.resolve(resourceId + ".ogg");
                Phonon.LOGGER.info("Downloading audio from {}", url);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

                HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
                );

                if (response.statusCode() == 200) {
                    Files.copy(response.body(), targetFile, StandardCopyOption.REPLACE_EXISTING);
                    cache.put(resourceId, targetFile);
                    Phonon.LOGGER.info("Downloaded audio {} to cache", resourceId);

                    if (callback != null) {
                        // Callback must run on main thread
                        Minecraft.getInstance().tell(() -> callback.onComplete(resourceId, targetFile));
                    }
                } else {
                    Phonon.LOGGER.error("Failed to download audio: HTTP {}", response.statusCode());
                    if (callback != null) {
                        Exception error = new IOException("HTTP " + response.statusCode());
                        Minecraft.getInstance().tell(() -> callback.onError(resourceId, error));
                    }
                }
            } catch (Exception e) {
                Phonon.LOGGER.error("Failed to download audio {}", resourceId, e);
                if (callback != null) {
                    Minecraft.getInstance().tell(() -> callback.onError(resourceId, e));
                }
            }
        });
    }

    public void shutdown() {
        downloadExecutor.shutdown();
    }
}
