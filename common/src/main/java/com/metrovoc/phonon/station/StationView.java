package com.metrovoc.phonon.station;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** What clients need to show a station. */
public record StationView(
    UUID id,
    String name,
    @Nullable UUID ownerId,
    String ownerName,
    boolean shared,
    List<UUID> queue,
    int cursor,
    Station.Repeat repeat,
    boolean shuffle
) {}
