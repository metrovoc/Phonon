package com.metrovoc.phonon.audio;

import java.util.UUID;

/**
 * Core data structure representing an audio resource.
 * Immutable by design - no setters, no special cases.
 */
public record AudioResource(
    UUID id,
    String name,
    String url,
    long durationMs,
    long sizeBytes
) {
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
}
