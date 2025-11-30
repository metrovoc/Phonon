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

public record AudioStreamStartPacket(
    UUID resourceId,
    byte[] headerBytes,
    int sampleRate,
    int startOffset,
    long startPositionMs,
    boolean isLiveStream
) implements CustomPacketPayload {

    /**
     * Constructor for non-live streams (backward compatible).
     */
    public AudioStreamStartPacket(UUID resourceId, byte[] headerBytes, int sampleRate, int startOffset, long startPositionMs) {
        this(resourceId, headerBytes, sampleRate, startOffset, startPositionMs, false);
    }

    public static final Type<AudioStreamStartPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "audio_stream_start"));

    public static final StreamCodec<ByteBuf, AudioStreamStartPacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        AudioStreamStartPacket::resourceId,
        ByteBufCodecs.BYTE_ARRAY,
        AudioStreamStartPacket::headerBytes,
        ByteBufCodecs.VAR_INT,
        AudioStreamStartPacket::sampleRate,
        ByteBufCodecs.VAR_INT,
        AudioStreamStartPacket::startOffset,
        ByteBufCodecs.VAR_LONG,
        AudioStreamStartPacket::startPositionMs,
        ByteBufCodecs.BOOL,
        AudioStreamStartPacket::isLiveStream,
        AudioStreamStartPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AudioStreamStartPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StreamingAudioManager.getInstance().receiveHeader(
                packet.resourceId(),
                packet.headerBytes(),
                packet.sampleRate(),
                packet.isLiveStream()
            );
        });
    }
}
