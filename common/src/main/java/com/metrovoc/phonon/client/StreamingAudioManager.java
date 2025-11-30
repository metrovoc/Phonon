package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.client.audio.OpusAudioStream;
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
 * Supports both legacy Vorbis streaming and new Opus packet-based streaming.
 */
public class StreamingAudioManager {
    private static final StreamingAudioManager instance = new StreamingAudioManager();
    private static final long CLEANUP_DELAY_MS = 5000;

    // Legacy Vorbis downloads
    private final Map<UUID, AudioDownloadSession> downloads = new ConcurrentHashMap<>();
    private final Map<UUID, List<Consumer<AudioDownloadSession>>> readyCallbacks = new ConcurrentHashMap<>();

    // Opus downloads
    private final Map<UUID, OpusDownloadSession> opusDownloads = new ConcurrentHashMap<>();
    private final Map<UUID, List<Consumer<OpusDownloadSession>>> opusReadyCallbacks = new ConcurrentHashMap<>();

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
     * Check if download exists and has header ready.
     */
    public boolean isReady(UUID resourceId) {
        AudioDownloadSession session = downloads.get(resourceId);
        return session != null && session.getBuffer().hasHeader();
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

        // Notify callbacks
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
     * Receive chunk data from network.
     */
    public void receiveChunk(UUID resourceId, byte[] data) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session != null) {
            session.receiveChunk(data);
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
        if (session == null || !session.getBuffer().hasHeader()) {
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
     * Add callback to be invoked when header is ready.
     */
    public void addReadyCallback(UUID resourceId, Consumer<AudioDownloadSession> callback) {
        AudioDownloadSession session = downloads.get(resourceId);
        if (session != null && session.getBuffer().hasHeader()) {
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

        for (OpusDownloadSession session : opusDownloads.values()) {
            session.close();
        }
        opusDownloads.clear();
        opusReadyCallbacks.clear();
    }

    // ==================== Opus Streaming API ====================

    /**
     * Get or create an Opus download session.
     */
    public OpusDownloadSession getOrCreateOpusDownload(UUID resourceId) {
        OpusDownloadSession session = opusDownloads.computeIfAbsent(resourceId,
            id -> new OpusDownloadSession(id));
        session.addRef();
        return session;
    }

    /**
     * Get existing Opus download session.
     */
    public OpusDownloadSession getOpusDownload(UUID resourceId) {
        return opusDownloads.get(resourceId);
    }

    /**
     * Receive OpusStreamStartPacket data.
     */
    public void receiveOpusStreamStart(UUID resourceId, int channels, long durationMs,
                                       int totalPackets, int startSequence, long startPositionMs) {
        OpusDownloadSession session = opusDownloads.get(resourceId);
        if (session == null) {
            Phonon.LOGGER.warn("Received Opus stream start for unknown session: {}", resourceId);
            return;
        }

        session.initStream(channels, durationMs, totalPackets, startSequence, startPositionMs);

        // Notify callbacks if ready
        if (session.isReady()) {
            notifyOpusReady(resourceId, session);
        }
    }

    /**
     * Receive an Opus packet.
     */
    public void receiveOpusPacket(UUID resourceId, int sequenceNumber, int totalPackets, byte[] opusData) {
        OpusDownloadSession session = opusDownloads.get(resourceId);
        if (session == null) {
            Phonon.LOGGER.debug("Received Opus packet for unknown session: {}", resourceId);
            return;
        }

        session.receivePacket(sequenceNumber, totalPackets, opusData);

        // Check if now ready and notify
        if (session.isReady() && !session.getPacketBuffer().isStarted()) {
            notifyOpusReady(resourceId, session);
        }
    }

    private void notifyOpusReady(UUID resourceId, OpusDownloadSession session) {
        List<Consumer<OpusDownloadSession>> callbacks = opusReadyCallbacks.remove(resourceId);
        if (callbacks != null) {
            for (Consumer<OpusDownloadSession> callback : callbacks) {
                try {
                    callback.accept(session);
                } catch (Exception e) {
                    Phonon.LOGGER.error("Error in Opus ready callback", e);
                }
            }
        }
    }

    /**
     * Check if Opus download is ready for playback.
     */
    public boolean isOpusReady(UUID resourceId) {
        OpusDownloadSession session = opusDownloads.get(resourceId);
        return session != null && session.isReady();
    }

    /**
     * Create a new Opus audio stream for playback.
     */
    public OpusAudioStream createOpusStream(UUID resourceId) {
        OpusDownloadSession session = opusDownloads.get(resourceId);
        if (session == null || !session.isReady()) {
            return null;
        }
        return session.createStream();
    }

    /**
     * Release a reference to an Opus download session.
     */
    public void releaseOpusDownload(UUID resourceId) {
        OpusDownloadSession session = opusDownloads.get(resourceId);
        if (session == null) return;

        int remaining = session.release();
        if (remaining <= 0) {
            scheduleOpusCleanup(resourceId);
        }
    }

    private void scheduleOpusCleanup(UUID resourceId) {
        scheduler.schedule(() -> {
            OpusDownloadSession session = opusDownloads.get(resourceId);
            if (session != null && session.getRefCount() <= 0) {
                opusDownloads.remove(resourceId);
                session.close();
                Phonon.LOGGER.debug("Cleaned up Opus download session: {}", resourceId);
            }
        }, CLEANUP_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Add callback for when Opus stream is ready.
     */
    public void addOpusReadyCallback(UUID resourceId, Consumer<OpusDownloadSession> callback) {
        OpusDownloadSession session = opusDownloads.get(resourceId);
        if (session != null && session.isReady()) {
            callback.accept(session);
            return;
        }

        opusReadyCallbacks.computeIfAbsent(resourceId, k -> new ArrayList<>()).add(callback);
    }

    /**
     * Check if an Opus download session exists.
     */
    public boolean hasOpusDownload(UUID resourceId) {
        return opusDownloads.containsKey(resourceId);
    }

    /**
     * End an Opus session.
     */
    public void endOpusSession(UUID resourceId) {
        OpusDownloadSession session = opusDownloads.remove(resourceId);
        if (session != null) {
            opusReadyCallbacks.remove(resourceId);
            session.close();
        }
    }
}
