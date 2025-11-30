package com.metrovoc.phonon.client.audio;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

/**
 * AudioStream backed by SharedAudioBuffer.
 * Each instance owns its own decoder and read cursor.
 * Multiple streams can read from the same buffer independently.
 */
public class StreamingAudioStream implements AudioStream {
    private static final int READ_CHUNK_SIZE = 4096;
    private static final int BYTES_PER_MS = 48; // Approximate: 48kHz mono

    private final SharedAudioBuffer buffer;
    private final AudioFormat format;

    private StreamingAudioDecoder decoder;
    private int readCursor;
    private boolean closed = false;

    public StreamingAudioStream(SharedAudioBuffer buffer, long startPositionMs) {
        this.buffer = buffer;
        this.decoder = new StreamingAudioDecoder();

        byte[] header = buffer.getHeader();
        if (header == null) {
            throw new IllegalStateException("Buffer has no header");
        }

        decoder.pushData(header);

        int sampleRate = decoder.isHeaderParsed() ? decoder.getSampleRate() : buffer.getSampleRate();
        this.format = new AudioFormat(sampleRate, 16, 1, true, false);

        if (startPositionMs > 0) {
            seekTo(startPositionMs);
        } else {
            this.readCursor = header.length;
        }
    }

    public void seekTo(long positionMs) {
        byte[] header = buffer.getHeader();
        if (header == null) return;

        // Estimate byte offset (rough approximation)
        int sampleRate = buffer.getSampleRate();
        int bytesPerSecond = sampleRate > 0 ? sampleRate / 8 : 6000; // ~48kbps for vorbis
        int approxOffset = header.length + (int)(positionMs * bytesPerSecond / 1000);

        // Find nearest OGG page boundary
        int pageOffset = buffer.findPageOffset(approxOffset);
        if (pageOffset < 0 || pageOffset < header.length) {
            pageOffset = header.length;
        }

        // Rebuild decoder
        decoder.close();
        decoder = new StreamingAudioDecoder();
        decoder.pushData(header);

        this.readCursor = pageOffset;
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

        // Feed more data to decoder if available
        feedDecoder();

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

    private void feedDecoder() {
        int available = buffer.getTotalBytes() - readCursor;
        if (available <= 0) return;

        int toRead = Math.min(available, READ_CHUNK_SIZE);
        byte[] data = new byte[toRead];
        int bytesRead = buffer.read(readCursor, data, 0, toRead);

        if (bytesRead > 0) {
            if (bytesRead < data.length) {
                data = Arrays.copyOf(data, bytesRead);
            }
            decoder.pushData(data);
            readCursor += bytesRead;
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            decoder.close();
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
