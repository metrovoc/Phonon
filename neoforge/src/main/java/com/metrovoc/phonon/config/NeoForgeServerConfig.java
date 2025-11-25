package com.metrovoc.phonon.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * NeoForge server config using ModConfigSpec.
 */
public class NeoForgeServerConfig {

    public static final NeoForgeServerConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<NeoForgeServerConfig, ModConfigSpec> pair = new ModConfigSpec.Builder()
            .configure(NeoForgeServerConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    // Transfer settings
    public final ModConfigSpec.IntValue chunkSize;
    public final ModConfigSpec.IntValue maxBytesPerTick;
    public final ModConfigSpec.IntValue maxBytesPerPlayerPerTick;

    // Download settings
    public final ModConfigSpec.IntValue downloadConnectTimeoutSeconds;
    public final ModConfigSpec.IntValue downloadReadTimeoutSeconds;

    private NeoForgeServerConfig(ModConfigSpec.Builder builder) {
        builder.push("transfer");

        chunkSize = builder
            .comment("Audio chunk size in bytes. Larger = fewer packets, faster transfer.")
            .defineInRange("chunkSize",
                PhononServerConfig.DEFAULT_CHUNK_SIZE,
                64 * 1024,
                1024 * 1024);

        maxBytesPerTick = builder
            .comment("Max bytes/tick for all audio transfers combined.")
            .comment("Increase if players experience slow downloads.")
            .comment("Decrease if audio transfers affect game responsiveness.")
            .defineInRange("maxBytesPerTick",
                PhononServerConfig.DEFAULT_MAX_BYTES_PER_TICK,
                64 * 1024,
                4 * 1024 * 1024);

        maxBytesPerPlayerPerTick = builder
            .comment("Max bytes/tick per player. Ensures fair bandwidth sharing.")
            .defineInRange("maxBytesPerPlayerPerTick",
                PhononServerConfig.DEFAULT_MAX_BYTES_PER_PLAYER_PER_TICK,
                64 * 1024,
                2 * 1024 * 1024);

        builder.pop();

        builder.push("download");

        downloadConnectTimeoutSeconds = builder
            .comment("Connection timeout when downloading audio from URLs (seconds)")
            .defineInRange("connectTimeoutSeconds",
                PhononServerConfig.DEFAULT_DOWNLOAD_CONNECT_TIMEOUT,
                5,
                120);

        downloadReadTimeoutSeconds = builder
            .comment("Read timeout when downloading audio from URLs (seconds)")
            .defineInRange("readTimeoutSeconds",
                PhononServerConfig.DEFAULT_DOWNLOAD_READ_TIMEOUT,
                30,
                600);

        builder.pop();
    }

    /**
     * Wire config values to common config holders.
     * Called after config is loaded/reloaded.
     */
    public static void bind() {
        PhononServerConfig.setChunkSize(INSTANCE.chunkSize::get);
        PhononServerConfig.setMaxBytesPerTick(INSTANCE.maxBytesPerTick::get);
        PhononServerConfig.setMaxBytesPerPlayerPerTick(INSTANCE.maxBytesPerPlayerPerTick::get);
        PhononServerConfig.setDownloadConnectTimeoutSeconds(INSTANCE.downloadConnectTimeoutSeconds::get);
        PhononServerConfig.setDownloadReadTimeoutSeconds(INSTANCE.downloadReadTimeoutSeconds::get);
    }
}
