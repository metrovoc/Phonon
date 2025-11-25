package com.metrovoc.phonon.platform;

import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.audio.AudioPlayer;
import com.metrovoc.phonon.network.packets.RequestAudioPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class NeoForgePlatformHelper implements PlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    @Override
    public boolean isServer() {
        return FMLEnvironment.dist.isDedicatedServer();
    }

    @Override
    public void sendToClient(ServerPlayer player, Object packet) {
        if (packet instanceof CustomPacketPayload payload) {
            player.connection.send(new ClientboundCustomPayloadPacket(payload));
        }
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
            ChunkPos chunkPos = new ChunkPos(pos);
            serverLevel.getChunkSource().chunkMap.getPlayers(chunkPos, false).forEach(player -> {
                player.connection.send(new ClientboundCustomPayloadPacket(payload));
            });
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
    public void runOnClient(Runnable task) {
        if (isClient()) {
            Minecraft.getInstance().tell(task);
        }
    }

    @Override
    public void requestAudioFromServer(UUID resourceId) {
        if (isClient()) {
            PacketDistributor.sendToServer(new RequestAudioPacket(resourceId));
        }
    }
}
