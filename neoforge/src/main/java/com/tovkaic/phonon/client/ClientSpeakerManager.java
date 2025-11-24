package com.tovkaic.phonon.client;

import com.tovkaic.phonon.audio.PlaybackState;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Optional;
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
        if (playback.playing()) {
            speakers.put(pos, playback);
            com.tovkaic.phonon.client.audio.AudioPlayer.getInstance()
                .play(pos, playback, playback.resourceId());
        } else {
            speakers.remove(pos);
            com.tovkaic.phonon.client.audio.AudioPlayer.getInstance().stop(pos);
        }
    }

    public Optional<PlaybackState> getSpeakerState(BlockPos pos) {
        return Optional.ofNullable(speakers.get(pos));
    }

    public void removeSpeaker(BlockPos pos) {
        speakers.remove(pos);
        com.tovkaic.phonon.client.audio.AudioPlayer.getInstance().stop(pos);
    }
}
