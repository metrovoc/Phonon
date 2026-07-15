package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.audio.StreamingAudioStream;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Client-thread owner of speaker playback and stream leases.
 */
public final class ClientSpeakerManager {
    private static final ClientSpeakerManager INSTANCE = new ClientSpeakerManager();

    private final Map<BlockPos, PlaybackState> speakers = new HashMap<>();
    private final Map<BlockPos, Float> speakerVolumes = new HashMap<>();
    private final Map<BlockPos, Long> activeStreams = new HashMap<>();

    private ClientSpeakerManager() {}

    public static ClientSpeakerManager getInstance() {
        return INSTANCE;
    }

    public void updateSpeaker(BlockPos pos, PlaybackState playback, float volume) {
        volume = sanitizeVolume(volume);
        speakerVolumes.put(pos, volume);
        PlaybackState previous = speakers.get(pos);

        if (Objects.equals(previous, playback)) {
            PlatformHelper.INSTANCE.setAudioVolume(pos, volume);
            return;
        }

        stopAndRelease(pos);

        if (playback.isPlaying()) {
            speakers.put(pos, playback);
            startPlayback(pos, playback, volume);
        } else if (playback.isPaused()) {
            speakers.put(pos, playback);
        } else {
            speakers.remove(pos);
        }
    }

    private void stopAndRelease(BlockPos pos) {
        PlatformHelper.INSTANCE.stopAudio(pos);
        Long streamId = activeStreams.remove(pos);
        if (streamId != null) {
            StreamingAudioManager.getInstance().release(streamId);
        }
    }

    private void startPlayback(BlockPos pos, PlaybackState playback, float volume) {
        UUID resourceId = playback.resourceId();
        if (resourceId == null) {
            return;
        }

        if (AudioCache.getInstance().isCached(resourceId)) {
            PlatformHelper.INSTANCE.playAudio(pos, playback, resourceId, volume);
            return;
        }

        long positionMs = playback.getCurrentPositionMs();
        StreamingAudioManager.Acquisition acquisition = StreamingAudioManager.getInstance()
            .acquire(resourceId, positionMs);
        long streamId = acquisition.session().getStreamId();
        activeStreams.put(pos, streamId);

        StreamingAudioManager.getInstance().addReadyCallback(streamId, session -> {
            PlaybackState current = speakers.get(pos);
            Long activeStream = activeStreams.get(pos);
            if (current == null || !current.isPlaying() || !resourceId.equals(current.resourceId())
                || !Objects.equals(activeStream, streamId)) {
                return;
            }

            float currentVolume = speakerVolumes.getOrDefault(pos, 0.5f);
            long currentPosition = current.getCurrentPositionMs();
            startStreamingPlayback(pos, streamId, currentPosition, currentVolume);
        });

        if (acquisition.requestRequired()) {
            PlatformHelper.INSTANCE.requestAudioFromServer(streamId, resourceId, positionMs);
            Phonon.LOGGER.info("Requested audio stream {} for {} at {}ms", streamId, resourceId, positionMs);
        }
    }

    private void startStreamingPlayback(BlockPos pos, long streamId, long positionMs, float volume) {
        StreamingAudioStream stream = StreamingAudioManager.getInstance().createStream(streamId, positionMs);
        if (stream == null) {
            Phonon.LOGGER.error("Failed to create decoder for audio stream {}", streamId);
            return;
        }
        PlatformHelper.INSTANCE.playStreamingAudio(pos, stream, volume);
    }

    public void updateVolume(BlockPos pos, float volume) {
        volume = sanitizeVolume(volume);
        speakerVolumes.put(pos, volume);
        PlatformHelper.INSTANCE.setAudioVolume(pos, volume);
    }

    private static float sanitizeVolume(float volume) {
        return Float.isFinite(volume)
            ? Math.max(0.0f, Math.min(1.0f, volume))
            : 0.5f;
    }

    public Optional<PlaybackState> getSpeakerState(BlockPos pos) {
        return Optional.ofNullable(speakers.get(pos));
    }

    public float getSpeakerVolume(BlockPos pos) {
        return speakerVolumes.getOrDefault(pos, 0.5f);
    }

    public void removeSpeaker(BlockPos pos) {
        speakers.remove(pos);
        speakerVolumes.remove(pos);
        stopAndRelease(pos);
    }

    public void clear() {
        for (BlockPos pos : List.copyOf(speakers.keySet())) {
            stopAndRelease(pos);
        }
        PlatformHelper.INSTANCE.stopAllAudio();
        speakers.clear();
        speakerVolumes.clear();
        StreamingAudioManager.getInstance().clear();
    }

    /** Restores both cached and in-flight playback after F3+T. */
    public void onResourcesReloaded() {
        for (Map.Entry<BlockPos, PlaybackState> entry : Map.copyOf(speakers).entrySet()) {
            if (!entry.getValue().isPlaying()) {
                continue;
            }
            BlockPos pos = entry.getKey();
            float volume = speakerVolumes.getOrDefault(pos, 0.5f);
            stopAndRelease(pos);
            startPlayback(pos, entry.getValue(), volume);
        }
    }
}
