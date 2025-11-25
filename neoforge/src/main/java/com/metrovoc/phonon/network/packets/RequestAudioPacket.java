package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.server.AudioTransferManager;
import com.metrovoc.phonon.server.ServerAudioStorage;
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
                if (ServerAudioStorage.getInstance().hasAudio(packet.resourceId)) {
                    AudioTransferManager.getInstance().queueTransfer(serverPlayer, packet.resourceId);
                    Phonon.LOGGER.debug("Player {} requested audio {}",
                        serverPlayer.getName().getString(), packet.resourceId);
                } else {
                    Phonon.LOGGER.warn("Player {} requested unavailable audio {}",
                        serverPlayer.getName().getString(), packet.resourceId);
                }
            }
        });
    }
}
