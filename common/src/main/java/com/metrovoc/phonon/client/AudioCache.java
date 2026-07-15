package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.config.PhononClientConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side audio cache.
 * Stores OGG files received via packet transfer.
 */
public class AudioCache {

    private static final AudioCache instance = new AudioCache();
    private final Map<UUID, Path> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAccess = new ConcurrentHashMap<>();
    private Path cacheDir;

    private AudioCache() {}

    public static AudioCache getInstance() {
        return instance;
    }

    public void initialize(Path gameDir) {
        this.cacheDir = resolveCacheDirectory(gameDir, PhononClientConfig.getCacheDirectory());
        try {
            Files.createDirectories(cacheDir);
            loadCacheIndex();
            StreamingAudioManager.getInstance().setCacheDir(cacheDir);
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to initialize audio cache", e);
        }
    }

    private static Path resolveCacheDirectory(Path gameDir, String configuredDirectory) {
        Path gameRoot = gameDir.toAbsolutePath().normalize();
        try {
            Path configured = Path.of(configuredDirectory);
            Path resolved = gameRoot.resolve(configured).normalize();
            if (!configured.isAbsolute() && !resolved.equals(gameRoot) && resolved.startsWith(gameRoot)) {
                return resolved;
            }
        } catch (InvalidPathException ignored) {
        }

        Phonon.LOGGER.warn(
            "Ignoring unsafe cache directory '{}'; using the default",
            configuredDirectory
        );
        return gameRoot.resolve(PhononClientConfig.DEFAULT_CACHE_DIRECTORY);
    }

    private void loadCacheIndex() {
        cache.clear();
        lastAccess.clear();
        try (var files = Files.list(cacheDir)) {
            files.forEach(path -> {
                String filename = path.getFileName().toString();
                if (filename.contains(".ogg.part-")) {
                    deleteQuietly(path);
                    return;
                }
                if (!filename.endsWith(".ogg")) {
                    return;
                }

                try {
                    UUID id = UUID.fromString(filename.substring(0, filename.length() - 4));
                    cache.put(id, path);
                    lastAccess.put(id, getLastModified(path));
                } catch (IllegalArgumentException ignored) {
                }
            });
            enforceSizeLimit();
            Phonon.LOGGER.info("Loaded {} cached audio files", cache.size());
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to load cache index", e);
        }
    }

    public Optional<Path> getCachedAudio(UUID resourceId) {
        Path path = cache.get(resourceId);
        if (path == null) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(path)) {
            cache.remove(resourceId, path);
            lastAccess.remove(resourceId);
            return Optional.empty();
        }
        lastAccess.put(resourceId, System.currentTimeMillis());
        return Optional.of(path);
    }

    public void registerCachedFile(UUID resourceId, Path file) {
        cache.put(resourceId, file);
        lastAccess.put(resourceId, System.currentTimeMillis());
        enforceSizeLimit();
    }

    public boolean isCached(UUID resourceId) {
        return getCachedAudio(resourceId).isPresent();
    }

    private synchronized void enforceSizeLimit() {
        int maxSizeMb = PhononClientConfig.getMaxCacheSizeMB();
        if (maxSizeMb <= 0) {
            return;
        }

        long maxBytes = maxSizeMb * 1024L * 1024L;
        long totalBytes = 0;
        List<CachedFile> files = new ArrayList<>();

        for (Map.Entry<UUID, Path> entry : cache.entrySet()) {
            try {
                long size = Files.size(entry.getValue());
                totalBytes += size;
                files.add(new CachedFile(
                    entry.getKey(),
                    entry.getValue(),
                    size,
                    lastAccess.getOrDefault(entry.getKey(), getLastModified(entry.getValue()))
                ));
            } catch (IOException e) {
                cache.remove(entry.getKey(), entry.getValue());
                lastAccess.remove(entry.getKey());
            }
        }

        if (totalBytes <= maxBytes) {
            return;
        }

        files.sort(Comparator.comparingLong(CachedFile::lastAccessMs));
        for (CachedFile file : files) {
            if (totalBytes <= maxBytes) {
                break;
            }
            if (deleteQuietly(file.path())) {
                cache.remove(file.id(), file.path());
                lastAccess.remove(file.id());
                totalBytes -= file.size();
            }
        }
    }

    private static long getLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static boolean deleteQuietly(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            Phonon.LOGGER.warn("Failed to delete cache file {}", path, e);
            return false;
        }
    }

    private record CachedFile(UUID id, Path path, long size, long lastAccessMs) {}
}
