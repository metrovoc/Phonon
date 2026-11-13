package com.metrovoc.phonon.config;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Client-side configuration values.
 * Platform-specific code populates these suppliers.
 */
public final class PhononClientConfig {

    private PhononClientConfig() {}

    // Defaults
    public static final String DEFAULT_CACHE_DIRECTORY = "phonon_cache";
    public static final int DEFAULT_MAX_CACHE_SIZE_MB = 512;

    // Cache settings
    private static Supplier<String> cacheDirectory = () -> DEFAULT_CACHE_DIRECTORY;
    private static IntSupplier maxCacheSizeMB = () -> DEFAULT_MAX_CACHE_SIZE_MB;

    private static ClientConfigStore store = (directory, cacheSizeMB) -> {};

    // Getters
    public static String getCacheDirectory() { return cacheDirectory.get(); }
    public static int getMaxCacheSizeMB() { return maxCacheSizeMB.getAsInt(); }

    // Setters (called by platform-specific config registration)
    public static void setCacheDirectory(Supplier<String> supplier) { cacheDirectory = supplier; }
    public static void setMaxCacheSizeMB(IntSupplier supplier) { maxCacheSizeMB = supplier; }

    /** How the loader saves settings changed on the config screen. */
    public static void setStore(ClientConfigStore configStore) { store = configStore; }

    public static void save(String directory, int cacheSizeMB) {
        store.save(directory, cacheSizeMB);
    }
}
