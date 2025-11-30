package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.StreamingAudioManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Sent when a live stream ends.
 * Allows clients to clean up resources and stop playback gracefully.
 */
public record LiveStreamEndPacket(UUID resourceId) implements CustomPacketPayload {

    public static final Type<LiveStreamEndPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "live_stream_end"));

    public static final StreamCodec<ByteBuf, LiveStreamEndPacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        LiveStreamEndPacket::resourceId,
        LiveStreamEndPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LiveStreamEndPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            StreamingAudioManager.getInstance().completeDownload(packet.resourceId());
        });
    }
}
