package com.tovkaic.phonon.network.packets;

import com.tovkaic.phonon.Constants;
import com.tovkaic.phonon.audio.AudioManager;
import com.tovkaic.phonon.audio.AudioResource;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client requests audio file from server.
 */
public record RequestAudioPacket(UUID resourceId) implements CustomPacketPayload {

    public static final Type<RequestAudioPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "request_audio"));

    public static final StreamCodec<ByteBuf, RequestAudioPacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        RequestAudioPacket::resourceId,
        RequestAudioPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestAudioPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                AudioManager.getInstance().getResource(packet.resourceId).ifPresent(resource -> {
                    // TODO: Download audio from URL and send chunks
                    // For MVP, we'll send the URL directly to client for download
                });
            }
        });
    }
}
