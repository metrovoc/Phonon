package com.metrovoc.phonon.audio;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Playback state synchronized across server and clients.
 * Contains what's playing and when it started. Volume is separate (per-speaker property).
 */
public record PlaybackState(
    @Nullable UUID resourceId,  // null when stopped
    long startTimeMs,
    boolean playing
) {
    public static final PlaybackState STOPPED = new PlaybackState(null, 0, false);

    /**
     * Calculate current playback position based on current time.
     */
    public long getCurrentPositionMs(long currentTimeMs) {
        if (!playing) return 0;
        return currentTimeMs - startTimeMs;
    }
}
