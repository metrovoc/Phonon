package com.metrovoc.phonon.audio;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Core data structure representing an audio resource.
 * Immutable by design - no setters, no special cases.
 * Tracks from before this was recorded, and from the music folder, have nobody as their adder.
 */
public record AudioResource(
    UUID id,
    String name,
    String url,
    long durationMs,
    long sizeBytes,
    @Nullable UUID addedBy,
    String addedByName
) {
    public AudioResource(UUID id, String name, String url, long durationMs, long sizeBytes) {
        this(id, name, url, durationMs, sizeBytes, null, "");
    }

    // 4-parameter constructor for backward compatibility
    public AudioResource(UUID id, String name, String url, long durationMs) {
        this(id, name, url, durationMs, 0L);
    }

    public AudioResource(String name, String url, long durationMs) {
        this(UUID.randomUUID(), name, url, durationMs, 0L);
    }

    public AudioResource(String name, String url) {
        this(UUID.randomUUID(), name, url, -1L, 0L);
    }

    public AudioResource withDurationMs(long duration) {
        return new AudioResource(id, name, url, duration, sizeBytes, addedBy, addedByName);
    }
}
