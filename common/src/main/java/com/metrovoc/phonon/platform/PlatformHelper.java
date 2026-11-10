package com.metrovoc.phonon.platform;

import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.client.audio.AudioPlayer;
import com.metrovoc.phonon.client.audio.SpeakerSoundInstance;
import com.metrovoc.phonon.client.audio.StreamingAudioStream;
import com.metrovoc.phonon.network.packets.AddFailedPacket;
import com.metrovoc.phonon.network.packets.CancelAudioStreamPacket;
import com.metrovoc.phonon.network.packets.RequestAudioPacket;
import com.metrovoc.phonon.network.packets.SearchPacket;
import com.metrovoc.phonon.network.packets.SearchResultsPacket;
import com.metrovoc.phonon.network.packets.SearchThumbnailPacket;
import com.metrovoc.phonon.network.packets.SpeakerActionPacket;
import com.metrovoc.phonon.network.packets.SpeakerSeekPacket;
import com.metrovoc.phonon.network.packets.SpeakerVolumePacket;
import com.metrovoc.phonon.network.packets.SyncAudioResourcesPacket;
import com.metrovoc.phonon.network.packets.SyncBoomboxPlacePacket;
import com.metrovoc.phonon.network.packets.SyncLibraryJobsPacket;
import com.metrovoc.phonon.network.packets.SyncPermissionsPacket;
import com.metrovoc.phonon.network.packets.SyncSpeakerStatePacket;
import com.metrovoc.phonon.network.packets.SyncStationPacket;
import com.metrovoc.phonon.network.packets.SyncStationsPacket;
import com.metrovoc.phonon.server.AudioFailure;
import com.metrovoc.phonon.server.LibraryJob;
import com.metrovoc.phonon.server.SearchResult;
import com.metrovoc.phonon.server.SearchSource;
import com.metrovoc.phonon.server.SpeakerAction;
import com.metrovoc.phonon.station.StationListener;
import com.metrovoc.phonon.station.StationView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;

/**
 * Platform abstraction layer. Loaders implement the primitives at the top; everything below them is shared.
 */
public interface PlatformHelper {

    PlatformHelper INSTANCE = ServiceLoader.load(PlatformHelper.class)
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Failed to load PlatformHelper"));

    // Loader primitives

    boolean isClient();

    boolean isModLoaded(String modId);

