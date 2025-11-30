package com.metrovoc.phonon.audio;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusException;
import io.github.jaredmdobson.concentus.OpusSignal;

/**
 * Wrapper for Concentus OpusEncoder.
 * Provides a simplified API for encoding PCM to Opus packets.
 *
 * Fixed at 48kHz sample rate (Opus native rate).
 */
public class OpusEncoder implements AutoCloseable {

    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_DURATION_MS = 20;
    public static final int FRAME_SIZE = SAMPLE_RATE * FRAME_DURATION_MS / 1000; // 960 samples

    private static final int MAX_PACKET_SIZE = 1275; // Maximum Opus packet size
    private static final int DEFAULT_BITRATE = 64000; // 64 kbps

    private final io.github.jaredmdobson.concentus.OpusEncoder encoder;
    private final int channels;
    private final byte[] encodeBuffer;
    private volatile boolean closed;

    public OpusEncoder(int channels) throws OpusException {
        this(channels, OpusApplication.OPUS_APPLICATION_AUDIO);
    }

    public OpusEncoder(int channels, OpusApplication application) throws OpusException {
        if (channels < 1 || channels > 2) {
            throw new IllegalArgumentException("Channels must be 1 or 2");
        }
        this.channels = channels;
        this.encoder = new io.github.jaredmdobson.concentus.OpusEncoder(SAMPLE_RATE, channels, application);
        this.encodeBuffer = new byte[MAX_PACKET_SIZE];
        this.closed = false;

        // Set defaults
        encoder.setBitrate(DEFAULT_BITRATE);
        encoder.setSignalType(OpusSignal.OPUS_SIGNAL_MUSIC);
    }

    /**
     * Encode PCM samples to an Opus packet.
     *
     * @param pcm       mono PCM samples (FRAME_SIZE samples)
     * @param output    output buffer for Opus data
     * @return number of bytes written to output
     */
    public int encode(short[] pcm, byte[] output) throws OpusException {
        return encode(pcm, 0, FRAME_SIZE, output, 0, output.length);
    }

    /**
     * Encode PCM samples to an Opus packet.
     *
     * @param pcm        PCM samples (interleaved if stereo)
     * @param pcmOffset  offset into pcm array
     * @param frameSize  number of samples per channel to encode
     * @param output     output buffer for Opus data
     * @param outOffset  offset into output buffer
     * @param outLen     maximum bytes to write
     * @return number of bytes written to output
     */
    public int encode(short[] pcm, int pcmOffset, int frameSize, byte[] output, int outOffset, int outLen) throws OpusException {
        if (closed) {
            throw new IllegalStateException("Encoder is closed");
        }

        return encoder.encode(pcm, pcmOffset, frameSize, output, outOffset, outLen);
    }

    /**
     * Encode a frame and return the Opus packet.
     *
     * @param pcm mono PCM samples (FRAME_SIZE samples)
     * @return Opus packet data (newly allocated array)
     */
    public byte[] encodeFrame(short[] pcm) throws OpusException {
        int len = encode(pcm, 0, FRAME_SIZE, encodeBuffer, 0, encodeBuffer.length);
        if (len <= 0) {
            return new byte[0];
        }
        byte[] result = new byte[len];
        System.arraycopy(encodeBuffer, 0, result, 0, len);
        return result;
    }

    /**
     * Set encoder bitrate in bits per second.
     */
    public void setBitrate(int bitrate) throws OpusException {
        encoder.setBitrate(bitrate);
    }

    /**
     * Get current bitrate.
     */
    public int getBitrate() {
        return encoder.getBitrate();
    }

    /**
     * Set complexity (0-10, higher = better quality but more CPU).
     */
    public void setComplexity(int complexity) throws OpusException {
        encoder.setComplexity(complexity);
    }

    /**
     * Enable/disable variable bitrate.
     */
    public void setVBR(boolean enabled) throws OpusException {
        encoder.setUseVBR(enabled);
    }

    /**
     * Reset encoder state.
     */
    public void reset() throws OpusException {
        encoder.resetState();
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
