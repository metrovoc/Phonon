package com.metrovoc.phonon.platform;

import com.metrovoc.phonon.audio.PlaybackState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ServiceLoader;
import java.util.UUID;

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

    // Network
    void sendToClient(ServerPlayer player, Object packet);

    void sendToServer(Object packet);

    void sendToAllTracking(Level level, BlockPos pos, Object packet);

    // Client-side audio (only called on client)
    void playAudio(BlockPos pos, PlaybackState playback, UUID resourceId, float volume);

    void stopAudio(BlockPos pos);

    void setAudioVolume(BlockPos pos, float volume);

    void stopAllAudio();

    // Client-side utilities
    void runOnClient(Runnable task);

    void requestAudioFromServer(UUID resourceId);
}
