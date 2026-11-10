package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.station.StationListener;
import io.netty.buffer.ByteBuf;
import com.metrovoc.phonon.audio.Receiver;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.metrovoc.phonon.server.SpeakerControl;
import net.minecraft.server.level.ServerPlayer;
import com.metrovoc.phonon.network.PacketContext;

/**
 * Client -> Server: Update speaker volume.
 * Volume is a per-speaker property, independent of playback state.
 */
public record SpeakerVolumePacket(
    Receiver pos,
    float volume
) implements CustomPacketPayload {

    public static final Type<SpeakerVolumePacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "speaker_volume"));

    public static final StreamCodec<ByteBuf, SpeakerVolumePacket> CODEC = StreamCodec.composite(
        ReceiverCodec.STREAM_CODEC,
        SpeakerVolumePacket::pos,
        ByteBufCodecs.FLOAT,
        SpeakerVolumePacket::volume,
        SpeakerVolumePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeakerVolumePacket packet, PacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            StationListener speaker = SpeakerPacketValidator.getAccessible(player, packet.pos);
            if (speaker == null || !SpeakerControl.canTune(player, speaker)) return;

            speaker.setVolume(packet.volume);
            float volume = speaker.getVolume();

            // Broadcast to all tracking players
            speaker.broadcast(new SyncSpeakerVolumePacket(packet.pos, volume));
        });
    }
}
