package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: Sync speaker volume.
 */
public record SyncSpeakerVolumePacket(
    BlockPos pos,
    float volume
) implements CustomPacketPayload {

    public static final Type<SyncSpeakerVolumePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_speaker_volume"));

    public static final StreamCodec<ByteBuf, SyncSpeakerVolumePacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SyncSpeakerVolumePacket::pos,
        ByteBufCodecs.FLOAT,
        SyncSpeakerVolumePacket::volume,
        SyncSpeakerVolumePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSpeakerVolumePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientSpeakerManager.getInstance().updateVolume(packet.pos, packet.volume);
        });
    }
}
