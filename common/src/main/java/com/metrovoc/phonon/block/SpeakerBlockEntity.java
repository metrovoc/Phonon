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
    private static final float DEFAULT_VOLUME = 0.5f;

    private static Supplier<BlockEntityType<SpeakerBlockEntity>> typeSupplier;
    private PlaybackState playback = PlaybackState.STOPPED;
    private float volume = DEFAULT_VOLUME;

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

        // Sync PLAYING blockstate for visual effects (light + particles)
        if (level != null) {
            BlockState state = getBlockState();
            boolean shouldPlay = playback.playing();
            if (state.getValue(SpeakerBlock.PLAYING) != shouldPlay) {
                level.setBlock(worldPosition, state.setValue(SpeakerBlock.PLAYING, shouldPlay), 3);
            }
        }
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = volume;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);

        // Speaker volume (independent of playback)
        tag.putFloat("volume", volume);

        // Playback state
        if (playback.resourceId() != null) {
            tag.putUUID("resourceId", playback.resourceId());
        }
        tag.putLong("startTime", playback.startTimeMs());
        tag.putBoolean("playing", playback.playing());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        // Load speaker volume (always present, fallback to default)
        volume = tag.contains("volume") ? tag.getFloat("volume") : DEFAULT_VOLUME;

        // Defensive loading for playback state
        try {
            if (!tag.contains("playing") || !tag.getBoolean("playing")) {
                playback = PlaybackState.STOPPED;
                return;
            }

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
            playback = new PlaybackState(resourceId, startTime, true);
        } catch (Exception e) {
            playback = PlaybackState.STOPPED;
        }
    }
}
