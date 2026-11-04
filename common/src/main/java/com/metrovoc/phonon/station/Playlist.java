package com.metrovoc.phonon.station;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A saved, named list of tracks, credited to whoever made it. */
public record Playlist(UUID id, String name, UUID ownerId, String ownerName, List<UUID> tracks) {
    public static final int MAX_TRACKS = Station.MAX_QUEUE;

    public Playlist withTrack(UUID track) {
        if (tracks.size() >= MAX_TRACKS || tracks.contains(track)) {
            return this;
        }
        List<UUID> updated = new ArrayList<>(tracks);
        updated.add(track);
        return new Playlist(id, name, ownerId, ownerName, List.copyOf(updated));
    }

    public Playlist withoutTrack(UUID track) {
        List<UUID> updated = new ArrayList<>(tracks);
        updated.remove(track);
        return new Playlist(id, name, ownerId, ownerName, List.copyOf(updated));
    }

    public Playlist renamed(String newName) {
        return new Playlist(id, newName, ownerId, ownerName, tracks);
    }

    /** Moves a track to a new place in the list. */
    public Playlist withTrackAt(UUID track, int index) {
        if (!tracks.contains(track)) {
            return this;
        }
        List<UUID> updated = new ArrayList<>(tracks);
        updated.remove(track);
        updated.add(Math.max(0, Math.min(index, updated.size())), track);
        return new Playlist(id, name, ownerId, ownerName, List.copyOf(updated));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("name", name);
        json.addProperty("ownerId", ownerId.toString());
        json.addProperty("ownerName", ownerName);
        JsonArray list = new JsonArray();
        tracks.forEach(track -> list.add(track.toString()));
        json.add("tracks", list);
        return json;
    }

    public static Playlist fromJson(JsonObject json) {
        List<UUID> tracks = new ArrayList<>();
        json.getAsJsonArray("tracks").forEach(track -> tracks.add(UUID.fromString(track.getAsString())));
        return new Playlist(
            UUID.fromString(json.get("id").getAsString()),
            json.get("name").getAsString(),
            UUID.fromString(json.get("ownerId").getAsString()),
            json.get("ownerName").getAsString(),
            List.copyOf(tracks));
    }
}
