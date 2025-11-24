package com.tovkaic.phonon.audio;

import java.util.UUID;

/**
 * Playback state synchronized across server and clients.
 * Contains everything needed to know "what's playing and where".
 */
public record PlaybackState(
    UUID resourceId,
    long startTimeMs,
    float volume,
    boolean playing
) {
    public static final PlaybackState STOPPED = new PlaybackState(
        UUID.randomUUID(), 0, 0, false
    );

    /**
     * Calculate current playback position based on current time.
     */
    public long getCurrentPositionMs(long currentTimeMs) {
        if (!playing) return 0;
        return currentTimeMs - startTimeMs;
    }
}
