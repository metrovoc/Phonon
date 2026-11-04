package com.metrovoc.phonon.client.audio;

import java.nio.ShortBuffer;

/**
 * Watches the mono samples a stream hands to the sound engine: how far it has written, and how loud each slice was.
 * Written on the sound thread and read on the client thread, so readers may see a slice a moment late.
 */
public final class StreamTap {
    public static final int SLICE_MS = 50;
    private static final int SLICES = 400;

    private final float[] levels = new float[SLICES];
    private final long[] slicePositions = new long[SLICES];
    private final int sampleRate;
    private final long startMs;
    private volatile long samplesWritten;
    private long sliceSquares;
    private int sliceSamples;
    private int cursor;

    public StreamTap(int sampleRate, long startMs) {
        this.sampleRate = Math.max(1, sampleRate);
        this.startMs = startMs;
        java.util.Arrays.fill(slicePositions, -1);
    }

    /** Position in the track up to which audio has been handed to the sound engine. */
    public long writtenMs() {
        return startMs + samplesWritten * 1000L / sampleRate;
    }

    public void record(ShortBuffer mono, int offset, int count) {
        int samplesPerSlice = Math.max(1, sampleRate * SLICE_MS / 1000);
        long written = samplesWritten;
        for (int i = 0; i < count; i++) {
            int sample = mono.get(offset + i);
            sliceSquares += (long) sample * sample;
            written++;
            if (++sliceSamples == samplesPerSlice) {
                slicePositions[cursor] = startMs + (written - samplesPerSlice) * 1000L / sampleRate;
                levels[cursor] = level(sliceSquares, sliceSamples);
                cursor = (cursor + 1) % SLICES;
                sliceSquares = 0;
                sliceSamples = 0;
            }
        }
        samplesWritten = written;
    }

    /** Loudness from 0 to 1 at a position in the track, or 0 if that slice is unknown. */
    public float levelAt(long positionMs) {
        for (int i = 0; i < SLICES; i++) {
            long start = slicePositions[i];
            if (start >= 0 && positionMs >= start && positionMs < start + SLICE_MS) {
                return levels[i];
            }
        }
        return 0;
    }

    /** Fills bars with the loudness of the most recent slices up to a position, newest last. */
    public void history(long positionMs, float[] bars, int msPerBar) {
        for (int i = 0; i < bars.length; i++) {
            bars[i] = levelAt(positionMs - (bars.length - 1 - i) * (long) msPerBar);
        }
    }

    /** Maps RMS to a perceptual 0 to 1 over a 48 dB range. */
    static float level(long squares, int samples) {
        double rms = Math.sqrt((double) squares / samples) / 32768.0;
        if (rms <= 0) {
            return 0;
        }
        double db = 20 * Math.log10(rms);
        return (float) Math.max(0, Math.min(1, 1 + db / 48));
    }
}
