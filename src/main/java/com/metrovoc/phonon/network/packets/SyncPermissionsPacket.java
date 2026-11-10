package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.ClientStations;
import com.metrovoc.phonon.config.PhononServerConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.metrovoc.phonon.network.PacketContext;

/**
 * Server -> Client: the server's rules, so a screen shows what a player may do before they try.
 */
public record SyncPermissionsPacket(boolean ownerOnlySpeakers, boolean anyoneCanAddTracks, boolean anyoneCanControlStations) implements CustomPacketPayload {
    public static final Type<SyncPermissionsPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sync_permissions"));

    public static final StreamCodec<ByteBuf, SyncPermissionsPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, SyncPermissionsPacket::ownerOnlySpeakers,
        ByteBufCodecs.BOOL, SyncPermissionsPacket::anyoneCanAddTracks,
        ByteBufCodecs.BOOL, SyncPermissionsPacket::anyoneCanControlStations,
        SyncPermissionsPacket::new
    );

    public static SyncPermissionsPacket current() {
        return new SyncPermissionsPacket(PhononServerConfig.getOwnerOnlySpeakers(), PhononServerConfig.getAnyoneCanAddTracks(),
            PhononServerConfig.getAnyoneCanControlStations());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncPermissionsPacket packet, PacketContext ctx) {
        ctx.enqueueWork(() -> ClientStations.getInstance().setPermissions(packet.ownerOnlySpeakers, packet.anyoneCanAddTracks, packet.anyoneCanControlStations));
    }
}
