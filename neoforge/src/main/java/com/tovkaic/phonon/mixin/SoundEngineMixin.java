package com.tovkaic.phonon.mixin;

import com.tovkaic.phonon.Phonon;
import com.tovkaic.phonon.client.audio.PhononSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept SoundEngine's play method and handle our custom AudioStream.
 *
 * This is required because Minecraft's standard audio loading expects static resource pack files,
 * but we need to provide dynamic audio streams from downloaded content.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    /**
     * Intercept the play method to detect our SpeakerSoundInstance.
     *
     * When we detect our custom sound, we need to handle it specially because
     * it provides a PhononAudioStream instead of loading from resource pack.
     */
    @Inject(method = "play", at = @At("HEAD"))
    private void onPlay(SoundInstance soundInstance, CallbackInfo ci) {
        if (soundInstance instanceof PhononSoundInstance phonon) {
            Phonon.LOGGER.info("SoundEngine intercepted PhononSoundInstance for resource {}",
                phonon.getResourceId());
            // The actual audio stream handling is done in the resource loading phase
            // We just log here for debugging
        }
    }
}
