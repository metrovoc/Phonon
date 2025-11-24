package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.registry.PhononRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

/**
 * SoundInstance that plays through Minecraft's SoundManager.
 *
 * This is the CORRECT way to play audio in Minecraft:
 * - Automatically compatible with Sound Physics Remastered
 * - Respects Minecraft volume settings
 * - Proper resource lifecycle management
 * - Works with F3+S debug screen
 */
public class SpeakerSoundInstance extends AbstractTickableSoundInstance {
    private final PhononAudioStream stream;
    private final BlockPos sourcePos;

    public SpeakerSoundInstance(PhononAudioStream stream, BlockPos pos, float volume) {
        super(
            PhononRegistry.SPEAKER_SOUND.get(),
            SoundSource.RECORDS,
            SoundInstance.createUnseededRandom()
        );

        this.stream = stream;
        this.sourcePos = pos;

        // 3D positioning (required for SPR)
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;

        this.looping = false;
        this.volume = volume;
        this.attenuation = Attenuation.LINEAR;
        this.delay = 0;

        Phonon.LOGGER.info("Created SpeakerSoundInstance at {} with volume {}", pos, volume);
    }

    public AudioStream getStream() {
        return this.stream;
    }

    @Override
    public void tick() {
        // Check if speaker still exists and is playing
        if (Minecraft.getInstance().level != null) {
            if (Minecraft.getInstance().level.getBlockEntity(sourcePos) instanceof SpeakerBlockEntity be) {
                if (!be.getPlayback().playing()) {
                    super.stop();
                }
            } else {
                // Block removed
                super.stop();
            }
        }
    }

    public BlockPos getPosition() {
        return sourcePos;
    }
}
