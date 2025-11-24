package com.tovkaic.phonon.block;

import com.tovkaic.phonon.audio.PlaybackState;
import net.minecraft.core.BlockPos;

/**
 * Data associated with a Speaker block in the world.
 * Stored in block entity, synchronized to clients.
 */
public record SpeakerData(
    BlockPos pos,
    PlaybackState playback
) {
    public SpeakerData(BlockPos pos) {
        this(pos, PlaybackState.STOPPED);
    }

    public SpeakerData withPlayback(PlaybackState newPlayback) {
        return new SpeakerData(pos, newPlayback);
    }
}
