package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.gui.ClientSearch;
import com.metrovoc.phonon.server.SearchResult;
import com.metrovoc.phonon.server.TitleSearch;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: what a title search found, or the message saying why it failed.
 */
public record SearchResultsPacket(int searchId, List<SearchResult> results, String failureKey) implements CustomPacketPayload {
    public static final Type<SearchResultsPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "search_results"));

    private static final StreamCodec<ByteBuf, SearchResult> RESULT_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(64), SearchResult::videoId,
        ByteBufCodecs.stringUtf8(1024), SearchResult::title,
        ByteBufCodecs.stringUtf8(1024), SearchResult::uploader,
        ByteBufCodecs.VAR_LONG, SearchResult::durationMs,
        SearchResult::new
    );

    public static final StreamCodec<ByteBuf, SearchResultsPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SearchResultsPacket::searchId,
        ByteBufCodecs.<ByteBuf, SearchResult, List<SearchResult>>collection(ArrayList::new, RESULT_CODEC, TitleSearch.MAX_RESULTS),
        SearchResultsPacket::results,
        ByteBufCodecs.stringUtf8(128), SearchResultsPacket::failureKey,
        SearchResultsPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SearchResultsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSearch.getInstance().setResults(packet.searchId, packet.results, packet.failureKey));
    }
}
