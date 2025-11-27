package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Phonon;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Direct OpenAL audio engine, bypassing Minecraft's SoundManager.
 *
 * All public methods are thread-safe (dispatched to render thread).
 * OpenAL calls happen only on the render thread which owns the context.
 */
public class PhononAudioEngine {
    private static PhononAudioEngine instance;

    private final Map<BlockPos, StereoSource> sources = new HashMap<>();
    private boolean initialized = false;

    private PhononAudioEngine() {}

    public static PhononAudioEngine getInstance() {
        if (instance == null) {
            instance = new PhononAudioEngine();
        }
        return instance;
    }

    public void init() {
        if (initialized) return;

        long context = ALC10.alcGetCurrentContext();
        if (context == 0L) {
            Phonon.LOGGER.error("No OpenAL context available!");
            return;
        }

        initialized = true;
        Phonon.LOGGER.info("PhononAudioEngine initialized");
    }

    public void shutdown() {
        if (!initialized) return;

        for (StereoSource source : sources.values()) {
            source.destroy();
        }
        sources.clear();
        initialized = false;

        Phonon.LOGGER.info("PhononAudioEngine shut down");
    }

    public void tick() {
        if (!initialized) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 listenerPos = mc.player.getEyePosition();
        updateListener(listenerPos, mc.player.getYRot(), mc.player.getXRot());

        Iterator<Map.Entry<BlockPos, StereoSource>> it = sources.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, StereoSource> entry = it.next();
            StereoSource source = entry.getValue();

            if (source.isFinished()) {
                source.destroy();
                it.remove();
            } else {
                source.update(listenerPos);
            }
        }
    }

    private void updateListener(Vec3 pos, float yaw, float pitch) {
        AL10.alListener3f(AL10.AL_POSITION, (float) pos.x, (float) pos.y, (float) pos.z);

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        float atX = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
        float atY = (float) (-Math.sin(pitchRad));
        float atZ = (float) (Math.cos(yawRad) * Math.cos(pitchRad));

        float upX = 0f, upY = 1f, upZ = 0f;

        AL10.alListenerfv(AL10.AL_ORIENTATION, new float[]{atX, atY, atZ, upX, upY, upZ});
    }

    public void play(BlockPos pos, Direction facing, Path audioFile, long seekMs, float volume) {
        Minecraft.getInstance().execute(() -> {
            if (!initialized) {
                init();
            }

            stop(pos);

            try {
                StereoSource source = new StereoSource(pos, facing, audioFile, seekMs, volume);
                sources.put(pos, source);
                Phonon.LOGGER.info("Started playback at {} (seek {}ms)", pos, seekMs);
            } catch (Exception e) {
                Phonon.LOGGER.error("Failed to create StereoSource at {}", pos, e);
            }
        });
    }

    public void stop(BlockPos pos) {
        StereoSource source = sources.remove(pos);
        if (source != null) {
            source.destroy();
            Phonon.LOGGER.debug("Stopped playback at {}", pos);
        }
    }

    public void setVolume(BlockPos pos, float volume) {
        StereoSource source = sources.get(pos);
        if (source != null) {
            source.setVolume(volume);
        }
    }

    public boolean isPlaying(BlockPos pos) {
        return sources.containsKey(pos);
    }

    public void stopAll() {
        for (StereoSource source : sources.values()) {
            source.destroy();
        }
        sources.clear();
        Phonon.LOGGER.info("Stopped all playback");
    }
}
