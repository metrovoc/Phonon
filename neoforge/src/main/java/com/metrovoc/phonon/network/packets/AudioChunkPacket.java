package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.AudioReceiver;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Binary audio chunk transfer (server -> client).
 * Carries actual audio data, not URLs.
 */
public record AudioChunkPacket(
    UUID resourceId,
    int chunkIndex,
    int totalChunks,
    byte[] data
) implements CustomPacketPayload {

    public static final int CHUNK_SIZE = 30 * 1024; // 30KB per chunk

    public static final Type<AudioChunkPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "audio_chunk"));

    public static final StreamCodec<ByteBuf, AudioChunkPacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        AudioChunkPacket::resourceId,
        ByteBufCodecs.VAR_INT,
        AudioChunkPacket::chunkIndex,
        ByteBufCodecs.VAR_INT,
        AudioChunkPacket::totalChunks,
        ByteBufCodecs.BYTE_ARRAY,
        AudioChunkPacket::data,
        AudioChunkPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AudioChunkPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AudioReceiver.getInstance().receiveChunk(
                packet.resourceId,
                packet.chunkIndex,
                packet.totalChunks,
                packet.data
            );
        });
    }

    public boolean isLastChunk() {
        return chunkIndex == totalChunks - 1;
    }
}
