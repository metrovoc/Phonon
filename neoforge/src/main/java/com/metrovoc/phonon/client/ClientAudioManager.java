package com.metrovoc.phonon.client;

import com.metrovoc.phonon.audio.AudioResource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side audio resource manager.
 * Mirrors server's AudioManager, but read-only.
 */
public class ClientAudioManager {
    private static ClientAudioManager instance;
    private final Map<UUID, AudioResource> resources = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();

    private ClientAudioManager() {}

    public static ClientAudioManager getInstance() {
        if (instance == null) {
            instance = new ClientAudioManager();
        }
        return instance;
    }

    public void setResources(List<AudioResource> resourceList) {
        resources.clear();
        nameIndex.clear();
        for (AudioResource resource : resourceList) {
            resources.put(resource.id(), resource);
            nameIndex.put(resource.name().toLowerCase(), resource.id());
        }
    }

    public Optional<AudioResource> getResource(UUID id) {
        return Optional.ofNullable(resources.get(id));
    }

    public List<AudioResource> getAllResources() {
        return new ArrayList<>(resources.values());
    }

    public List<AudioResource> searchResources(String query) {
        String lowerQuery = query.toLowerCase();
        return resources.values().stream()
            .filter(r -> r.name().toLowerCase().contains(lowerQuery))
            .toList();
    }
}
