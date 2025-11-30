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
 * Initiates an Opus audio stream.
 *
 * Sent before any OpusPackets to establish stream parameters:
 * - resourceId: identifies the audio resource
 * - channels: 1 (mono) or 2 (stereo)
 * - durationMs: total duration (-1 if unknown/live)
 * - totalPackets: total packets to expect (-1 if unknown)
 * - startSequence: first sequence number (for seek support)
 * - startPositionMs: playback start position in milliseconds
 */
public record OpusStreamStartPacket(
    UUID resourceId,
    int channels,
    long durationMs,
    int totalPackets,
    int startSequence,
    long startPositionMs
) implements CustomPacketPayload {

    public static final int SAMPLE_RATE = 48000; // Opus native rate

    public static final Type<OpusStreamStartPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "opus_stream_start"));

    public static final StreamCodec<ByteBuf, OpusStreamStartPacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        OpusStreamStartPacket::resourceId,
        ByteBufCodecs.VAR_INT,
        OpusStreamStartPacket::channels,
        ByteBufCodecs.VAR_LONG,
        OpusStreamStartPacket::durationMs,
        ByteBufCodecs.VAR_INT,
        OpusStreamStartPacket::totalPackets,
        ByteBufCodecs.VAR_INT,
        OpusStreamStartPacket::startSequence,
        ByteBufCodecs.VAR_LONG,
        OpusStreamStartPacket::startPositionMs,
        OpusStreamStartPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpusStreamStartPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StreamingAudioManager.getInstance().receiveOpusStreamStart(
                packet.resourceId(),
                packet.channels(),
                packet.durationMs(),
                packet.totalPackets(),
                packet.startSequence(),
                packet.startPositionMs()
            );
        });
    }

    public int getSampleRate() {
        return SAMPLE_RATE;
    }
}
