package com.metrovoc.phonon.neoforge;

import com.metrovoc.phonon.client.audio.SpeakerSoundInstance;

import com.metrovoc.phonon.audio.Receiver;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;

import java.util.concurrent.CompletableFuture;

/** Hands the speaker's stream to the sound engine through NeoForge's stream hook. */
public final class NeoForgeSpeakerSound extends SpeakerSoundInstance {
    private NeoForgeSpeakerSound(AudioStream stream, Receiver receiver, float volume) {
        super(stream, receiver, volume);
    }

    /**
     * Typed as the base class so the platform helper, which a dedicated server loads too, never names this class in
     * a way that makes the JVM load client-only sound classes to verify it.
     */
    public static SpeakerSoundInstance create(AudioStream stream, Receiver receiver, float volume) {
        return new NeoForgeSpeakerSound(stream, receiver, volume);
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(stream());
    }
}
