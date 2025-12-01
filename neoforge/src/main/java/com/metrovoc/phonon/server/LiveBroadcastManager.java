package com.metrovoc.phonon.server;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.network.packets.AudioStreamStartPacket;
import com.metrovoc.phonon.network.packets.LiveBroadcastChunkPacket;
import com.metrovoc.phonon.network.packets.LiveBroadcastEndPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages live audio broadcasts on the server.
 * Forwards audio chunks from broadcaster to listeners in range.
 */
public class LiveBroadcastManager {
    private static LiveBroadcastManager instance;

    private final Map<UUID, LiveBroadcast> activeBroadcasts = new ConcurrentHashMap<>();
    private final Map<BlockPos, UUID> speakerToBroadcast = new ConcurrentHashMap<>();

    private MinecraftServer server;

    private LiveBroadcastManager() {}

    public static LiveBroadcastManager getInstance() {
        if (instance == null) {
            instance = new LiveBroadcastManager();
        }
        return instance;
    }

    public static void reset() {
        if (instance != null) {
            instance.activeBroadcasts.clear();
            instance.speakerToBroadcast.clear();
        }
        instance = null;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Start a live broadcast from a player to a speaker.
     */
    public void startBroadcast(ServerPlayer broadcaster, BlockPos speakerPos, UUID streamId, String deviceName) {
        // End any existing broadcast on this speaker
        UUID existing = speakerToBroadcast.get(speakerPos);
        if (existing != null) {
            endBroadcast(existing);
        }

        LiveBroadcast broadcast = new LiveBroadcast(
            streamId,
            broadcaster,
            speakerPos,
            deviceName,
            System.currentTimeMillis()
        );

        activeBroadcasts.put(streamId, broadcast);
        speakerToBroadcast.put(speakerPos, streamId);

        Phonon.LOGGER.info("Live broadcast started: {} at {} by {}",
            streamId, speakerPos, broadcaster.getName().getString());

        // Notify nearby players that a live stream is starting
        notifyListenersOfStart(broadcast);
    }

    /**
     * Forward audio chunk to all listeners.
     */
    public void forwardChunk(UUID streamId, byte[] data) {
        LiveBroadcast broadcast = activeBroadcasts.get(streamId);
        if (broadcast == null) return;

        // First chunk might contain header - track it
        if (!broadcast.headerSent && data.length > 0) {
            broadcast.headerBytes = data;
            broadcast.headerSent = true;
        }

        // Forward to all players tracking this speaker (except broadcaster)
        LiveBroadcastChunkPacket packet = new LiveBroadcastChunkPacket(streamId, data);

        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(broadcast.broadcaster.getUUID())) continue;

            double distance = player.blockPosition().distSqr(broadcast.speakerPos);
            if (distance <= 64 * 64) { // 64 blocks range
                PacketDistributor.sendToPlayer(player, packet);
            }
        }

        broadcast.chunksSent++;
    }

    /**
     * End a live broadcast.
     */
    public void endBroadcast(UUID streamId) {
        LiveBroadcast broadcast = activeBroadcasts.remove(streamId);
        if (broadcast == null) return;

        speakerToBroadcast.remove(broadcast.speakerPos);

        Phonon.LOGGER.info("Live broadcast ended: {} ({} chunks sent)",
            streamId, broadcast.chunksSent);

        // Notify listeners
        notifyListenersOfEnd(broadcast);
    }

    /**
     * Get broadcast for a speaker position.
     */
    public Optional<LiveBroadcast> getBroadcastForSpeaker(BlockPos pos) {
        UUID streamId = speakerToBroadcast.get(pos);
        if (streamId == null) return Optional.empty();
        return Optional.ofNullable(activeBroadcasts.get(streamId));
    }

    /**
     * Check if a speaker has an active broadcast.
     */
    public boolean hasBroadcast(BlockPos pos) {
        return speakerToBroadcast.containsKey(pos);
    }

    /**
     * End all broadcasts by a player (e.g., on disconnect).
     */
    public void endBroadcastsByPlayer(UUID playerId) {
        List<UUID> toEnd = new ArrayList<>();
        for (LiveBroadcast broadcast : activeBroadcasts.values()) {
            if (broadcast.broadcaster.getUUID().equals(playerId)) {
                toEnd.add(broadcast.streamId);
            }
        }
        toEnd.forEach(this::endBroadcast);
    }

    private void notifyListenersOfStart(LiveBroadcast broadcast) {
        if (server == null) return;

        // Send stream start packet to nearby players
        // They'll create a download session marked as live
        AudioStreamStartPacket startPacket = new AudioStreamStartPacket(
            broadcast.streamId,
            new byte[0], // Header will come with first chunk
            44100,       // Default sample rate
            0,
            0,
            true         // isLiveStream
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(broadcast.broadcaster.getUUID())) continue;

            double distance = player.blockPosition().distSqr(broadcast.speakerPos);
            if (distance <= 64 * 64) {
                PacketDistributor.sendToPlayer(player, startPacket);
            }
        }
    }

    private void notifyListenersOfEnd(LiveBroadcast broadcast) {
        if (server == null) return;

        LiveBroadcastEndPacket endPacket = new LiveBroadcastEndPacket(broadcast.streamId);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(broadcast.broadcaster.getUUID())) continue;

            double distance = player.blockPosition().distSqr(broadcast.speakerPos);
            if (distance <= 64 * 64) {
                PacketDistributor.sendToPlayer(player, endPacket);
            }
        }
    }

    /**
     * Active broadcast record.
     */
    public static class LiveBroadcast {
        public final UUID streamId;
        public final ServerPlayer broadcaster;
        public final BlockPos speakerPos;
        public final String deviceName;
        public final long startTime;

        byte[] headerBytes;
        boolean headerSent = false;
        int chunksSent = 0;

        LiveBroadcast(UUID streamId, ServerPlayer broadcaster, BlockPos speakerPos,
                      String deviceName, long startTime) {
            this.streamId = streamId;
            this.broadcaster = broadcaster;
            this.speakerPos = speakerPos;
            this.deviceName = deviceName;
            this.startTime = startTime;
        }
    }
}
