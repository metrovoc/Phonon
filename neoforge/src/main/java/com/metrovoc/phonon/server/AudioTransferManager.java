package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.AudioStreamInfo;
import com.metrovoc.phonon.audio.OggPageScanner;
import com.metrovoc.phonon.config.PhononServerConfig;
import com.metrovoc.phonon.network.packets.AudioChunkPacket;
import com.metrovoc.phonon.network.packets.AudioStreamStartPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fair server-side stream scheduler. Disk reads are prefetched off-thread so a
 * cold filesystem page never stalls the server tick.
 */
public final class AudioTransferManager {
    private static final int READ_AHEAD_CHUNKS = 2;
    private static final int MAX_TRANSFERS_PER_PLAYER = 16;
    private static final int MAX_TOTAL_TRANSFERS = 256;

    private static AudioTransferManager instance;

    private final Map<UUID, ArrayDeque<PendingTransfer>> playerQueues = new HashMap<>();
    private final List<UUID> activePlayers = new ArrayList<>();
    private final ExecutorService readExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "Phonon-AudioRead");
        thread.setDaemon(true);
        return thread;
    });

    private int roundRobinIndex;

    private AudioTransferManager() {}

    public static AudioTransferManager getInstance() {
        if (instance == null) {
            instance = new AudioTransferManager();
        }
        return instance;
    }

    public static void reset() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    public void queueTransfer(
        ServerPlayer player,
        long streamId,
        UUID resourceId,
        long requestedPositionMs
    ) {
        ServerAudioStorage storage = ServerAudioStorage.getInstance();
        Path audioPath = storage.getAudioPath(resourceId).orElse(null);
        AudioStreamInfo streamInfo = storage.getStreamInfo(resourceId).orElse(null);
        if (audioPath == null || streamInfo == null) {
            Phonon.LOGGER.warn("Cannot stream invalid or missing audio {}", resourceId);
            return;
        }

        UUID playerId = player.getUUID();
        ArrayDeque<PendingTransfer> queue = playerQueues.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        if (queue.size() >= MAX_TRANSFERS_PER_PLAYER || getActiveTransferCount() >= MAX_TOTAL_TRANSFERS) {
            Phonon.LOGGER.warn("Rejected excess audio stream request from {}", player.getName().getString());
            if (queue.isEmpty()) {
                playerQueues.remove(playerId);
            }
            return;
        }
        if (queue.stream().anyMatch(transfer -> transfer.streamId == streamId)) {
            return;
        }

        long durationMs = streamInfo.durationMs();
        long positionMs = Math.max(0, requestedPositionMs);
        if (durationMs > 0) {
            positionMs = Math.min(positionMs, Math.max(0, durationMs - 1));
        }

        OggPageScanner.SeekPoint seekPoint = streamInfo.findSeekPoint(positionMs);
        long fileSize = storage.getAudioSize(resourceId);
        if (seekPoint.fileOffset() >= fileSize) {
            return;
        }

        int readSize = Math.max(1, Math.min(
            PhononServerConfig.getChunkSize(),
            Math.min(
                PhononServerConfig.getMaxBytesPerTick(),
                PhononServerConfig.getMaxBytesPerPlayerPerTick()
            )
        ));

        try {
            PendingTransfer transfer = new PendingTransfer(
                streamId,
                resourceId,
                audioPath,
                streamInfo,
                seekPoint,
                fileSize,
                readSize,
                readExecutor
            );
            queue.add(transfer);
            if (!activePlayers.contains(playerId)) {
                activePlayers.add(playerId);
            }

            Phonon.LOGGER.debug(
                "Queued stream {} for {} at {}ms (file offset {})",
                streamId,
                player.getName().getString(),
                seekPoint.timeMs(),
                seekPoint.fileOffset()
            );
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to open audio {} for streaming", resourceId, e);
            if (queue.isEmpty()) {
                playerQueues.remove(playerId);
            }
        }
    }

    public void tick(MinecraftServer server) {
        removeDisconnectedPlayers(server);
        if (activePlayers.isEmpty()) {
            return;
        }

        int maxTotal = PhononServerConfig.getMaxBytesPerTick();
        int maxPerPlayer = PhononServerConfig.getMaxBytesPerPlayerPerTick();
        int totalBytesSent = 0;
        Map<UUID, Integer> playerBytesSent = new HashMap<>();
        int consecutiveStalls = 0;

        while (!activePlayers.isEmpty()
            && totalBytesSent < maxTotal
            && consecutiveStalls < activePlayers.size()) {
            normalizeRoundRobinIndex();
            UUID playerId = activePlayers.get(roundRobinIndex);
            ArrayDeque<PendingTransfer> queue = playerQueues.get(playerId);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            if (player == null || queue == null || queue.isEmpty()) {
                removeActivePlayerAt(roundRobinIndex);
                continue;
            }

            int sentToPlayer = playerBytesSent.getOrDefault(playerId, 0);
            if (sentToPlayer >= maxPerPlayer) {
                advanceRoundRobin();
                consecutiveStalls++;
                continue;
            }

            PendingTransfer transfer = queue.peek();
            if (!transfer.headerSent) {
                PacketDistributor.sendToPlayer(player, transfer.createStartPacket());
                transfer.headerSent = true;
            }

            ChunkData chunk;
            try {
                chunk = transfer.peekReadyChunk();
            } catch (CompletionException e) {
                Phonon.LOGGER.error("Asynchronous read failed for stream {}", transfer.streamId, e.getCause());
                finishHeadTransfer(playerId, queue, false);
                consecutiveStalls = 0;
                continue;
            }

            if (chunk == null) {
                advanceRoundRobin();
                consecutiveStalls++;
                continue;
            }

            int remainingTotalBudget = maxTotal - totalBytesSent;
            int remainingPlayerBudget = maxPerPlayer - sentToPlayer;
            if (chunk.data.length > Math.min(remainingTotalBudget, remainingPlayerBudget)
                && (totalBytesSent > 0 || sentToPlayer > 0)) {
                advanceRoundRobin();
                consecutiveStalls++;
                continue;
            }

            chunk = transfer.consumeReadyChunk();

            PacketDistributor.sendToPlayer(player, new AudioChunkPacket(
                transfer.streamId,
                chunk.index,
                chunk.last,
                chunk.data
            ));
            totalBytesSent += chunk.data.length;
            playerBytesSent.put(playerId, sentToPlayer + chunk.data.length);
            consecutiveStalls = 0;

            if (chunk.last) {
                finishHeadTransfer(playerId, queue, true);
            } else {
                advanceRoundRobin();
            }
        }
    }

    private void removeDisconnectedPlayers(MinecraftServer server) {
        for (int index = activePlayers.size() - 1; index >= 0; index--) {
            UUID playerId = activePlayers.get(index);
            if (server.getPlayerList().getPlayer(playerId) == null) {
                closeQueue(playerQueues.remove(playerId));
                activePlayers.remove(index);
                if (index < roundRobinIndex) {
                    roundRobinIndex--;
                }
            }
        }
        normalizeRoundRobinIndex();
    }

    private void finishHeadTransfer(UUID playerId, ArrayDeque<PendingTransfer> queue, boolean completed) {
        PendingTransfer transfer = queue.poll();
        if (transfer != null) {
            transfer.close();
            Phonon.LOGGER.debug(
                "Audio stream {} {}",
                transfer.streamId,
                completed ? "completed" : "aborted"
            );
        }

        if (queue.isEmpty()) {
            playerQueues.remove(playerId);
            removeActivePlayerAt(roundRobinIndex);
        } else {
            advanceRoundRobin();
        }
    }

    private void normalizeRoundRobinIndex() {
        if (activePlayers.isEmpty()) {
            roundRobinIndex = 0;
        } else if (roundRobinIndex < 0 || roundRobinIndex >= activePlayers.size()) {
            roundRobinIndex = 0;
        }
    }

    private void advanceRoundRobin() {
        roundRobinIndex++;
        normalizeRoundRobinIndex();
    }

    private void removeActivePlayerAt(int index) {
        activePlayers.remove(index);
        normalizeRoundRobinIndex();
    }

    public void cancelTransfer(UUID playerId, long streamId) {
        ArrayDeque<PendingTransfer> queue = playerQueues.get(playerId);
        if (queue == null) {
            return;
        }

        Iterator<PendingTransfer> iterator = queue.iterator();
        while (iterator.hasNext()) {
            PendingTransfer transfer = iterator.next();
            if (transfer.streamId == streamId) {
                iterator.remove();
                transfer.close();
                break;
            }
        }

        if (queue.isEmpty()) {
            playerQueues.remove(playerId);
            int index = activePlayers.indexOf(playerId);
            if (index >= 0) {
                activePlayers.remove(index);
                if (index < roundRobinIndex) {
                    roundRobinIndex--;
                }
                normalizeRoundRobinIndex();
            }
        }
    }

    public void cancelPlayerTransfers(UUID playerId) {
        closeQueue(playerQueues.remove(playerId));
        int index = activePlayers.indexOf(playerId);
        if (index >= 0) {
            activePlayers.remove(index);
            if (index < roundRobinIndex) {
                roundRobinIndex--;
            }
            normalizeRoundRobinIndex();
        }
    }

    public int getActiveTransferCount() {
        return playerQueues.values().stream().mapToInt(ArrayDeque::size).sum();
    }

    public void shutdown() {
        for (ArrayDeque<PendingTransfer> queue : playerQueues.values()) {
            closeQueue(queue);
        }
        playerQueues.clear();
        activePlayers.clear();
        readExecutor.shutdownNow();
    }

    private static void closeQueue(ArrayDeque<PendingTransfer> queue) {
        if (queue != null) {
            queue.forEach(PendingTransfer::close);
            queue.clear();
        }
    }

    private static final class PendingTransfer implements AutoCloseable {
        private final long streamId;
        private final UUID resourceId;
        private final AudioStreamInfo streamInfo;
        private final OggPageScanner.SeekPoint seekPoint;
        private final long endOffset;
        private final int readSize;
        private final ExecutorService executor;
        private final FileChannel channel;
        private final ArrayDeque<CompletableFuture<ChunkData>> readAhead = new ArrayDeque<>();

        private long nextReadOffset;
        private int nextChunkIndex;
        private boolean headerSent;
        private volatile boolean closed;

        private PendingTransfer(
            long streamId,
            UUID resourceId,
            Path audioPath,
            AudioStreamInfo streamInfo,
            OggPageScanner.SeekPoint seekPoint,
            long endOffset,
            int readSize,
            ExecutorService executor
        ) throws IOException {
            this.streamId = streamId;
            this.resourceId = resourceId;
            this.streamInfo = streamInfo;
            this.seekPoint = seekPoint;
            this.endOffset = endOffset;
            this.readSize = readSize;
            this.executor = executor;
            this.channel = FileChannel.open(audioPath, StandardOpenOption.READ);
            this.nextReadOffset = seekPoint.fileOffset();
            fillReadAhead();
        }

        private AudioStreamStartPacket createStartPacket() {
            boolean cacheable = seekPoint.fileOffset() == streamInfo.headerBytes().length;
            return new AudioStreamStartPacket(
                streamId,
                resourceId,
                streamInfo.headerBytes(),
                streamInfo.sampleRate(),
                seekPoint.timeMs(),
                cacheable
            );
        }

        private ChunkData peekReadyChunk() {
            CompletableFuture<ChunkData> next = readAhead.peek();
            if (next == null || !next.isDone()) {
                return null;
            }

            return next.join();
        }

        private ChunkData consumeReadyChunk() {
            CompletableFuture<ChunkData> next = readAhead.poll();
            if (next == null) {
                throw new IllegalStateException("No prefetched chunk to consume");
            }
            ChunkData result = next.join();
            fillReadAhead();
            return result;
        }

        private void fillReadAhead() {
            while (!closed && readAhead.size() < READ_AHEAD_CHUNKS && nextReadOffset < endOffset) {
                long offset = nextReadOffset;
                int length = (int) Math.min(readSize, endOffset - offset);
                int index = nextChunkIndex++;
                boolean last = offset + length >= endOffset;
                nextReadOffset += length;

                readAhead.add(CompletableFuture.supplyAsync(
                    () -> readChunk(channel, offset, length, index, last),
                    executor
                ));
            }
        }

        private static ChunkData readChunk(
            FileChannel channel,
            long offset,
            int length,
            int index,
            boolean last
        ) {
            byte[] data = new byte[length];
            ByteBuffer target = ByteBuffer.wrap(data);
            try {
                while (target.hasRemaining()) {
                    int read = channel.read(target, offset + target.position());
                    if (read < 0) {
                        throw new IOException("Unexpected end of audio file");
                    }
                    if (read == 0) {
                        Thread.onSpinWait();
                    }
                }
                return new ChunkData(index, data, last);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            readAhead.forEach(future -> future.cancel(true));
            readAhead.clear();
            try {
                channel.close();
            } catch (IOException e) {
                Phonon.LOGGER.warn("Failed to close stream {}", streamId, e);
            }
        }
    }

    private record ChunkData(int index, byte[] data, boolean last) {}
}
