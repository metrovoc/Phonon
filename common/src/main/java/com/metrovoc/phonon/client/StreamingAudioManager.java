package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.client.audio.SharedAudioBuffer;
import com.metrovoc.phonon.client.audio.StreamingAudioStream;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manages audio downloads by resourceId.
 * Multiple speakers sharing the same resource use the same download session.
 */
public class StreamingAudioManager {
    private static final StreamingAudioManager instance = new StreamingAudioManager();
    private static final long CLEANUP_DELAY_MS = 5000;
    private static final int MIN_DATA_FOR_PLAYBACK = 16 * 1024;

    private final Map<UUID, AudioDownloadSession> downloads = new ConcurrentHashMap<>();
    private final Map<UUID, List<Consumer<AudioDownloadSession>>> readyCallbacks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Phonon-StreamingCleanup");
        t.setDaemon(true);
        return t;
    });

    private Path cacheDir;

    public static StreamingAudioManager getInstance() {
        return instance;
    }

    public void setCacheDir(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Get or create a download session for the resource.
     * Increments ref count automatically.
     */
    public AudioDownloadSession getOrCreateDownload(UUID resourceId, boolean canCache) {
        AudioDownloadSession session = downloads.computeIfAbsent(resourceId,
            id -> new AudioDownloadSession(id, canCache));
        session.addRef();
        return session;
    }

    /**
     * Get existing download session without incrementing ref count.
     */
    public AudioDownloadSession getDownload(UUID resourceId) {
        return downloads.get(resourceId);
    }

    /**
     * Check if download exists and has enough data ready for playback.
     */
    public boolean isReady(UUID resourceId) {
        AudioDownloadSession session = downloads.get(resourceId);
        return session != null && session.getBuffer().hasEnoughData(MIN_DATA_FOR_PLAYBACK);
    }

    /**
     * Receive header data from network.
     */
    public void receiveHeader(UUID resourceId, byte[] headerBytes, int sampleRate) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session == null) {
            Phonon.LOGGER.warn("Received header for unknown session: {}", resourceId);
            return;
        }

        session.receiveHeader(headerBytes, sampleRate);
        // Don't trigger callbacks here - wait for enough data in receiveChunk()
    }

    /**
     * Receive chunk data from network.
     */
    public void receiveChunk(UUID resourceId, byte[] data) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session == null) return;

        session.receiveChunk(data);
        tryNotifyCallbacks(resourceId, session);
    }

    private void tryNotifyCallbacks(UUID resourceId, AudioDownloadSession session) {
        if (!session.getBuffer().hasEnoughData(MIN_DATA_FOR_PLAYBACK)) {
            return;
        }

        List<Consumer<AudioDownloadSession>> callbacks = readyCallbacks.remove(resourceId);
        if (callbacks != null) {
            for (Consumer<AudioDownloadSession> callback : callbacks) {
                try {
                    callback.accept(session);
                } catch (Exception e) {
                    Phonon.LOGGER.error("Error in ready callback", e);
                }
            }
        }
    }

    /**
     * Mark download as complete.
     */
    public void completeDownload(UUID resourceId) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session != null) {
            session.markComplete();

            if (cacheDir != null && session.canCache()) {
                session.saveToCache(cacheDir);
            }
        }
    }

    /**
     * Create a new stream for playback.
     * The caller is responsible for calling releaseDownload when done.
     */
    public StreamingAudioStream createStream(UUID resourceId, long startPositionMs) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session == null || !session.getBuffer().hasEnoughData(MIN_DATA_FOR_PLAYBACK)) {
            return null;
        }
        return new StreamingAudioStream(session.getBuffer(), startPositionMs);
    }

    /**
     * Release a reference to the download session.
     * Schedules cleanup if ref count reaches zero.
     */
    public void releaseDownload(UUID resourceId) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session == null) return;

        int remaining = session.release();
        if (remaining <= 0) {
            scheduleCleanup(resourceId);
        }
    }

    private void scheduleCleanup(UUID resourceId) {
        scheduler.schedule(() -> {
            AudioDownloadSession session = downloads.get(resourceId);
            if (session != null && session.getRefCount() <= 0) {
                downloads.remove(resourceId);
                session.close();
                Phonon.LOGGER.debug("Cleaned up download session: {}", resourceId);
            }
        }, CLEANUP_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Add callback to be invoked when enough data is ready for playback.
     */
    public void addReadyCallback(UUID resourceId, Consumer<AudioDownloadSession> callback) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session != null && session.getBuffer().hasEnoughData(MIN_DATA_FOR_PLAYBACK)) {
            callback.accept(session);
            return;
        }

        readyCallbacks.computeIfAbsent(resourceId, k -> new ArrayList<>()).add(callback);
    }

    /**
     * Check if a download session exists.
     */
    public boolean hasDownload(UUID resourceId) {
        return downloads.containsKey(resourceId);
    }

    /**
     * Force end a session (e.g., when speaker is removed).
     */
    public void endSession(UUID resourceId) {
        AudioDownloadSession session = downloads.remove(resourceId);
        if (session != null) {
            readyCallbacks.remove(resourceId);
            session.close();
        }
    }

    public void clear() {
        for (AudioDownloadSession session : downloads.values()) {
            session.close();
        }
        downloads.clear();
        readyCallbacks.clear();
    }
}
