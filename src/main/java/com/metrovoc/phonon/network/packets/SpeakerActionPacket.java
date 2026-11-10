package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.AudioLimits;
import com.metrovoc.phonon.station.StationListener;
import com.metrovoc.phonon.server.SpeakerAction;
import com.metrovoc.phonon.server.SpeakerControl;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import com.metrovoc.phonon.audio.Receiver;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import com.metrovoc.phonon.network.PacketContext;

import java.util.UUID;

/**
 * Client -> Server: something asked of a speaker from its screen.
 */
public record SpeakerActionPacket(
    Receiver pos,
    SpeakerAction action,
    UUID target,
    UUID second,
    int number,
    String text
) implements CustomPacketPayload {
    private static final SpeakerAction[] ACTIONS = SpeakerAction.values();

    public static final Type<SpeakerActionPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "speaker_action"));

    private static final StreamCodec<ByteBuf, SpeakerAction> ACTION_CODEC = StreamCodec.of(
        (buf, action) -> buf.writeByte(action.ordinal()),
        buf -> {
            int id = buf.readUnsignedByte();
            if (id >= ACTIONS.length) {
                throw new DecoderException("Unknown speaker action: " + id);
            }
            return ACTIONS[id];
        }
    );

    public static final StreamCodec<ByteBuf, SpeakerActionPacket> CODEC = StreamCodec.composite(
        ReceiverCodec.STREAM_CODEC,
        SpeakerActionPacket::pos,
        ACTION_CODEC,
        SpeakerActionPacket::action,
        UUIDCodec.STREAM_CODEC,
        SpeakerActionPacket::target,
        UUIDCodec.STREAM_CODEC,
        SpeakerActionPacket::second,
        ByteBufCodecs.VAR_INT,
        SpeakerActionPacket::number,
        ByteBufCodecs.stringUtf8(AudioLimits.MAX_RESOURCE_URL_CHARS),
        SpeakerActionPacket::text,
        SpeakerActionPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeakerActionPacket packet, PacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            StationListener speaker = SpeakerPacketValidator.getAccessible(player, packet.pos);
            if (speaker == null) return;

            SpeakerControl.handle(player, speaker, packet.action, packet.target, packet.second, packet.number, packet.text);
        });
    }
}
