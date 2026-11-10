package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.ClientStations;
import com.metrovoc.phonon.station.Playlist;
import com.metrovoc.phonon.station.StationManager;
import com.metrovoc.phonon.station.StationView;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.metrovoc.phonon.network.PacketContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: all shared stations and playlists.
 */
public record SyncStationsPacket(List<StationView> stations, List<Playlist> playlists) implements CustomPacketPayload {
    public static final Type<SyncStationsPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sync_stations"));

    public static final StreamCodec<ByteBuf, SyncStationsPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.<ByteBuf, StationView, List<StationView>>collection(ArrayList::new, StationCodecs.VIEW, StationManager.MAX_SHARED_STATIONS),
        SyncStationsPacket::stations,
        ByteBufCodecs.<ByteBuf, Playlist, List<Playlist>>collection(ArrayList::new, StationCodecs.PLAYLIST, StationManager.MAX_PLAYLISTS),
        SyncStationsPacket::playlists,
        SyncStationsPacket::new
    );

    public static SyncStationsPacket current() {
        StationManager manager = StationManager.getInstance();
        return new SyncStationsPacket(manager.sharedViews(), manager.playlists());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncStationsPacket packet, PacketContext ctx) {
        ctx.enqueueWork(() -> ClientStations.getInstance().setShared(List.copyOf(packet.stations), List.copyOf(packet.playlists)));
    }
}
