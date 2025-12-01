package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.StreamingAudioManager;
import com.metrovoc.phonon.server.LiveBroadcastManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Bidirectional: Live audio chunk data.
 * Client -> Server: Broadcaster sends audio data.
 * Server -> Client: Server forwards to listeners.
 */
public record LiveBroadcastChunkPacket(
    UUID streamId,
    byte[] data
) implements CustomPacketPayload {

    public static final Type<LiveBroadcastChunkPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "live_broadcast_chunk"));

    public static final StreamCodec<ByteBuf, LiveBroadcastChunkPacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        LiveBroadcastChunkPacket::streamId,
        ByteBufCodecs.BYTE_ARRAY,
        LiveBroadcastChunkPacket::data,
        LiveBroadcastChunkPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LiveBroadcastChunkPacket packet, IPayloadContext ctx) {
        if (ctx.flow() == PacketFlow.CLIENTBOUND) {
            handleClient(packet, ctx);
        } else {
            handleServer(packet, ctx);
        }
    }

    private static void handleServer(LiveBroadcastChunkPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer)) return;
            LiveBroadcastManager.getInstance().forwardChunk(packet.streamId, packet.data);
        });
    }

    private static void handleClient(LiveBroadcastChunkPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (StreamingAudioManager.getInstance().hasDownload(packet.streamId)) {
                StreamingAudioManager.getInstance().receiveChunk(packet.streamId, packet.data);
            }
        });
    }
}
