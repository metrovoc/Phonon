package com.metrovoc.phonon.network.packets;

import com.metrovoc.phonon.block.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

final class SpeakerPacketValidator {
    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0;

    private SpeakerPacketValidator() {}

    static SpeakerBlockEntity getAccessibleSpeaker(ServerPlayer player, BlockPos pos) {
        double distanceSquared = player.distanceToSqr(
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5
        );
        if (distanceSquared > MAX_INTERACTION_DISTANCE_SQUARED || !player.level().isLoaded(pos)) {
            return null;
        }
        return player.level().getBlockEntity(pos) instanceof SpeakerBlockEntity speaker
            ? speaker
            : null;
    }
}
