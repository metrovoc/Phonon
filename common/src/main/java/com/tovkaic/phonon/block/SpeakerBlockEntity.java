package com.tovkaic.phonon.block;

import com.tovkaic.phonon.audio.PlaybackState;
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
        tag.putUUID("resourceId", playback.resourceId());
        tag.putLong("startTime", playback.startTimeMs());
        tag.putFloat("volume", playback.volume());
        tag.putBoolean("playing", playback.playing());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        // Handle empty/first-time blocks gracefully
        if (!tag.contains("resourceId")) {
            playback = PlaybackState.STOPPED;
            return;
        }

        UUID resourceId = tag.getUUID("resourceId");
        long startTime = tag.getLong("startTime");
        float volume = tag.getFloat("volume");
        boolean playing = tag.getBoolean("playing");
        playback = new PlaybackState(resourceId, startTime, volume, playing);
    }
}
