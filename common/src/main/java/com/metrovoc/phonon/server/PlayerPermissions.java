package com.metrovoc.phonon.server;

import net.minecraft.server.level.ServerPlayer;

/** Version-specific permission lookup, kept in one place. */
final class PlayerPermissions {
    private PlayerPermissions() {}

    static boolean isOperator(ServerPlayer player) {
        return player.hasPermissions(2);
    }
}
