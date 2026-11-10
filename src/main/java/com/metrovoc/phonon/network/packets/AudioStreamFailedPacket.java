package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.StreamingAudioManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.metrovoc.phonon.network.PacketContext;

/** A rejected or aborted transfer; only temporary capacity failures should be retried. */
public record AudioStreamFailedPacket(long streamId, boolean retryable) implements CustomPacketPayload {
    public static final Type<AudioStreamFailedPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "audio_stream_failed"));

    public static final StreamCodec<ByteBuf, AudioStreamFailedPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, AudioStreamFailedPacket::streamId,
        ByteBufCodecs.BOOL, AudioStreamFailedPacket::retryable,
        AudioStreamFailedPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AudioStreamFailedPacket packet, PacketContext context) {
        context.enqueueWork(() -> StreamingAudioManager.getInstance().receiveFailure(packet.streamId, packet.retryable));
    }
}
