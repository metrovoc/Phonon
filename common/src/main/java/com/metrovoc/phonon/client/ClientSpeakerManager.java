package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 客户端 Speaker 管理器。
 * 追踪所有活跃 speaker 及其播放状态。
 */
public class ClientSpeakerManager {
    private static ClientSpeakerManager instance;
    private final Map<BlockPos, PlaybackState> speakers = new ConcurrentHashMap<>();
    private final Map<BlockPos, Float> speakerVolumes = new ConcurrentHashMap<>();
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
            PlatformHelper.INSTANCE.stopAudio(pos);
            endStreamingSessionIfNeeded(oldState);
        }

        if (playback.isPlaying()) {
            speakers.put(pos, playback);
            startPlayback(pos, playback, volume);
        } else if (playback.isPaused()) {
            speakers.put(pos, playback);
            PlatformHelper.INSTANCE.stopAudio(pos);
        } else {
            speakers.remove(pos);
            PlatformHelper.INSTANCE.stopAudio(pos);
            endStreamingSessionIfNeeded(oldState);
        }
    }

    private void endStreamingSessionIfNeeded(PlaybackState state) {
        if (state != null && state.resourceId() != null) {
            StreamingAudioManager.getInstance().endSession(state.resourceId());
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

        // Priority 2: Use active streaming session
        StreamingAudioSession session = StreamingAudioManager.getInstance().getSession(resourceId);
        if (session != null && session.isReady()) {
            PlatformHelper.INSTANCE.playStreamingAudio(pos, session, volume);
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

        // Priority 4: Request new streaming transfer
        if (pendingRequests.contains(resourceId)) {
            return;
        }

        pendingRequests.add(resourceId);
        long currentPositionMs = playback.getCurrentPositionMs(System.currentTimeMillis());
        PlatformHelper.INSTANCE.requestAudioFromServer(resourceId, currentPositionMs);

        Phonon.LOGGER.info("Requesting streaming audio {} from position {}ms", resourceId, currentPositionMs);

        StreamingAudioManager.getInstance().setReadyCallback(resourceId, createStreamingCallback(pos, resourceId));
    }

    private BiConsumer<UUID, StreamingAudioSession> createStreamingCallback(BlockPos pos, UUID resourceId) {
        return (id, session) -> {
            if (!id.equals(resourceId)) {
                return;
            }
            pendingRequests.remove(resourceId);
            PlaybackState current = speakers.get(pos);
            float currentVolume = speakerVolumes.getOrDefault(pos, 0.5f);
            if (current != null && current.isPlaying() && resourceId.equals(current.resourceId())) {
                PlatformHelper.INSTANCE.playStreamingAudio(pos, session, currentVolume);
            }
        };
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
        PlatformHelper.INSTANCE.stopAudio(pos);
        endStreamingSessionIfNeeded(oldState);
    }

    public void clearPendingRequests() {
        pendingRequests.clear();
    }

    /**
     * F3+T 资源重载后调用，恢复正在播放的音频。
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
