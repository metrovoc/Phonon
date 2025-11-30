package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.AudioStreamInfo;
import com.metrovoc.phonon.audio.OpusEncoder;
import com.metrovoc.phonon.config.PhononServerConfig;
import com.metrovoc.phonon.network.packets.AudioChunkPacket;
import com.metrovoc.phonon.network.packets.AudioStreamStartPacket;
import com.metrovoc.phonon.network.packets.OpusPacket;
import com.metrovoc.phonon.network.packets.OpusStreamStartPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages audio transfer to clients.
 * Supports both legacy Vorbis streaming and new Opus packet-based streaming.
 */
public class AudioTransferManager {

    private static AudioTransferManager instance;

    // Opus transcoding cache to avoid re-transcoding the same file
    private final Map<UUID, TranscodedAudio> transcodeCache = new ConcurrentHashMap<>();

    private final Map<UUID, Queue<PendingTransfer>> playerQueues = new ConcurrentHashMap<>();
    private final List<UUID> activePlayerOrder = new ArrayList<>();
    private int roundRobinIndex = 0;

    // Configuration
    private boolean useOpus = true; // Default to Opus

    private AudioTransferManager() {}

    public static AudioTransferManager getInstance() {
        if (instance == null) {
            instance = new AudioTransferManager();
        }
        return instance;
    }

    public void setUseOpus(boolean useOpus) {
        this.useOpus = useOpus;
    }

    public boolean isUseOpus() {
        return useOpus;
    }

    public void queueTransfer(ServerPlayer player, UUID resourceId) {
        queueTransfer(player, resourceId, 0);
    }

    public void queueTransfer(ServerPlayer player, UUID resourceId, long startPositionMs) {
        ServerAudioStorage storage = ServerAudioStorage.getInstance();
        Path audioPath = storage.getAudioPath(resourceId).orElse(null);
        if (audioPath == null) {
            Phonon.LOGGER.warn("Cannot queue transfer: audio {} not found on server", resourceId);
            return;
        }

        UUID playerId = player.getUUID();
        Queue<PendingTransfer> queue = playerQueues.computeIfAbsent(playerId, k -> new ConcurrentLinkedQueue<>());

        for (PendingTransfer t : queue) {
            if (t.resourceId.equals(resourceId)) {
                Phonon.LOGGER.debug("Transfer {} already queued for player {}", resourceId, player.getName().getString());
                return;
            }
        }

        PendingTransfer transfer;
        if (useOpus) {
            transfer = createOpusTransfer(resourceId, audioPath, startPositionMs, storage);
        } else {
            transfer = createVorbisTransfer(resourceId, audioPath, startPositionMs, storage);
        }

        if (transfer == null) {
            Phonon.LOGGER.error("Failed to create transfer for {}", resourceId);
            return;
        }

        queue.add(transfer);

        synchronized (activePlayerOrder) {
            if (!activePlayerOrder.contains(playerId)) {
                activePlayerOrder.add(playerId);
            }
        }

        Phonon.LOGGER.info("Queued {} transfer {} for player {} ({} packets, startPositionMs={})",
            transfer.isOpus ? "Opus" : "Vorbis",
            resourceId, player.getName().getString(),
            transfer.isOpus ? transfer.opusPackets.size() : transfer.totalChunks,
            startPositionMs);
    }

    private PendingTransfer createOpusTransfer(UUID resourceId, Path audioPath, long startPositionMs,
                                               ServerAudioStorage storage) {
        // Check cache first
        TranscodedAudio cached = transcodeCache.get(resourceId);
        if (cached == null) {
            // Transcode OGG to Opus
            VorbisToOpusTranscoder.TranscodeResult result = VorbisToOpusTranscoder.transcode(audioPath);
            if (result == null) {
                Phonon.LOGGER.error("Failed to transcode {} to Opus", audioPath);
                return null;
            }

            cached = new TranscodedAudio(result.packets(), result.durationMs(), 1);
            transcodeCache.put(resourceId, cached);
            Phonon.LOGGER.debug("Transcoded {} to {} Opus packets", resourceId, result.packets().size());
        }

        // Calculate start sequence based on position
        int startSequence = 0;
        if (startPositionMs > 0) {
            startSequence = (int) (startPositionMs / OpusEncoder.FRAME_DURATION_MS);
            startSequence = Math.min(startSequence, cached.packets.size() - 1);
            startSequence = Math.max(0, startSequence);
        }

        return new PendingTransfer(
            resourceId,
            audioPath,
            cached.packets,
            cached.durationMs,
            cached.channels,
            startSequence,
            startPositionMs
        );
    }

