package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.audio.Receiver;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;

import java.util.concurrent.CompletableFuture;

/** Hands the speaker's stream to the sound engine through NeoForge's stream hook. */
public final class NeoForgeSpeakerSound extends SpeakerSoundInstance {
    public NeoForgeSpeakerSound(AudioStream stream, Receiver receiver, float volume) {
        super(stream, receiver, volume);
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(stream());
    }
}
