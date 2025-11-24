package com.metrovoc.phonon.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;

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
}
