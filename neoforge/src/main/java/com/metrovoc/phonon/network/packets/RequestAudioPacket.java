package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.server.AudioTransferManager;
import com.metrovoc.phonon.server.ServerAudioStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record RequestAudioPacket(
    long streamId,
    UUID resourceId,
    long startPositionMs
) implements CustomPacketPayload {
    public static final Type<RequestAudioPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "request_audio"));

    public static final StreamCodec<ByteBuf, RequestAudioPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG,
        RequestAudioPacket::streamId,
        UUIDCodec.STREAM_CODEC,
        RequestAudioPacket::resourceId,
        ByteBufCodecs.VAR_LONG,
        RequestAudioPacket::startPositionMs,
        RequestAudioPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestAudioPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || packet.streamId <= 0) {
                return;
            }

            if (AudioManager.getInstance().getResource(packet.resourceId).isEmpty()
                || !ServerAudioStorage.getInstance().hasAudio(packet.resourceId)) {
                Phonon.LOGGER.warn(
                    "Player {} requested unavailable audio {}",
                    player.getName().getString(),
                    packet.resourceId
                );
                return;
            }

            AudioTransferManager.getInstance().queueTransfer(
                player,
                packet.streamId,
                packet.resourceId,
                Math.max(0, packet.startPositionMs)
            );
        });
    }
}
