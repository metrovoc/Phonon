package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sync speaker playback state to clients.
 * Includes server timestamp for clock synchronization.
 */
public record SyncSpeakerStatePacket(BlockPos pos, PlaybackState playback, long serverTimeMs) implements CustomPacketPayload {

    public static final Type<SyncSpeakerStatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_speaker_state"));

    public static final StreamCodec<ByteBuf, SyncSpeakerStatePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SyncSpeakerStatePacket::pos,
        StreamCodec.composite(
            UUIDCodec.STREAM_CODEC,
            PlaybackState::resourceId,
            ByteBufCodecs.VAR_LONG,
            PlaybackState::startTimeMs,
            ByteBufCodecs.FLOAT,
            PlaybackState::volume,
            ByteBufCodecs.BOOL,
            PlaybackState::playing,
            PlaybackState::new
        ),
        SyncSpeakerStatePacket::playback,
        ByteBufCodecs.VAR_LONG,
        SyncSpeakerStatePacket::serverTimeMs,
        SyncSpeakerStatePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSpeakerStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // Calculate clock offset between server and client
            long clientTimeMs = System.currentTimeMillis();
            long clockOffset = packet.serverTimeMs - clientTimeMs;

            // Adjust playback time to client's clock
            PlaybackState adjusted = new PlaybackState(
                packet.playback.resourceId(),
                packet.playback.startTimeMs() - clockOffset,
                packet.playback.volume(),
                packet.playback.playing()
            );

            ClientSpeakerManager.getInstance().updateSpeaker(packet.pos, adjusted);
        });
    }
}
