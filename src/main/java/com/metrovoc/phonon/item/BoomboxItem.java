package com.metrovoc.phonon.item;

import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.block.SpeakerBlock;
import com.metrovoc.phonon.server.SpeakerControl;
import com.metrovoc.phonon.station.Boombox;
import com.metrovoc.phonon.station.StationManager;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/** A speaker you carry. Use it to open its controls; sneak and use it to switch it on or off. */
public class BoomboxItem extends Item {
    private static Supplier<DataComponentType<UUID>> idComponent;
    private static Supplier<DataComponentType<Boolean>> playingComponent;

    public static void setComponents(Supplier<DataComponentType<UUID>> id, Supplier<DataComponentType<Boolean>> playing) {
        idComponent = id;
        playingComponent = playing;
    }

    public BoomboxItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static UUID idOf(ItemStack stack) {
        return stack.get(idComponent.get());
    }

    /** Gives a new boombox its id, which is what its state is kept under. */
    public static UUID assignId(ItemStack stack) {
        UUID id = idOf(stack);
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(idComponent.get(), id);
        }
        return id;
    }

    public static boolean isShownPlaying(ItemStack stack) {
        return stack.has(playingComponent.get());
    }

    /** Lights the item up while it plays. Returns whether that changed anything. */
    public static boolean showPlaying(ItemStack stack, boolean playing) {
        if (stack.has(playingComponent.get()) == playing) {
            return false;
        }
        if (playing) {
            stack.set(playingComponent.get(), true);
        } else {
            stack.remove(playingComponent.get());
        }
        return true;
    }

    public static boolean carries(Player player, UUID id) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (id.equals(idOf(player.getInventory().getItem(slot)))) {
                return true;
            }
        }
        return false;
    }

    @Override
    //? if >=1.21.2 {
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return useBoombox(level, player, hand);
    }
    //?} else {
    /*public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // As vanilla does before 1.21.2, only the client swings the arm.
        InteractionResult result = useBoombox(level, player, hand);
        if (result == InteractionResult.SUCCESS && !level.isClientSide()) {
            result = InteractionResult.CONSUME;
        }
        return new net.minecraft.world.InteractionResultHolder<>(result, player.getItemInHand(hand));
    }
    *///?}

    private static InteractionResult useBoombox(Level level, Player player, InteractionHand hand) {
        UUID id = idOf(player.getItemInHand(hand));
        if (id == null) {
            return InteractionResult.PASS;
        }
        if (!player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                SpeakerBlock.openGui(new Receiver.Boombox(id));
            }
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            Boombox boombox = StationManager.getInstance().boomboxes().get(id);
            if (boombox != null && SpeakerControl.canControl(server, boombox)) {
                SpeakerControl.toggle(boombox);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.5f, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }

    /** Its lights changing shouldn't make the player's hand bob. */
    public boolean shouldCauseReequipAnimation(ItemStack from, ItemStack to, boolean slotChanged) {
        return slotChanged;
    }
}
