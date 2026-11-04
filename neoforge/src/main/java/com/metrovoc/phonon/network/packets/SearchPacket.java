package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.server.SearchSource;
import com.metrovoc.phonon.server.TitleSearch;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: find this title, optionally after updating yt-dlp.
 */
public record SearchPacket(int searchId, int source, String text, boolean updateFirst) implements CustomPacketPayload {
    public static final Type<SearchPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "search"));

    public static final StreamCodec<ByteBuf, SearchPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SearchPacket::searchId,
        ByteBufCodecs.VAR_INT, SearchPacket::source,
        ByteBufCodecs.stringUtf8(2048), SearchPacket::text,
        ByteBufCodecs.BOOL, SearchPacket::updateFirst,
        SearchPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SearchPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                TitleSearch.request(player, packet.searchId, SearchSource.of(packet.source), packet.text, packet.updateFirst);
            }
        });
    }
}
