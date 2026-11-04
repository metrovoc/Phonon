package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.station.Boombox;
import com.metrovoc.phonon.station.StationListener;
import com.metrovoc.phonon.station.StationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

final class SpeakerPacketValidator {
    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0;

    private SpeakerPacketValidator() {}

    /** A speaker within the player's reach, or a boombox the player has. Otherwise null. */
    static StationListener getAccessible(ServerPlayer player, Receiver receiver) {
        return switch (receiver) {
            case Receiver.Block block -> getAccessibleSpeaker(player, block.pos());
            case Receiver.Boombox id -> {
                Boombox boombox = StationManager.getInstance().boomboxes().get(id.id());
                yield boombox != null && boombox.heldBy(player) ? boombox : null;
            }
        };
    }

    private static SpeakerBlockEntity getAccessibleSpeaker(ServerPlayer player, BlockPos pos) {
        double distanceSquared = player.distanceToSqr(
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5
        );
        if (distanceSquared > MAX_INTERACTION_DISTANCE_SQUARED || !player.serverLevel().isLoaded(pos)) {
            return null;
        }
        return player.serverLevel().getBlockEntity(pos) instanceof SpeakerBlockEntity speaker
            ? speaker
            : null;
    }
}
