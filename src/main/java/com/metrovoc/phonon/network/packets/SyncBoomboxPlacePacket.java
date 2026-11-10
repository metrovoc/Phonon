package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import com.metrovoc.phonon.network.PacketContext;

import java.util.UUID;

/**
 * Server -> Client: what has a boombox now, kept apart from its playback so a change of hands never touches the audio.
 * A negative entity id means this player can no longer hear it.
 */
public record SyncBoomboxPlacePacket(UUID id, int entityId, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<SyncBoomboxPlacePacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sync_boombox_place"));

    public static final StreamCodec<ByteBuf, SyncBoomboxPlacePacket> CODEC = StreamCodec.composite(
        UUIDCodec.STREAM_CODEC,
        SyncBoomboxPlacePacket::id,
        ByteBufCodecs.VAR_INT,
        SyncBoomboxPlacePacket::entityId,
        ByteBufCodecs.DOUBLE,
        SyncBoomboxPlacePacket::x,
        ByteBufCodecs.DOUBLE,
        SyncBoomboxPlacePacket::y,
        ByteBufCodecs.DOUBLE,
        SyncBoomboxPlacePacket::z,
        SyncBoomboxPlacePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncBoomboxPlacePacket packet, PacketContext context) {
        context.enqueueWork(() -> {
            if (packet.entityId < 0) {
                ClientSpeakerManager.getInstance().removeBoombox(packet.id);
            } else {
                ClientSpeakerManager.getInstance().placeBoombox(packet.id, packet.entityId, new Vec3(packet.x, packet.y, packet.z));
            }
        });
    }
}
