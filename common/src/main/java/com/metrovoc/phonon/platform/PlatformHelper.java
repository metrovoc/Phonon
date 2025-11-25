package com.metrovoc.phonon.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ServiceLoader;

/**
 * Platform abstraction layer.
 * Common defines interface, loaders implement specifics.
 */
public interface PlatformHelper {

    PlatformHelper INSTANCE = ServiceLoader.load(PlatformHelper.class)
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Failed to load PlatformHelper"));

    String getPlatformName();

    boolean isClient();

    boolean isServer();

    void sendToClient(ServerPlayer player, Object packet);

    void sendToServer(Object packet);

    void sendToAllTracking(Level level, BlockPos pos, Object packet);
}
