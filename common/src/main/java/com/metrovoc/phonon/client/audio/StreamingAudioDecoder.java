package com.metrovoc.phonon.client.audio;

import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.locks.ReentrantLock;

public class StreamingAudioDecoder implements AutoCloseable {
    private static final int INITIAL_BUFFER_SIZE = 64 * 1024;
    private static final int MIN_DATA_FOR_PLAYBACK = 16 * 1024;
    private static final int MAX_VORBIS_FRAME_SAMPLES = 8192;

    private final ReentrantLock lock = new ReentrantLock();

    private long decoderHandle;
    private ByteBuffer accumBuffer;
    private ShortBuffer pendingPcm;
    private int accumSize;
    private int consumedTotal;

    private int sampleRate;
    private int channels;
    private boolean headerParsed;
    private boolean closed;

    public StreamingAudioDecoder() {
        this.accumBuffer = MemoryUtil.memAlloc(INITIAL_BUFFER_SIZE);
        this.pendingPcm = MemoryUtil.memAllocShort(MAX_VORBIS_FRAME_SAMPLES);
        this.pendingPcm.limit(0);
        this.accumSize = 0;
        this.consumedTotal = 0;
        this.headerParsed = false;
        this.closed = false;
    }

    public boolean pushData(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return false;
        }

        lock.lock();
        try {
            if (closed) {
                return false;
            }

            ensureCapacity(chunk.length);
            accumBuffer.position(accumSize);
            accumBuffer.put(chunk);
            accumSize += chunk.length;

            if (!headerParsed) {
                return tryParseHeader();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    private void ensureCapacity(int additionalBytes) {
        if (consumedTotal > 0 && accumSize + additionalBytes > accumBuffer.capacity()) {
            compactBuffer();
        }

        int required = accumSize + additionalBytes;
        if (required > accumBuffer.capacity()) {
            int newCapacity = Math.max(accumBuffer.capacity() * 2, required);
            ByteBuffer newBuffer = MemoryUtil.memAlloc(newCapacity);
            accumBuffer.position(0);
            accumBuffer.limit(accumSize);
            newBuffer.put(accumBuffer);
            MemoryUtil.memFree(accumBuffer);
            accumBuffer = newBuffer;
        }
        // Always reset limit to capacity for subsequent writes
        accumBuffer.limit(accumBuffer.capacity());
    }

    private boolean tryParseHeader() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer consumed = stack.mallocInt(1);
            IntBuffer error = stack.mallocInt(1);

            accumBuffer.position(0);
            accumBuffer.limit(accumSize);

            long handle = STBVorbis.stb_vorbis_open_pushdata(accumBuffer, consumed, error, null);

            if (handle == 0) {
                int err = error.get(0);
                return err == STBVorbis.VORBIS_need_more_data;
            }

            this.decoderHandle = handle;
            this.consumedTotal = consumed.get(0);
            this.headerParsed = true;

            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(decoderHandle, info);
            this.sampleRate = info.sample_rate();
            this.channels = info.channels();

            compactBuffer();
            return true;
        }
    }

    private void compactBuffer() {
        int remaining = accumSize - consumedTotal;
        if (remaining > 0 && consumedTotal > 0) {
            accumBuffer.position(consumedTotal);
            accumBuffer.limit(accumSize);
            accumBuffer.compact();
        }
        accumSize = remaining;
        consumedTotal = 0;
        accumBuffer.position(accumSize);
        accumBuffer.limit(accumBuffer.capacity());
    }

