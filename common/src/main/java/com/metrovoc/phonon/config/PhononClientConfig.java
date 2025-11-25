package com.metrovoc.phonon.config;

import java.util.function.BooleanSupplier;
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
    public static final boolean DEFAULT_ENABLE_DEBUG_LOGGING = false;

    // Cache settings
    private static Supplier<String> cacheDirectory = () -> DEFAULT_CACHE_DIRECTORY;
    private static IntSupplier maxCacheSizeMB = () -> DEFAULT_MAX_CACHE_SIZE_MB;

    // Debug settings
    private static BooleanSupplier enableDebugLogging = () -> DEFAULT_ENABLE_DEBUG_LOGGING;

    // Getters
    public static String getCacheDirectory() { return cacheDirectory.get(); }
    public static int getMaxCacheSizeMB() { return maxCacheSizeMB.getAsInt(); }
    public static boolean isDebugLoggingEnabled() { return enableDebugLogging.getAsBoolean(); }

    // Setters (called by platform-specific config registration)
    public static void setCacheDirectory(Supplier<String> supplier) { cacheDirectory = supplier; }
    public static void setMaxCacheSizeMB(IntSupplier supplier) { maxCacheSizeMB = supplier; }
    public static void setEnableDebugLogging(BooleanSupplier supplier) { enableDebugLogging = supplier; }
}
