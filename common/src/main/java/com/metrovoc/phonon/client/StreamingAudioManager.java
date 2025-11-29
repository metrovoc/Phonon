package com.metrovoc.phonon.client;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class StreamingAudioManager {
    private static final StreamingAudioManager instance = new StreamingAudioManager();

    private final Map<UUID, StreamingAudioSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, BiConsumer<UUID, StreamingAudioSession>> readyCallbacks = new ConcurrentHashMap<>();
    private Path cacheDir;

    public static StreamingAudioManager getInstance() {
        return instance;
    }

    public void setCacheDir(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    public void setReadyCallback(UUID resourceId, BiConsumer<UUID, StreamingAudioSession> callback) {
        readyCallbacks.put(resourceId, callback);
    }

    public void startSession(UUID resourceId, long startPositionMs, byte[] headerBytes, int sampleRate) {
        StreamingAudioSession session = new StreamingAudioSession(resourceId, startPositionMs);
        session.receiveHeader(headerBytes, sampleRate);
        session.onReady(s -> {
            BiConsumer<UUID, StreamingAudioSession> callback = readyCallbacks.remove(resourceId);
            if (callback != null) {
                callback.accept(resourceId, s);
            }
        });
        sessions.put(resourceId, session);
    }

    public void receiveChunk(UUID resourceId, byte[] data) {
        StreamingAudioSession session = sessions.get(resourceId);
        if (session != null) {
            session.receiveChunk(data);
        }
    }

    public boolean hasActiveSession(UUID resourceId) {
        return sessions.containsKey(resourceId);
    }

    public StreamingAudioSession getSession(UUID resourceId) {
        return sessions.get(resourceId);
    }

    public void endSession(UUID resourceId) {
        StreamingAudioSession session = sessions.remove(resourceId);
        if (session != null) {
            if (cacheDir != null && session.canSaveToCache()) {
                session.saveToCache(cacheDir);
            }
            session.close();
        }
    }

    public void clear() {
        sessions.values().forEach(StreamingAudioSession::close);
        sessions.clear();
    }
}
