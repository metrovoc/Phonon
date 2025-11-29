package com.metrovoc.phonon.client.audio;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class StreamingAudioStream implements AudioStream {
    private final StreamingAudioDecoder decoder;
    private final AudioFormat format;
    private boolean closed = false;

    public StreamingAudioStream(StreamingAudioDecoder decoder, int sampleRate) {
        this.decoder = decoder;
        this.format = new AudioFormat(sampleRate, 16, 1, true, false);
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int bufferSize) {
        if (closed) {
            return MemoryUtil.memAlloc(0);
        }

        int samplesToRead = bufferSize / 2;
        ShortBuffer decoded = decoder.decode(samplesToRead);

        if (decoded == null) {
            return MemoryUtil.memAlloc(0);
        }

        int decodedSamples = decoded.remaining();
        ByteBuffer output = MemoryUtil.memAlloc(decodedSamples * 2);
        output.asShortBuffer().put(decoded);
        output.position(0);
        output.limit(decodedSamples * 2);

        MemoryUtil.memFree(decoded);
        return output;
    }

    @Override
    public void close() {
        closed = true;
    }
}
