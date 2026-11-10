package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.metrovoc.phonon.client.ClientStations;
import com.metrovoc.phonon.platform.PlatformHelper;
import io.netty.buffer.ByteBuf;
import com.metrovoc.phonon.audio.Receiver;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.metrovoc.phonon.network.PacketContext;

import java.util.UUID;

/**
 * Sends a playback snapshot rather than cross-machine wall-clock anchors.
 */
public record SyncSpeakerStatePacket(
    Receiver pos,
    UUID resourceId,
    long positionAtSendMs,
    float speed,
    float volume,
    UUID stationId
) implements CustomPacketPayload {
    public static final Type<SyncSpeakerStatePacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sync_speaker_state"));

    public static final StreamCodec<ByteBuf, SyncSpeakerStatePacket> CODEC = StreamCodec.composite(
        ReceiverCodec.STREAM_CODEC,
        SyncSpeakerStatePacket::pos,
        UUIDCodec.STREAM_CODEC,
        SyncSpeakerStatePacket::resourceId,
        ByteBufCodecs.VAR_LONG,
        SyncSpeakerStatePacket::positionAtSendMs,
        ByteBufCodecs.FLOAT,
        SyncSpeakerStatePacket::speed,
        ByteBufCodecs.FLOAT,
        SyncSpeakerStatePacket::volume,
        UUIDCodec.STREAM_CODEC,
        SyncSpeakerStatePacket::stationId,
        SyncSpeakerStatePacket::new
    );

    public static SyncSpeakerStatePacket snapshot(
        Receiver pos,
        PlaybackState playback,
        float volume,
        UUID stationId,
        long nowMs
    ) {
        return new SyncSpeakerStatePacket(
            pos,
            playback.resourceId(),
            playback.getCurrentPositionMs(nowMs),
            playback.speed(),
            volume,
            stationId
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSpeakerStatePacket packet, PacketContext context) {
        context.enqueueWork(() -> {
            PlaybackState playback;
            if (packet.resourceId == null) {
                playback = PlaybackState.STOPPED;
            } else {
                long latencyMs = PlatformHelper.INSTANCE.getEstimatedOneWayLatencyMs();
                long estimatedPosition = packet.positionAtSendMs
                    + Math.max(0, (long) (latencyMs * packet.speed));
                playback = new PlaybackState(
                    packet.resourceId,
                    PlaybackState.nowMs(),
                    estimatedPosition,
                    packet.speed
                );
            }
            ClientSpeakerManager.getInstance().updateSpeaker(packet.pos, playback, packet.volume);
            ClientStations.getInstance().setSpeakerStation(packet.pos, packet.stationId);
        });
    }
}
