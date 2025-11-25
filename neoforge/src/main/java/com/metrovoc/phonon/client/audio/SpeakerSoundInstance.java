package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

import java.util.concurrent.CompletableFuture;

/**
 * SoundInstance that plays through Minecraft's SoundManager.
 *
 * This is the CORRECT way to play audio in Minecraft:
 * - Automatically compatible with Sound Physics Remastered
 * - Respects Minecraft volume settings
 * - Proper resource lifecycle management
 * - Works with F3+S debug screen
 * - No sounds.json dependency - fully programmatic
 */
public class SpeakerSoundInstance extends AbstractTickableSoundInstance {
    private static final ResourceLocation DUMMY_SOUND_LOCATION =
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "speaker");
    private static final Sound DUMMY_SOUND = new Sound(
        DUMMY_SOUND_LOCATION,
        ConstantFloat.of(1.0f),  // volume (identity at 1.0)
        ConstantFloat.of(1.0f),  // pitch (identity at 1.0)
        1,                        // weight
        Sound.Type.FILE,
        true,                     // stream (important for long audio)
        false,                    // preload
        16                        // attenuation distance
    );

    private final PhononAudioStream stream;
    private final BlockPos sourcePos;

    public SpeakerSoundInstance(PhononAudioStream stream, BlockPos pos, float volume) {
        super(
            SoundEvent.createVariableRangeEvent(DUMMY_SOUND_LOCATION),
            SoundSource.BLOCKS,
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

        Phonon.LOGGER.debug("Created SpeakerSoundInstance at {} with volume {}", pos, volume);
    }

    /**
     * Override to bypass sounds.json resolution entirely.
     * Returns our dummy Sound object directly.
     */
    @Override
    public Sound getSound() {
        return DUMMY_SOUND;
    }

    /**
     * Override to bypass SoundManager's sound event lookup.
     * This prevents "Unknown sound" warnings in logs.
     * CRITICAL: Must set this.sound field - getVolume() reads it directly, not via getSound()
     */
    @Override
    public WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager soundManager) {
        this.sound = DUMMY_SOUND;
        return new WeighedSoundEvents(DUMMY_SOUND_LOCATION, null);
    }

    /**
     * Override to provide our custom audio stream instead of loading from resource pack.
     * This is the key to SPR compatibility - Minecraft's SoundEngine will use this stream.
     */
    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(this.stream);
    }

    @Override
    public void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            super.stop();
            return;
        }

        if (!(level.getBlockEntity(sourcePos) instanceof SpeakerBlockEntity)) {
            super.stop();
            return;
        }

        var state = ClientSpeakerManager.getInstance().getSpeakerState(sourcePos);
        if (state.isEmpty() || !state.get().playing()) {
            super.stop();
        }
    }

    public BlockPos getPosition() {
        return sourcePos;
    }
}
