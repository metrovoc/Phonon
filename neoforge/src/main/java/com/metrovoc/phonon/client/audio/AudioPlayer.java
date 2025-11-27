package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.block.SpeakerBlock;
import com.metrovoc.phonon.client.AudioCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Audio player using direct OpenAL via PhononAudioEngine.
 */
public class AudioPlayer {
    private static AudioPlayer instance;

    private AudioPlayer() {}

    public static AudioPlayer getInstance() {
        if (instance == null) {
            instance = new AudioPlayer();
        }
        return instance;
    }

    public void play(BlockPos pos, PlaybackState playback, UUID resourceId, float volume) {
        Path cachedAudio = AudioCache.getInstance().getCachedAudio(resourceId).orElse(null);
        if (cachedAudio == null || !Files.exists(cachedAudio)) {
            Phonon.LOGGER.error("Audio {} not cached", resourceId);
            return;
        }

        Direction facing = getFacing(pos);
        long currentTime = System.currentTimeMillis();
        long seekMs = playback.getCurrentPositionMs(currentTime);

        PhononAudioEngine.getInstance().play(pos, facing, cachedAudio, seekMs, volume);
    }

    public void stop(BlockPos pos) {
        PhononAudioEngine.getInstance().stop(pos);
    }

    public void setVolume(BlockPos pos, float volume) {
        PhononAudioEngine.getInstance().setVolume(pos, volume);
    }

    public boolean isPlaying(BlockPos pos) {
        return PhononAudioEngine.getInstance().isPlaying(pos);
    }

    public void stopAll() {
        PhononAudioEngine.getInstance().stopAll();
    }

    private Direction getFacing(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Direction.NORTH;

        BlockState state = mc.level.getBlockState(pos);
        if (state.hasProperty(SpeakerBlock.FACING)) {
            return state.getValue(SpeakerBlock.FACING);
        }
        return Direction.NORTH;
    }
}
