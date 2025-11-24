package com.metrovoc.phonon.client;

import com.metrovoc.phonon.audio.PlaybackState;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side speaker manager.
 * Tracks all active speakers and their playback states.
 */
public class ClientSpeakerManager {
    private static ClientSpeakerManager instance;
    private final Map<BlockPos, PlaybackState> speakers = new ConcurrentHashMap<>();

    private ClientSpeakerManager() {}

    public static ClientSpeakerManager getInstance() {
        if (instance == null) {
            instance = new ClientSpeakerManager();
        }
        return instance;
    }

    public void updateSpeaker(BlockPos pos, PlaybackState playback) {
        if (playback.playing() && playback.resourceId() != null) {
            speakers.put(pos, playback);

            UUID resourceId = playback.resourceId();

            // Check if audio is already cached
            if (AudioCache.getInstance().getCachedAudio(resourceId).isPresent()) {
                // Already cached - play immediately
                com.metrovoc.phonon.client.audio.OpenALAudioPlayer.getInstance()
                    .play(pos, playback, resourceId);
            } else {
                // Not cached - download first, then play
                ClientAudioManager.getInstance().getResource(resourceId).ifPresent(resource -> {
                    AudioCache.getInstance().downloadAudio(resource.id(), resource.url(),
                        new AudioCache.DownloadCallback() {
                            @Override
                            public void onComplete(UUID id, java.nio.file.Path file) {
                                // Download complete - now play
                                com.metrovoc.phonon.client.audio.OpenALAudioPlayer.getInstance()
                                    .play(pos, playback, resourceId);
                            }

                            @Override
                            public void onError(UUID id, Exception e) {
                                // Download failed - log error
                                com.metrovoc.phonon.Phonon.LOGGER.error(
                                    "Failed to download audio for speaker at {}", pos, e
                                );
                            }
                        }
                    );
                });
            }
        } else {
            speakers.remove(pos);
            com.metrovoc.phonon.client.audio.OpenALAudioPlayer.getInstance().stop(pos);
        }
    }

    public Optional<PlaybackState> getSpeakerState(BlockPos pos) {
        return Optional.ofNullable(speakers.get(pos));
    }

    public void removeSpeaker(BlockPos pos) {
        speakers.remove(pos);
        com.metrovoc.phonon.client.audio.OpenALAudioPlayer.getInstance().stop(pos);
    }
}
