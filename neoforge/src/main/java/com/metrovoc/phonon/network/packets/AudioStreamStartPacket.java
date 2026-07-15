package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.AudioLimits;
import com.metrovoc.phonon.client.StreamingAudioManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record AudioStreamStartPacket(
    long streamId,
    UUID resourceId,
    byte[] headerBytes,
    int sampleRate,
    long streamStartPositionMs,
    boolean cacheable
) implements CustomPacketPayload {
    public static final Type<AudioStreamStartPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "audio_stream_start"));

    public static final StreamCodec<ByteBuf, AudioStreamStartPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG,
        AudioStreamStartPacket::streamId,
        UUIDCodec.STREAM_CODEC,
        AudioStreamStartPacket::resourceId,
        ByteBufCodecs.byteArray(AudioLimits.MAX_HEADER_BYTES),
        AudioStreamStartPacket::headerBytes,
        ByteBufCodecs.VAR_INT,
        AudioStreamStartPacket::sampleRate,
        ByteBufCodecs.VAR_LONG,
        AudioStreamStartPacket::streamStartPositionMs,
        ByteBufCodecs.BOOL,
        AudioStreamStartPacket::cacheable,
        AudioStreamStartPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AudioStreamStartPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> StreamingAudioManager.getInstance().receiveStart(
            packet.streamId,
            packet.resourceId,
            packet.headerBytes,
            packet.sampleRate,
            packet.streamStartPositionMs,
            packet.cacheable
        ));
    }
}
