package com.metrovoc.phonon.platform;

import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.client.audio.FabricSpeakerSound;
import com.metrovoc.phonon.client.audio.SpeakerSoundInstance;
import com.metrovoc.phonon.config.FabricConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public class FabricPlatformHelper implements PlatformHelper {

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.tracking(level, chunk)) {
            sendToPlayer(player, payload);
        }
    }

    @Override
    public void sendToAllPlayers(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.all(server)) {
            sendToPlayer(player, payload);
        }
    }

    @Override
    public SpeakerSoundInstance newSpeakerSound(AudioStream stream, Receiver receiver, float volume) {
        return FabricSpeakerSound.create(stream, receiver, volume);
    }

    @Override
    public void reloadServerConfig() {
        FabricConfig.loadServer();
        MinecraftServer server = com.metrovoc.phonon.PhononFabric.server();
        if (server != null) {
            sendToAllPlayers(server, com.metrovoc.phonon.network.packets.SyncPermissionsPacket.current());
        }
    }
}
