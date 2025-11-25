package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.network.packets.AudioChunkPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Server-side audio transfer manager with flow control.
 * Schedules chunk transfers across multiple players to protect TPS.
 *
 * Flow control strategy:
 * - Max bytes per tick: 128KB total (~2.5MB/s @ 20 TPS)
 * - Max bytes per player per tick: 64KB
 * - Round-robin scheduling across players
 */
public class AudioTransferManager {

    private static final int MAX_BYTES_PER_TICK = 128 * 1024;
    private static final int MAX_BYTES_PER_PLAYER_PER_TICK = 64 * 1024;

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
        Path audioPath = com.metrovoc.phonon.server.ServerAudioStorage.getInstance().getAudioPath(resourceId).orElse(null);
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

        long fileSize = com.metrovoc.phonon.server.ServerAudioStorage.getInstance().getAudioSize(resourceId);
        int totalChunks = (int) Math.ceil((double) fileSize / com.metrovoc.phonon.server.ServerAudioStorage.CHUNK_SIZE);

        PendingTransfer transfer = new PendingTransfer(resourceId, audioPath, totalChunks, fileSize);
        queue.add(transfer);

        synchronized (activePlayerOrder) {
            if (!activePlayerOrder.contains(playerId)) {
                activePlayerOrder.add(playerId);
            }
        }

        Phonon.LOGGER.info("Queued transfer {} for player {} ({} chunks, {} bytes)",
            resourceId, player.getName().getString(), totalChunks, fileSize);
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

            while (totalBytesSent < MAX_BYTES_PER_TICK && playersProcessed < activePlayerOrder.size() && iterations < maxIterations) {
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
                if (playerBytes >= MAX_BYTES_PER_PLAYER_PER_TICK) {
                    roundRobinIndex++;
                    playersProcessed++;
                    continue;
                }

                PendingTransfer transfer = queue.peek();
                if (transfer == null) {
                    roundRobinIndex++;
                    continue;
                }

                int chunkIndex = transfer.nextChunkIndex;
                byte[] chunkData = readChunk(transfer.audioPath, chunkIndex);

                if (chunkData != null) {
                    AudioChunkPacket packet = new AudioChunkPacket(
                        transfer.resourceId,
                        chunkIndex,
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
                    Phonon.LOGGER.error("Failed to read chunk {} for {}", chunkIndex, transfer.resourceId);
                }

                roundRobinIndex++;
            }
        }
    }

    private byte[] readChunk(Path audioPath, int chunkIndex) {
        try (RandomAccessFile raf = new RandomAccessFile(audioPath.toFile(), "r")) {
            long offset = (long) chunkIndex * com.metrovoc.phonon.server.ServerAudioStorage.CHUNK_SIZE;
            raf.seek(offset);

            long remaining = raf.length() - offset;
            int bytesToRead = (int) Math.min(com.metrovoc.phonon.server.ServerAudioStorage.CHUNK_SIZE, remaining);

            if (bytesToRead <= 0) return null;

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
        final long fileSize;
        int nextChunkIndex = 0;

        PendingTransfer(UUID resourceId, Path audioPath, int totalChunks, long fileSize) {
            this.resourceId = resourceId;
            this.audioPath = audioPath;
            this.totalChunks = totalChunks;
            this.fileSize = fileSize;
        }
    }
}
