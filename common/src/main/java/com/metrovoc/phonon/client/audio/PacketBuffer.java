package com.metrovoc.phonon.client.audio;

import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple Jitter Buffer for Opus packets.
 * Stores packets by sequence number, allows ordered retrieval.
 *
 * Design:
 * - Packets arrive potentially out of order
 * - Buffer holds packets until playback catches up
 * - Missing packets trigger PLC (packet loss concealment)
 * - Old packets (below playback cursor) are discarded
 */
public class PacketBuffer {

    private static final int DEFAULT_BUFFER_DELAY_PACKETS = 3;
    private static final int MAX_BUFFER_SIZE = 100;
    private static final int MAX_GAP_BEFORE_RESET = 50;

    private final ReentrantLock lock = new ReentrantLock();
    private final TreeMap<Integer, byte[]> packets = new TreeMap<>();

    private int bufferDelayPackets;
    private int nextPlaybackSeq = -1;
    private int highestReceivedSeq = -1;
    private boolean started = false;
    private volatile boolean complete = false;
    private int totalPackets = -1;

    public PacketBuffer() {
        this(DEFAULT_BUFFER_DELAY_PACKETS);
    }

    public PacketBuffer(int bufferDelayPackets) {
        this.bufferDelayPackets = bufferDelayPackets;
    }

    /**
     * Add a packet to the buffer.
     *
     * @param sequenceNumber packet sequence (0-based)
     * @param data           Opus encoded data
     */
    public void push(int sequenceNumber, byte[] data) {
        lock.lock();
        try {
            if (complete) return;

            // First packet received
            if (highestReceivedSeq < 0) {
                highestReceivedSeq = sequenceNumber;
                nextPlaybackSeq = Math.max(0, sequenceNumber - bufferDelayPackets);
            }

            // Update highest received
            if (sequenceNumber > highestReceivedSeq) {
                highestReceivedSeq = sequenceNumber;
            }

            // Discard if too old
            if (sequenceNumber < nextPlaybackSeq) {
                return;
            }

            // Check for huge gaps (stream reset)
            if (sequenceNumber - highestReceivedSeq > MAX_GAP_BEFORE_RESET) {
                packets.clear();
                nextPlaybackSeq = sequenceNumber;
                highestReceivedSeq = sequenceNumber;
            }

            packets.put(sequenceNumber, data);

            // Prevent unbounded growth
            while (packets.size() > MAX_BUFFER_SIZE) {
                packets.pollFirstEntry();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get next packet for playback.
     *
     * @return packet data, or null if packet is missing (caller should use PLC)
     */
    public byte[] poll() {
        lock.lock();
        try {
            if (!started || nextPlaybackSeq < 0) {
                return null;
            }

            byte[] data = packets.remove(nextPlaybackSeq);
            nextPlaybackSeq++;

            return data;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Peek at next packet without removing.
     */
    public byte[] peek() {
        lock.lock();
        try {
            if (nextPlaybackSeq < 0) return null;
            return packets.get(nextPlaybackSeq);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if buffer has enough data to start playback.
     */
    public boolean isReadyToStart() {
        lock.lock();
        try {
            if (started) return true;
            if (highestReceivedSeq < 0) return false;

            int bufferedCount = highestReceivedSeq - nextPlaybackSeq + 1;
            return bufferedCount >= bufferDelayPackets;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Mark buffer as ready to start playback.
     */
    public void start() {
        lock.lock();
        try {
            started = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if playback has started.
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Get current playback sequence number.
     */
    public int getPlaybackSequence() {
        return nextPlaybackSeq;
    }

    /**
     * Get highest received sequence number.
     */
    public int getHighestReceivedSequence() {
        return highestReceivedSeq;
    }

    /**
     * Get number of buffered packets.
     */
    public int getBufferedCount() {
        lock.lock();
        try {
            return packets.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Mark stream as complete (no more packets coming).
     */
    public void markComplete(int totalPackets) {
        lock.lock();
        try {
            this.complete = true;
            this.totalPackets = totalPackets;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if stream is complete and all packets consumed.
     */
    public boolean isFinished() {
        lock.lock();
        try {
            return complete && (totalPackets < 0 || nextPlaybackSeq >= totalPackets);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if stream is marked complete.
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Seek to a specific sequence number.
     */
    public void seekTo(int sequenceNumber) {
        lock.lock();
        try {
            packets.clear();
            nextPlaybackSeq = sequenceNumber;
            highestReceivedSeq = sequenceNumber - 1;
            started = false;
            complete = false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reset buffer to initial state.
     */
    public void reset() {
        lock.lock();
        try {
            packets.clear();
            nextPlaybackSeq = -1;
            highestReceivedSeq = -1;
            started = false;
            complete = false;
            totalPackets = -1;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clear all buffered data.
     */
    public void clear() {
        reset();
    }

    public void setBufferDelay(int packets) {
        this.bufferDelayPackets = packets;
    }

    public int getBufferDelay() {
        return bufferDelayPackets;
    }
}
