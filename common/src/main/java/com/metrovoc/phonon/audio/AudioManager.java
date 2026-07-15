package com.metrovoc.phonon.audio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side audio resource manager.
 * Single source of truth for all audio resources.
 */
public class AudioManager {
    private static final AudioManager instance = new AudioManager();
    private static final Comparator<AudioResource> RESOURCE_ORDER =
        Comparator.comparing(AudioResource::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(AudioResource::id);

    private final Map<UUID, AudioResource> resources = new HashMap<>();
    private final Map<String, UUID> nameIndex = new HashMap<>();
    private volatile List<AudioResource> sortedResources = List.of();

    private AudioManager() {}

    public static AudioManager getInstance() {
        return instance;
    }

    public synchronized boolean addResource(AudioResource resource) {
        boolean added = addResourceToIndexes(resource);
        if (added) {
            rebuildSnapshot();
        }
        return added;
    }

    private boolean addResourceToIndexes(AudioResource resource) {
        if (resource == null || resource.id() == null || resource.name() == null || resource.url() == null
            || resource.name().isBlank()
            || resource.name().length() > AudioLimits.MAX_RESOURCE_NAME_CHARS
            || resource.url().length() > AudioLimits.MAX_RESOURCE_URL_CHARS) {
            return false;
        }
        if (!resources.containsKey(resource.id())
            && resources.size() >= AudioLimits.MAX_RESOURCE_COUNT) {
            return false;
        }
        String normalizedName = normalize(resource.name());
        UUID existingId = nameIndex.get(normalizedName);
        if (existingId != null && !existingId.equals(resource.id())) {
            return false;
        }

        AudioResource previous = resources.put(resource.id(), resource);
        if (previous != null && !previous.name().equals(resource.name())) {
            nameIndex.remove(normalize(previous.name()), resource.id());
        }
        nameIndex.put(normalizedName, resource.id());
        return true;
    }

    public synchronized void updateResource(AudioResource resource) {
        addResource(resource);
    }

    public synchronized void removeResource(UUID id) {
        AudioResource resource = resources.remove(id);
        if (resource != null) {
            nameIndex.remove(normalize(resource.name()), id);
            rebuildSnapshot();
        }
    }

    public synchronized void loadResources(List<AudioResource> resourceList) {
        resources.clear();
        nameIndex.clear();
        for (AudioResource resource : resourceList) {
            addResourceToIndexes(resource);
        }
        rebuildSnapshot();
    }

    public synchronized Optional<AudioResource> getResource(UUID id) {
        return Optional.ofNullable(resources.get(id));
    }

    public synchronized Optional<AudioResource> getResourceByName(String name) {
        UUID id = nameIndex.get(normalize(name));
        return id != null ? Optional.ofNullable(resources.get(id)) : Optional.empty();
    }

    public List<AudioResource> getAllResources() {
        return sortedResources;
    }

    public List<AudioResource> searchResources(String query) {
        String lowerQuery = normalize(query);
        return sortedResources.stream()
            .filter(r -> normalize(r.name()).contains(lowerQuery))
            .toList();
    }

    private void rebuildSnapshot() {
        List<AudioResource> snapshot = new ArrayList<>(resources.values());
        snapshot.sort(RESOURCE_ORDER);
        sortedResources = List.copyOf(snapshot);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
