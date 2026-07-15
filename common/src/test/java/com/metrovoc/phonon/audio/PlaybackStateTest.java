package com.metrovoc.phonon.audio;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackStateTest {
    private static final UUID RESOURCE_ID = UUID.fromString("a17a0be0-bfe8-4ef9-9fa5-0fb4f50a4104");

    @Test
    void calculatesPositionFromAMonotonicAnchor() {
        PlaybackState state = new PlaybackState(RESOURCE_ID, 1_000, 500, 1.0f);

        assertEquals(1_000, state.getCurrentPositionMs(1_500));
        assertEquals(0, state.getCurrentPositionMs(0));
        assertTrue(state.isPlaying());
    }

    @Test
    void sanitizesInvalidSpeedsAndSaturatesOverflow() {
        PlaybackState invalid = new PlaybackState(RESOURCE_ID, 0, 10, Float.NaN);
        PlaybackState excessive = new PlaybackState(RESOURCE_ID, 0, Long.MAX_VALUE - 10, Float.MAX_VALUE);

        assertTrue(invalid.isPaused());
        assertEquals(10, invalid.getCurrentPositionMs(5_000));
        assertEquals(16.0f, excessive.speed());
        assertEquals(Long.MAX_VALUE, excessive.getCurrentPositionMs(1));
    }
}
