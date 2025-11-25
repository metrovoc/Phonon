package com.metrovoc.phonon.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/**
 * Container menu for speaker block.
 * MVP: Simple menu without slots, just data sync.
 */
public class SpeakerMenu extends AbstractContainerMenu {
    private static Supplier<MenuType<SpeakerMenu>> typeSupplier;

    private final BlockPos speakerPos;
    private final Level level;

    public static void setTypeSupplier(Supplier<MenuType<SpeakerMenu>> supplier) {
        typeSupplier = supplier;
    }

    public SpeakerMenu(int containerId, Inventory playerInv, BlockPos speakerPos) {
        super(typeSupplier.get(), containerId);
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
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
