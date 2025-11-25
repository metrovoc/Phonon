package com.tovkaic.phonon.client.audio;

import com.tovkaic.phonon.Phonon;
import com.tovkaic.phonon.audio.PlaybackState;
import com.tovkaic.phonon.client.AudioCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import org.lwjgl.openal.AL10;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAL-based audio player for speaker playback.
 *
 * Direct OpenAL Implementation Benefits:
 * - Bypasses Minecraft's resource loading completely
 * - Full control over 3D positioning
 * - SPR can still intercept OpenAL calls (it hooks at the OpenAL level)
 * - Simpler architecture than Mixin-based solutions
 *
 * Technical Notes:
 * - Uses streaming buffers for large audio files
 * - Automatically downmixes to mono for 3D positioning
 * - Respects Minecraft's master volume
 */
public class OpenALAudioPlayer {
    private static final OpenALAudioPlayer instance = new OpenALAudioPlayer();

    private final Map<BlockPos, PlayingSound> playingSounds = new ConcurrentHashMap<>();

    private static class PlayingSound {
        final int sourceId;
        final int[] bufferIds;
        final PhononAudioStream stream;
        boolean shouldStop = false;

        PlayingSound(int sourceId, int[] bufferIds, PhononAudioStream stream) {
            this.sourceId = sourceId;
            this.bufferIds = bufferIds;
            this.stream = stream;
        }
    }

    private OpenALAudioPlayer() {}

    public static OpenALAudioPlayer getInstance() {
        return instance;
    }

    /**
     * Start or update playback at a speaker position.
     */
    public void play(BlockPos pos, PlaybackState playback, UUID resourceId) {
        // Check if audio is cached
        Path cachedAudio = AudioCache.getInstance().getCachedAudio(resourceId).orElse(null);
        if (cachedAudio == null || !Files.exists(cachedAudio)) {
            Phonon.LOGGER.error("Audio {} not cached", resourceId);
            return;
        }

        // Stop existing sound
        stop(pos);

        try {
            // Calculate seek position
            long currentTime = System.currentTimeMillis();
            long playbackPosition = playback.getCurrentPositionMs(currentTime);

            // Create audio stream
            PhononAudioStream stream = new PhononAudioStream(cachedAudio);

            // Seek to position
            if (playbackPosition > 0) {
                stream.seekMs(playbackPosition);
            }

            // Create OpenAL source
            int sourceId = AL10.alGenSources();
            checkALError("Generate source");

            // Configure 3D properties (CRITICAL for SPR compatibility)
            AL10.alSourcef(sourceId, AL10.AL_PITCH, 1.0f);
            AL10.alSourcef(sourceId, AL10.AL_GAIN, getEffectiveVolume(playback.volume()));

            // 3D positioning
            AL10.alSource3f(sourceId, AL10.AL_POSITION,
                (float) pos.getX() + 0.5f,
                (float) pos.getY() + 0.5f,
                (float) pos.getZ() + 0.5f);
            AL10.alSource3f(sourceId, AL10.AL_VELOCITY, 0, 0, 0);

            // Attenuation settings (matches Minecraft's LINEAR attenuation)
            AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
            AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 1.0f);
            AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, 16.0f);
            AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, 64.0f);

            AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_FALSE);

            checkALError("Configure source");

            // Create streaming buffers (4 buffers for smooth streaming)
            int[] bufferIds = new int[4];
            for (int i = 0; i < bufferIds.length; i++) {
                bufferIds[i] = AL10.alGenBuffers();
                fillBuffer(bufferIds[i], stream);
            }
            checkALError("Create buffers");

            // Queue buffers
            AL10.alSourceQueueBuffers(sourceId, bufferIds);
            checkALError("Queue buffers");

            // Start playback
            AL10.alSourcePlay(sourceId);
            checkALError("Play source");

            PlayingSound sound = new PlayingSound(sourceId, bufferIds, stream);
            playingSounds.put(pos, sound);

            Phonon.LOGGER.info("Started OpenAL playback at {} (source {})", pos, sourceId);

        } catch (Exception e) {
            Phonon.LOGGER.error("Failed to play audio at {}", pos, e);
        }
    }

    /**
     * Fill a buffer with audio data from the stream.
     */
    private void fillBuffer(int bufferId, PhononAudioStream stream) throws Exception {
        ByteBuffer data = stream.read(16384); // 16KB chunks

        try {
            if (data.remaining() > 0) {
                int format = stream.getFormat().getChannels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
                AL10.alBufferData(bufferId, format, data, (int) stream.getFormat().getSampleRate());
            }
        } finally {
            // Free the direct buffer after OpenAL copies the data
            org.lwjgl.system.MemoryUtil.memFree(data);
        }
    }

    /**
     * Get effective volume (respects Minecraft's volume settings).
     */
    private float getEffectiveVolume(float volume) {
        Options options = Minecraft.getInstance().options;
        float masterVolume = (float) options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
        float recordsVolume = (float) options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.RECORDS);
        return volume * masterVolume * recordsVolume;
    }

    /**
     * Stop playback at a position.
     */
    public void stop(BlockPos pos) {
        PlayingSound sound = playingSounds.remove(pos);
        if (sound != null) {
            AL10.alSourceStop(sound.sourceId);
            AL10.alDeleteSources(sound.sourceId);
            AL10.alDeleteBuffers(sound.bufferIds);
            sound.stream.close();
            Phonon.LOGGER.info("Stopped OpenAL playback at {}", pos);
        }
    }

    /**
     * Update streaming (called every tick).
     */
    public void tick() {
        playingSounds.forEach((pos, sound) -> {
            int processed = AL10.alGetSourcei(sound.sourceId, AL10.AL_BUFFERS_PROCESSED);

            // Refill processed buffers
            while (processed-- > 0) {
                int bufferId = AL10.alSourceUnqueueBuffers(sound.sourceId);

                try {
                    fillBuffer(bufferId, sound.stream);
                    AL10.alSourceQueueBuffers(sound.sourceId, bufferId);
                } catch (Exception e) {
                    Phonon.LOGGER.error("Error refilling buffer", e);
                    AL10.alDeleteBuffers(bufferId);
                    sound.shouldStop = true;
                }
            }

            // Check if finished
            int state = AL10.alGetSourcei(sound.sourceId, AL10.AL_SOURCE_STATE);
            if (state == AL10.AL_STOPPED || sound.shouldStop) {
                stop(pos);
            }
        });
    }

    /**
     * Stop all playback.
     */
    public void stopAll() {
        playingSounds.keySet().forEach(this::stop);
    }

    private void checkALError(String operation) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            throw new RuntimeException("OpenAL Error during " + operation + ": " + error);
        }
    }
}
