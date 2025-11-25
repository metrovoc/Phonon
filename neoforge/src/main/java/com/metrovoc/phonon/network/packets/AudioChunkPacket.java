package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.AudioCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Audio file chunk transfer (server -> client).
 * For MVP, we send URL instead of actual chunks.
 */
public record AudioChunkPacket(UUID resourceId, String url) implements CustomPacketPayload {

    public static final Type<AudioChunkPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "audio_chunk"));

    public static final StreamCodec<ByteBuf, AudioChunkPacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        AudioChunkPacket::resourceId,
        ByteBufCodecs.STRING_UTF8,
        AudioChunkPacket::url,
        AudioChunkPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AudioChunkPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AudioCache.getInstance().downloadAudio(packet.resourceId, packet.url);
        });
    }
}
