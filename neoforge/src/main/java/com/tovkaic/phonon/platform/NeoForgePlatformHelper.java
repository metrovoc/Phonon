package com.tovkaic.phonon.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLEnvironment;

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
        // TODO: Implement NeoForge packet sending
    }

    @Override
    public void sendToServer(Object packet) {
        // TODO: Implement NeoForge packet sending
    }

    @Override
    public void sendToAllTracking(Level level, BlockPos pos, Object packet) {
        // TODO: Implement NeoForge packet broadcasting
    }
}
