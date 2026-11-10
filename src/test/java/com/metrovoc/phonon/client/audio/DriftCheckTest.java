package com.metrovoc.phonon.client.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DriftCheckTest {
    private static final long LONG_TRACK = 300_000;

    @Test
    void healthyStreamIsLeftAlone() {
        assertFalse(DriftCheck.needsResync(13_000, 9_000, LONG_TRACK, 200));
    }

    @Test
    void streamBehindTheClockResyncs() {
        assertTrue(DriftCheck.needsResync(9_000, 16_000, LONG_TRACK, 200));
    }

    @Test
    void streamFarAheadOfTheClockResyncs() {
        assertTrue(DriftCheck.needsResync(30_000, 9_000, LONG_TRACK, 200));
    }

    @Test
    void newStreamsGetTimeToFillTheirQueue() {
        assertFalse(DriftCheck.needsResync(0, 16_000, LONG_TRACK, 5));
    }

    @Test
    void finishedStreamsAreNotBehind() {
        assertFalse(DriftCheck.needsResync(299_500, 299_990, LONG_TRACK, 200));
    }
}