    private PendingTransfer createVorbisTransfer(UUID resourceId, Path audioPath, long startPositionMs,
                                                 ServerAudioStorage storage) {
        long fileSize = storage.getAudioSize(resourceId);
        AudioStreamInfo streamInfo = storage.getStreamInfo(resourceId).orElse(null);

        int startOffset;
        long dataSize;
        if (streamInfo != null) {
            startOffset = streamInfo.findOffsetForTime(startPositionMs);
            dataSize = fileSize - startOffset;
        } else {
            startOffset = 0;
            dataSize = fileSize;
        }

        int totalChunks = (int) Math.ceil((double) dataSize / PhononServerConfig.getChunkSize());

        return new PendingTransfer(resourceId, audioPath, totalChunks, startPositionMs, streamInfo, startOffset);
    }

    public void tick(net.minecraft.server.MinecraftServer server) {
        if (activePlayerOrder.isEmpty()) return;

        int totalBytesSent = 0;
        Map<UUID, Integer> playerBytesSent = new HashMap<>();

        synchronized (activePlayerOrder) {
            activePlayerOrder.removeIf(playerId -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    playerQueues.remove(playerId);
                    return true;
                }
                return false;
            });

            if (activePlayerOrder.isEmpty()) return;

            int playersProcessed = 0;
            int maxIterations = activePlayerOrder.size() * 10;
            int iterations = 0;

            int maxBytesPerTick = PhononServerConfig.getMaxBytesPerTick();
            int maxBytesPerPlayerPerTick = PhononServerConfig.getMaxBytesPerPlayerPerTick();

            while (totalBytesSent < maxBytesPerTick && playersProcessed < activePlayerOrder.size() && iterations < maxIterations) {
                iterations++;

                if (roundRobinIndex >= activePlayerOrder.size()) {
                    roundRobinIndex = 0;
                }

                UUID playerId = activePlayerOrder.get(roundRobinIndex);
                Queue<PendingTransfer> queue = playerQueues.get(playerId);

                if (queue == null || queue.isEmpty()) {
                    activePlayerOrder.remove(roundRobinIndex);
                    playerQueues.remove(playerId);
                    continue;
                }

                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    activePlayerOrder.remove(roundRobinIndex);
                    playerQueues.remove(playerId);
                    continue;
                }

                int playerBytes = playerBytesSent.getOrDefault(playerId, 0);
                if (playerBytes >= maxBytesPerPlayerPerTick) {
                    roundRobinIndex++;
                    playersProcessed++;
                    continue;
                }

                PendingTransfer transfer = queue.peek();
                if (transfer == null) {
                    roundRobinIndex++;
                    continue;
                }

                int bytesSent;
                if (transfer.isOpus) {
                    bytesSent = sendOpusPacket(player, transfer, queue);
                } else {
                    bytesSent = sendVorbisChunk(player, transfer, queue);
                }

                if (bytesSent > 0) {
                    totalBytesSent += bytesSent;
                    playerBytesSent.merge(playerId, bytesSent, Integer::sum);
                }

