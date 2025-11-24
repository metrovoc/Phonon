package com.tovkaic.phonon.audio;

import java.util.UUID;

/**
 * Core data structure representing an audio resource.
 * Immutable by design - no setters, no special cases.
 */
public record AudioResource(
    UUID id,
    String name,
    String url,
    long durationMs
) {
    public AudioResource(String name, String url, long durationMs) {
        this(UUID.randomUUID(), name, url, durationMs);
    }

    public AudioResource(String name, String url) {
        this(UUID.randomUUID(), name, url, -1);
    }
}
