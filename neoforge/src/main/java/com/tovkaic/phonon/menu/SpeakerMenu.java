package com.tovkaic.phonon.menu;

import com.tovkaic.phonon.audio.AudioResource;
import com.tovkaic.phonon.audio.PlaybackState;
import com.tovkaic.phonon.block.SpeakerBlockEntity;
import com.tovkaic.phonon.network.packets.SyncSpeakerStatePacket;
import com.tovkaic.phonon.platform.PlatformHelper;
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
        super(com.tovkaic.phonon.registry.PhononRegistry.SPEAKER_MENU.get(), containerId);
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
            long serverTime = System.currentTimeMillis();
            PlaybackState playback = new PlaybackState(
                resourceId,
                serverTime,
                volume,
                true
            );
            speaker.setPlayback(playback);

            // Send sync packet with server timestamp for clock sync
            PlatformHelper.INSTANCE.sendToAllTracking(
                level,
                speakerPos,
                new SyncSpeakerStatePacket(speakerPos, playback, serverTime)
            );
        }
    }

    /**
     * Stop playback.
     */
    public void stopAudio() {
        if (level.isClientSide) return;

        if (level.getBlockEntity(speakerPos) instanceof SpeakerBlockEntity speaker) {
            speaker.setPlayback(PlaybackState.STOPPED);

            // Send sync packet with current server time
            PlatformHelper.INSTANCE.sendToAllTracking(
                level,
                speakerPos,
                new SyncSpeakerStatePacket(speakerPos, PlaybackState.STOPPED, System.currentTimeMillis())
            );
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
