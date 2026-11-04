package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.gui.ClientSearch;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: a link this player added couldn't be downloaded, so the screen can say what to do next.
 */
public record AddFailedPacket(String url, String failure) implements CustomPacketPayload {
    public static final Type<AddFailedPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "add_failed"));

    public static final StreamCodec<ByteBuf, AddFailedPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(2048), AddFailedPacket::url,
        ByteBufCodecs.stringUtf8(64), AddFailedPacket::failure,
        AddFailedPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AddFailedPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSearch.getInstance().linkFailed(packet.url, packet.failure));
    }
}
