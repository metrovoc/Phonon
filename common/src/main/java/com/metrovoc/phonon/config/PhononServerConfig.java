package com.metrovoc.phonon.config;

import com.metrovoc.phonon.audio.AudioLimits;

import java.util.function.IntSupplier;

/**
 * Server-side configuration values.
 * Platform-specific code populates these suppliers.
 */
public final class PhononServerConfig {

    private PhononServerConfig() {}

    public static final int DEFAULT_CHUNK_SIZE = 256 * 1024;
    public static final int DEFAULT_MAX_BYTES_PER_TICK = 512 * 1024;
    public static final int DEFAULT_MAX_BYTES_PER_PLAYER_PER_TICK = 256 * 1024;
    public static final int DEFAULT_DOWNLOAD_CONNECT_TIMEOUT = 30;
    public static final int DEFAULT_DOWNLOAD_READ_TIMEOUT = 300;
    public static final int DEFAULT_MAX_AUDIO_SIZE_MB = 512;
    public static final int MAX_AUDIO_SIZE_MB = AudioLimits.MAX_AUDIO_SIZE_MB;

    // Transfer settings
    private static IntSupplier chunkSize = () -> DEFAULT_CHUNK_SIZE;
    private static IntSupplier maxBytesPerTick = () -> DEFAULT_MAX_BYTES_PER_TICK;
    private static IntSupplier maxBytesPerPlayerPerTick = () -> DEFAULT_MAX_BYTES_PER_PLAYER_PER_TICK;

    // Download settings
    private static IntSupplier downloadConnectTimeoutSeconds = () -> DEFAULT_DOWNLOAD_CONNECT_TIMEOUT;
    private static IntSupplier downloadReadTimeoutSeconds = () -> DEFAULT_DOWNLOAD_READ_TIMEOUT;
    private static IntSupplier maxAudioSizeMB = () -> DEFAULT_MAX_AUDIO_SIZE_MB;

    // Getters
    public static int getChunkSize() { return chunkSize.getAsInt(); }
    public static int getMaxBytesPerTick() { return maxBytesPerTick.getAsInt(); }
    public static int getMaxBytesPerPlayerPerTick() { return maxBytesPerPlayerPerTick.getAsInt(); }
    public static int getDownloadConnectTimeoutSeconds() { return downloadConnectTimeoutSeconds.getAsInt(); }
    public static int getDownloadReadTimeoutSeconds() { return downloadReadTimeoutSeconds.getAsInt(); }
    public static int getMaxAudioSizeMB() { return maxAudioSizeMB.getAsInt(); }

    // Setters (called by platform-specific config registration)
    public static void setChunkSize(IntSupplier supplier) { chunkSize = supplier; }
    public static void setMaxBytesPerTick(IntSupplier supplier) { maxBytesPerTick = supplier; }
    public static void setMaxBytesPerPlayerPerTick(IntSupplier supplier) { maxBytesPerPlayerPerTick = supplier; }
    public static void setDownloadConnectTimeoutSeconds(IntSupplier supplier) { downloadConnectTimeoutSeconds = supplier; }
    public static void setDownloadReadTimeoutSeconds(IntSupplier supplier) { downloadReadTimeoutSeconds = supplier; }
    public static void setMaxAudioSizeMB(IntSupplier supplier) { maxAudioSizeMB = supplier; }
}
