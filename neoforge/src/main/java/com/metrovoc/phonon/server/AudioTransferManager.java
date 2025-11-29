package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.AudioStreamInfo;
import com.metrovoc.phonon.config.PhononServerConfig;
import com.metrovoc.phonon.network.packets.AudioChunkPacket;
import com.metrovoc.phonon.network.packets.AudioStreamStartPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioTransferManager {

    private static AudioTransferManager instance;

    private final Map<UUID, Queue<PendingTransfer>> playerQueues = new ConcurrentHashMap<>();
    private final List<UUID> activePlayerOrder = new ArrayList<>();
    private int roundRobinIndex = 0;

    private AudioTransferManager() {}

    public static AudioTransferManager getInstance() {
        if (instance == null) {
            instance = new AudioTransferManager();
        }
        return instance;
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
        PendingTransfer transfer = new PendingTransfer(resourceId, audioPath, totalChunks, startPositionMs, streamInfo, startOffset);
        queue.add(transfer);

        synchronized (activePlayerOrder) {
            if (!activePlayerOrder.contains(playerId)) {
                activePlayerOrder.add(playerId);
            }
        }

        Phonon.LOGGER.info("Queued transfer {} for player {} ({} chunks, startOffset={}, startPositionMs={})",
            resourceId, player.getName().getString(), totalChunks, startOffset, startPositionMs);
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

                    totalBytesSent += chunkData.length;
                    playerBytesSent.merge(playerId, chunkData.length, Integer::sum);

                    transfer.nextChunkIndex++;

                    if (transfer.nextChunkIndex >= transfer.totalChunks) {
                        queue.poll();
                        Phonon.LOGGER.info("Transfer complete: {} to {}", transfer.resourceId, player.getName().getString());

                        if (queue.isEmpty()) {
                            activePlayerOrder.remove(roundRobinIndex);
                            playerQueues.remove(playerId);
                        }
                    }
                } else {
                    queue.poll();
                    Phonon.LOGGER.error("Failed to read chunk at offset {} for {}", readOffset, transfer.resourceId);
                }

                roundRobinIndex++;
            }
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

    public void shutdown() {
        playerQueues.clear();
        activePlayerOrder.clear();
    }

    private static class PendingTransfer {
        final UUID resourceId;
        final Path audioPath;
        final int totalChunks;
        final long startPositionMs;
        final AudioStreamInfo streamInfo;
        final int startOffset;
        int nextChunkIndex = 0;
        boolean headerSent = false;

        PendingTransfer(UUID resourceId, Path audioPath, int totalChunks, long startPositionMs,
                        AudioStreamInfo streamInfo, int startOffset) {
            this.resourceId = resourceId;
            this.audioPath = audioPath;
            this.totalChunks = totalChunks;
            this.startPositionMs = startPositionMs;
            this.streamInfo = streamInfo;
            this.startOffset = startOffset;
        }
    }
}
