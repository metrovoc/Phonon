package com.metrovoc.phonon.mixin;

import com.metrovoc.phonon.client.PhononClient;
import com.metrovoc.phonon.client.audio.FabricSpeakerSound;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.AudioStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells a speaker sound which OpenAL source it got, as NeoForge's PlayStreamingSourceEvent does. */
@Mixin(Channel.class)
abstract class ChannelMixin {
    @Inject(method = "attachBufferStream", at = @At("HEAD"))
    private void phonon$attachSpeaker(AudioStream stream, CallbackInfo ci) {
        FabricSpeakerSound sound = FabricSpeakerSound.claim(stream);
        if (sound != null) {
            PhononClient.streamStarted(sound, (Channel) (Object) this);
        }
    }
}
