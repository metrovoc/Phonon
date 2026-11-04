package com.metrovoc.phonon.platform;

import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.client.audio.StreamingAudioStream;
import com.metrovoc.phonon.server.LibraryJob;
import com.metrovoc.phonon.server.SpeakerAction;
import com.metrovoc.phonon.station.StationView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.List;
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

    // Network
    void sendToServer(Object packet);

    void sendToAllTracking(Level level, BlockPos pos, Object packet);

    void broadcastAudioResources(MinecraftServer server);

    void broadcastSpeakerState(Level level, BlockPos pos, SpeakerBlockEntity speaker);

    /** A private station's details, for players near its speaker. */
    void broadcastStationView(Level level, BlockPos pos, StationView view);

    /** Shared stations and playlists, for everyone. */
    void broadcastStations(MinecraftServer server);

    void broadcastLibraryJobs(MinecraftServer server, List<LibraryJob> jobs);

    // Speaker controls (only called on client)
    void sendSpeakerAction(BlockPos pos, SpeakerAction action, UUID target, UUID second, int number, String text);

    void seekSpeaker(BlockPos pos, long positionMs);

    void setSpeakerVolume(BlockPos pos, float volume);

    // Client-side audio (only called on client)
    void playAudio(BlockPos pos, PlaybackState playback, UUID resourceId, float volume);

    void stopAudio(BlockPos pos);

    void setAudioVolume(BlockPos pos, float volume);

    /** Fills bars with recent loudness from 0 to 1, newest last, or zeros when nothing is audible. */
    void readAudioLevels(BlockPos pos, long positionMs, float[] bars, int msPerBar);

    void stopAllAudio();

    void playStreamingAudio(BlockPos pos, StreamingAudioStream stream, float volume);

    // Client-side utilities
    void runOnClient(Runnable task);

    void requestAudioFromServer(long streamId, UUID resourceId, long startPositionMs);

    void cancelAudioStream(long streamId);

    long getEstimatedOneWayLatencyMs();
}
