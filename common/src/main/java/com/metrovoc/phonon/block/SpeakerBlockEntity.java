package com.metrovoc.phonon.block;

import com.metrovoc.phonon.audio.PlaybackState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Block entity storing speaker state.
 * No complex logic here - just data storage and sync.
 */
public class SpeakerBlockEntity extends BlockEntity {
    private static Supplier<BlockEntityType<SpeakerBlockEntity>> typeSupplier;
    private PlaybackState playback = PlaybackState.STOPPED;

    public static void setTypeSupplier(Supplier<BlockEntityType<SpeakerBlockEntity>> supplier) {
        typeSupplier = supplier;
    }

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(typeSupplier.get(), pos, state);
    }

    public PlaybackState getPlayback() {
        return playback;
    }

    public void setPlayback(PlaybackState playback) {
        this.playback = playback;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);

        // Save null-safe: only write UUID if not null
        if (playback.resourceId() != null) {
            tag.putUUID("resourceId", playback.resourceId());
        }
        tag.putLong("startTime", playback.startTimeMs());
        tag.putFloat("volume", playback.volume());
        tag.putBoolean("playing", playback.playing());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        // Defensive loading: gracefully handle missing or corrupt data
        try {
            // If no playing state or not playing, reset to STOPPED
            if (!tag.contains("playing") || !tag.getBoolean("playing")) {
                playback = PlaybackState.STOPPED;
                return;
            }

            // Playing state requires valid resourceId
            if (!tag.contains("resourceId")) {
                playback = PlaybackState.STOPPED;
                return;
            }

            UUID resourceId = tag.getUUID("resourceId");
            if (resourceId == null) {
                playback = PlaybackState.STOPPED;
                return;
            }

            long startTime = tag.getLong("startTime");
            float volume = tag.getFloat("volume");
            playback = new PlaybackState(resourceId, startTime, volume, true);
        } catch (Exception e) {
            // Corrupt data: reset to STOPPED rather than crash world loading
            playback = PlaybackState.STOPPED;
        }
    }
}
