package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Phonon;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;

/**
 * OGG Vorbis audio stream using LWJGL's STBVorbis.
 *
 * CRITICAL: Always outputs MONO for 3D positioning (SPR compatibility).
 * Stereo files are automatically downmixed.
 */
public class PhononAudioStream implements AudioStream {
    private final long decoder;
    private final ByteBuffer fileBuffer;
    private final SharedOggFile sharedFile;
    private final AudioFormat format;
    private final int channels;
    private final int sampleRate;
    private final int totalSamples;
    private ShortBuffer pcmScratch;
    private boolean closed = false;
    private StreamTap tap;

    public PhononAudioStream(Path oggFile) throws IOException {
        this.sharedFile = SharedOggFile.acquire(oggFile);
        this.fileBuffer = sharedFile.decoderView();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            this.decoder = STBVorbis.stb_vorbis_open_memory(fileBuffer, error, null);

            if (decoder == 0) {
                sharedFile.release();
                throw new IOException("Failed to open OGG file: error code " + error.get(0));
            }

            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(decoder, info);

            this.channels = info.channels();
            this.sampleRate = info.sample_rate();
            this.totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);

            Phonon.LOGGER.debug("Loaded OGG: {} channels, {}Hz, {} samples",
                channels, sampleRate, totalSamples);
        }

        this.format = new AudioFormat(
            sampleRate,
            16,
            1, // MONO
            true,
            false
        );
        this.pcmScratch = channels > 1 ? MemoryUtil.memAllocShort(8192 * channels) : null;
        this.tap = new StreamTap(sampleRate, 0);
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int bufferSize) throws IOException {
        if (closed) {
            return BufferUtils.createByteBuffer(0);
        }

        int samplesToRead = bufferSize / 2;

        // SoundBuffer drops PCM after upload; returned buffers must own a GC cleaner.
        ByteBuffer outputBuffer = BufferUtils.createByteBuffer(samplesToRead * 2);
        ShortBuffer outputShorts = outputBuffer.asShortBuffer();

        ShortBuffer pcmBuffer = channels == 1 ? outputShorts : ensurePcmScratch(samplesToRead * channels);
        int samplesRead = STBVorbis.stb_vorbis_get_samples_short_interleaved(
            decoder,
            channels,
            pcmBuffer
        );

        if (samplesRead == 0) {
            outputBuffer.limit(0);
            return outputBuffer;
        }

        if (channels == 1) {
            outputShorts.position(samplesRead);
        } else {
            for (int i = 0; i < samplesRead; i++) {
                int sum = 0;
                for (int channel = 0; channel < channels; channel++) {
                    sum += pcmBuffer.get(i * channels + channel);
                }
                outputShorts.put((short) (sum / channels));
            }
        }

        tap.record(outputShorts, 0, outputShorts.position());
        outputBuffer.position(0);
        outputBuffer.limit(outputShorts.position() * 2);
        return outputBuffer;
    }

    private ShortBuffer ensurePcmScratch(int requiredSamples) {
        if (pcmScratch.capacity() < requiredSamples) {
            MemoryUtil.memFree(pcmScratch);
            pcmScratch = MemoryUtil.memAllocShort(requiredSamples);
        }
        pcmScratch.clear();
        pcmScratch.limit(requiredSamples);
        return pcmScratch;
    }

    public void seek(int samplePosition) {
        if (!closed && samplePosition >= 0 && samplePosition < totalSamples) {
            STBVorbis.stb_vorbis_seek(decoder, samplePosition);
        }
    }

    public StreamTap tap() {
        return tap;
    }

    public void seekMs(long milliseconds) {
        tap = new StreamTap(sampleRate, Math.max(0, milliseconds));
        if (milliseconds <= 0) {
            seek(0);
            return;
        }
        long samplePosition = milliseconds > Long.MAX_VALUE / sampleRate
            ? Long.MAX_VALUE
            : milliseconds * sampleRate / 1000L;
        seek((int) Math.min(Math.max(0, totalSamples - 1L), samplePosition));
    }

    @Override
    public void close() {
        if (!closed) {
            STBVorbis.stb_vorbis_close(decoder);
            sharedFile.release();
            if (pcmScratch != null) {
                MemoryUtil.memFree(pcmScratch);
                pcmScratch = null;
            }
            closed = true;
        }
    }
}
