package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.metrovoc.phonon.client.ClientStations;
import com.metrovoc.phonon.config.PhononServerConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.metrovoc.phonon.network.PacketContext;

/**
 * Server -> Client: the server's rules, so a screen shows what a player may do before they try, and how the world
 * sounds, so every player hears speakers the same way.
 */
public record SyncSettingsPacket(boolean ownerOnlySpeakers, boolean anyoneCanAddTracks, boolean anyoneCanControlStations,
                                 boolean muffleBehindWalls, int hearingDistance) implements CustomPacketPayload {
    public static final Type<SyncSettingsPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sync_settings"));

    public static final StreamCodec<ByteBuf, SyncSettingsPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, SyncSettingsPacket::ownerOnlySpeakers,
        ByteBufCodecs.BOOL, SyncSettingsPacket::anyoneCanAddTracks,
        ByteBufCodecs.BOOL, SyncSettingsPacket::anyoneCanControlStations,
        ByteBufCodecs.BOOL, SyncSettingsPacket::muffleBehindWalls,
        ByteBufCodecs.VAR_INT, SyncSettingsPacket::hearingDistance,
        SyncSettingsPacket::new
    );

    public static SyncSettingsPacket current() {
        return new SyncSettingsPacket(PhononServerConfig.getOwnerOnlySpeakers(), PhononServerConfig.getAnyoneCanAddTracks(),
            PhononServerConfig.getAnyoneCanControlStations(), PhononServerConfig.getMuffleBehindWalls(),
            PhononServerConfig.getHearingDistance());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSettingsPacket packet, PacketContext ctx) {
        ctx.enqueueWork(() -> {
            ClientStations.getInstance().setPermissions(packet.ownerOnlySpeakers, packet.anyoneCanAddTracks, packet.anyoneCanControlStations);
            ClientSpeakerManager.getInstance().setSound(packet.muffleBehindWalls, packet.hearingDistance);
        });
    }
}
