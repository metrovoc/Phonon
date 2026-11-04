package com.metrovoc.phonon.platform;

import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.network.packets.SyncSpeakerStatePacket;
import com.metrovoc.phonon.client.audio.AudioPlayer;
import com.metrovoc.phonon.client.audio.StreamingAudioStream;
import com.metrovoc.phonon.network.packets.RequestAudioPacket;
import com.metrovoc.phonon.network.packets.CancelAudioStreamPacket;
import com.metrovoc.phonon.network.packets.SpeakerActionPacket;
import com.metrovoc.phonon.network.packets.SyncLibraryJobsPacket;
import com.metrovoc.phonon.network.packets.SyncStationPacket;
import com.metrovoc.phonon.network.packets.SyncStationsPacket;
import com.metrovoc.phonon.server.LibraryJob;
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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

public class NeoForgePlatformHelper implements PlatformHelper {

    private boolean isClient() {
        return FMLEnvironment.getDist().isClient();
    }

    @Override
    public void sendToServer(Object packet) {
        if (packet instanceof CustomPacketPayload payload) {
            ClientPacketDistributor.sendToServer(payload);
        }
    }

    @Override
    public void sendToAllTracking(Level level, BlockPos pos, Object packet) {
        if (level instanceof ServerLevel serverLevel && packet instanceof CustomPacketPayload payload) {
            PacketDistributor.sendToPlayersTrackingChunk(serverLevel, ChunkPos.containing(pos), payload);
        }
    }

    @Override
    public void broadcastAudioResources(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(new SyncAudioResourcesPacket(AudioManager.getInstance().getAllResources()));
    }

    @Override
    public void broadcastSpeakerState(Level level, BlockPos pos, SpeakerBlockEntity speaker) {
        sendToAllTracking(level, pos,
            SyncSpeakerStatePacket.snapshot(pos, speaker.getPlayback(), speaker.getVolume(), speaker.station().id(), PlaybackState.nowMs()));
    }

    @Override
    public void broadcastStationView(Level level, BlockPos pos, StationView view) {
        sendToAllTracking(level, pos, new SyncStationPacket(view));
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
    public void readAudioLevels(BlockPos pos, long positionMs, float[] bars, int msPerBar) {
        if (isClient()) {
            AudioPlayer.getInstance().readLevels(pos, positionMs, bars, msPerBar);
        }
    }

    @Override
    public void sendSpeakerAction(BlockPos pos, SpeakerAction action, UUID target, UUID second, int number, String text) {
        sendToServer(new SpeakerActionPacket(pos, action, target, second, number, text));
    }

    @Override
    public void seekSpeaker(BlockPos pos, long positionMs) {
        sendToServer(new SpeakerSeekPacket(pos, positionMs));
    }

    @Override
    public void setSpeakerVolume(BlockPos pos, float volume) {
        sendToServer(new SpeakerVolumePacket(pos, volume));
    }

    // Client-side audio methods - delegate to AudioPlayer

    @Override
    public void playAudio(BlockPos pos, PlaybackState playback, UUID resourceId, float volume) {
        if (isClient()) {
            AudioPlayer.getInstance().play(pos, playback, resourceId, volume);
        }
    }

    @Override
    public void stopAudio(BlockPos pos) {
        if (isClient()) {
            AudioPlayer.getInstance().stop(pos);
        }
    }

    @Override
    public void setAudioVolume(BlockPos pos, float volume) {
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
    public void playStreamingAudio(BlockPos pos, StreamingAudioStream stream, float volume) {
        if (isClient()) {
            AudioPlayer.getInstance().playStreaming(pos, stream, volume);
        }
    }

    @Override
    public void runOnClient(Runnable task) {
        if (isClient()) {
            Minecraft.getInstance().execute(task);
        }
    }

    @Override
    public void requestAudioFromServer(long streamId, UUID resourceId, long startPositionMs) {
        if (isClient() && Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new RequestAudioPacket(streamId, resourceId, startPositionMs));
        }
    }

    @Override
    public void cancelAudioStream(long streamId) {
        if (isClient() && Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new CancelAudioStreamPacket(streamId));
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