                roundRobinIndex++;
            }
        }
    }

    private int sendOpusPacket(ServerPlayer player, PendingTransfer transfer, Queue<PendingTransfer> queue) {
        // Send stream start packet first
        if (!transfer.headerSent) {
            OpusStreamStartPacket startPacket = new OpusStreamStartPacket(
                transfer.resourceId,
                transfer.opusChannels,
                transfer.opusDurationMs,
                transfer.opusPackets.size(),
                transfer.opusStartSequence,
                transfer.startPositionMs
            );
            PacketDistributor.sendToPlayer(player, startPacket);
            transfer.headerSent = true;
        }

        // Send next Opus packet
        int currentIndex = transfer.opusStartSequence + transfer.nextChunkIndex;
        if (currentIndex >= transfer.opusPackets.size()) {
            queue.poll();
            Phonon.LOGGER.info("Opus transfer complete: {} to {}", transfer.resourceId, player.getName().getString());

            if (queue.isEmpty()) {
                synchronized (activePlayerOrder) {
                    activePlayerOrder.remove(player.getUUID());
                }
                playerQueues.remove(player.getUUID());
            }
            return 0;
        }

        byte[] opusData = transfer.opusPackets.get(currentIndex);
        OpusPacket packet = new OpusPacket(
            transfer.resourceId,
            currentIndex,
            transfer.opusPackets.size(),
            opusData
        );

        PacketDistributor.sendToPlayer(player, packet);
        transfer.nextChunkIndex++;

        // Check completion
        if (transfer.opusStartSequence + transfer.nextChunkIndex >= transfer.opusPackets.size()) {
            queue.poll();
            Phonon.LOGGER.info("Opus transfer complete: {} to {}", transfer.resourceId, player.getName().getString());

            if (queue.isEmpty()) {
                synchronized (activePlayerOrder) {
                    activePlayerOrder.remove(player.getUUID());
                }
                playerQueues.remove(player.getUUID());
            }
        }

        return opusData.length;
    }

    private int sendVorbisChunk(ServerPlayer player, PendingTransfer transfer, Queue<PendingTransfer> queue) {
        if (!transfer.headerSent && transfer.streamInfo != null) {
            AudioStreamStartPacket startPacket = new AudioStreamStartPacket(
                transfer.resourceId,
                transfer.streamInfo.headerBytes(),
                transfer.streamInfo.sampleRate(),
                transfer.startOffset,
                transfer.startPositionMs
            );
            PacketDistributor.sendToPlayer(player, startPacket);
            transfer.headerSent = true;
        }

        int chunkSize = PhononServerConfig.getChunkSize();
        long readOffset = transfer.startOffset + (long) transfer.nextChunkIndex * chunkSize;
        byte[] chunkData = readChunkAt(transfer.audioPath, readOffset, chunkSize);

        if (chunkData != null) {
            AudioChunkPacket packet = new AudioChunkPacket(
                transfer.resourceId,
                transfer.nextChunkIndex,
                transfer.totalChunks,
                chunkData
            );

            PacketDistributor.sendToPlayer(player, packet);
            transfer.nextChunkIndex++;

            if (transfer.nextChunkIndex >= transfer.totalChunks) {
                queue.poll();
                Phonon.LOGGER.info("Vorbis transfer complete: {} to {}", transfer.resourceId, player.getName().getString());

                if (queue.isEmpty()) {
                    synchronized (activePlayerOrder) {
                        activePlayerOrder.remove(player.getUUID());
                    }
                    playerQueues.remove(player.getUUID());
                }
            }

            return chunkData.length;
        } else {
            queue.poll();
            Phonon.LOGGER.error("Failed to read chunk at offset {} for {}", readOffset, transfer.resourceId);
            return 0;
        }
    }

    private byte[] readChunkAt(Path audioPath, long offset, int maxSize) {
        try (RandomAccessFile raf = new RandomAccessFile(audioPath.toFile(), "r")) {
            if (offset >= raf.length()) return null;

            raf.seek(offset);
            long remaining = raf.length() - offset;
            int bytesToRead = (int) Math.min(maxSize, remaining);

            byte[] buffer = new byte[bytesToRead];
            raf.readFully(buffer);
            return buffer;

        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to read audio chunk", e);
            return null;
        }
    }

    public boolean hasPendingTransfer(UUID playerId, UUID resourceId) {
        Queue<PendingTransfer> queue = playerQueues.get(playerId);
        if (queue == null) return false;
        return queue.stream().anyMatch(t -> t.resourceId.equals(resourceId));
    }

    public void cancelPlayerTransfers(UUID playerId) {
        playerQueues.remove(playerId);
        synchronized (activePlayerOrder) {
            activePlayerOrder.remove(playerId);
        }
    }

    public int getActiveTransferCount() {
        return playerQueues.values().stream().mapToInt(Queue::size).sum();
    }

    public void clearTranscodeCache() {
        transcodeCache.clear();
    }

    public void clearTranscodeCache(UUID resourceId) {
        transcodeCache.remove(resourceId);
    }

    public void shutdown() {
        playerQueues.clear();
        activePlayerOrder.clear();
        transcodeCache.clear();
    }

    // Cached transcoded audio
    private record TranscodedAudio(List<byte[]> packets, long durationMs, int channels) {}

    // Unified pending transfer supporting both Opus and Vorbis
    private static class PendingTransfer {
        final UUID resourceId;
        final Path audioPath;
        final boolean isOpus;

        // Opus fields
        final List<byte[]> opusPackets;
        final long opusDurationMs;
        final int opusChannels;
        final int opusStartSequence;

        // Vorbis fields
        final int totalChunks;
        final AudioStreamInfo streamInfo;
        final int startOffset;

        // Common
        final long startPositionMs;
        int nextChunkIndex = 0;
        boolean headerSent = false;

        // Opus constructor
        PendingTransfer(UUID resourceId, Path audioPath, List<byte[]> opusPackets,
                        long durationMs, int channels, int startSequence, long startPositionMs) {
            this.resourceId = resourceId;
            this.audioPath = audioPath;
            this.isOpus = true;
            this.opusPackets = opusPackets;
            this.opusDurationMs = durationMs;
            this.opusChannels = channels;
            this.opusStartSequence = startSequence;
            this.startPositionMs = startPositionMs;

            // Unused Vorbis fields
            this.totalChunks = 0;
            this.streamInfo = null;
            this.startOffset = 0;
        }

        // Vorbis constructor
        PendingTransfer(UUID resourceId, Path audioPath, int totalChunks, long startPositionMs,
                        AudioStreamInfo streamInfo, int startOffset) {
            this.resourceId = resourceId;
            this.audioPath = audioPath;
            this.isOpus = false;
            this.totalChunks = totalChunks;
            this.startPositionMs = startPositionMs;
            this.streamInfo = streamInfo;
            this.startOffset = startOffset;

            // Unused Opus fields
            this.opusPackets = null;
            this.opusDurationMs = 0;
            this.opusChannels = 0;
            this.opusStartSequence = 0;
        }
    }
}
