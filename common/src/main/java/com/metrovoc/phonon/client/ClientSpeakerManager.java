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
 * Client-side speaker manager.
 * Tracks all active speakers and their playback states.
 * Requests audio from server via packet transfer.
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

        if (playback.playing() && playback.resourceId() != null) {
            speakers.put(pos, playback);

            UUID resourceId = playback.resourceId();

            if (AudioCache.getInstance().isCached(resourceId)) {
                PlatformHelper.INSTANCE.playAudio(pos, playback, resourceId, volume);
            } else if (AudioReceiver.getInstance().isTransferInProgress(resourceId)) {
                AudioReceiver.getInstance().onTransferComplete(resourceId, file -> {
                    PlaybackState current = speakers.get(pos);
                    float currentVolume = speakerVolumes.getOrDefault(pos, 0.5f);
                    if (current != null && current.playing() && resourceId.equals(current.resourceId())) {
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
                    if (current != null && current.playing() && resourceId.equals(current.resourceId())) {
                        PlatformHelper.INSTANCE.playAudio(pos, current, resourceId, currentVolume);
                    }
                });
            }
        } else {
            speakers.remove(pos);
            PlatformHelper.INSTANCE.stopAudio(pos);
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
}
