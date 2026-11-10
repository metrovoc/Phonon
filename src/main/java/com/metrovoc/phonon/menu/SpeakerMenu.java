package com.metrovoc.phonon.menu;

import com.metrovoc.phonon.audio.Receiver;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Container menu for speaker block.
 * MVP: Simple menu without slots, just data sync.
 */
public class SpeakerMenu extends AbstractContainerMenu {
    private static Supplier<MenuType<SpeakerMenu>> typeSupplier;

    private final Receiver receiver;

    public static void setTypeSupplier(Supplier<MenuType<SpeakerMenu>> supplier) {
        typeSupplier = supplier;
    }

    public SpeakerMenu(int containerId, Inventory playerInv, Receiver receiver) {
        super(typeSupplier.get(), containerId);
        this.receiver = receiver;
    }

    public Receiver receiver() {
        return receiver;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
