package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.gui.ClientSearch;
import com.metrovoc.phonon.server.TitleSearch;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: one search result's picture, as a small PNG.
 */
public record SearchThumbnailPacket(int searchId, String videoId, byte[] png) implements CustomPacketPayload {
    public static final Type<SearchThumbnailPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "search_thumbnail"));

    public static final StreamCodec<ByteBuf, SearchThumbnailPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SearchThumbnailPacket::searchId,
        ByteBufCodecs.stringUtf8(64), SearchThumbnailPacket::videoId,
        ByteBufCodecs.byteArray(TitleSearch.MAX_THUMBNAIL_BYTES), SearchThumbnailPacket::png,
        SearchThumbnailPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SearchThumbnailPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSearch.getInstance().setThumbnail(packet.searchId, packet.videoId, packet.png));
    }
}
