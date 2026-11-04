package com.metrovoc.phonon.client;

import com.metrovoc.phonon.server.LibraryJob;
import com.metrovoc.phonon.station.Playlist;
import com.metrovoc.phonon.station.StationView;
import com.metrovoc.phonon.audio.Receiver;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the speaker screen shows about stations, playlists and audio being added. */
public final class ClientStations {
    private static final ClientStations INSTANCE = new ClientStations();

    private final Map<UUID, StationView> views = new HashMap<>();
    private final Map<Receiver, UUID> speakerStations = new HashMap<>();
    private List<StationView> shared = List.of();
    private List<Playlist> playlists = List.of();
    private List<LibraryJob> jobs = List.of();
    private boolean ownerOnlySpeakers;
    private boolean anyoneCanAddTracks;
    private boolean anyoneCanControlStations;

    private ClientStations() {}

    public static ClientStations getInstance() {
        return INSTANCE;
    }

    public void put(StationView view) {
        views.put(view.id(), view);
        if (view.shared()) {
            shared = shared.stream().map(existing -> existing.id().equals(view.id()) ? view : existing).toList();
        }
    }

    public void setSpeakerStation(Receiver receiver, UUID stationId) {
        speakerStations.put(receiver, stationId);
    }

    public void setShared(List<StationView> shared, List<Playlist> playlists) {
        this.shared = shared;
        this.playlists = playlists;
        views.entrySet().removeIf(entry -> entry.getValue().shared());
        shared.forEach(view -> views.put(view.id(), view));
    }

    public void setPermissions(boolean ownerOnlySpeakers, boolean anyoneCanAddTracks, boolean anyoneCanControlStations) {
        this.ownerOnlySpeakers = ownerOnlySpeakers;
        this.anyoneCanAddTracks = anyoneCanAddTracks;
        this.anyoneCanControlStations = anyoneCanControlStations;
    }

    public boolean ownerOnlySpeakers() {
        return ownerOnlySpeakers;
    }

    public boolean anyoneCanAddTracks() {
        return anyoneCanAddTracks;
    }

    public boolean anyoneCanControlStations() {
        return anyoneCanControlStations;
    }

    /** Shows a playlist's new order right away, before the server confirms it. */
    public void moveInPlaylist(UUID playlist, UUID track, int index) {
        playlists = playlists.stream().map(each -> each.id().equals(playlist) ? each.withTrackAt(track, index) : each).toList();
    }

    public void setJobs(List<LibraryJob> jobs) {
        this.jobs = jobs;
    }

    @Nullable
    public StationView stationOf(Receiver receiver) {
        UUID id = speakerStations.get(receiver);
        return id == null ? null : views.get(id);
    }

    public List<StationView> shared() {
        return shared;
    }

    public List<Playlist> playlists() {
        return playlists;
    }

    public List<LibraryJob> jobs() {
        return jobs;
    }

    public void clear() {
        views.clear();
        speakerStations.clear();
        shared = List.of();
        playlists = List.of();
        jobs = List.of();
    }
}
