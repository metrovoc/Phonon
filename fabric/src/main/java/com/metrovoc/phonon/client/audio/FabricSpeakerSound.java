package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.audio.Receiver;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/** Hands the speaker's stream to the sound engine through Fabric's stream hook. */
public final class FabricSpeakerSound extends SpeakerSoundInstance {
    /** Which sound owns each stream, so the channel the stream lands in can find it (see ChannelMixin). */
    private static final Map<AudioStream, FabricSpeakerSound> OWNERS = Collections.synchronizedMap(new WeakHashMap<>());

    private FabricSpeakerSound(AudioStream stream, Receiver receiver, float volume) {
        super(stream, receiver, volume);
    }

    /**
     * Typed as the base class so the platform helper, which a dedicated server loads too, never names this class in
     * a way that makes the JVM load client-only sound classes to verify it.
     */
    public static SpeakerSoundInstance create(AudioStream stream, Receiver receiver, float volume) {
        return new FabricSpeakerSound(stream, receiver, volume);
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
        OWNERS.put(stream(), this);
        return CompletableFuture.completedFuture(stream());
    }

    /** The sound playing {@code stream}, once; null for any other stream. */
    public static FabricSpeakerSound claim(AudioStream stream) {
        return OWNERS.remove(stream);
    }
}
