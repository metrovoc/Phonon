package com.metrovoc.phonon.client.audio;

import io.github.jaredmdobson.concentus.OpusException;

/**
 * Wrapper for Concentus OpusDecoder.
 * Provides a simplified API for decoding Opus packets to PCM.
 *
 * Fixed at 48kHz sample rate (Opus native rate).
 * Supports both mono and stereo, outputs mono (downmixed if stereo).
 */
public class OpusDecoder implements AutoCloseable {

    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_DURATION_MS = 20;
    public static final int FRAME_SIZE = SAMPLE_RATE * FRAME_DURATION_MS / 1000; // 960 samples

    private final io.github.jaredmdobson.concentus.OpusDecoder decoder;
    private final int channels;
    private final short[] decodeBuffer;
    private volatile boolean closed;

    public OpusDecoder(int channels) throws OpusException {
        if (channels < 1 || channels > 2) {
            throw new IllegalArgumentException("Channels must be 1 or 2");
        }
        this.channels = channels;
        this.decoder = new io.github.jaredmdobson.concentus.OpusDecoder(SAMPLE_RATE, channels);
        this.decodeBuffer = new short[FRAME_SIZE * channels];
        this.closed = false;
    }

    /**
     * Decode an Opus packet to mono PCM samples.
     *
     * @param opusData encoded Opus packet
     * @param output   output buffer for mono PCM (must have room for FRAME_SIZE samples)
     * @return number of samples decoded
     */
    public int decode(byte[] opusData, short[] output) throws OpusException {
        return decode(opusData, 0, opusData.length, output, 0);
    }

    /**
     * Decode an Opus packet to mono PCM samples.
     *
     * @param opusData   encoded Opus packet
     * @param dataOffset offset into opusData
     * @param dataLen    length of opus data
     * @param output     output buffer for mono PCM
     * @param outOffset  offset into output buffer
     * @return number of samples decoded
     */
    public int decode(byte[] opusData, int dataOffset, int dataLen, short[] output, int outOffset) throws OpusException {
        if (closed) {
            throw new IllegalStateException("Decoder is closed");
        }

        int samples = decoder.decode(opusData, dataOffset, dataLen, decodeBuffer, 0, FRAME_SIZE, false);

        if (samples <= 0) {
            return 0;
        }

        if (channels == 1) {
            System.arraycopy(decodeBuffer, 0, output, outOffset, samples);
        } else {
            // Downmix stereo to mono
            for (int i = 0; i < samples; i++) {
                int left = decodeBuffer[i * 2];
                int right = decodeBuffer[i * 2 + 1];
                output[outOffset + i] = (short) ((left + right) / 2);
            }
        }

        return samples;
    }

    /**
     * Decode with packet loss concealment (PLC).
     * Call this when a packet is lost to generate concealment audio.
     *
     * @param output    output buffer for mono PCM
     * @param outOffset offset into output buffer
     * @return number of samples generated
     */
    public int decodePLC(short[] output, int outOffset) throws OpusException {
        if (closed) {
            throw new IllegalStateException("Decoder is closed");
        }

        int samples = decoder.decode(null, 0, 0, decodeBuffer, 0, FRAME_SIZE, true);

        if (samples <= 0) {
            return 0;
        }

        if (channels == 1) {
            System.arraycopy(decodeBuffer, 0, output, outOffset, samples);
        } else {
            for (int i = 0; i < samples; i++) {
                int left = decodeBuffer[i * 2];
                int right = decodeBuffer[i * 2 + 1];
                output[outOffset + i] = (short) ((left + right) / 2);
            }
        }

        return samples;
    }

    /**
     * Reset decoder state. Call after seek operations.
     */
    public void reset() {
        if (!closed) {
            decoder.resetState();
        }
    }

    public int getChannels() {
        return channels;
    }

    public int getSampleRate() {
        return SAMPLE_RATE;
    }

    @Override
    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}
