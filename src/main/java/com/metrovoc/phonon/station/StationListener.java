package com.metrovoc.phonon.station;

import com.metrovoc.phonon.audio.Receiver;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The server's view of a speaker or boombox: what a player's controls act on. */
public interface StationListener {
    Receiver receiver();

    /** The station it's tuned to, or its own private one. */
    Station station();

    /** Listens to a shared station, or with null goes back to its own. */
    void tune(@Nullable UUID stationId);

    /** Who may control it when speakers are owner-only; null means anyone. */
    @Nullable
    UUID getOwnerId();

    float getVolume();

    void setVolume(float volume);

    /** Sends a packet to every player who can hear it. */
    void broadcast(net.minecraft.network.protocol.common.custom.CustomPacketPayload packet);
}
