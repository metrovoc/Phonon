package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.client.audio.StreamingAudioStream;
import com.metrovoc.phonon.platform.PlatformHelper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Owns explicitly identified network streams and their speaker references.
 * All map mutations happen on the client thread; only cache persistence runs on
 * the maintenance executor.
 */
public final class StreamingAudioManager {
    private static final StreamingAudioManager INSTANCE = new StreamingAudioManager();
    private static final long CLEANUP_DELAY_MS = 5_000;
    private static final int MIN_DATA_FOR_PLAYBACK = 32 * 1024;

    private final Map<Long, AudioDownloadSession> sessions = new HashMap<>();
    private final Map<UUID, List<AudioDownloadSession>> sessionsByResource = new HashMap<>();
    private final Map<Long, List<Consumer<AudioDownloadSession>>> readyCallbacks = new HashMap<>();
    private final ScheduledExecutorService maintenanceExecutor = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "Phonon-StreamingMaintenance");
        thread.setDaemon(true);
        return thread;
    });

    private long nextStreamId = 1;
    private Path cacheDir;

    private StreamingAudioManager() {}

    public static StreamingAudioManager getInstance() {
        return INSTANCE;
    }

    public void setCacheDir(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    public Acquisition acquire(UUID resourceId, long positionMs) {
        List<AudioDownloadSession> candidates = sessionsByResource.get(resourceId);
        if (candidates != null) {
            for (AudioDownloadSession candidate : candidates) {
                if (candidate.canShare(positionMs)) {
                    candidate.addRef();
                    return new Acquisition(candidate, false);
                }
            }
        }

        long streamId = nextStreamId++;
        AudioDownloadSession session = new AudioDownloadSession(streamId, resourceId, positionMs);
        session.addRef();
        sessions.put(streamId, session);
        sessionsByResource.computeIfAbsent(resourceId, ignored -> new ArrayList<>()).add(session);
        return new Acquisition(session, true);
    }

    public void receiveStart(
        long streamId,
        UUID resourceId,
        byte[] headerBytes,
        int sampleRate,
        long streamStartPositionMs,
        boolean cacheable
    ) {
        AudioDownloadSession session = sessions.get(streamId);
        if (session == null || !session.getResourceId().equals(resourceId)) {
            Phonon.LOGGER.warn("Ignoring start for unknown audio stream {}", streamId);
            return;
        }

        if (!session.receiveHeader(headerBytes, sampleRate, streamStartPositionMs, cacheable)) {
            Phonon.LOGGER.warn("Ignoring duplicate or invalid start for audio stream {}", streamId);
        }
    }

    public void receiveChunk(long streamId, int chunkIndex, byte[] data, boolean last) {
        AudioDownloadSession session = sessions.get(streamId);
        if (session == null) {
            return;
        }

        if (!session.receiveChunk(chunkIndex, data)) {
            Phonon.LOGGER.warn("Rejected out-of-order chunk {} for audio stream {}", chunkIndex, streamId);
            removeSession(session, true);
            return;
        }

        notifyReady(streamId, session);
        if (last && session.markComplete()) {
            notifyReady(streamId, session);
            persistCache(session);
        }
    }

    private void notifyReady(long streamId, AudioDownloadSession session) {
        if (!session.getBuffer().hasEnoughData(MIN_DATA_FOR_PLAYBACK)) {
            return;
        }

        List<Consumer<AudioDownloadSession>> callbacks = readyCallbacks.remove(streamId);
        if (callbacks == null) {
            return;
        }

        for (Consumer<AudioDownloadSession> callback : callbacks) {
            try {
                callback.accept(session);
            } catch (Exception e) {
                Phonon.LOGGER.error("Audio stream ready callback failed", e);
            }
        }
    }

    private void persistCache(AudioDownloadSession session) {
        Path directory = cacheDir;
        if (directory != null) {
            maintenanceExecutor.execute(() -> session.saveToCache(directory));
        }
    }

    public void addReadyCallback(long streamId, Consumer<AudioDownloadSession> callback) {
        AudioDownloadSession session = sessions.get(streamId);
        if (session == null || session.isClosed()) {
            return;
        }
        if (session.getBuffer().hasEnoughData(MIN_DATA_FOR_PLAYBACK)) {
            callback.accept(session);
            return;
        }
        readyCallbacks.computeIfAbsent(streamId, ignored -> new ArrayList<>()).add(callback);
    }

    public StreamingAudioStream createStream(long streamId, long positionMs) {
        AudioDownloadSession session = sessions.get(streamId);
        if (session == null || !session.getBuffer().hasEnoughData(MIN_DATA_FOR_PLAYBACK)) {
            return null;
        }
        return new StreamingAudioStream(session.getBuffer(), positionMs);
    }

    public void release(long streamId) {
        AudioDownloadSession session = sessions.get(streamId);
        if (session == null || session.release() > 0) {
            return;
        }

        maintenanceExecutor.schedule(
            () -> PlatformHelper.INSTANCE.runOnClient(() -> cleanupIfUnused(streamId)),
            CLEANUP_DELAY_MS,
            TimeUnit.MILLISECONDS
        );
    }

    private void cleanupIfUnused(long streamId) {
        AudioDownloadSession session = sessions.get(streamId);
        if (session != null && session.getRefCount() == 0) {
            removeSession(session, !session.isDownloadComplete());
        }
    }

    private void removeSession(AudioDownloadSession session, boolean cancelServerTransfer) {
        sessions.remove(session.getStreamId());
        readyCallbacks.remove(session.getStreamId());

        List<AudioDownloadSession> resourceSessions = sessionsByResource.get(session.getResourceId());
        if (resourceSessions != null) {
            resourceSessions.remove(session);
            if (resourceSessions.isEmpty()) {
                sessionsByResource.remove(session.getResourceId());
            }
        }

        if (cancelServerTransfer) {
            PlatformHelper.INSTANCE.cancelAudioStream(session.getStreamId());
        }
        session.close();
    }

    public void clear() {
        for (AudioDownloadSession session : List.copyOf(sessions.values())) {
            removeSession(session, !session.isDownloadComplete());
        }
    }

    public record Acquisition(AudioDownloadSession session, boolean requestRequired) {}
}
