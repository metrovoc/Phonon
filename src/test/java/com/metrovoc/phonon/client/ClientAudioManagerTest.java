package com.metrovoc.phonon.client;

import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.audio.PlaybackState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientAudioManagerTest {
    private final ClientAudioManager manager = ClientAudioManager.getInstance();

    @AfterEach
    void clear() {
        manager.setResources(List.of());
    }

    @Test
    void stoppedBoomboxHasNoMetadataInEmptyOrPopulatedLibrary() {
        manager.setResources(List.of());
        assertTrue(manager.getResource(PlaybackState.STOPPED.resourceId()).isEmpty());
        AudioResource track = new AudioResource("Track", "https://example.com/track.ogg");
        manager.setResources(List.of(track));
        assertTrue(manager.getResource(PlaybackState.STOPPED.resourceId()).isEmpty());
        assertEquals(track, manager.getResource(track.id()).orElseThrow());
    }
}
