package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.client.audio.OpusAudioStream;
import com.metrovoc.phonon.client.audio.PacketBuffer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages Opus audio download for a single resource.
 * Stores packets in a PacketBuffer for streaming playback.
 * Multiple speakers can share the same session via ref counting.
 */
public class OpusDownloadSession {

    private final UUID resourceId;
    private final PacketBuffer packetBuffer;
    private final AtomicInteger refCount = new AtomicInteger(0);

    private volatile boolean closed = false;
    private volatile boolean streamStarted = false;

    private int channels = 1;
    private long durationMs = -1;
    private int totalPackets = -1;
    private int startSequence = 0;
    private long startPositionMs = 0;

    public OpusDownloadSession(UUID resourceId) {
        this.resourceId = resourceId;
        this.packetBuffer = new PacketBuffer();
    }

    /**
     * Initialize stream parameters from OpusStreamStartPacket.
     */
    public void initStream(int channels, long durationMs, int totalPackets, int startSequence, long startPositionMs) {
        if (streamStarted) {
            Phonon.LOGGER.warn("Stream {} already started, ignoring duplicate init", resourceId);
            return;
        }

        int bufferedBefore = packetBuffer.getBufferedCount();

        this.channels = channels;
        this.durationMs = durationMs;
        this.totalPackets = totalPackets;
        this.startSequence = startSequence;
        this.startPositionMs = startPositionMs;
        this.streamStarted = true;

        if (startSequence > 0) {
            Phonon.LOGGER.info("Seeking to startSequence={}, clearing {} buffered packets", startSequence, bufferedBefore);
            packetBuffer.seekTo(startSequence);
        }

        Phonon.LOGGER.info("Opus stream {} init: channels={}, duration={}ms, total={}, startSeq={}, bufferedBefore={}",
            resourceId, channels, durationMs, totalPackets, startSequence, bufferedBefore);
    }

    /**
     * Receive an Opus packet.
     */
    public void receivePacket(int sequenceNumber, int totalPackets, byte[] opusData) {
        if (closed) return;

        packetBuffer.push(sequenceNumber, opusData);

        // Update total packets if provided
        if (totalPackets > 0 && this.totalPackets < 0) {
            this.totalPackets = totalPackets;
        }

        // Check for completion
        if (totalPackets > 0 && sequenceNumber == totalPackets - 1) {
            markComplete();
        }
    }

    /**
     * Mark stream as complete.
     */
    public void markComplete() {
        packetBuffer.markComplete(totalPackets);
        Phonon.LOGGER.debug("Opus stream {} complete: {} packets", resourceId, totalPackets);
    }

    /**
     * Create a new audio stream for playback.
     */
    public OpusAudioStream createStream() {
        if (!streamStarted) {
            Phonon.LOGGER.warn("Cannot create stream: Opus stream {} not initialized", resourceId);
            return null;
        }
        return new OpusAudioStream(packetBuffer, channels);
    }

    /**
     * Check if stream is ready for playback.
     */
    public boolean isReady() {
        return streamStarted && packetBuffer.isReadyToStart();
    }

    /**
     * Check if stream initialization packet received.
     */
    public boolean isStreamStarted() {
        return streamStarted;
    }

    /**
     * Seek to a position (resets buffer for new packets).
     */
    public void seekTo(int sequenceNumber) {
        packetBuffer.seekTo(sequenceNumber);
    }

    /**
     * Calculate sequence number for a time position.
     */
    public int getSequenceForTime(long positionMs) {
        // 20ms per frame
        return (int) (positionMs / 20);
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

    public UUID getResourceId() {
        return resourceId;
    }

    public PacketBuffer getPacketBuffer() {
        return packetBuffer;
    }

    public int getChannels() {
        return channels;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getTotalPackets() {
        return totalPackets;
    }

    public long getStartPositionMs() {
        return startPositionMs;
    }

    public void close() {
        closed = true;
        packetBuffer.clear();
    }

    public boolean isClosed() {
        return closed;
    }
}
