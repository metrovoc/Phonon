package com.metrovoc.phonon.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** Version-specific permission lookup, kept in one place. */
final class PlayerPermissions {
    private PlayerPermissions() {}

    static boolean isOperator(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
