package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Client-side chunk receiver and assembler.
 * Receives chunks from server, assembles complete files, writes to cache.
 */
public class AudioReceiver {

    private static final AudioReceiver instance = new AudioReceiver();

    // Active transfers: resourceId -> PendingTransfer
    private final Map<UUID, PendingTransfer> pendingTransfers = new ConcurrentHashMap<>();

    // Callbacks waiting for transfer completion
    private final Map<UUID, Consumer<Path>> completionCallbacks = new ConcurrentHashMap<>();

    private Path cacheDir;

    private AudioReceiver() {}

    public static AudioReceiver getInstance() {
        return instance;
    }

    public void initialize(Path gameDir) {
        this.cacheDir = gameDir.resolve("phonon_cache");
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to create cache directory", e);
        }
    }

    /**
     * Register callback for when a transfer completes.
     */
    public void onTransferComplete(UUID resourceId, Consumer<Path> callback) {
        // Check if already cached
        Path cached = cacheDir.resolve(resourceId + ".ogg");
        if (Files.exists(cached)) {
            Minecraft.getInstance().tell(() -> callback.accept(cached));
            return;
        }

        // Check if transfer already complete
        PendingTransfer transfer = pendingTransfers.get(resourceId);
        if (transfer != null && transfer.isComplete()) {
            Minecraft.getInstance().tell(() -> callback.accept(transfer.outputPath));
            return;
        }

        completionCallbacks.put(resourceId, callback);
    }

    /**
     * Receive a chunk from server.
     */
    public void receiveChunk(UUID resourceId, int chunkIndex, int totalChunks, byte[] data) {
        PendingTransfer transfer = pendingTransfers.computeIfAbsent(resourceId,
            id -> new PendingTransfer(id, totalChunks, cacheDir.resolve(id + ".ogg")));

        transfer.addChunk(chunkIndex, data);

        if (transfer.isComplete()) {
            finalizeTransfer(resourceId, transfer);
        }
    }

    private void finalizeTransfer(UUID resourceId, PendingTransfer transfer) {
        try {
            byte[] completeData = transfer.assemble();
            Files.write(transfer.outputPath, completeData);

            // Register in AudioCache
            AudioCache.getInstance().registerCachedFile(resourceId, transfer.outputPath);

            Phonon.LOGGER.info("Audio transfer complete: {} ({} bytes)", resourceId, completeData.length);

            // Trigger callback
            Consumer<Path> callback = completionCallbacks.remove(resourceId);
            if (callback != null) {
                Minecraft.getInstance().tell(() -> callback.accept(transfer.outputPath));
            }

            pendingTransfers.remove(resourceId);

        } catch (IOException e) {
            Phonon.LOGGER.error("Failed to write audio file", e);
            pendingTransfers.remove(resourceId);
            completionCallbacks.remove(resourceId);
        }
    }

    /**
     * Check if a transfer is in progress for the given resource.
     */
    public boolean isTransferInProgress(UUID resourceId) {
        return pendingTransfers.containsKey(resourceId);
    }

    /**
     * Get transfer progress (0.0 - 1.0).
     */
    public float getTransferProgress(UUID resourceId) {
        PendingTransfer transfer = pendingTransfers.get(resourceId);
        if (transfer == null) return 0f;
        return transfer.getProgress();
    }

    /**
     * Tracks chunks for a single file transfer.
     */
    private static class PendingTransfer {
        final UUID resourceId;
        final int totalChunks;
        final Path outputPath;
        final byte[][] chunks;
        int receivedCount = 0;

        PendingTransfer(UUID resourceId, int totalChunks, Path outputPath) {
            this.resourceId = resourceId;
            this.totalChunks = totalChunks;
            this.outputPath = outputPath;
            this.chunks = new byte[totalChunks][];
        }

        synchronized void addChunk(int index, byte[] data) {
            if (index >= 0 && index < totalChunks && chunks[index] == null) {
                chunks[index] = data;
                receivedCount++;
            }
        }

        synchronized boolean isComplete() {
            return receivedCount == totalChunks;
        }

        synchronized float getProgress() {
            return totalChunks > 0 ? (float) receivedCount / totalChunks : 0f;
        }

        synchronized byte[] assemble() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    out.write(chunk);
                }
            }
            return out.toByteArray();
        }
    }
}
