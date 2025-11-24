package com.tovkaic.phonon.audio;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Playback state synchronized across server and clients.
 * Contains everything needed to know "what's playing and where".
 */
public record PlaybackState(
    @Nullable UUID resourceId,  // null when stopped
    long startTimeMs,
    float volume,
    boolean playing
) {
    public static final PlaybackState STOPPED = new PlaybackState(
        null, 0, 0, false
    );

    /**
     * Calculate current playback position based on current time.
     */
    public long getCurrentPositionMs(long currentTimeMs) {
        if (!playing) return 0;
        return currentTimeMs - startTimeMs;
    }
}
