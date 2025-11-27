package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.client.audio.PhononAudioStream.ChannelMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A stereo audio source using two OpenAL sources (L/R) offset for 3D positioning.
 *
 * Data flow:
 * OGG file -> PhononAudioStream (L) -> buffers -> sourceL
 *          -> PhononAudioStream (R) -> buffers -> sourceR
 */
public class StereoSource {
    private static final float STEREO_OFFSET = 0.5f;
    private static final int BUFFER_COUNT = 4;
    private static final int BUFFER_SIZE = 8192;

    private final int sourceL;
    private final int sourceR;
    private final PhononAudioStream streamL;
    private final PhononAudioStream streamR;
    private final Queue<Integer> freeBuffersL = new ArrayDeque<>();
    private final Queue<Integer> freeBuffersR = new ArrayDeque<>();

    private final BlockPos pos;
    private final Direction facing;
    private final int sampleRate;

    private float volume;
    private float occlusionGain = 1.0f;
    private boolean finished = false;
    private boolean destroyed = false;

    public StereoSource(BlockPos pos, Direction facing, Path audioFile, long seekMs, float volume) throws IOException {
        this.pos = pos;
        this.facing = facing;
        this.volume = volume;

        this.streamL = new PhononAudioStream(audioFile, ChannelMode.LEFT);
        this.streamR = new PhononAudioStream(audioFile, ChannelMode.RIGHT);
        this.sampleRate = streamL.getSampleRate();

        if (seekMs > 0) {
            streamL.seekMs(seekMs);
            streamR.seekMs(seekMs);
        }

        this.sourceL = AL10.alGenSources();
        this.sourceR = AL10.alGenSources();

        setupSource(sourceL, getLeftPosition());
        setupSource(sourceR, getRightPosition());

        for (int i = 0; i < BUFFER_COUNT; i++) {
            freeBuffersL.add(AL10.alGenBuffers());
            freeBuffersR.add(AL10.alGenBuffers());
        }

        fillBuffers();

        AL10.alSourcePlay(sourceL);
        AL10.alSourcePlay(sourceR);
    }

    private void setupSource(int source, Vec3 position) {
        AL10.alSourcef(source, AL10.AL_GAIN, volume * occlusionGain);
        AL10.alSource3f(source, AL10.AL_POSITION, (float) position.x, (float) position.y, (float) position.z);
        AL10.alSourcef(source, AL10.AL_PITCH, 1.0f);

        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 4.0f);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, 64.0f);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0f);
        AL10.alDistanceModel(AL10.AL_INVERSE_DISTANCE_CLAMPED);
    }

    private Vec3 getLeftPosition() {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 offset = getPerpendicularOffset(-STEREO_OFFSET);
        return center.add(offset);
    }

    private Vec3 getRightPosition() {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 offset = getPerpendicularOffset(STEREO_OFFSET);
        return center.add(offset);
    }

    private Vec3 getPerpendicularOffset(float distance) {
        return switch (facing) {
            case NORTH -> new Vec3(distance, 0, 0);
            case SOUTH -> new Vec3(-distance, 0, 0);
            case EAST -> new Vec3(0, 0, distance);
            case WEST -> new Vec3(0, 0, -distance);
            default -> Vec3.ZERO;
        };
    }

    public void update(Vec3 listenerPos) {
        if (destroyed || finished) return;

        processFinishedBuffers(sourceL, streamL, freeBuffersL);
        processFinishedBuffers(sourceR, streamR, freeBuffersR);

        fillBuffers();

        int stateL = AL10.alGetSourcei(sourceL, AL10.AL_SOURCE_STATE);
        int stateR = AL10.alGetSourcei(sourceR, AL10.AL_SOURCE_STATE);

        if (stateL == AL10.AL_STOPPED && stateR == AL10.AL_STOPPED) {
            int queuedL = AL10.alGetSourcei(sourceL, AL10.AL_BUFFERS_QUEUED);
            int queuedR = AL10.alGetSourcei(sourceR, AL10.AL_BUFFERS_QUEUED);
            if (queuedL == 0 && queuedR == 0) {
                finished = true;
            }
        }
    }

    private void processFinishedBuffers(int source, PhononAudioStream stream, Queue<Integer> freeBuffers) {
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int buffer = AL10.alSourceUnqueueBuffers(source);
            freeBuffers.add(buffer);
        }
    }

    private void fillBuffers() {
        while (!freeBuffersL.isEmpty() && !freeBuffersR.isEmpty()) {
            try {
                ByteBuffer dataL = streamL.read(BUFFER_SIZE);
                ByteBuffer dataR = streamR.read(BUFFER_SIZE);

                if (dataL.remaining() == 0 && dataR.remaining() == 0) {
                    MemoryUtil.memFree(dataL);
                    MemoryUtil.memFree(dataR);
                    break;
                }

                if (dataL.remaining() > 0) {
                    int bufferL = freeBuffersL.poll();
                    AL10.alBufferData(bufferL, AL10.AL_FORMAT_MONO16, dataL, sampleRate);
                    AL10.alSourceQueueBuffers(sourceL, bufferL);
                }
                MemoryUtil.memFree(dataL);

                if (dataR.remaining() > 0) {
                    int bufferR = freeBuffersR.poll();
                    AL10.alBufferData(bufferR, AL10.AL_FORMAT_MONO16, dataR, sampleRate);
                    AL10.alSourceQueueBuffers(sourceR, bufferR);
                }
                MemoryUtil.memFree(dataR);

            } catch (IOException e) {
                Phonon.LOGGER.error("Error reading audio stream", e);
                finished = true;
                break;
            }
        }
    }

    public void setVolume(float volume) {
        this.volume = volume;
        applyGain();
    }

    public void setOcclusion(float gain) {
        this.occlusionGain = gain;
        applyGain();
    }

    private void applyGain() {
        float gain = volume * occlusionGain;
        AL10.alSourcef(sourceL, AL10.AL_GAIN, gain);
        AL10.alSourcef(sourceR, AL10.AL_GAIN, gain);
    }

    public boolean isFinished() {
        return finished;
    }

    public void destroy() {
        if (destroyed) return;
        destroyed = true;

        AL10.alSourceStop(sourceL);
        AL10.alSourceStop(sourceR);

        int processed = AL10.alGetSourcei(sourceL, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            AL10.alSourceUnqueueBuffers(sourceL);
        }
        processed = AL10.alGetSourcei(sourceR, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            AL10.alSourceUnqueueBuffers(sourceR);
        }

        for (int buffer : freeBuffersL) {
            AL10.alDeleteBuffers(buffer);
        }
        for (int buffer : freeBuffersR) {
            AL10.alDeleteBuffers(buffer);
        }
        freeBuffersL.clear();
        freeBuffersR.clear();

        AL10.alDeleteSources(sourceL);
        AL10.alDeleteSources(sourceR);

        try {
            streamL.close();
            streamR.close();
        } catch (Exception e) {
            Phonon.LOGGER.warn("Error closing audio streams", e);
        }
    }
}
