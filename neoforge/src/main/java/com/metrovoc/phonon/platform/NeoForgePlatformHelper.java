package com.metrovoc.phonon.platform;

import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.client.audio.NeoForgeSpeakerSound;
import com.metrovoc.phonon.client.audio.SpeakerSoundInstance;
import com.metrovoc.phonon.config.NeoForgeServerConfig;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;

public class NeoForgePlatformHelper implements PlatformHelper {

    @Override
    public boolean isClient() {
        return FMLEnvironment.getDist().isClient();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk, payload);
    }

    @Override
    public void sendToAllPlayers(MinecraftServer server, CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }

    @Override
    public SpeakerSoundInstance newSpeakerSound(AudioStream stream, Receiver receiver, float volume) {
        return NeoForgeSpeakerSound.create(stream, receiver, volume);
    }

    @Override
    public void reloadServerConfig() {
        NeoForgeServerConfig.SPEC.afterReload();
        NeoForgeServerConfig.bind();
    }
}
