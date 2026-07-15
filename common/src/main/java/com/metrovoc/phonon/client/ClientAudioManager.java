package com.metrovoc.phonon.client;

import com.metrovoc.phonon.audio.AudioResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Immutable client-thread snapshot of server audio metadata. */
public final class ClientAudioManager {
    private static final ClientAudioManager INSTANCE = new ClientAudioManager();
    private static final Comparator<AudioResource> RESOURCE_ORDER =
        Comparator.comparing(AudioResource::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(AudioResource::id);

    private Map<UUID, AudioResource> resourcesById = Map.of();
    private List<IndexedResource> resources = List.of();
    private List<AudioResource> allResources = List.of();

    private ClientAudioManager() {}

    public static ClientAudioManager getInstance() {
        return INSTANCE;
    }

    public void setResources(List<AudioResource> resourceList) {
        Map<UUID, AudioResource> byId = new HashMap<>();
        List<IndexedResource> indexed = new ArrayList<>(resourceList.size());
        resourceList.stream()
            .sorted(RESOURCE_ORDER)
            .forEach(resource -> {
                byId.put(resource.id(), resource);
                indexed.add(new IndexedResource(resource, normalize(resource.name())));
            });
        resourcesById = Map.copyOf(byId);
        resources = List.copyOf(indexed);
        allResources = indexed.stream().map(IndexedResource::resource).toList();
    }

    public Optional<AudioResource> getResource(UUID id) {
        return Optional.ofNullable(resourcesById.get(id));
    }

    public List<AudioResource> getAllResources() {
        return allResources;
    }

    public List<AudioResource> searchResources(String query) {
        String normalizedQuery = normalize(query);
        return resources.stream()
            .filter(entry -> entry.normalizedName().contains(normalizedQuery))
            .map(IndexedResource::resource)
            .toList();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private record IndexedResource(AudioResource resource, String normalizedName) {}
}
