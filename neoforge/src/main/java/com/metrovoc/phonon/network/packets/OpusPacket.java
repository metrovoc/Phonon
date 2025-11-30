package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.StreamingAudioManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Network packet for a single Opus audio frame.
 *
 * Each packet contains:
 * - resourceId: identifies the audio stream
 * - sequenceNumber: for ordering and loss detection (0-based)
 * - totalPackets: total packets in stream (-1 if unknown/streaming)
 * - opusData: encoded Opus frame (typically 20ms of audio)
 */
public record OpusPacket(
    UUID resourceId,
    int sequenceNumber,
    int totalPackets,
    byte[] opusData
) implements CustomPacketPayload {

    public static final Type<OpusPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "opus_packet"));

    public static final StreamCodec<ByteBuf, OpusPacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        OpusPacket::resourceId,
        ByteBufCodecs.VAR_INT,
        OpusPacket::sequenceNumber,
        ByteBufCodecs.VAR_INT,
        OpusPacket::totalPackets,
        ByteBufCodecs.BYTE_ARRAY,
        OpusPacket::opusData,
        OpusPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpusPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StreamingAudioManager.getInstance().receiveOpusPacket(
                packet.resourceId(),
                packet.sequenceNumber(),
                packet.totalPackets(),
                packet.opusData()
            );
        });
    }

    public boolean isLastPacket() {
        return totalPackets > 0 && sequenceNumber == totalPackets - 1;
    }
}
