package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.server.LiveBroadcastManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client -> Server: Start a live broadcast from client's audio input to a speaker.
 */
public record LiveBroadcastStartPacket(
    BlockPos speakerPos,
    UUID streamId,
    String deviceName
) implements CustomPacketPayload {

    public static final Type<LiveBroadcastStartPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "live_broadcast_start"));

    public static final StreamCodec<ByteBuf, LiveBroadcastStartPacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        LiveBroadcastStartPacket::speakerPos,
        UUIDCodec.STREAM_CODEC,
        LiveBroadcastStartPacket::streamId,
        ByteBufCodecs.STRING_UTF8,
        LiveBroadcastStartPacket::deviceName,
        LiveBroadcastStartPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LiveBroadcastStartPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            LiveBroadcastManager.getInstance().startBroadcast(
                player,
                packet.speakerPos,
                packet.streamId,
                packet.deviceName
            );
        });
    }
}
