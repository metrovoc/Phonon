package com.tovkaic.phonon.client.audio;

import com.tovkaic.phonon.Phonon;
import com.tovkaic.phonon.audio.PlaybackState;
import com.tovkaic.phonon.client.AudioCache;
import net.minecraft.client.Minecraft;
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

    /**
     * Start or update playback at a speaker position.
     *
     * @param pos Speaker position
     * @param playback Playback state from server
     * @param resourceId Audio resource ID
     */
    public void play(BlockPos pos, PlaybackState playback, UUID resourceId) {
        // Check if audio is cached
        Path cachedAudio = AudioCache.getInstance().getCachedAudio(resourceId).orElse(null);
        if (cachedAudio == null || !Files.exists(cachedAudio)) {
            Phonon.LOGGER.warn("Audio {} not cached yet, requesting download", resourceId);
            // TODO: Send RequestAudioPacket to server
            return;
        }

        // Stop existing sound at this position
        stop(pos);

        try {
            // Calculate seek position for sync
            long currentTime = System.currentTimeMillis();
            long playbackPosition = playback.getCurrentPositionMs(currentTime);

            // Create audio stream
            PhononAudioStream stream = new PhononAudioStream(cachedAudio);

            // Seek to correct position
            if (playbackPosition > 0) {
                stream.seekMs(playbackPosition);
            }

            // Create sound instance and play through SoundManager
            SpeakerSoundInstance sound = new SpeakerSoundInstance(stream, pos, playback.volume());
            Minecraft.getInstance().getSoundManager().play(sound);

            playingSounds.put(pos, sound);

            Phonon.LOGGER.info("Started playing audio {} at {} (seek {}ms)",
                resourceId, pos, playbackPosition);

        } catch (Exception e) {
            Phonon.LOGGER.error("Failed to play audio at {}", pos, e);
        }
    }

    /**
     * Stop playback at a speaker position.
     */
    public void stop(BlockPos pos) {
        SpeakerSoundInstance sound = playingSounds.remove(pos);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
            Phonon.LOGGER.info("Stopped audio at {}", pos);
        }
    }

    /**
     * Update volume for a playing speaker.
     */
    public void setVolume(BlockPos pos, float volume) {
        SpeakerSoundInstance sound = playingSounds.get(pos);
        if (sound != null) {
            // Volume changes require restart (limitation of current implementation)
            Phonon.LOGGER.warn("Volume change requires sound restart (not implemented yet)");
        }
    }

    /**
     * Check if a speaker is currently playing.
     */
    public boolean isPlaying(BlockPos pos) {
        return playingSounds.containsKey(pos);
    }

    /**
     * Stop all playback (called on disconnect).
     */
    public void stopAll() {
        for (SpeakerSoundInstance sound : playingSounds.values()) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
        playingSounds.clear();
        Phonon.LOGGER.info("Stopped all audio playback");
    }

    /**
     * Cleanup sounds that have finished playing.
     */
    public void tick() {
        // SoundManager handles cleanup automatically
    }
}
