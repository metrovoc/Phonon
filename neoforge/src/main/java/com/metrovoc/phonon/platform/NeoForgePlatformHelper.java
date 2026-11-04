package com.metrovoc.phonon.platform;

import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.network.packets.SyncBoomboxPlacePacket;
import com.metrovoc.phonon.station.StationListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import com.metrovoc.phonon.network.packets.SyncSpeakerStatePacket;
import com.metrovoc.phonon.client.audio.AudioPlayer;
import com.metrovoc.phonon.client.audio.StreamingAudioStream;
import com.metrovoc.phonon.network.packets.RequestAudioPacket;
import com.metrovoc.phonon.network.packets.CancelAudioStreamPacket;
import com.metrovoc.phonon.network.packets.SpeakerActionPacket;
import com.metrovoc.phonon.network.packets.SyncLibraryJobsPacket;
import com.metrovoc.phonon.network.packets.SyncStationPacket;
import com.metrovoc.phonon.network.packets.SyncStationsPacket;
import com.metrovoc.phonon.network.packets.AddFailedPacket;
import com.metrovoc.phonon.network.packets.SearchPacket;
import com.metrovoc.phonon.network.packets.SearchResultsPacket;
import com.metrovoc.phonon.network.packets.SyncPermissionsPacket;
import com.metrovoc.phonon.server.AudioFailure;
import com.metrovoc.phonon.server.SearchSource;
import com.metrovoc.phonon.network.packets.SearchThumbnailPacket;
import com.metrovoc.phonon.server.LibraryJob;
import com.metrovoc.phonon.server.SearchResult;
import com.metrovoc.phonon.server.SpeakerAction;
import com.metrovoc.phonon.station.StationView;
import com.metrovoc.phonon.network.packets.SpeakerSeekPacket;
import com.metrovoc.phonon.network.packets.SpeakerVolumePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.network.packets.SyncAudioResourcesPacket;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

public class NeoForgePlatformHelper implements PlatformHelper {

    private boolean isClient() {
        return FMLEnvironment.dist.isClient();
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
            PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(pos), payload);
        }
    }

    @Override
    public void broadcastAudioResources(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(new SyncAudioResourcesPacket(AudioManager.getInstance().getAllResources()));
    }

    private static SyncSpeakerStatePacket snapshot(StationListener listener) {
        return SyncSpeakerStatePacket.snapshot(listener.receiver(), listener.station().playback(), listener.getVolume(),
            listener.station().id(), PlaybackState.nowMs());
    }

    @Override
    public void broadcastSpeakerState(StationListener listener) {
        listener.broadcast(snapshot(listener));
    }

    @Override
    public void sendListenerState(ServerPlayer player, StationListener listener) {
        sendToPlayer(player, snapshot(listener));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object packet) {
        if (packet instanceof CustomPacketPayload payload) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @Override
    public void sendBoomboxPlace(ServerPlayer player, UUID id, int entityId, Vec3 position) {
        sendToPlayer(player, new SyncBoomboxPlacePacket(id, entityId, position.x, position.y, position.z));
    }

    @Override
    public void sendBoomboxGone(ServerPlayer player, UUID id) {
        sendToPlayer(player, new SyncBoomboxPlacePacket(id, -1, 0, 0, 0));
    }

    @Override
    public void sendStationView(ServerPlayer player, StationView view) {
        sendToPlayer(player, new SyncStationPacket(view));
    }

    @Override
    public void broadcastStationView(StationListener listener, StationView view) {
        listener.broadcast(new SyncStationPacket(view));
    }

    @Override
    public void broadcastStations(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(SyncStationsPacket.current());
    }

    @Override
    public void broadcastLibraryJobs(MinecraftServer server, List<LibraryJob> jobs) {
        PacketDistributor.sendToAllPlayers(new SyncLibraryJobsPacket(jobs));
    }

    @Override
    public void sendSearchResults(ServerPlayer player, int searchId, List<SearchResult> results, AudioFailure failure) {
        sendToPlayer(player, new SearchResultsPacket(searchId, results, failure == null ? "" : failure.name()));
    }

    @Override
    public void sendAddFailed(ServerPlayer player, String url, AudioFailure failure) {
        sendToPlayer(player, new AddFailedPacket(url, failure.name()));
    }

    @Override
    public void sendPermissions(ServerPlayer player) {
        sendToPlayer(player, SyncPermissionsPacket.current());
    }

    @Override
    public void sendSearch(int searchId, SearchSource source, String text, boolean updateFirst) {
        sendToServer(new SearchPacket(searchId, source.ordinal(), text, updateFirst));
    }

    @Override
    public void sendSearchThumbnail(ServerPlayer player, int searchId, String videoId, byte[] png) {
        sendToPlayer(player, new SearchThumbnailPacket(searchId, videoId, png));
    }

    @Override
    public void readAudioLevels(Receiver pos, long positionMs, float[] bars, int msPerBar) {
        if (isClient()) {
            AudioPlayer.getInstance().readLevels(pos, positionMs, bars, msPerBar);
        }
    }

    @Override
    public void sendSpeakerAction(Receiver pos, SpeakerAction action, UUID target, UUID second, int number, String text) {
        sendToServer(new SpeakerActionPacket(pos, action, target, second, number, text));
    }

    @Override
    public void seekSpeaker(Receiver pos, long positionMs) {
        sendToServer(new SpeakerSeekPacket(pos, positionMs));
    }

    @Override
    public void setSpeakerVolume(Receiver pos, float volume) {
        sendToServer(new SpeakerVolumePacket(pos, volume));
    }

    // Client-side audio methods - delegate to AudioPlayer

    @Override
    public void playAudio(Receiver pos, PlaybackState playback, UUID resourceId, float volume) {
        if (isClient()) {
            AudioPlayer.getInstance().play(pos, playback, resourceId, volume);
        }
    }

    @Override
    public void stopAudio(Receiver pos) {
        if (isClient()) {
            AudioPlayer.getInstance().stop(pos);
        }
    }

    @Override
    public void setAudioVolume(Receiver pos, float volume) {
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
    public void playStreamingAudio(Receiver pos, StreamingAudioStream stream, float volume) {
        if (isClient()) {
            AudioPlayer.getInstance().playStreaming(pos, stream, volume);
        }
    }

    @Override
    public void runOnClient(Runnable task) {
        if (isClient()) {
            Minecraft.getInstance().tell(task);
        }
    }

    @Override
    public void requestAudioFromServer(long streamId, UUID resourceId, long startPositionMs) {
        if (isClient() && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new RequestAudioPacket(streamId, resourceId, startPositionMs));
        }
    }

    @Override
    public void cancelAudioStream(long streamId) {
        if (isClient() && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new CancelAudioStreamPacket(streamId));
        }
    }

    @Override
    public long getEstimatedOneWayLatencyMs() {
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
