package com.tovkaic.phonon.client;

import com.tovkaic.phonon.Phonon;

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

    public void downloadAudio(UUID resourceId, String url) {
        if (cache.containsKey(resourceId)) {
            Phonon.LOGGER.info("Audio {} already cached", resourceId);
            return;
        }

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
                } else {
                    Phonon.LOGGER.error("Failed to download audio: HTTP {}", response.statusCode());
                }
            } catch (Exception e) {
                Phonon.LOGGER.error("Failed to download audio {}", resourceId, e);
            }
        });
    }

    public void shutdown() {
        downloadExecutor.shutdown();
    }
}
