package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.station.StationListener;
import com.metrovoc.phonon.platform.PlatformHelper;
import com.metrovoc.phonon.server.SpeakerControl;
import io.netty.buffer.ByteBuf;
import com.metrovoc.phonon.audio.Receiver;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Seek 到指定位置。
 */
public record SpeakerSeekPacket(
    Receiver pos,
    long seekPositionMs
) implements CustomPacketPayload {

    public static final Type<SpeakerSeekPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "speaker_seek"));

    public static final StreamCodec<ByteBuf, SpeakerSeekPacket> CODEC = StreamCodec.composite(
        ReceiverCodec.STREAM_CODEC,
        SpeakerSeekPacket::pos,
        ByteBufCodecs.VAR_LONG,
        SpeakerSeekPacket::seekPositionMs,
        SpeakerSeekPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeakerSeekPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            StationListener speaker = SpeakerPacketValidator.getAccessible(player, packet.pos);
            if (speaker == null) return;

            if (SpeakerControl.canControl(player, speaker)) {
                SpeakerControl.seek(speaker, packet.seekPositionMs);
            }
        });
    }
}
