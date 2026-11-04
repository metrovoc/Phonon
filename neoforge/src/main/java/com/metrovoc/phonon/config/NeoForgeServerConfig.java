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
    public final ModConfigSpec.IntValue maxAudioSizeMB;
    public final ModConfigSpec.IntValue audioQuality;

    // Tool settings
    public final ModConfigSpec.BooleanValue allowToolInstall;
    public final ModConfigSpec.BooleanValue autoUpdateYtDlp;
    public final ModConfigSpec.ConfigValue<String> downloadProxy;
    public final ModConfigSpec.ConfigValue<String> cookiesFile;
    public final ModConfigSpec.BooleanValue anyoneCanControlStations;

    // Permission settings
    public final ModConfigSpec.BooleanValue ownerOnlySpeakers;
    public final ModConfigSpec.BooleanValue anyoneCanAddTracks;

    private NeoForgeServerConfig(ModConfigSpec.Builder builder) {
        builder.push("transfer");

        chunkSize = builder
            .comment("Audio chunk size in bytes. Larger = fewer packets, faster transfer.")
            .defineInRange("chunkSize",
                PhononServerConfig.DEFAULT_CHUNK_SIZE,
                64 * 1024,
                512 * 1024);

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

        maxAudioSizeMB = builder
            .comment("Maximum stored size of one converted OGG file in megabytes.")
            .defineInRange("maxAudioSizeMB",
                PhononServerConfig.DEFAULT_MAX_AUDIO_SIZE_MB,
                16,
                PhononServerConfig.MAX_AUDIO_SIZE_MB);

        audioQuality = builder
            .comment("Vorbis quality for newly added audio, 0 (smallest) to 10 (best). Existing tracks keep theirs.")
            .defineInRange("audioQuality",
                PhononServerConfig.DEFAULT_AUDIO_QUALITY,
                0,
                10);

        builder.pop();

        builder.push("tools");

        allowToolInstall = builder
            .comment("Let operators install yt-dlp, FFmpeg and Deno into the game folder from an in-game prompt.")
            .define("allowToolInstall", true);

        autoUpdateYtDlp = builder
            .comment("Update the yt-dlp Phonon installed when the server starts, if it's over 30 days old. This runs newly downloaded code.")
            .define("autoUpdateYtDlp", false);

        downloadProxy = builder
            .comment("Send yt-dlp's traffic through this proxy, such as socks5://host:1080. Can help when a site blocks this server's address.")
            .define("downloadProxy", "");

        cookiesFile = builder
            .comment("A Netscape-format cookies file for yt-dlp, relative to the server folder. Keep it private. yt-dlp warns the account can be banned, so use a spare one.")
            .define("cookiesFile", "");

        builder.pop();

        builder.push("permissions");

        ownerOnlySpeakers = builder
            .comment("Only the player who placed a speaker, and operators, can control it. Older speakers stay open.")
            .define("ownerOnlySpeakers", false);

        anyoneCanAddTracks = builder
            .comment("Let every player add library tracks from the speaker screen, not only operators. Players can delete only what they added.")
            .define("anyoneCanAddTracks", false);

        anyoneCanControlStations = builder
            .comment("Let anyone tuned to a station change what it plays. Otherwise only its creator and operators can.")
            .define("anyoneCanControlStations", false);

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
        PhononServerConfig.setMaxAudioSizeMB(INSTANCE.maxAudioSizeMB::get);
        PhononServerConfig.setAudioQuality(INSTANCE.audioQuality::get);
        PhononServerConfig.setAllowToolInstall(INSTANCE.allowToolInstall::get);
        PhononServerConfig.setOwnerOnlySpeakers(INSTANCE.ownerOnlySpeakers::get);
        PhononServerConfig.setAnyoneCanAddTracks(INSTANCE.anyoneCanAddTracks::get);
        PhononServerConfig.setAnyoneCanControlStations(INSTANCE.anyoneCanControlStations::get);
        PhononServerConfig.setAutoUpdateYtDlp(INSTANCE.autoUpdateYtDlp::get);
        PhononServerConfig.setDownloadProxy(INSTANCE.downloadProxy::get);
        PhononServerConfig.setCookiesFile(INSTANCE.cookiesFile::get);
    }
}
