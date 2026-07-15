package com.metrovoc.phonon.client.audio;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Independent decoder view over a shared network download.
 */
public final class StreamingAudioStream implements AudioStream {
    private static final int READ_CHUNK_SIZE = 16 * 1024;
    private static final int DISCARD_BUFFER_SAMPLES = 8192;
    private static final long STARVATION_WAIT_MS = 100;

    private final SharedAudioBuffer buffer;
    private final AudioFormat format;
    private final ShortBuffer discardBuffer = MemoryUtil.memAllocShort(DISCARD_BUFFER_SAMPLES);
    private final StreamingAudioDecoder decoder = new StreamingAudioDecoder();

    private int readCursor;
    private long samplesToSkip;
    private boolean closed;

    public StreamingAudioStream(SharedAudioBuffer buffer, long targetPositionMs) {
        this.buffer = buffer;

        byte[] header = buffer.getHeader();
        if (header == null) {
            close();
            throw new IllegalStateException("Buffer has no OGG header");
        }

        decoder.pushData(header);
        int sampleRate = decoder.isHeaderParsed() ? decoder.getSampleRate() : buffer.getSampleRate();
        if (sampleRate <= 0) {
            close();
            throw new IllegalStateException("Invalid OGG sample rate");
        }

        format = new AudioFormat(sampleRate, 16, 1, true, false);
        readCursor = header.length;

        long skipMs = Math.max(0, targetPositionMs - buffer.getStreamStartPositionMs());
        samplesToSkip = skipMs > Long.MAX_VALUE / sampleRate
            ? Long.MAX_VALUE
            : skipMs * sampleRate / 1000L;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int bufferSize) {
        if (closed || bufferSize <= 0) {
            return MemoryUtil.memAlloc(0);
        }

        int sampleCapacity = Math.max(1, bufferSize / Short.BYTES);
        ByteBuffer output = MemoryUtil.memAlloc(sampleCapacity * Short.BYTES)
            .order(ByteOrder.nativeOrder());
        ShortBuffer pcm = output.asShortBuffer();

        fill(pcm);

        int decodedSamples = pcm.position();
        if (decodedSamples == 0) {
            MemoryUtil.memFree(output);
            return MemoryUtil.memAlloc(0);
        }

        output.position(0);
        output.limit(decodedSamples * Short.BYTES);
        return output;
    }

    private void fill(ShortBuffer output) {
        while (output.hasRemaining() && !closed) {
            int decoded = samplesToSkip > 0 ? discardSamples() : decoder.decode(output);
            if (decoded > 0) {
                continue;
            }
            if (!feedDecoder()) {
                return;
            }
        }
    }

    private int discardSamples() {
        discardBuffer.clear();
        discardBuffer.limit((int) Math.min(discardBuffer.capacity(), samplesToSkip));
        int decoded = decoder.decode(discardBuffer);
        samplesToSkip -= decoded;
        return decoded;
    }

    private boolean feedDecoder() {
        int available = buffer.getTotalBytes() - readCursor;
        if (available <= 0 && !buffer.isComplete()) {
            try {
                buffer.awaitDataAfter(readCursor, STARVATION_WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            available = buffer.getTotalBytes() - readCursor;
        }

        if (available <= 0) {
            return false;
        }

        int length = Math.min(available, READ_CHUNK_SIZE);
        byte[] data = new byte[length];
        int bytesRead = buffer.read(readCursor, data, 0, length);
        if (bytesRead <= 0) {
            return false;
        }

        if (bytesRead != data.length) {
            byte[] exact = new byte[bytesRead];
            System.arraycopy(data, 0, exact, 0, bytesRead);
            data = exact;
        }

        decoder.pushData(data);
        readCursor += bytesRead;
        return true;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        decoder.close();
        MemoryUtil.memFree(discardBuffer);
    }

    public boolean isClosed() {
        return closed;
    }
}
