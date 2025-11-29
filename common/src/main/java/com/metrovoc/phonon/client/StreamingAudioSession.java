package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.client.audio.StreamingAudioDecoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

public class StreamingAudioSession {
    private final UUID resourceId;
    private final StreamingAudioDecoder decoder;
    private final long startPositionMs;
    private final ByteArrayOutputStream cacheBuffer;

    private Consumer<StreamingAudioSession> onReadyCallback;
    private boolean ready = false;

    public StreamingAudioSession(UUID resourceId, long startPositionMs) {
        this.resourceId = resourceId;
        this.startPositionMs = startPositionMs;
        this.decoder = new StreamingAudioDecoder();
        this.cacheBuffer = (startPositionMs == 0) ? new ByteArrayOutputStream() : null;
    }

    public void receiveHeader(byte[] headerBytes, int sampleRate) {
        decoder.pushData(headerBytes);
        if (cacheBuffer != null) {
            try {
                cacheBuffer.write(headerBytes);
            } catch (IOException ignored) {}
        }
    }

    public void receiveChunk(byte[] data) {
        decoder.pushData(data);
        if (cacheBuffer != null) {
            try {
                cacheBuffer.write(data);
            } catch (IOException ignored) {}
        }

        if (!ready && decoder.hasEnoughData()) {
            ready = true;
            if (onReadyCallback != null) {
                onReadyCallback.accept(this);
            }
        }
    }

    public void saveToCache(Path cacheDir) {
        if (cacheBuffer == null || cacheBuffer.size() == 0) {
            return;
        }
        try {
            Path file = cacheDir.resolve(resourceId + ".ogg");
            Files.write(file, cacheBuffer.toByteArray());
            AudioCache.getInstance().registerCachedFile(resourceId, file);
            Phonon.LOGGER.info("Saved streaming audio to cache: {} ({} bytes)", resourceId, cacheBuffer.size());
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to save streaming audio to cache", e);
        }
    }

    public boolean canSaveToCache() {
        return cacheBuffer != null;
    }

    public void onReady(Consumer<StreamingAudioSession> callback) {
        this.onReadyCallback = callback;
        if (ready) {
            callback.accept(this);
        }
    }

    public StreamingAudioDecoder getDecoder() {
        return decoder;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public long getStartPositionMs() {
        return startPositionMs;
    }

    public boolean isReady() {
        return ready;
    }

    public void close() {
        decoder.close();
    }
}
