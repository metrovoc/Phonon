package com.metrovoc.phonon.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;

/**
 * Container menu for speaker block.
 * Handles server-side logic for speaker control.
 *
 * MVP: Simple menu without slots, just data sync.
 */
public class SpeakerMenu extends AbstractContainerMenu {
    private final BlockPos speakerPos;
    private final Level level;

    public SpeakerMenu(int containerId, Inventory playerInv, BlockPos speakerPos) {
        super(com.metrovoc.phonon.registry.PhononRegistry.SPEAKER_MENU.get(), containerId);
        this.speakerPos = speakerPos;
        this.level = playerInv.player.level();
    }

    public BlockPos getSpeakerPos() {
        return speakerPos;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(
            speakerPos.getX() + 0.5,
            speakerPos.getY() + 0.5,
            speakerPos.getZ() + 0.5
        ) <= 64.0;
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
