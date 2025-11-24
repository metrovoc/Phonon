package com.tovkaic.phonon.client.audio;

import com.tovkaic.phonon.Phonon;
import net.minecraft.core.BlockPos;
import org.lwjgl.openal.AL10;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;

/**
 * OpenAL audio source for 3D positioned audio playback.
 *
 * This bypasses Minecraft's SoundManager and talks directly to OpenAL.
 * Benefits:
 * - Full control over 3D positioning
 * - Can seek to specific timestamps
 * - SPR will still process it (OpenAL level)
 */
public class OpenALAudioSource {
    private final int sourceId;
    private final int bufferId;
    private final PhononAudioStream stream;
    private final BlockPos position;
    private boolean playing = false;
    private boolean stopped = false;

    public OpenALAudioSource(Path audioFile, BlockPos position, float volume, long seekToMs) {
        this.position = position;

        // Create OpenAL source and buffer
        this.sourceId = AL10.alGenSources();
        this.bufferId = AL10.alGenBuffers();

        try {
            // Open audio stream
            this.stream = new PhononAudioStream(audioFile);

            // Seek to start position if needed
            if (seekToMs > 0) {
                stream.seekMs(seekToMs);
            }

            // Read entire audio into buffer (MVP: no streaming)
            ByteBuffer audioData = readEntireStream();

            // Upload to OpenAL
            AL10.alBufferData(
                bufferId,
                AL10.AL_FORMAT_MONO16, // MONO for 3D
                audioData,
                (int) stream.getFormat().getSampleRate()
            );

            // Configure source
            AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferId);
            AL10.alSourcef(sourceId, AL10.AL_GAIN, volume);
            AL10.alSourcef(sourceId, AL10.AL_PITCH, 1.0f);
            AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_FALSE);

            // 3D positioning
            AL10.alSource3f(sourceId, AL10.AL_POSITION,
                position.getX() + 0.5f,
                position.getY() + 0.5f,
                position.getZ() + 0.5f
            );

            // Distance attenuation model (similar to Minecraft)
            AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, 4.0f);
            AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, 16.0f);
            AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 1.0f);

            checkALError("Source setup");

            Phonon.LOGGER.info("Created OpenAL source at {} with volume {}",
                position, volume);

        } catch (Exception e) {
            cleanup();
            throw new RuntimeException("Failed to create audio source", e);
        }
    }

    private ByteBuffer readEntireStream() throws Exception {
        // Read audio in chunks
        int chunkSize = 4096 * 16;
        ByteBuffer fullBuffer = ByteBuffer.allocateDirect((int) (stream.getDurationMs() * stream.getFormat().getSampleRate() / 500));

        while (true) {
            ByteBuffer chunk = stream.read(chunkSize);
            if (chunk.remaining() == 0) break;
            fullBuffer.put(chunk);
        }

        fullBuffer.flip();
        return fullBuffer;
    }

    public void play() {
        if (!stopped && !playing) {
            AL10.alSourcePlay(sourceId);
            playing = true;
            checkALError("Play");
            Phonon.LOGGER.info("Playing audio at {}", position);
        }
    }

    public void stop() {
        if (!stopped) {
            AL10.alSourceStop(sourceId);
            playing = false;
            stopped = true;
            checkALError("Stop");
        }
    }

    public void pause() {
        if (playing) {
            AL10.alSourcePause(sourceId);
            playing = false;
            checkALError("Pause");
        }
    }

    public boolean isPlaying() {
        if (stopped) return false;
        int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
        return state == AL10.AL_PLAYING;
    }

    public void setVolume(float volume) {
        AL10.alSourcef(sourceId, AL10.AL_GAIN, volume);
        checkALError("Set volume");
    }

    public BlockPos getPosition() {
        return position;
    }

    public void cleanup() {
        if (sourceId != 0) {
            AL10.alSourceStop(sourceId);
            AL10.alDeleteSources(sourceId);
        }
        if (bufferId != 0) {
            AL10.alDeleteBuffers(bufferId);
        }
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception e) {
                Phonon.LOGGER.error("Error closing stream", e);
            }
        }
    }

    private void checkALError(String operation) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            Phonon.LOGGER.error("OpenAL error during {}: {}", operation, error);
        }
    }
}
