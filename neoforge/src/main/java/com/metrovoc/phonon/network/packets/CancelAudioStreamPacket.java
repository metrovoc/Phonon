package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.server.AudioTransferManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CancelAudioStreamPacket(long streamId) implements CustomPacketPayload {
    public static final Type<CancelAudioStreamPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "cancel_audio_stream"));

    public static final StreamCodec<ByteBuf, CancelAudioStreamPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG,
        CancelAudioStreamPacket::streamId,
        CancelAudioStreamPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CancelAudioStreamPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && packet.streamId > 0) {
                AudioTransferManager.getInstance().cancelTransfer(player.getUUID(), packet.streamId);
            }
        });
    }
}
