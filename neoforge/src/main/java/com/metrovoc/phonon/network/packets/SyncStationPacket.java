package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.ClientStations;
import com.metrovoc.phonon.station.StationView;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client: one station's queue and modes changed.
 */
public record SyncStationPacket(StationView view) implements CustomPacketPayload {
    public static final Type<SyncStationPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sync_station"));

    public static final StreamCodec<ByteBuf, SyncStationPacket> CODEC =
        StationCodecs.VIEW.map(SyncStationPacket::new, SyncStationPacket::view);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncStationPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientStations.getInstance().put(packet.view));
    }
}
