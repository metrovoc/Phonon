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

        if (playback.isPlaying()) {
            speakers.put(pos, playback);
            startPlayback(pos, playback, volume);
        } else if (playback.isPaused()) {
            speakers.put(pos, playback);
            // 暂停: 停止音频但保留状态
            PlatformHelper.INSTANCE.stopAudio(pos);
        } else {
            // 停止
            speakers.remove(pos);
            PlatformHelper.INSTANCE.stopAudio(pos);
        }
    }

    private void startPlayback(BlockPos pos, PlaybackState playback, float volume) {
        UUID resourceId = playback.resourceId();
        if (resourceId == null) return;

        if (AudioCache.getInstance().isCached(resourceId)) {
            PlatformHelper.INSTANCE.playAudio(pos, playback, resourceId, volume);
        } else if (AudioReceiver.getInstance().isTransferInProgress(resourceId)) {
            AudioReceiver.getInstance().onTransferComplete(resourceId, file -> {
                PlaybackState current = speakers.get(pos);
                float currentVolume = speakerVolumes.getOrDefault(pos, 0.5f);
                if (current != null && current.isPlaying() && resourceId.equals(current.resourceId())) {
                    PlatformHelper.INSTANCE.playAudio(pos, current, resourceId, currentVolume);
                }
            });
        } else if (!pendingRequests.contains(resourceId)) {
            pendingRequests.add(resourceId);
            PlatformHelper.INSTANCE.requestAudioFromServer(resourceId);

            Phonon.LOGGER.info("Requesting audio {} from server", resourceId);

            AudioReceiver.getInstance().onTransferComplete(resourceId, file -> {
                pendingRequests.remove(resourceId);
                PlaybackState current = speakers.get(pos);
                float currentVolume = speakerVolumes.getOrDefault(pos, 0.5f);
                if (current != null && current.isPlaying() && resourceId.equals(current.resourceId())) {
                    PlatformHelper.INSTANCE.playAudio(pos, current, resourceId, currentVolume);
                }
            });
        }
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
        speakers.remove(pos);
        PlatformHelper.INSTANCE.stopAudio(pos);
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
