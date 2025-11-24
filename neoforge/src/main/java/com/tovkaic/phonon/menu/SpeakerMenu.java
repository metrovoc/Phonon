package com.tovkaic.phonon.menu;

import com.tovkaic.phonon.audio.AudioResource;
import com.tovkaic.phonon.audio.PlaybackState;
import com.tovkaic.phonon.block.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;

import java.util.UUID;

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
        super(null, containerId); // TODO: Register MenuType
        this.speakerPos = speakerPos;
        this.level = playerInv.player.level();
    }

    public BlockPos getSpeakerPos() {
        return speakerPos;
    }

    /**
     * Start playback of a resource.
     */
    public void playAudio(UUID resourceId, float volume) {
        if (level.isClientSide) return;

        if (level.getBlockEntity(speakerPos) instanceof SpeakerBlockEntity speaker) {
            PlaybackState playback = new PlaybackState(
                resourceId,
                System.currentTimeMillis(),
                volume,
                true
            );
            speaker.setPlayback(playback);

            // TODO: Send sync packet to all tracking players
        }
    }

    /**
     * Stop playback.
     */
    public void stopAudio() {
        if (level.isClientSide) return;

        if (level.getBlockEntity(speakerPos) instanceof SpeakerBlockEntity speaker) {
            speaker.setPlayback(PlaybackState.STOPPED);

            // TODO: Send sync packet to all tracking players
        }
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
