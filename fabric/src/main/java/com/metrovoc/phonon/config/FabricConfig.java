package com.metrovoc.phonon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.metrovoc.phonon.Phonon;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phonon's settings on Fabric, as JSON files in the config folder with the same sections, keys and limits as
 * NeoForge's TOML files. Missing or invalid values fall back to defaults and are written back.
 */
public final class FabricConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FabricConfig() {}

    private static Path file(String name) {
        return FabricLoader.getInstance().getConfigDir().resolve(name);
    }

    public static void loadServer() {
        Path path = file("phonon-server.json");
        Values v = new Values(read(path));

        int chunkSize = v.integer("transfer", "chunkSize", PhononServerConfig.DEFAULT_CHUNK_SIZE, 64 * 1024, 512 * 1024);
        int maxBytesPerTick = v.integer("transfer", "maxBytesPerTick", PhononServerConfig.DEFAULT_MAX_BYTES_PER_TICK, 64 * 1024, 4 * 1024 * 1024);
        int maxBytesPerPlayerPerTick = v.integer("transfer", "maxBytesPerPlayerPerTick", PhononServerConfig.DEFAULT_MAX_BYTES_PER_PLAYER_PER_TICK, 64 * 1024, 2 * 1024 * 1024);
        int connectTimeout = v.integer("download", "connectTimeoutSeconds", PhononServerConfig.DEFAULT_DOWNLOAD_CONNECT_TIMEOUT, 5, 120);
        int readTimeout = v.integer("download", "readTimeoutSeconds", PhononServerConfig.DEFAULT_DOWNLOAD_READ_TIMEOUT, 30, 600);
        int maxAudioSize = v.integer("download", "maxAudioSizeMB", PhononServerConfig.DEFAULT_MAX_AUDIO_SIZE_MB, 16, PhononServerConfig.MAX_AUDIO_SIZE_MB);
        int audioQuality = v.integer("download", "audioQuality", PhononServerConfig.DEFAULT_AUDIO_QUALITY, 0, 10);
        boolean allowToolInstall = v.bool("tools", "allowToolInstall", true);
        boolean autoUpdateYtDlp = v.bool("tools", "autoUpdateYtDlp", false);
        String downloadProxy = v.string("tools", "downloadProxy", "");
        String cookiesFile = v.string("tools", "cookiesFile", "");
        boolean ownerOnlySpeakers = v.bool("permissions", "ownerOnlySpeakers", false);
        boolean anyoneCanAddTracks = v.bool("permissions", "anyoneCanAddTracks", false);
        boolean anyoneCanControlStations = v.bool("permissions", "anyoneCanControlStations", false);

        PhononServerConfig.setChunkSize(() -> chunkSize);
        PhononServerConfig.setMaxBytesPerTick(() -> maxBytesPerTick);
        PhononServerConfig.setMaxBytesPerPlayerPerTick(() -> maxBytesPerPlayerPerTick);
        PhononServerConfig.setDownloadConnectTimeoutSeconds(() -> connectTimeout);
        PhononServerConfig.setDownloadReadTimeoutSeconds(() -> readTimeout);
        PhononServerConfig.setMaxAudioSizeMB(() -> maxAudioSize);
        PhononServerConfig.setAudioQuality(() -> audioQuality);
        PhononServerConfig.setAllowToolInstall(() -> allowToolInstall);
        PhononServerConfig.setAutoUpdateYtDlp(() -> autoUpdateYtDlp);
        PhononServerConfig.setDownloadProxy(() -> downloadProxy);
        PhononServerConfig.setCookiesFile(() -> cookiesFile);
        PhononServerConfig.setOwnerOnlySpeakers(() -> ownerOnlySpeakers);
        PhononServerConfig.setAnyoneCanAddTracks(() -> anyoneCanAddTracks);
        PhononServerConfig.setAnyoneCanControlStations(() -> anyoneCanControlStations);

        write(path, v.root);
    }

    public static void loadClient() {
        Path path = file("phonon-client.json");
        Values v = new Values(read(path));
        apply(v.string("cache", "directory", PhononClientConfig.DEFAULT_CACHE_DIRECTORY),
            v.integer("cache", "maxSizeMB", PhononClientConfig.DEFAULT_MAX_CACHE_SIZE_MB, 0, 10240),
            v.decimal("audio", "maxDistance", PhononClientConfig.DEFAULT_MAX_AUDIO_DISTANCE, 16.0, 256.0));
        write(path, v.root);
    }

    /** Saves settings from the config screen. */
    public static void saveClient(String cacheDirectory, int maxCacheSizeMB, double maxAudioDistance) {
        JsonObject root = new JsonObject();
        JsonObject cache = new JsonObject();
        cache.addProperty("directory", cacheDirectory);
        cache.addProperty("maxSizeMB", maxCacheSizeMB);
        JsonObject audio = new JsonObject();
        audio.addProperty("maxDistance", maxAudioDistance);
        root.add("cache", cache);
        root.add("audio", audio);
        write(file("phonon-client.json"), root);
        apply(cacheDirectory, maxCacheSizeMB, maxAudioDistance);
    }

    private static void apply(String cacheDirectory, int maxCacheSizeMB, double maxAudioDistance) {
        PhononClientConfig.setCacheDirectory(() -> cacheDirectory);
        PhononClientConfig.setMaxCacheSizeMB(() -> maxCacheSizeMB);
        PhononClientConfig.setMaxAudioDistance(() -> maxAudioDistance);
    }

    private static JsonObject read(Path path) {
        if (!Files.exists(path)) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (IOException | RuntimeException e) {
            Phonon.LOGGER.warn("Couldn't read {}, using defaults", path, e);
            return new JsonObject();
        }
    }

    private static void write(Path path, JsonObject root) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root) + "\n");
        } catch (IOException e) {
            Phonon.LOGGER.warn("Couldn't write {}", path, e);
        }
    }

    /** Reads values from sections, replacing missing or invalid ones with defaults in place. */
    private record Values(JsonObject root) {
        private JsonObject section(String name) {
            if (root.get(name) instanceof JsonObject section) {
                return section;
            }
            JsonObject section = new JsonObject();
            root.add(name, section);
            return section;
        }

        int integer(String section, String key, int fallback, int min, int max) {
            JsonObject s = section(section);
            int value = fallback;
            try {
                if (s.has(key)) {
                    value = s.get(key).getAsInt();
                }
            } catch (RuntimeException ignored) {
            }
            value = value < min || value > max ? fallback : value;
            s.addProperty(key, value);
            return value;
        }

        double decimal(String section, String key, double fallback, double min, double max) {
            JsonObject s = section(section);
            double value = fallback;
            try {
                if (s.has(key)) {
                    value = s.get(key).getAsDouble();
                }
            } catch (RuntimeException ignored) {
            }
            value = !Double.isFinite(value) || value < min || value > max ? fallback : value;
            s.addProperty(key, value);
            return value;
        }

        boolean bool(String section, String key, boolean fallback) {
            JsonObject s = section(section);
            boolean value = fallback;
            try {
                if (s.has(key) && s.get(key).getAsJsonPrimitive().isBoolean()) {
                    value = s.get(key).getAsBoolean();
                }
            } catch (RuntimeException ignored) {
            }
            s.addProperty(key, value);
            return value;
        }

        String string(String section, String key, String fallback) {
            JsonObject s = section(section);
            String value = fallback;
            try {
                if (s.has(key) && s.get(key).getAsJsonPrimitive().isString()) {
                    value = s.get(key).getAsString();
                }
            } catch (RuntimeException ignored) {
            }
            s.addProperty(key, value);
            return value;
        }
    }
}
