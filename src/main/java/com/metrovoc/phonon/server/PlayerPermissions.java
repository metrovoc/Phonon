package com.metrovoc.phonon.server;

import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.11
import net.minecraft.server.permissions.Permissions;

/** Version-specific permission lookup, kept in one place. */
final class PlayerPermissions {
    private PlayerPermissions() {}

    static boolean isOperator(ServerPlayer player) {
        //? if >=1.21.11 {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        //?} else
        /*return player.hasPermissions(2);*/
    }
}
