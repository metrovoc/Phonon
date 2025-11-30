package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.AudioCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.core.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Audio player using Minecraft's SoundManager.
 *
 * Features:
 * - Plays through official audio pipeline (SPR compatible)
 * - Automatic stereo-to-mono downmix (handled by PhononAudioStream)
 * - Timestamp-based seeking for perfect sync
 * - Respects Minecraft volume settings
 * - Supports both Vorbis (legacy) and Opus streaming
 */
public class AudioPlayer {
    private static AudioPlayer instance;
    private final Map<BlockPos, SpeakerSoundInstance> playingSounds = new ConcurrentHashMap<>();

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
            long currentTime = System.currentTimeMillis();
            long playbackPosition = playback.getCurrentPositionMs(currentTime);

            PhononAudioStream stream = new PhononAudioStream(cachedAudio);

            if (playbackPosition > 0) {
                stream.seekMs(playbackPosition);
            }

            SpeakerSoundInstance sound = new SpeakerSoundInstance(stream, pos, volume);
            Minecraft.getInstance().getSoundManager().play(sound);

            playingSounds.put(pos, sound);

            Phonon.LOGGER.info("Started playing audio {} at {} (seek {}ms)",
                resourceId, pos, playbackPosition);

        } catch (Exception e) {
            Phonon.LOGGER.error("Failed to play audio at {}", pos, e);
        }
    }

    /**
     * Play legacy Vorbis streaming audio.
     */
    public void playStreaming(BlockPos pos, StreamingAudioStream stream, float volume) {
        stop(pos);

        SpeakerSoundInstance sound = new SpeakerSoundInstance(stream, pos, volume);
        Minecraft.getInstance().getSoundManager().play(sound);
        playingSounds.put(pos, sound);

        Phonon.LOGGER.info("Started Vorbis streaming audio at {}", pos);
    }

    /**
     * Play Opus streaming audio.
     */
    public void playOpusStreaming(BlockPos pos, OpusAudioStream stream, float volume) {
        stop(pos);

        SpeakerSoundInstance sound = new SpeakerSoundInstance(stream, pos, volume);
        Minecraft.getInstance().getSoundManager().play(sound);
        playingSounds.put(pos, sound);

        Phonon.LOGGER.info("Started Opus streaming audio at {}", pos);
    }

    /**
     * Play any AudioStream type.
     */
    public void playStream(BlockPos pos, AudioStream stream, float volume) {
        stop(pos);

        SpeakerSoundInstance sound = new SpeakerSoundInstance(stream, pos, volume);
        Minecraft.getInstance().getSoundManager().play(sound);
        playingSounds.put(pos, sound);

        Phonon.LOGGER.info("Started audio stream at {}", pos);
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

    public boolean isPlaying(BlockPos pos) {
        return playingSounds.containsKey(pos);
    }

    public void stopAll() {
        for (SpeakerSoundInstance sound : playingSounds.values()) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
        playingSounds.clear();
        Phonon.LOGGER.info("Stopped all audio playback");
    }
}
