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

/**
 * Sequential data for one explicitly identified stream. Resource UUID and
 * total-chunk metadata are sent once in {@link AudioStreamStartPacket}.
 */
public record AudioChunkPacket(
    long streamId,
    int chunkIndex,
    boolean last,
    byte[] data
) implements CustomPacketPayload {
    public static final Type<AudioChunkPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "audio_chunk"));

    public static final StreamCodec<ByteBuf, AudioChunkPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG,
        AudioChunkPacket::streamId,
        ByteBufCodecs.VAR_INT,
        AudioChunkPacket::chunkIndex,
        ByteBufCodecs.BOOL,
        AudioChunkPacket::last,
        ByteBufCodecs.byteArray(AudioLimits.MAX_CHUNK_BYTES),
        AudioChunkPacket::data,
        AudioChunkPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AudioChunkPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> StreamingAudioManager.getInstance().receiveChunk(
            packet.streamId,
            packet.chunkIndex,
            packet.data,
            packet.last
        ));
    }
}