    /**
     * Decodes directly into the caller's PCM buffer, avoiding an intermediate
     * native allocation and copy for every OpenAL buffer fill.
     */
    public int decode(ShortBuffer output) {
        lock.lock();
        try {
            if (closed || !headerParsed || decoderHandle == 0 || !output.hasRemaining()) {
                return 0;
            }

            int totalDecoded = 0;
            totalDecoded += drainPending(output);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer channelsOut = stack.mallocInt(1);
                IntBuffer samplesOut = stack.mallocInt(1);
                PointerBuffer outputPtr = stack.mallocPointer(1);

                while (output.hasRemaining()) {
                    int availableData = accumSize - consumedTotal;
                    if (availableData <= 0) {
                        break;
                    }

                    accumBuffer.position(consumedTotal);
                    accumBuffer.limit(accumSize);

                    int consumed = STBVorbis.stb_vorbis_decode_frame_pushdata(
                            decoderHandle, accumBuffer, channelsOut, outputPtr, samplesOut);

                    if (consumed == 0) {
                        break;
                    }

                    consumedTotal += consumed;
                    int samples = samplesOut.get(0);

                    if (samples > 0) {
                        int chans = channelsOut.get(0);
                        long outputAddr = outputPtr.get(0);

                        if (samples <= output.remaining()) {
                            writeMonoSamples(output, outputAddr, chans, samples);
                            totalDecoded += samples;
                        } else {
                            ensurePendingCapacity(samples);
                            pendingPcm.clear();
                            writeMonoSamples(pendingPcm, outputAddr, chans, samples);
                            pendingPcm.flip();
                            totalDecoded += drainPending(output);
                        }
                    }
                }
            }

            if (consumedTotal > INITIAL_BUFFER_SIZE / 2) {
                compactBuffer();
            }

            return totalDecoded;
        } finally {
            lock.unlock();
        }
    }

    private int drainPending(ShortBuffer output) {
        int count = Math.min(output.remaining(), pendingPcm.remaining());
        if (count == 0) {
            return 0;
        }

        int originalLimit = pendingPcm.limit();
        pendingPcm.limit(pendingPcm.position() + count);
        output.put(pendingPcm);
        pendingPcm.limit(originalLimit);
        return count;
    }

    private void ensurePendingCapacity(int samples) {
        if (pendingPcm.capacity() >= samples) {
            return;
        }
        MemoryUtil.memFree(pendingPcm);
        pendingPcm = MemoryUtil.memAllocShort(samples);
    }

    private void writeMonoSamples(ShortBuffer output, long channelArrayAddr, int chans, int samples) {
        PointerBuffer channelPtrs = MemoryUtil.memPointerBuffer(channelArrayAddr, chans);
        long ch0Addr = channelPtrs.get(0);
        FloatBuffer channel0 = MemoryUtil.memFloatBuffer(ch0Addr, samples);

        if (chans == 1) {
            for (int i = 0; i < samples; i++) {
                output.put(floatToShort(channel0.get(i)));
            }
        } else if (chans == 2) {
            long ch1Addr = channelPtrs.get(1);
            FloatBuffer channel1 = MemoryUtil.memFloatBuffer(ch1Addr, samples);
            for (int i = 0; i < samples; i++) {
                float mixed = (channel0.get(i) + channel1.get(i)) * 0.5f;
                output.put(floatToShort(mixed));
            }
        } else {
            for (int i = 0; i < samples; i++) {
                float mixed = 0.0f;
                for (int channel = 0; channel < chans; channel++) {
                    mixed += MemoryUtil.memGetFloat(channelPtrs.get(channel) + (long) i * Float.BYTES);
                }
                output.put(floatToShort(mixed / chans));
            }
        }
    }

    private short floatToShort(float f) {
        int val = (int) (f * 32767.0f);
        return (short) Math.max(-32768, Math.min(32767, val));
    }

    public boolean hasEnoughData() {
        lock.lock();
        try {
            return headerParsed && (accumSize - consumedTotal) >= MIN_DATA_FOR_PLAYBACK;
        } finally {
            lock.unlock();
        }
    }

    public boolean isHeaderParsed() {
        lock.lock();
        try {
            return headerParsed;
        } finally {
            lock.unlock();
        }
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
    }

    public int getBufferedBytes() {
        lock.lock();
        try {
            return accumSize - consumedTotal;
        } finally {
            lock.unlock();
        }
    }

    public void flush() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            if (decoderHandle != 0) {
                STBVorbis.stb_vorbis_flush_pushdata(decoderHandle);
            }
            consumedTotal = 0;
            accumSize = 0;
            pendingPcm.clear();
            pendingPcm.limit(0);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;

            if (decoderHandle != 0) {
                STBVorbis.stb_vorbis_close(decoderHandle);
                decoderHandle = 0;
            }

            if (accumBuffer != null) {
                MemoryUtil.memFree(accumBuffer);
                accumBuffer = null;
            }

            if (pendingPcm != null) {
                MemoryUtil.memFree(pendingPcm);
                pendingPcm = null;
            }
        } finally {
            lock.unlock();
        }
    }
}
