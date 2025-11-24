package com.tovkaic.phonon.client.audio;

import com.tovkaic.phonon.Phonon;
import com.tovkaic.phonon.audio.PlaybackState;
import com.tovkaic.phonon.client.AudioCache;
import net.minecraft.core.BlockPos;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Audio player using OpenAL for 3D positioned playback.
 *
 * Features:
 * - Direct OpenAL control for precise 3D positioning
 * - Automatic stereo-to-mono downmix (SPR compatible)
 * - Timestamp-based seeking for perfect sync
 * - Distance attenuation matching Minecraft
 */
public class AudioPlayer {
    private static AudioPlayer instance;
    private final Map<BlockPos, OpenALAudioSource> playingSources = new ConcurrentHashMap<>();

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
        if (cachedAudio == null) {
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

            // Create and start OpenAL source
            OpenALAudioSource source = new OpenALAudioSource(
                cachedAudio,
                pos,
                playback.volume(),
                playbackPosition
            );

            source.play();
            playingSources.put(pos, source);

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
        OpenALAudioSource source = playingSources.remove(pos);
        if (source != null) {
            source.stop();
            source.cleanup();
            Phonon.LOGGER.info("Stopped audio at {}", pos);
        }
    }

    /**
     * Update volume for a playing speaker.
     */
    public void setVolume(BlockPos pos, float volume) {
        OpenALAudioSource source = playingSources.get(pos);
        if (source != null) {
            source.setVolume(volume);
        }
    }

    /**
     * Check if a speaker is currently playing.
     */
    public boolean isPlaying(BlockPos pos) {
        OpenALAudioSource source = playingSources.get(pos);
        return source != null && source.isPlaying();
    }

    /**
     * Stop all playback (called on disconnect).
     */
    public void stopAll() {
        for (OpenALAudioSource source : playingSources.values()) {
            source.stop();
            source.cleanup();
        }
        playingSources.clear();
        Phonon.LOGGER.info("Stopped all audio playback");
    }

    /**
     * Cleanup sources that have finished playing.
     */
    public void tick() {
        playingSources.entrySet().removeIf(entry -> {
            if (!entry.getValue().isPlaying()) {
                entry.getValue().cleanup();
                return true;
            }
            return false;
        });
    }
}
