package com.metrovoc.phonon.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * NeoForge implementation of client config using ModConfigSpec.
 */
public class NeoForgeClientConfig {

    public static final NeoForgeClientConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<NeoForgeClientConfig, ModConfigSpec> pair = new ModConfigSpec.Builder()
            .configure(NeoForgeClientConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    // Cache settings
    public final ModConfigSpec.ConfigValue<String> cacheDirectory;
    public final ModConfigSpec.IntValue maxCacheSizeMB;

    // Debug settings
    public final ModConfigSpec.BooleanValue enableDebugLogging;

    private NeoForgeClientConfig(ModConfigSpec.Builder builder) {
        builder.push("cache");

        cacheDirectory = builder
            .comment("Directory name for cached audio files (relative to game directory)")
            .define("directory", PhononClientConfig.DEFAULT_CACHE_DIRECTORY);

        maxCacheSizeMB = builder
            .comment("Maximum cache size in megabytes")
            .comment("Set to 0 for unlimited (not recommended)")
            .comment("Oldest files are deleted when limit is exceeded")
            .defineInRange("maxSizeMB",
                PhononClientConfig.DEFAULT_MAX_CACHE_SIZE_MB,
                0,
                10240);

        builder.pop();

        builder.push("debug");

        enableDebugLogging = builder
            .comment("Enable verbose debug logging")
            .define("enableLogging", PhononClientConfig.DEFAULT_ENABLE_DEBUG_LOGGING);

        builder.pop();
    }

    /**
     * Wire config values to common config holders.
     * Called after config is loaded/reloaded.
     */
    public static void bind() {
        PhononClientConfig.setCacheDirectory(INSTANCE.cacheDirectory::get);
        PhononClientConfig.setMaxCacheSizeMB(INSTANCE.maxCacheSizeMB::get);
        PhononClientConfig.setEnableDebugLogging(INSTANCE.enableDebugLogging::get);
    }
}
