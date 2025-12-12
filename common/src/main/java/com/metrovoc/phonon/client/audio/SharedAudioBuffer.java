package com.metrovoc.phonon.client.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe buffer for streaming audio data.
 * Supports one writer (download thread) and multiple readers (decoder streams).
 * Each reader maintains its own cursor position.
 */
public class SharedAudioBuffer {
    private static final int CHUNK_SIZE = 16 * 1024;
    private static final byte[] OGG_MAGIC = {'O', 'g', 'g', 'S'};

    private final List<byte[]> chunks = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private int totalBytes = 0;
    private boolean complete = false;

    private byte[] headerData = null;
    private int sampleRate = 0;

    public void append(byte[] data) {
        if (data == null || data.length == 0) return;

        lock.writeLock().lock();
        try {
            int offset = 0;
            while (offset < data.length) {
                int lastChunkSpace = getLastChunkSpace();
                if (lastChunkSpace > 0) {
                    byte[] lastChunk = chunks.get(chunks.size() - 1);
                    int lastChunkUsed = CHUNK_SIZE - lastChunkSpace;
                    int toCopy = Math.min(lastChunkSpace, data.length - offset);
                    System.arraycopy(data, offset, lastChunk, lastChunkUsed, toCopy);
                    offset += toCopy;
                    totalBytes += toCopy;
                } else {
                    byte[] newChunk = new byte[CHUNK_SIZE];
                    int toCopy = Math.min(CHUNK_SIZE, data.length - offset);
                    System.arraycopy(data, offset, newChunk, 0, toCopy);
                    chunks.add(newChunk);
                    offset += toCopy;
                    totalBytes += toCopy;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private int getLastChunkSpace() {
        if (chunks.isEmpty()) return 0;
        int usedInLast = totalBytes % CHUNK_SIZE;
        if (usedInLast == 0 && totalBytes > 0) return 0;
        return CHUNK_SIZE - usedInLast;
    }

    public void setHeader(byte[] header, int sampleRate) {
        lock.writeLock().lock();
        try {
            this.headerData = header;
            this.sampleRate = sampleRate;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void markComplete() {
        lock.writeLock().lock();
        try {
            complete = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Read data from buffer at specified offset.
     * @return actual bytes read
     */
    public int read(int globalOffset, byte[] dest, int destOffset, int length) {
        lock.readLock().lock();
        try {
            if (globalOffset >= totalBytes) return 0;

            int available = Math.min(length, totalBytes - globalOffset);
            int read = 0;

            while (read < available) {
                int currentGlobalPos = globalOffset + read;
                int chunkIndex = currentGlobalPos / CHUNK_SIZE;
                int offsetInChunk = currentGlobalPos % CHUNK_SIZE;

                if (chunkIndex >= chunks.size()) break;

                byte[] chunk = chunks.get(chunkIndex);
                int bytesInThisChunk;
                if (chunkIndex == chunks.size() - 1) {
                    int usedInLast = totalBytes % CHUNK_SIZE;
                    if (usedInLast == 0 && totalBytes > 0) usedInLast = CHUNK_SIZE;
                    bytesInThisChunk = usedInLast - offsetInChunk;
                } else {
                    bytesInThisChunk = CHUNK_SIZE - offsetInChunk;
                }

                int toCopy = Math.min(bytesInThisChunk, available - read);
                System.arraycopy(chunk, offsetInChunk, dest, destOffset + read, toCopy);
                read += toCopy;
            }

            return read;
        } finally {
            lock.readLock().unlock();
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

    /**
     * Check if buffer has enough data for playback to start.
     * Requires header + minimum audio data beyond header.
     */
    public boolean hasEnoughData(int minBytesAfterHeader) {
        lock.readLock().lock();
        try {
            if (headerData == null) return false;
            int dataAfterHeader = totalBytes - headerData.length;
            return dataAfterHeader >= minBytesAfterHeader;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Find nearest OGG page boundary at or before the given offset.
     * Returns -1 if no page found.
     */
    public int findPageOffset(int approximateOffset) {
        lock.readLock().lock();
        try {
            if (totalBytes == 0) return -1;

            int searchStart = Math.min(approximateOffset, totalBytes - 4);
            if (searchStart < 0) searchStart = 0;

            // Search backwards for "OggS" magic
            byte[] window = new byte[Math.min(8192, searchStart + 4)];
            int windowStart = Math.max(0, searchStart - window.length + 4);
            int bytesRead = read(windowStart, window, 0, window.length);

            for (int i = bytesRead - 4; i >= 0; i--) {
                if (window[i] == OGG_MAGIC[0] &&
                    window[i + 1] == OGG_MAGIC[1] &&
                    window[i + 2] == OGG_MAGIC[2] &&
                    window[i + 3] == OGG_MAGIC[3]) {
                    return windowStart + i;
                }
            }

            return -1;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all data as byte array (for caching).
     */
    public byte[] toByteArray() {
        lock.readLock().lock();
        try {
            byte[] result = new byte[totalBytes];
            read(0, result, 0, totalBytes);
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
}
