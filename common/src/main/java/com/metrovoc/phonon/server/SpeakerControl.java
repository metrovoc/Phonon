package com.metrovoc.phonon.server;

import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/** Everything that changes what a speaker plays, whether a player or redstone asked. */
public final class SpeakerControl {
    private SpeakerControl() {}

    /** Resumes a paused track, or starts a track from the beginning. */
    public static void play(Level level, BlockPos pos, SpeakerBlockEntity speaker, UUID resourceId) {
        if (resourceId == null || AudioManager.getInstance().getResource(resourceId).isEmpty()) {
            return;
        }
        PlaybackState current = speaker.getPlayback();
        long now = PlaybackState.nowMs();
        long from = current.isPaused() && resourceId.equals(current.resourceId()) ? current.positionAtAnchorMs() : 0;
        apply(level, pos, speaker, new PlaybackState(resourceId, now, from, 1.0f));
    }

    public static void pause(Level level, BlockPos pos, SpeakerBlockEntity speaker) {
        PlaybackState current = speaker.getPlayback();
        if (current.isPlaying()) {
            long now = PlaybackState.nowMs();
            apply(level, pos, speaker, new PlaybackState(current.resourceId(), now, current.getCurrentPositionMs(now), 0f));
        }
    }

    public static void stop(Level level, BlockPos pos, SpeakerBlockEntity speaker) {
        apply(level, pos, speaker, PlaybackState.STOPPED);
    }

    public static void seek(Level level, BlockPos pos, SpeakerBlockEntity speaker, long positionMs) {
        PlaybackState current = speaker.getPlayback();
        if (current.isStopped()) {
            return;
        }
        long durationMs = ServerSpeakerManager.getDurationMs(current.resourceId());
        long position = Math.max(0, positionMs);
        if (durationMs > 0) {
            position = Math.min(position, Math.max(0, durationMs - 1));
        }
        apply(level, pos, speaker, new PlaybackState(current.resourceId(), PlaybackState.nowMs(), position, current.speed()));
    }

    /** Pauses when playing; otherwise resumes, or starts the last track, or the first in the library. */
    public static void toggle(Level level, BlockPos pos, SpeakerBlockEntity speaker) {
        PlaybackState current = speaker.getPlayback();
        if (current.isPlaying()) {
            pause(level, pos, speaker);
        } else if (current.isPaused()) {
            play(level, pos, speaker, current.resourceId());
        } else {
            UUID last = speaker.getLastTrack();
            boolean known = last != null && AudioManager.getInstance().getResource(last).isPresent();
            List<AudioResource> tracks = AudioManager.getInstance().getAllResources();
            if (known) {
                play(level, pos, speaker, last);
            } else if (!tracks.isEmpty()) {
                play(level, pos, speaker, tracks.get(0).id());
            }
        }
    }

    /** Moves through the library in list order, wrapping at the ends. */
    public static void step(Level level, BlockPos pos, SpeakerBlockEntity speaker, int direction) {
        List<AudioResource> tracks = AudioManager.getInstance().getAllResources();
        if (tracks.isEmpty()) {
            return;
        }
        UUID from = speaker.getPlayback().isStopped() ? speaker.getLastTrack() : speaker.getPlayback().resourceId();
        int index = -1;
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).id().equals(from)) {
                index = i;
            }
        }
        int next = index < 0 ? 0 : Math.floorMod(index + direction, tracks.size());
        speaker.setPlayback(PlaybackState.STOPPED);
        play(level, pos, speaker, tracks.get(next).id());
    }

    private static void apply(Level level, BlockPos pos, SpeakerBlockEntity speaker, PlaybackState state) {
        speaker.setPlayback(state);
        ServerSpeakerManager.getInstance().registerSpeaker(
            level.dimension(), pos, state, state.isStopped() ? -1 : ServerSpeakerManager.getDurationMs(state.resourceId()));
        PlatformHelper.INSTANCE.broadcastSpeakerState(level, pos, speaker);
    }
}
