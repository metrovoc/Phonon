package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.client.audio.SharedAudioBuffer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages audio download for a single resource.
 * Acts as producer: receives network data and writes to SharedAudioBuffer.
 * Multiple speakers can share the same download session via ref counting.
 */
public class AudioDownloadSession {
    private final UUID resourceId;
    private final SharedAudioBuffer buffer;
    private final AtomicInteger refCount = new AtomicInteger(0);
    private final boolean canCache;
    private final boolean isLiveStream;

    private volatile boolean closed = false;

    public AudioDownloadSession(UUID resourceId, boolean canCache) {
        this(resourceId, canCache, false);
    }

    public AudioDownloadSession(UUID resourceId, boolean canCache, boolean isLiveStream) {
        this.resourceId = resourceId;
        this.buffer = new SharedAudioBuffer();
        this.canCache = canCache;
        this.isLiveStream = isLiveStream;
    }

    public boolean isLiveStream() {
        return isLiveStream;
    }

    public void receiveHeader(byte[] headerBytes, int sampleRate) {
        if (closed) return;

        buffer.setHeader(headerBytes, sampleRate);
        buffer.append(headerBytes);

        Phonon.LOGGER.debug("Received header for {}: {} bytes, {}Hz",
            resourceId, headerBytes.length, sampleRate);
    }

    public void receiveChunk(byte[] data) {
        if (closed) return;

        buffer.append(data);
    }

    public void markComplete() {
        buffer.markComplete();
        Phonon.LOGGER.debug("Download complete for {}: {} bytes total",
            resourceId, buffer.getTotalBytes());
    }

    public void addRef() {
        refCount.incrementAndGet();
    }

    public int release() {
        return refCount.decrementAndGet();
    }

    public int getRefCount() {
        return refCount.get();
    }

    public SharedAudioBuffer getBuffer() {
        return buffer;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public boolean canCache() {
        return canCache;
    }

    public void saveToCache(Path cacheDir) {
        if (!canCache || buffer.getTotalBytes() == 0) return;

        try {
            Path file = cacheDir.resolve(resourceId + ".ogg");
            Files.write(file, buffer.toByteArray());
            AudioCache.getInstance().registerCachedFile(resourceId, file);
            Phonon.LOGGER.info("Saved streaming audio to cache: {} ({} bytes)",
                resourceId, buffer.getTotalBytes());
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to save streaming audio to cache", e);
        }
    }

    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}
