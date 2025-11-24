package com.tovkaic.phonon.client.audio;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.OggAudioStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;

public class PhononAudioStream implements AudioStream {
    private final OggAudioStream delegate;
    private final InputStream inputStream;

    public PhononAudioStream(Path file, long startOffsetMs) throws IOException {
        this.inputStream = Files.newInputStream(file);
        this.delegate = new OggAudioStream(inputStream);
        
        if (startOffsetMs > 0) {
            skipTo(startOffsetMs);
        }
    }

    private void skipTo(long offsetMs) throws IOException {
        AudioFormat format = delegate.getFormat();
        long bytesPerSecond = (long) (format.getSampleRate() * format.getFrameSize());
        long bytesToSkip = (bytesPerSecond * offsetMs) / 1000;
        
        // Read and discard
        int bufferSize = 4096;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        while (bytesToSkip > 0) {
            int toRead = (int) Math.min(bufferSize, bytesToSkip);
            // OggAudioStream.read expects a ByteBuffer
            // We need to verify if OggAudioStream.read returns the buffer with data
            // Actually, AudioStream.read(int) returns ByteBuffer.
            ByteBuffer read = delegate.read(toRead);
            if (read == null || read.remaining() == 0) break;
            bytesToSkip -= read.remaining();
        }
    }

    @Override
    public AudioFormat getFormat() {
        return delegate.getFormat();
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        return delegate.read(size);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
        inputStream.close();
    }
}
