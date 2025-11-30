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

    /**
     * Result of checking poll status - distinguishes between different null cases.
     */
    public enum PollResult {
        DATA_AVAILABLE,  // Packet exists in buffer
        PACKET_LOSS,     // Gap in sequence - packet truly missing
        BUFFER_EMPTY     // Caught up to network - waiting for more data
    }

    private static final int DEFAULT_BUFFER_DELAY_PACKETS = 10;
    private static final int MAX_BUFFER_SIZE = 3000;  // ~1 minute of audio (50 packets/sec)
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

            // First packet received - start from this sequence, not backwards
            if (highestReceivedSeq < 0) {
                highestReceivedSeq = sequenceNumber;
                nextPlaybackSeq = sequenceNumber;  // Start from first actual packet
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

            // Prevent unbounded growth - but ONLY discard already-played packets
            while (packets.size() > MAX_BUFFER_SIZE) {
                Integer firstKey = packets.firstKey();
                if (firstKey != null && firstKey < nextPlaybackSeq) {
                    // Safe to discard - already played
                    packets.pollFirstEntry();
                } else {
                    // All remaining packets are still needed - stop discarding
                    // Server is sending faster than playback, which is fine
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check the status before polling.
     * Caller should check this before calling poll() to distinguish
     * between true packet loss and buffer underflow.
     */
    public PollResult checkPollStatus() {
        lock.lock();
        try {
            if (!started || nextPlaybackSeq < 0) {
                return PollResult.BUFFER_EMPTY;
            }

            // Caught up to network - waiting for more data (underflow)
            if (nextPlaybackSeq > highestReceivedSeq) {
                return PollResult.BUFFER_EMPTY;
            }

            // Packet should exist but isn't in map - true loss (gap)
            if (!packets.containsKey(nextPlaybackSeq)) {
                // Debug: what sequences DO we have?
                Integer firstKey = packets.isEmpty() ? null : packets.firstKey();
                Integer lastKey = packets.isEmpty() ? null : packets.lastKey();
                com.metrovoc.phonon.Phonon.LOGGER.warn(
                    "PACKET_LOSS: nextPlaybackSeq={}, highestReceivedSeq={}, packets.size={}, firstKey={}, lastKey={}",
                    nextPlaybackSeq, highestReceivedSeq, packets.size(), firstKey, lastKey);
                return PollResult.PACKET_LOSS;
            }

            return PollResult.DATA_AVAILABLE;
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

            // If we've caught up to or passed the highest received, wait for more
            if (nextPlaybackSeq > highestReceivedSeq) {
                return null;  // Don't advance, wait for packet to arrive
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

            // Check ACTUAL packet count, not theoretical range
            // Also ensure the first packet we need actually exists
            return packets.size() >= bufferDelayPackets
                && packets.containsKey(nextPlaybackSeq);
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
