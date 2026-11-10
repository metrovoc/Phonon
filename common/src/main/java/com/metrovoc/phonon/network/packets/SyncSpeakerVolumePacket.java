package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import io.netty.buffer.ByteBuf;
import com.metrovoc.phonon.audio.Receiver;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.metrovoc.phonon.network.PacketContext;

/**
 * Server -> Client: Sync speaker volume.
 */
public record SyncSpeakerVolumePacket(
    Receiver pos,
    float volume
) implements CustomPacketPayload {

    public static final Type<SyncSpeakerVolumePacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sync_speaker_volume"));

    public static final StreamCodec<ByteBuf, SyncSpeakerVolumePacket> CODEC = StreamCodec.composite(
        ReceiverCodec.STREAM_CODEC,
        SyncSpeakerVolumePacket::pos,
        ByteBufCodecs.FLOAT,
        SyncSpeakerVolumePacket::volume,
        SyncSpeakerVolumePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSpeakerVolumePacket packet, PacketContext ctx) {
        ctx.enqueueWork(() -> {
            ClientSpeakerManager.getInstance().updateVolume(packet.pos, packet.volume);
        });
    }
}