    void sendToServer(CustomPacketPayload payload);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload);

    void sendToAllPlayers(MinecraftServer server, CustomPacketPayload payload);

    /** A sound that plays {@code stream} through the loader's way of giving the sound engine a custom stream. */
    SpeakerSoundInstance newSpeakerSound(AudioStream stream, Receiver receiver, float volume);

    /** Reads the server config file again and applies it. */
    void reloadServerConfig();

    // Network

    default void sendToAllTracking(Level level, BlockPos pos, CustomPacketPayload payload) {
        if (level instanceof ServerLevel serverLevel) {
            sendToPlayersTrackingChunk(serverLevel, ChunkPos.containing(pos), payload);
        }
    }

    default void broadcastAudioResources(MinecraftServer server) {
        sendToAllPlayers(server, new SyncAudioResourcesPacket(AudioManager.getInstance().getAllResources()));
    }

    private static SyncSpeakerStatePacket snapshot(StationListener listener) {
        return SyncSpeakerStatePacket.snapshot(listener.receiver(), listener.station().playback(), listener.getVolume(),
            listener.station().id(), PlaybackState.nowMs());
    }

    default void broadcastSpeakerState(StationListener listener) {
        listener.broadcast(snapshot(listener));
    }

    /** What a boombox is doing, for one player who can now hear it. */
    default void sendListenerState(ServerPlayer player, StationListener listener) {
        sendToPlayer(player, snapshot(listener));
    }

    /** Where a boombox is, for a player who can hear it. */
    default void sendBoomboxPlace(ServerPlayer player, UUID id, int entityId, Vec3 position) {
        sendToPlayer(player, new SyncBoomboxPlacePacket(id, entityId, position.x, position.y, position.z));
    }

    /** A player can no longer hear this boombox. */
    default void sendBoomboxGone(ServerPlayer player, UUID id) {
        sendToPlayer(player, new SyncBoomboxPlacePacket(id, -1, 0, 0, 0));
    }

    default void sendStationView(ServerPlayer player, StationView view) {
        sendToPlayer(player, new SyncStationPacket(view));
    }

    /** A private station's details, for players near its speaker. */
    default void broadcastStationView(StationListener listener, StationView view) {
        listener.broadcast(new SyncStationPacket(view));
    }

    /** Shared stations and playlists, for everyone. */
    default void broadcastStations(MinecraftServer server) {
        sendToAllPlayers(server, SyncStationsPacket.current());
    }

    default void broadcastLibraryJobs(MinecraftServer server, List<LibraryJob> jobs) {
        sendToAllPlayers(server, new SyncLibraryJobsPacket(jobs));
    }

    default void sendSearchResults(ServerPlayer player, int searchId, List<SearchResult> results, AudioFailure failure) {
        sendToPlayer(player, new SearchResultsPacket(searchId, results, failure == null ? "" : failure.name()));
    }

    default void sendAddFailed(ServerPlayer player, String url, AudioFailure failure) {
        sendToPlayer(player, new AddFailedPacket(url, failure.name()));
    }

    /** Tells a player the server's rules, so their screen can show what they may do. */
    default void sendPermissions(ServerPlayer player) {
        sendToPlayer(player, SyncPermissionsPacket.current());
    }

    default void sendSearch(int searchId, SearchSource source, String text, boolean updateFirst) {
        sendToServer(new SearchPacket(searchId, source.ordinal(), text, updateFirst));
    }

    default void sendSearchThumbnail(ServerPlayer player, int searchId, String videoId, byte[] png) {
        sendToPlayer(player, new SearchThumbnailPacket(searchId, videoId, png));
    }

    // Speaker controls (only called on client)

    default void sendSpeakerAction(Receiver receiver, SpeakerAction action, UUID target, UUID second, int number, String text) {
        sendToServer(new SpeakerActionPacket(receiver, action, target, second, number, text));
    }

    default void seekSpeaker(Receiver receiver, long positionMs) {
        sendToServer(new SpeakerSeekPacket(receiver, positionMs));
    }

    default void setSpeakerVolume(Receiver receiver, float volume) {
        sendToServer(new SpeakerVolumePacket(receiver, volume));
    }

    // Client-side audio (only called on client)

    default void playAudio(Receiver receiver, PlaybackState playback, UUID resourceId, float volume) {
        if (isClient()) {
            AudioPlayer.getInstance().play(receiver, playback, resourceId, volume);
        }
    }

    default void stopAudio(Receiver receiver) {
        if (isClient()) {
            AudioPlayer.getInstance().stop(receiver);
        }
    }

    default void setAudioVolume(Receiver receiver, float volume) {
        if (isClient()) {
            AudioPlayer.getInstance().setVolume(receiver, volume);
        }
    }

    /** Fills bars with recent loudness from 0 to 1, newest last, or zeros when nothing is audible. */
    default void readAudioLevels(Receiver receiver, long positionMs, float[] bars, int msPerBar) {
        if (isClient()) {
            AudioPlayer.getInstance().readLevels(receiver, positionMs, bars, msPerBar);
        }
    }

    default void stopAllAudio() {
        if (isClient()) {
            AudioPlayer.getInstance().stopAll();
        }
    }

    default void playStreamingAudio(Receiver receiver, StreamingAudioStream stream, float volume) {
        if (isClient()) {
            AudioPlayer.getInstance().playStreaming(receiver, stream, volume);
        }
    }

    // Client-side utilities

    default void runOnClient(Runnable task) {
        if (isClient()) {
            Minecraft.getInstance().execute(task);
        }
    }

    default void requestAudioFromServer(long streamId, UUID resourceId, long startPositionMs) {
        if (isClient() && Minecraft.getInstance().getConnection() != null) {
            sendToServer(new RequestAudioPacket(streamId, resourceId, startPositionMs));
        }
    }

    default void cancelAudioStream(long streamId) {
        if (isClient() && Minecraft.getInstance().getConnection() != null) {
            sendToServer(new CancelAudioStreamPacket(streamId));
        }
    }

    default long getEstimatedOneWayLatencyMs() {
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
