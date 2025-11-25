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

    // WebUI settings
    public final ModConfigSpec.BooleanValue webUiEnabled;
    public final ModConfigSpec.IntValue webUiPort;
    public final ModConfigSpec.ConfigValue<String> webUiToken;

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

        builder.push("webui");

        webUiEnabled = builder
            .comment("Enable the WebUI for managing audio tracks.")
            .comment("WARNING: The WebUI binds to 0.0.0.0 and is accessible from any network interface.")
            .comment("If your server is exposed to the internet, ensure you set a strong token below,")
            .comment("or use a firewall to restrict access to trusted IPs only.")
            .define("enabled", PhononServerConfig.DEFAULT_WEBUI_ENABLED);

        webUiPort = builder
            .comment("Port for the WebUI. If in use, will try incrementing ports up to +10.")
            .defineInRange("port",
                PhononServerConfig.DEFAULT_WEBUI_PORT,
                1024,
                65535);

        webUiToken = builder
            .comment("Authentication token for WebUI API access.")
            .comment("Leave empty to disable authentication (not recommended for public servers).")
            .comment("Clients must send 'Authorization: Bearer <token>' header.")
            .define("token", PhononServerConfig.DEFAULT_WEBUI_TOKEN);

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
        PhononServerConfig.setWebUiEnabled(INSTANCE.webUiEnabled::get);
        PhononServerConfig.setWebUiPort(INSTANCE.webUiPort::get);
        PhononServerConfig.setWebUiToken(INSTANCE.webUiToken::get);
    }
}
