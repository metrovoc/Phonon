package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.ClientAudioManager;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.metrovoc.phonon.client.AudioCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Audio player using Minecraft's SoundManager.
 *
 * Features:
 * - Plays through official audio pipeline (SPR compatible)
 * - Automatic stereo-to-mono downmix (handled by PhononAudioStream)
 * - Timestamp-based seeking for perfect sync
 * - Respects Minecraft volume settings
 */
public class AudioPlayer {
    private static AudioPlayer instance;
    private static final float MUSIC_DUCK_VOLUME = 0.03f;

    private final Map<BlockPos, SpeakerSoundInstance> playingSounds = new HashMap<>();

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
            Phonon.LOGGER.error("Audio {} not cached (caller should have downloaded first)", resourceId);
            return;
        }

        stop(pos);

        try {
            long playbackPosition = playback.getCurrentPositionMs();

            PhononAudioStream stream = new PhononAudioStream(cachedAudio);

            if (playbackPosition > 0) {
                stream.seekMs(playbackPosition);
            }

            SpeakerSoundInstance sound = new SpeakerSoundInstance(stream, pos, volume);
            Minecraft.getInstance().getSoundManager().play(sound);

            playingSounds.put(pos, sound);

            Phonon.LOGGER.debug("Started playing audio {} at {} (seek {}ms)",
                resourceId, pos, playbackPosition);

        } catch (Exception e) {
            Phonon.LOGGER.error("Failed to play audio at {}", pos, e);
        }
    }

    public void playStreaming(BlockPos pos, StreamingAudioStream stream, float volume) {
        stop(pos);

        SpeakerSoundInstance sound = new SpeakerSoundInstance(stream, pos, volume);
        Minecraft.getInstance().getSoundManager().play(sound);
        playingSounds.put(pos, sound);

        Phonon.LOGGER.debug("Started streaming audio at {}", pos);
    }

    public void stop(BlockPos pos) {
        SpeakerSoundInstance sound = playingSounds.remove(pos);
        if (sound != null) {
            sound.fadeOut();
            Phonon.LOGGER.debug("Stopped audio at {}", pos);
        }
    }

    /** Runs each client tick: keeps audio in step with the clock, and quiets game music near a playing speaker. */
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean audible = false;
        for (Map.Entry<BlockPos, SpeakerSoundInstance> entry : Map.copyOf(playingSounds).entrySet()) {
            SpeakerSoundInstance sound = entry.getValue();
            audible |= sound.audibleVolume() > MUSIC_DUCK_VOLUME;
            StreamTap tap = sound.tap();
            var state = ClientSpeakerManager.getInstance().getSpeakerState(entry.getKey());
            if (tap != null && state.isPresent() && state.get().isPlaying()) {
                long durationMs = ClientAudioManager.getInstance().getResource(state.get().resourceId())
                    .map(AudioResource::durationMs).orElse(-1L);
                if (DriftCheck.needsResync(tap.writtenMs(), state.get().getCurrentPositionMs(), durationMs, sound.age())) {
                    ClientSpeakerManager.getInstance().resync(entry.getKey());
                }
            }
        }
        if (audible) {
            minecraft.getMusicManager().stopPlaying();
        }
    }

    public void readLevels(BlockPos pos, long positionMs, float[] bars, int msPerBar) {
        SpeakerSoundInstance sound = playingSounds.get(pos);
        StreamTap tap = sound == null ? null : sound.tap();
        if (tap == null) {
            java.util.Arrays.fill(bars, 0);
        } else {
            tap.history(positionMs, bars, msPerBar);
        }
    }

    public void setVolume(BlockPos pos, float volume) {
        SpeakerSoundInstance sound = playingSounds.get(pos);
        if (sound != null) {
            sound.setVolume(volume);
        }
    }

    public void stopAll() {
        for (SpeakerSoundInstance sound : playingSounds.values()) {
            sound.stopNow();
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
        playingSounds.clear();
        Phonon.LOGGER.info("Stopped all audio playback");
    }
}
