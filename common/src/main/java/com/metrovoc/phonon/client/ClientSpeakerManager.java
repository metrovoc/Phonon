package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.audio.OpusAudioStream;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side speaker manager.
 * Tracks all active speakers and their playback state.
 */
public class ClientSpeakerManager {
    private static ClientSpeakerManager instance;

    private final Map<BlockPos, PlaybackState> speakers = new ConcurrentHashMap<>();
    private final Map<BlockPos, Float> speakerVolumes = new ConcurrentHashMap<>();
    private final Map<BlockPos, UUID> activeStreams = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRequests = ConcurrentHashMap.newKeySet();

    private ClientSpeakerManager() {}

    public static ClientSpeakerManager getInstance() {
        if (instance == null) {
            instance = new ClientSpeakerManager();
        }
        return instance;
    }

    public void updateSpeaker(BlockPos pos, PlaybackState playback, float volume) {
        speakerVolumes.put(pos, volume);
        PlaybackState oldState = speakers.get(pos);

        // Stop old playback if resource changed
        if (oldState != null && !java.util.Objects.equals(oldState.resourceId(), playback.resourceId())) {
            stopAndCleanup(pos, oldState);
        }

        if (playback.isPlaying()) {
            speakers.put(pos, playback);
            startPlayback(pos, playback, volume);
        } else if (playback.isPaused()) {
            speakers.put(pos, playback);
            stopAndCleanup(pos, oldState);
        } else {
            speakers.remove(pos);
            stopAndCleanup(pos, oldState);
        }
    }

    private void stopAndCleanup(BlockPos pos, PlaybackState state) {
        PlatformHelper.INSTANCE.stopAudio(pos);

        UUID streamResourceId = activeStreams.remove(pos);
        if (streamResourceId != null) {
            StreamingAudioManager.getInstance().releaseOpusDownload(streamResourceId);
        }
    }

    private void startPlayback(BlockPos pos, PlaybackState playback, float volume) {
        UUID resourceId = playback.resourceId();
        if (resourceId == null) return;

        // Priority 1: Use cache (fully downloaded file)
        if (AudioCache.getInstance().isCached(resourceId)) {
            PlatformHelper.INSTANCE.playAudio(pos, playback, resourceId, volume);
            return;
        }

        long currentPositionMs = playback.getCurrentPositionMs(System.currentTimeMillis());

        // Priority 2: Check if Opus download already in progress and ready
        if (StreamingAudioManager.getInstance().isOpusReady(resourceId)) {
            startOpusPlayback(pos, resourceId, volume);
            return;
        }

        // Priority 3: Wait for in-progress transfer
        if (AudioReceiver.getInstance().isTransferInProgress(resourceId)) {
            AudioReceiver.getInstance().onTransferComplete(resourceId, file -> {
                PlaybackState current = speakers.get(pos);
                float currentVolume = speakerVolumes.getOrDefault(pos, 0.5f);
                if (current != null && current.isPlaying() && resourceId.equals(current.resourceId())) {
                    PlatformHelper.INSTANCE.playAudio(pos, current, resourceId, currentVolume);
                }
            });
            return;
        }

        // Priority 4: Start new streaming download
        if (pendingRequests.contains(resourceId)) {
            // Already requested, just add callback
            addOpusReadyCallback(pos, resourceId);
            return;
        }

        pendingRequests.add(resourceId);
        activeStreams.put(pos, resourceId);

        // Request from server (server will send Opus packets, session created on first packet)
        PlatformHelper.INSTANCE.requestAudioFromServer(resourceId, currentPositionMs);
        Phonon.LOGGER.info("Requesting streaming audio {} from position {}ms", resourceId, currentPositionMs);

        // Add callback for when Opus stream is ready
        addOpusReadyCallback(pos, resourceId);
    }

    private void addOpusReadyCallback(BlockPos pos, UUID resourceId) {
        StreamingAudioManager.getInstance().addOpusReadyCallback(resourceId, session -> {
            pendingRequests.remove(resourceId);

            PlaybackState current = speakers.get(pos);
            if (current == null || !current.isPlaying() || !resourceId.equals(current.resourceId())) {
                return;
            }

            float volume = speakerVolumes.getOrDefault(pos, 0.5f);
            startOpusPlayback(pos, resourceId, volume);
        });
    }

    private void startOpusPlayback(BlockPos pos, UUID resourceId, float volume) {
        OpusAudioStream stream = StreamingAudioManager.getInstance().createOpusStream(resourceId);
        if (stream == null) {
            Phonon.LOGGER.error("Failed to create Opus stream for {}", resourceId);
            return;
        }

        // Track this stream for cleanup
        UUID oldResourceId = activeStreams.put(pos, resourceId);
        if (oldResourceId != null && !oldResourceId.equals(resourceId)) {
            StreamingAudioManager.getInstance().releaseOpusDownload(oldResourceId);
        }

        PlatformHelper.INSTANCE.playOpusStreamingAudio(pos, stream, volume);
    }

    public void updateVolume(BlockPos pos, float volume) {
        speakerVolumes.put(pos, volume);
        PlatformHelper.INSTANCE.setAudioVolume(pos, volume);
    }

    public Optional<PlaybackState> getSpeakerState(BlockPos pos) {
        return Optional.ofNullable(speakers.get(pos));
    }

    public float getSpeakerVolume(BlockPos pos) {
        return speakerVolumes.getOrDefault(pos, 0.5f);
    }

    public void removeSpeaker(BlockPos pos) {
        PlaybackState oldState = speakers.remove(pos);
        speakerVolumes.remove(pos);
        stopAndCleanup(pos, oldState);
    }

    public void clearPendingRequests() {
        pendingRequests.clear();
    }

    /**
     * Called after F3+T resource reload to restore playing audio.
     */
    public void onResourcesReloaded() {
        for (Map.Entry<BlockPos, PlaybackState> entry : speakers.entrySet()) {
            BlockPos pos = entry.getKey();
            PlaybackState state = entry.getValue();

            if (state.isPlaying()) {
                UUID resourceId = state.resourceId();
                float volume = speakerVolumes.getOrDefault(pos, 0.5f);

                if (AudioCache.getInstance().isCached(resourceId)) {
                    PlatformHelper.INSTANCE.playAudio(pos, state, resourceId, volume);
                    Phonon.LOGGER.debug("Restored playback at {} after resource reload", pos);
                }
            }
        }
    }
}
