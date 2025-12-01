package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.StreamingAudioManager;
import com.metrovoc.phonon.server.LiveBroadcastManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Bidirectional: End a live broadcast.
 * Client -> Server: Broadcaster stops streaming.
 * Server -> Client: Server notifies listeners.
 */
public record LiveBroadcastEndPacket(UUID streamId) implements CustomPacketPayload {

    public static final Type<LiveBroadcastEndPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "live_broadcast_end"));

    public static final StreamCodec<ByteBuf, LiveBroadcastEndPacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        LiveBroadcastEndPacket::streamId,
        LiveBroadcastEndPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LiveBroadcastEndPacket packet, IPayloadContext ctx) {
        if (ctx.flow() == PacketFlow.CLIENTBOUND) {
            handleClient(packet, ctx);
        } else {
            handleServer(packet, ctx);
        }
    }

    private static void handleServer(LiveBroadcastEndPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer)) return;
            LiveBroadcastManager.getInstance().endBroadcast(packet.streamId);
        });
    }

    private static void handleClient(LiveBroadcastEndPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StreamingAudioManager.getInstance().completeDownload(packet.streamId);
        });
    }
}
