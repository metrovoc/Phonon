package com.metrovoc.phonon.audio;

/** Shared bounds for stored audio and the network streaming protocol. */
public final class AudioLimits {
    public static final int MAX_HEADER_BYTES = 256 * 1024;
    public static final int MAX_CHUNK_BYTES = 512 * 1024;
    public static final int MAX_RESOURCE_COUNT = 10_000;
    public static final int MAX_RESOURCE_NAME_CHARS = 256;
    public static final int MAX_RESOURCE_URL_CHARS = 8_192;
    public static final int MAX_SAMPLE_RATE = 384_000;
    public static final int MAX_AUDIO_SIZE_MB = 2_047;

    private AudioLimits() {}
}
