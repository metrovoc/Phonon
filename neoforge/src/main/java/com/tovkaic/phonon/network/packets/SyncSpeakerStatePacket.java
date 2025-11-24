package com.tovkaic.phonon.network.packets;

import com.tovkaic.phonon.Constants;
import com.tovkaic.phonon.audio.PlaybackState;
import com.tovkaic.phonon.client.ClientSpeakerManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sync speaker playback state to clients.
 */
public record SyncSpeakerStatePacket(BlockPos pos, PlaybackState playback) implements CustomPacketPayload {

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
        SyncSpeakerStatePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSpeakerStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientSpeakerManager.getInstance().updateSpeaker(packet.pos, packet.playback);
        });
    }
}
