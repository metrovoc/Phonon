package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.PlaybackState;
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
            Minecraft.getInstance().getSoundManager().stop(sound);
            Phonon.LOGGER.debug("Stopped audio at {}", pos);
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
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
        playingSounds.clear();
        Phonon.LOGGER.info("Stopped all audio playback");
    }
}
