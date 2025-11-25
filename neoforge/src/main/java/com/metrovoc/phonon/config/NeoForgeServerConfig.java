package com.metrovoc.phonon.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * NeoForge implementation of server config using ModConfigSpec.
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
            .comment("Size of each audio chunk in bytes when transferring to clients")
            .comment("Larger chunks = fewer packets but more memory per packet")
            .defineInRange("chunkSize",
                PhononServerConfig.DEFAULT_CHUNK_SIZE,
                8 * 1024,
                128 * 1024);

        maxBytesPerTick = builder
            .comment("Maximum total bytes to send per server tick across all players")
            .comment("At 20 TPS: 128KB/tick = ~2.5 MB/s, 256KB/tick = ~5 MB/s")
            .defineInRange("maxBytesPerTick",
                PhononServerConfig.DEFAULT_MAX_BYTES_PER_TICK,
                32 * 1024,
                1024 * 1024);

        maxBytesPerPlayerPerTick = builder
            .comment("Maximum bytes to send per player per tick")
            .comment("Prevents one player from consuming all bandwidth")
            .defineInRange("maxBytesPerPlayerPerTick",
                PhononServerConfig.DEFAULT_MAX_BYTES_PER_PLAYER_PER_TICK,
                16 * 1024,
                512 * 1024);

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
