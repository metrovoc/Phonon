package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.audio.AudioPlayer;
import com.metrovoc.phonon.network.packets.RequestAudioPacket;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

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

    // Track pending audio requests to avoid duplicates
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

            // Check if audio is already cached
            if (AudioCache.getInstance().isCached(resourceId)) {
                // Already cached - play immediately
                AudioPlayer.getInstance().play(pos, playback, resourceId, volume);
            } else if (AudioReceiver.getInstance().isTransferInProgress(resourceId)) {
                // Transfer in progress - register callback
                AudioReceiver.getInstance().onTransferComplete(resourceId, file -> {
                    // Check if speaker is still playing this resource
                    PlaybackState current = speakers.get(pos);
                    float currentVolume = speakerVolumes.getOrDefault(pos, 0.5f);
                    if (current != null && current.playing() && resourceId.equals(current.resourceId())) {
                        AudioPlayer.getInstance().play(pos, current, resourceId, currentVolume);
                    }
                });
            } else if (!pendingRequests.contains(resourceId)) {
                // Request audio from server
                pendingRequests.add(resourceId);
                PacketDistributor.sendToServer(new RequestAudioPacket(resourceId));

                Phonon.LOGGER.info("Requesting audio {} from server", resourceId);

                // Register callback for when transfer completes
                AudioReceiver.getInstance().onTransferComplete(resourceId, file -> {
                    pendingRequests.remove(resourceId);
                    // Check if speaker is still playing this resource
                    PlaybackState current = speakers.get(pos);
                    float currentVolume = speakerVolumes.getOrDefault(pos, 0.5f);
                    if (current != null && current.playing() && resourceId.equals(current.resourceId())) {
                        AudioPlayer.getInstance().play(pos, current, resourceId, currentVolume);
                    }
                });
            }
        } else {
            speakers.remove(pos);
            AudioPlayer.getInstance().stop(pos);
        }
    }

    public void updateVolume(BlockPos pos, float volume) {
        speakerVolumes.put(pos, volume);
        AudioPlayer.getInstance().setVolume(pos, volume);
    }

    public Optional<PlaybackState> getSpeakerState(BlockPos pos) {
        return Optional.ofNullable(speakers.get(pos));
    }

    public float getSpeakerVolume(BlockPos pos) {
        return speakerVolumes.getOrDefault(pos, 0.5f);
    }

    public void removeSpeaker(BlockPos pos) {
        speakers.remove(pos);
        AudioPlayer.getInstance().stop(pos);
    }

    /**
     * Clear all pending requests (e.g., on disconnect).
     */
    public void clearPendingRequests() {
        pendingRequests.clear();
    }
}
