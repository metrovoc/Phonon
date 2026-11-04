package com.metrovoc.phonon.platform;

import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.audio.StreamingAudioStream;
import com.metrovoc.phonon.server.LibraryJob;
import com.metrovoc.phonon.server.SearchResult;
import com.metrovoc.phonon.server.SpeakerAction;
import com.metrovoc.phonon.station.StationView;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.station.StationListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
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

    void broadcastSpeakerState(StationListener listener);

    void sendToPlayer(ServerPlayer player, Object packet);

    /** Where a boombox is, for a player who can hear it. */
    void sendBoomboxPlace(ServerPlayer player, UUID id, int entityId, Vec3 position);

    /** A player can no longer hear this boombox. */
    void sendBoomboxGone(ServerPlayer player, UUID id);

    /** What a boombox is doing, for one player who can now hear it. */
    void sendListenerState(ServerPlayer player, StationListener listener);

    void sendStationView(ServerPlayer player, StationView view);

    /** A private station's details, for players near its speaker. */
    void broadcastStationView(StationListener listener, StationView view);

    /** Shared stations and playlists, for everyone. */
    void broadcastStations(MinecraftServer server);

    void broadcastLibraryJobs(MinecraftServer server, List<LibraryJob> jobs);

    void sendSearchResults(ServerPlayer player, int searchId, List<SearchResult> results, String failureKey);

    void sendSearchThumbnail(ServerPlayer player, int searchId, String videoId, byte[] png);

    // Speaker controls (only called on client)
    void sendSpeakerAction(Receiver receiver, SpeakerAction action, UUID target, UUID second, int number, String text);

    void seekSpeaker(Receiver receiver, long positionMs);

    void setSpeakerVolume(Receiver receiver, float volume);

    // Client-side audio (only called on client)
    void playAudio(Receiver receiver, PlaybackState playback, UUID resourceId, float volume);

    void stopAudio(Receiver receiver);

    void setAudioVolume(Receiver receiver, float volume);

    /** Fills bars with recent loudness from 0 to 1, newest last, or zeros when nothing is audible. */
    void readAudioLevels(Receiver receiver, long positionMs, float[] bars, int msPerBar);

    void stopAllAudio();

    void playStreamingAudio(Receiver receiver, StreamingAudioStream stream, float volume);

    // Client-side utilities
    void runOnClient(Runnable task);

    void requestAudioFromServer(long streamId, UUID resourceId, long startPositionMs);

    void cancelAudioStream(long streamId);

    long getEstimatedOneWayLatencyMs();
}
