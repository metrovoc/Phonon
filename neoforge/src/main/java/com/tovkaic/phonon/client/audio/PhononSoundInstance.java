package com.tovkaic.phonon.client.audio;

import com.tovkaic.phonon.Phonon;
import com.tovkaic.phonon.audio.PlaybackState;
import com.tovkaic.phonon.registry.PhononRegistry;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;

/**
 * SoundInstance that plays dynamic audio through Minecraft's SoundManager.
 *
 * This is the CORRECT way to play audio in Minecraft:
 * - Automatically compatible with Sound Physics Remastered
 * - Respects Minecraft volume settings
 * - Proper resource lifecycle management
 */
public class PhononSoundInstance extends AbstractTickableSoundInstance {
    private final UUID resourceId;
    private final BlockPos sourcePos;
    private PlaybackState playbackState;
    private PhononAudioStream stream;

    public PhononSoundInstance(UUID resourceId, BlockPos pos, PlaybackState initialState) {
        super(
            PhononRegistry.SPEAKER_SOUND.get(),
            SoundSource.RECORDS,
            SoundInstance.createUnseededRandom()
        );

        this.resourceId = resourceId;
        this.sourcePos = pos;
        this.playbackState = initialState;

        // 3D positioning (required for SPR compatibility)
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;

        this.looping = false;
        this.volume = initialState.volume();
        this.attenuation = Attenuation.LINEAR;
        this.delay = 0;

        Phonon.LOGGER.info("Created PhononSoundInstance for resource {} at {} with volume {}",
            resourceId, pos, initialState.volume());
    }

    public void setStream(PhononAudioStream stream) {
        this.stream = stream;
    }

    public AudioStream getStream() {
        return this.stream;
    }

    public void updateState(PlaybackState state) {
        this.playbackState = state;
        this.volume = state.volume();
    }

    @Override
    public void tick() {
        if (!playbackState.playing()) {
            this.stop();
        }
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public BlockPos getSourcePos() {
        return sourcePos;
    }

    public long getStartTimeMs() {
        return playbackState.startTimeMs();
    }
}
