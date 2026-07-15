package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.AudioLimits;
import com.metrovoc.phonon.client.audio.SharedAudioBuffer;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * A single server stream response shared by nearby playback requests.
 */
public final class AudioDownloadSession {
    private static final long SHARE_WINDOW_MS = 2_000;

    private final long streamId;
    private final UUID resourceId;
    private final long requestedPositionMs;
    private final SharedAudioBuffer buffer = new SharedAudioBuffer();

    private int refCount;
    private int nextChunkIndex;
    private boolean started;
    private boolean downloadComplete;
    private boolean cacheable;
    private boolean closed;

    public AudioDownloadSession(long streamId, UUID resourceId, long requestedPositionMs) {
        this.streamId = streamId;
        this.resourceId = resourceId;
        this.requestedPositionMs = Math.max(0, requestedPositionMs);
    }

    public boolean receiveHeader(
        byte[] headerBytes,
        int sampleRate,
        long streamStartPositionMs,
        boolean cacheable
    ) {
        if (closed || started || headerBytes == null || headerBytes.length == 0
            || headerBytes.length > AudioLimits.MAX_HEADER_BYTES
            || sampleRate <= 0 || sampleRate > AudioLimits.MAX_SAMPLE_RATE) {
            return false;
        }

        this.cacheable = cacheable;
        this.started = true;
        buffer.setHeader(headerBytes, sampleRate, streamStartPositionMs);
        buffer.append(headerBytes);
        Phonon.LOGGER.debug(
            "Started audio stream {} for {} at {}ms ({} byte header)",
            streamId,
            resourceId,
            streamStartPositionMs,
            headerBytes.length
        );
        return true;
    }

    public boolean receiveChunk(int chunkIndex, byte[] data) {
        if (closed || !started || chunkIndex != nextChunkIndex || data == null || data.length == 0
            || data.length > AudioLimits.MAX_CHUNK_BYTES
            || buffer.getTotalBytes() > maxStreamBytes() - data.length) {
            return false;
        }

        buffer.append(data);
        nextChunkIndex++;
        return true;
    }

    private static int maxStreamBytes() {
        return AudioLimits.MAX_AUDIO_SIZE_MB * 1024 * 1024;
    }

    public boolean markComplete() {
        if (closed || downloadComplete) {
            return false;
        }
        downloadComplete = true;
        buffer.markComplete();
        return true;
    }

    public boolean canShare(long positionMs) {
        if (closed) {
            return false;
        }
        long candidatePositionMs = Math.max(0, positionMs);
        long earliestAvailableMs = started
            ? buffer.getStreamStartPositionMs()
            : requestedPositionMs;
        long latestUsefulMs = requestedPositionMs > Long.MAX_VALUE - SHARE_WINDOW_MS
            ? Long.MAX_VALUE
            : requestedPositionMs + SHARE_WINDOW_MS;
        return candidatePositionMs >= earliestAvailableMs
            && candidatePositionMs <= latestUsefulMs;
    }

    public void addRef() {
        if (closed) {
            throw new IllegalStateException("Cannot retain a closed audio stream");
        }
        refCount++;
    }

    public int release() {
        if (refCount == 0) {
            return 0;
        }
        return --refCount;
    }

    public void saveToCache(Path cacheDir) {
        if (!cacheable || !downloadComplete || buffer.getTotalBytes() == 0) {
            return;
        }

        Path target = cacheDir.resolve(resourceId + ".ogg");
        Path temporary = cacheDir.resolve(resourceId + ".ogg.part-" + streamId);
        try {
            Files.createDirectories(cacheDir);
            buffer.writeTo(temporary);
            moveAtomically(temporary, target);
            AudioCache.getInstance().registerCachedFile(resourceId, target);
            Phonon.LOGGER.info(
                "Saved audio {} to cache without full-buffer assembly ({} bytes)",
                resourceId,
                buffer.getTotalBytes()
            );
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to save streaming audio {} to cache", resourceId, e);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        buffer.markComplete();
    }

    public long getStreamId() {
        return streamId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public SharedAudioBuffer getBuffer() {
        return buffer;
    }

    public int getRefCount() {
        return refCount;
    }

    public boolean isDownloadComplete() {
        return downloadComplete;
    }

    public boolean isClosed() {
        return closed;
    }
}
