package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Phonon;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * OGG Vorbis audio stream using LWJGL's STBVorbis.
 *
 * CRITICAL: Always outputs MONO for 3D positioning (SPR compatibility).
 * Stereo files are automatically downmixed.
 */
public class PhononAudioStream implements AudioStream {
    private final long decoder;
    private final ByteBuffer fileBuffer;  // MUST keep reference for STBVorbis
    private final AudioFormat format;
    private final int channels;
    private final int sampleRate;
    private final int totalSamples;
    private int currentSample = 0;
    private boolean closed = false;

    public PhononAudioStream(Path oggFile) throws IOException {
        // Read entire file into memory (required by STBVorbis)
        try (FileChannel channel = FileChannel.open(oggFile, StandardOpenOption.READ)) {
            this.fileBuffer = MemoryUtil.memAlloc((int) channel.size());
            channel.read(fileBuffer);
            fileBuffer.flip();
        }

        // Initialize STBVorbis decoder
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            this.decoder = STBVorbis.stb_vorbis_open_memory(fileBuffer, error, null);

            if (decoder == 0) {
                MemoryUtil.memFree(fileBuffer);
                throw new IOException("Failed to open OGG file: error code " + error.get(0));
            }

            // Get audio info
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(decoder, info);

            this.channels = info.channels();
            this.sampleRate = info.sample_rate();
            this.totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);

            Phonon.LOGGER.info("Loaded OGG: {} channels, {}Hz, {} samples",
                channels, sampleRate, totalSamples);
        }

        // Output format: ALWAYS MONO, 16-bit signed
        this.format = new AudioFormat(
            sampleRate,
            16,
            1, // MONO (not stereo!)
            true,
            false
        );
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int bufferSize) throws IOException {
        if (closed) {
            return MemoryUtil.memAlloc(0);  // Empty direct buffer
        }

        // Calculate samples to read (2 bytes per sample)
        int samplesToRead = bufferSize / 2;

        ByteBuffer outputBuffer = MemoryUtil.memAlloc(samplesToRead * 2);  // MUST be direct memory
        ShortBuffer outputShorts = outputBuffer.asShortBuffer();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Decode interleaved samples
            ShortBuffer pcmBuffer = stack.mallocShort(samplesToRead * channels);

            int samplesRead = STBVorbis.stb_vorbis_get_samples_short_interleaved(
                decoder,
                channels,
                pcmBuffer
            );

            if (samplesRead == 0) {
                // End of stream - free allocated buffer and return empty direct buffer
                MemoryUtil.memFree(outputBuffer);
                return MemoryUtil.memAlloc(0);
            }

            // Downmix to mono if needed
            if (channels == 1) {
                // Already mono, direct copy
                for (int i = 0; i < samplesRead; i++) {
                    outputShorts.put(pcmBuffer.get(i));
                }
            } else {
                // Stereo to mono: average left and right
                for (int i = 0; i < samplesRead; i++) {
                    short left = pcmBuffer.get(i * 2);
                    short right = pcmBuffer.get(i * 2 + 1);

                    // Average and clamp
                    int mixed = (left + right) / 2;
                    outputShorts.put((short) Math.max(-32768, Math.min(32767, mixed)));
                }
            }

            currentSample += samplesRead;
        }

        outputBuffer.position(0);
        outputBuffer.limit(outputShorts.position() * 2);
        return outputBuffer;
    }

    /**
     * Seek to a specific sample position (for timestamp sync).
     */
    public void seek(int samplePosition) {
        if (!closed && samplePosition >= 0 && samplePosition < totalSamples) {
            STBVorbis.stb_vorbis_seek(decoder, samplePosition);
            currentSample = samplePosition;
        }
    }

    /**
     * Seek to a specific time in milliseconds.
     */
    public void seekMs(long milliseconds) {
        int samplePosition = (int) ((milliseconds * sampleRate) / 1000);
        seek(samplePosition);
    }

    public int getCurrentSample() {
        return currentSample;
    }

    public int getTotalSamples() {
        return totalSamples;
    }

    public long getDurationMs() {
        return (totalSamples * 1000L) / sampleRate;
    }

    @Override
    public void close() {
        if (!closed) {
            STBVorbis.stb_vorbis_close(decoder);
            MemoryUtil.memFree(fileBuffer);  // Release the file buffer
            closed = true;
        }
    }
}
