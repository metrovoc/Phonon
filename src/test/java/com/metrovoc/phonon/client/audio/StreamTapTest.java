package com.metrovoc.phonon.client.audio;

import org.junit.jupiter.api.Test;

import java.nio.ShortBuffer;

import static org.junit.jupiter.api.Assertions.*;

class StreamTapTest {
    private static ShortBuffer constant(int samples, short value) {
        ShortBuffer buffer = ShortBuffer.allocate(samples);
        for (int i = 0; i < samples; i++) {
            buffer.put(i, value);
        }
        return buffer;
    }

    @Test
    void writtenPositionCountsFromTheStart() {
        StreamTap tap = new StreamTap(1000, 5000);
        tap.record(constant(250, (short) 100), 0, 250);
        assertEquals(5250, tap.writtenMs());
    }

    @Test
    void loudnessIsFoundByTrackPosition() {
        StreamTap tap = new StreamTap(1000, 0);
        tap.record(constant(50, (short) 0), 0, 50);
        tap.record(constant(50, (short) 32000), 0, 50);
        assertEquals(0, tap.levelAt(25));
        assertTrue(tap.levelAt(75) > 0.9f);
        assertEquals(0, tap.levelAt(5000));
    }

    @Test
    void historyEndsAtTheRequestedPosition() {
        StreamTap tap = new StreamTap(1000, 0);
        tap.record(constant(100, (short) 32000), 0, 100);
        float[] bars = new float[4];
        tap.history(75, bars, 50);
        assertEquals(0, bars[0]);
        assertTrue(bars[2] > 0.9f && bars[3] > 0.9f);
    }
}
