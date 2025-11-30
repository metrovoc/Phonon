package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Phonon;
import net.minecraft.client.sounds.AudioStream;
import io.github.jaredmdobson.concentus.OpusException;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/**
 * AudioStream that reads from PacketBuffer and decodes Opus packets.
 * Outputs mono 16-bit PCM at 48kHz.
 */
public class OpusAudioStream implements AudioStream {

    private static final int BUFFER_SAMPLES = OpusDecoder.FRAME_SIZE * 4; // ~80ms of audio

    private final PacketBuffer packetBuffer;
    private final AudioFormat format;
    private final int channels;

    private OpusDecoder decoder;
    private final short[] decodeOutput;
    private final short[] pcmBuffer;
    private int pcmBufferPos;
    private int pcmBufferLen;

    private volatile boolean closed = false;
    private int consecutiveLostPackets = 0;
    private static final int MAX_CONSECUTIVE_LOST = 10;

    public OpusAudioStream(PacketBuffer packetBuffer, int channels) {
        this.packetBuffer = packetBuffer;
        this.channels = channels;
        this.format = new AudioFormat(OpusDecoder.SAMPLE_RATE, 16, 1, true, false);
        this.decodeOutput = new short[OpusDecoder.FRAME_SIZE];
        this.pcmBuffer = new short[BUFFER_SAMPLES];
        this.pcmBufferPos = 0;
        this.pcmBufferLen = 0;

        try {
            this.decoder = new OpusDecoder(channels);
        } catch (OpusException e) {
            Phonon.LOGGER.error("Failed to create Opus decoder", e);
            this.decoder = null;
        }
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int bufferSize) {
        if (closed || decoder == null) {
            return MemoryUtil.memAlloc(0);
        }

        int samplesToRead = bufferSize / 2;
        short[] outputBuffer = new short[samplesToRead];
        int samplesRead = 0;

        while (samplesRead < samplesToRead) {
            // First, drain from pcm buffer
            if (pcmBufferPos < pcmBufferLen) {
                int available = pcmBufferLen - pcmBufferPos;
                int toCopy = Math.min(available, samplesToRead - samplesRead);

                // Actually copy the data
                System.arraycopy(pcmBuffer, pcmBufferPos, outputBuffer, samplesRead, toCopy);
                samplesRead += toCopy;
                pcmBufferPos += toCopy;
                continue;
            }

            // Buffer exhausted, decode more
            if (!fillBuffer()) {
                break;
            }
        }

        if (samplesRead == 0) {
            return MemoryUtil.memAlloc(0);
        }

        // Assemble output
        ByteBuffer output = MemoryUtil.memAlloc(samplesRead * 2);
        output.asShortBuffer().put(outputBuffer, 0, samplesRead);
        output.position(0);
        output.limit(samplesRead * 2);

        return output;
    }

    private boolean fillBuffer() {
        if (!packetBuffer.isStarted()) {
            if (packetBuffer.isReadyToStart()) {
                packetBuffer.start();
                Phonon.LOGGER.info("Stream playback started, buffered={}", packetBuffer.getBufferedCount());
            } else {
                return false;
            }
        }

        // Check status before polling to distinguish underflow from loss
        PacketBuffer.PollResult status = packetBuffer.checkPollStatus();

        if (status == PacketBuffer.PollResult.BUFFER_EMPTY) {
            // Underflow: caught up to network, data hasn't arrived yet
            // Use PLC to fill audio but DON'T count as loss
            if (packetBuffer.isFinished()) {
                return false;
            }
            try {
                int decoded = decoder.decodePLC(decodeOutput, 0);
                if (decoded > 0) {
                    System.arraycopy(decodeOutput, 0, pcmBuffer, 0, decoded);
                    pcmBufferPos = 0;
                    pcmBufferLen = decoded;
                    return true;
                }
            } catch (OpusException e) {
                // Ignore PLC errors during underflow
            }
            return false;
        }

        // DATA_AVAILABLE or PACKET_LOSS - proceed with poll
        byte[] opusData = packetBuffer.poll();

        try {
            int decoded;
            if (opusData != null) {
                decoded = decoder.decode(opusData, decodeOutput);
                consecutiveLostPackets = 0;
            } else {
                // True packet loss (gap in sequence)
                consecutiveLostPackets++;
                if (consecutiveLostPackets > MAX_CONSECUTIVE_LOST) {
                    Phonon.LOGGER.warn("Too many consecutive lost packets ({}), stopping", consecutiveLostPackets);
                    return false;
                }
                decoded = decoder.decodePLC(decodeOutput, 0);
            }

            if (decoded > 0) {
                System.arraycopy(decodeOutput, 0, pcmBuffer, 0, decoded);
                pcmBufferPos = 0;
                pcmBufferLen = decoded;
                return true;
            }
        } catch (OpusException e) {
            Phonon.LOGGER.warn("Opus decode error: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Reset decoder state (call after seek).
     */
    public void resetDecoder() {
        if (decoder != null) {
            decoder.reset();
        }
        pcmBufferPos = 0;
        pcmBufferLen = 0;
        consecutiveLostPackets = 0;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (decoder != null) {
                decoder.close();
                decoder = null;
            }
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public PacketBuffer getPacketBuffer() {
        return packetBuffer;
    }
}
