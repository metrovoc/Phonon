package com.tovkaic.phonon.audio;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side audio resource manager.
 * Single source of truth for all audio resources.
 */
public class AudioManager {
    private static final AudioManager instance = new AudioManager();
    private final Map<UUID, AudioResource> resources = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();

    private AudioManager() {}

    public static AudioManager getInstance() {
        return instance;
    }

    public void addResource(AudioResource resource) {
        resources.put(resource.id(), resource);
        nameIndex.put(resource.name().toLowerCase(), resource.id());
    }

    public void removeResource(UUID id) {
        AudioResource resource = resources.remove(id);
        if (resource != null) {
            nameIndex.remove(resource.name().toLowerCase());
        }
    }

    public void loadResources(List<AudioResource> resourceList) {
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

    public Optional<AudioResource> getResourceByName(String name) {
        UUID id = nameIndex.get(name.toLowerCase());
        return id != null ? getResource(id) : Optional.empty();
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
