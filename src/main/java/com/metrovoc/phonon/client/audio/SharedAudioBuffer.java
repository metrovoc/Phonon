package com.metrovoc.phonon.client.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * One-writer, many-reader segmented audio buffer.
 *
 * <p>Incoming packet arrays are retained instead of copied. Callers therefore
 * transfer ownership of appended arrays and must not mutate them afterwards.</p>
 */
public final class SharedAudioBuffer {
    private final List<byte[]> segments = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Condition dataAvailable = lock.writeLock().newCondition();

    private int[] segmentEnds = new int[16];
    private int totalBytes;
    private boolean complete;
    private byte[] headerData;
    private int sampleRate;
    private long streamStartPositionMs;

    public void append(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }

        lock.writeLock().lock();
        try {
            if (complete) {
                return;
            }

            int newTotal = Math.addExact(totalBytes, data.length);
            ensureIndexCapacity(segments.size() + 1);
            segments.add(data);
            totalBytes = newTotal;
            segmentEnds[segments.size() - 1] = newTotal;
            dataAvailable.signalAll();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void ensureIndexCapacity(int required) {
        if (required <= segmentEnds.length) {
            return;
        }

        int newCapacity = Math.max(required, segmentEnds.length << 1);
        int[] expanded = new int[newCapacity];
        System.arraycopy(segmentEnds, 0, expanded, 0, segmentEnds.length);
        segmentEnds = expanded;
    }

    public void setHeader(byte[] header, int sampleRate, long streamStartPositionMs) {
        lock.writeLock().lock();
        try {
            this.headerData = header;
            this.sampleRate = sampleRate;
            this.streamStartPositionMs = Math.max(0, streamStartPositionMs);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean markComplete() {
        lock.writeLock().lock();
        try {
            if (complete) {
                return false;
            }
            complete = true;
            dataAvailable.signalAll();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Reads up to {@code length} bytes at an absolute stream-buffer offset.
     */
    public int read(int globalOffset, byte[] destination, int destinationOffset, int length) {
        if (globalOffset < 0 || destinationOffset < 0 || length < 0
            || destinationOffset > destination.length
            || length > destination.length - destinationOffset) {
            throw new IndexOutOfBoundsException();
        }

        lock.readLock().lock();
        try {
            return readLocked(globalOffset, destination, destinationOffset, length);
        } finally {
            lock.readLock().unlock();
        }
    }

    private int readLocked(int globalOffset, byte[] destination, int destinationOffset, int length) {
        if (globalOffset >= totalBytes || length == 0) {
            return 0;
        }

        int available = Math.min(length, totalBytes - globalOffset);
        int segmentIndex = findSegment(globalOffset);
        int copied = 0;

        while (copied < available && segmentIndex < segments.size()) {
            int segmentStart = segmentIndex == 0 ? 0 : segmentEnds[segmentIndex - 1];
            byte[] segment = segments.get(segmentIndex);
            int offsetInSegment = globalOffset + copied - segmentStart;
            int copyLength = Math.min(segment.length - offsetInSegment, available - copied);
            System.arraycopy(segment, offsetInSegment, destination, destinationOffset + copied, copyLength);
            copied += copyLength;
            segmentIndex++;
        }

        return copied;
    }

    private int findSegment(int offset) {
        int low = 0;
        int high = segments.size() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (segmentEnds[middle] <= offset) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /**
     * Waits briefly for producer progress to avoid returning an artificial EOF
     * to Minecraft's streaming sound channel during a network jitter window.
     */
    public boolean awaitDataAfter(int offset, long timeout, TimeUnit unit) throws InterruptedException {
        long remaining = unit.toNanos(timeout);
        lock.writeLock().lockInterruptibly();
        try {
            while (totalBytes <= offset && !complete && remaining > 0) {
                remaining = dataAvailable.awaitNanos(remaining);
            }
            return totalBytes > offset;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public byte[] getHeader() {
        lock.readLock().lock();
        try {
            return headerData;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getSampleRate() {
        lock.readLock().lock();
        try {
            return sampleRate;
        } finally {
            lock.readLock().unlock();
        }
    }

    public long getStreamStartPositionMs() {
        lock.readLock().lock();
        try {
            return streamStartPositionMs;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getTotalBytes() {
        lock.readLock().lock();
        try {
            return totalBytes;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isComplete() {
        lock.readLock().lock();
        try {
            return complete;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasHeader() {
        lock.readLock().lock();
        try {
            return headerData != null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean hasEnoughData(int minBytesAfterHeader) {
        lock.readLock().lock();
        try {
            if (headerData == null) {
                return false;
            }
            int audioBytes = totalBytes - headerData.length;
            return audioBytes >= minBytesAfterHeader || complete && audioBytes > 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Writes a completed snapshot without first assembling a second full-size
     * heap array.
     */
    public void writeTo(Path file) throws IOException {
        byte[][] snapshot;
        lock.readLock().lock();
        try {
            if (!complete) {
                throw new IllegalStateException("Cannot persist an incomplete audio buffer");
            }
            snapshot = segments.toArray(byte[][]::new);
        } finally {
            lock.readLock().unlock();
        }

        try (FileChannel channel = FileChannel.open(
            file,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )) {
            for (byte[] segment : snapshot) {
                ByteBuffer source = ByteBuffer.wrap(segment);
                while (source.hasRemaining()) {
                    channel.write(source);
                }
            }
            channel.force(false);
        }
    }

    public byte[] toByteArray() {
        lock.readLock().lock();
        try {
            byte[] result = new byte[totalBytes];
            readLocked(0, result, 0, totalBytes);
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
}
