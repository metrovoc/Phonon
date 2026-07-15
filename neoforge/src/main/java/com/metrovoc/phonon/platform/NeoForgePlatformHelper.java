package com.metrovoc.phonon.platform;

import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.audio.AudioPlayer;
import com.metrovoc.phonon.client.audio.StreamingAudioStream;
import com.metrovoc.phonon.network.packets.RequestAudioPacket;
import com.metrovoc.phonon.network.packets.CancelAudioStreamPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class NeoForgePlatformHelper implements PlatformHelper {

    private boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    @Override
    public void sendToServer(Object packet) {
        if (packet instanceof CustomPacketPayload payload) {
            PacketDistributor.sendToServer(payload);
        }
    }

    @Override
    public void sendToAllTracking(Level level, BlockPos pos, Object packet) {
        if (level instanceof ServerLevel serverLevel && packet instanceof CustomPacketPayload payload) {
            PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(pos), payload);
        }
    }

    // Client-side audio methods - delegate to AudioPlayer

    @Override
    public void playAudio(BlockPos pos, PlaybackState playback, UUID resourceId, float volume) {
        if (isClient()) {
            AudioPlayer.getInstance().play(pos, playback, resourceId, volume);
        }
    }

    @Override
    public void stopAudio(BlockPos pos) {
        if (isClient()) {
            AudioPlayer.getInstance().stop(pos);
        }
    }

    @Override
    public void setAudioVolume(BlockPos pos, float volume) {
        if (isClient()) {
            AudioPlayer.getInstance().setVolume(pos, volume);
        }
    }

    @Override
    public void stopAllAudio() {
        if (isClient()) {
            AudioPlayer.getInstance().stopAll();
        }
    }

    @Override
    public void playStreamingAudio(BlockPos pos, StreamingAudioStream stream, float volume) {
        if (isClient()) {
            AudioPlayer.getInstance().playStreaming(pos, stream, volume);
        }
    }

    @Override
    public void runOnClient(Runnable task) {
        if (isClient()) {
            Minecraft.getInstance().execute(task);
        }
    }

    @Override
    public void requestAudioFromServer(long streamId, UUID resourceId, long startPositionMs) {
        if (isClient() && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new RequestAudioPacket(streamId, resourceId, startPositionMs));
        }
    }

    @Override
    public void cancelAudioStream(long streamId) {
        if (isClient() && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new CancelAudioStreamPacket(streamId));
        }
    }

    @Override
    public long getEstimatedOneWayLatencyMs() {
        if (!isClient()) {
            return 0;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return 0;
        }
        var playerInfo = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        return playerInfo == null ? 0 : Math.max(0, playerInfo.getLatency() / 2L);
    }
}
