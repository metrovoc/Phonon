package com.metrovoc.phonon.station;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.metrovoc.phonon.audio.PlaybackState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * What one or more speakers play: a queue, a play mode and one clock, so every speaker on it stays in phase.
 * A speaker that isn't tuned anywhere has its own private station; shared stations have a name and can be tuned to.
 */
public final class Station {
    public enum Repeat { OFF, ALL, ONE }

    public static final int MAX_QUEUE = 2000;
    public static final int MAX_NAME_CHARS = 48;
    private static final long RESTART_INSTEAD_OF_PREVIOUS_MS = 3000;

    private final UUID id;
    private final String name;
    @Nullable private final UUID ownerId;
    private final String ownerName;
    private final boolean shared;
    private final List<UUID> queue = new ArrayList<>();
    private int cursor;
    private Repeat repeat = Repeat.OFF;
    private boolean shuffle;
    private PlaybackState playback = PlaybackState.STOPPED;

    public Station(UUID id, String name, @Nullable UUID ownerId, String ownerName, boolean shared) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.shared = shared;
    }

    public static Station ownedBySpeaker() {
        return new Station(UUID.randomUUID(), "", null, "", false);
    }

    public UUID id() { return id; }
    public String name() { return name; }
    @Nullable public UUID ownerId() { return ownerId; }
    public String ownerName() { return ownerName; }
    public boolean shared() { return shared; }
    public List<UUID> queue() { return Collections.unmodifiableList(queue); }
    public int cursor() { return cursor; }
    public Repeat repeat() { return repeat; }
    public boolean shuffle() { return shuffle; }
    public PlaybackState playback() { return playback; }

    @Nullable
    public UUID currentTrack() {
        return cursor >= 0 && cursor < queue.size() ? queue.get(cursor) : null;
    }

    /** Replaces the queue and starts one of its tracks from the beginning. */
    public void playFrom(List<UUID> tracks, int index, Random random) {
        int selected = Math.max(0, Math.min(index, tracks.size() - 1));
        int start = Math.min(selected, Math.max(0, tracks.size() - MAX_QUEUE));
        List<UUID> window = List.copyOf(tracks.subList(start, Math.min(tracks.size(), start + MAX_QUEUE)));
        queue.clear();
        queue.addAll(window);
        cursor = selected - start;
        if (shuffle) {
            shuffleUpcoming(random);
        }
        startCurrent();
    }

    /** Jumps to a place in the queue. */
    public void jumpTo(int index) {
        if (index >= 0 && index < queue.size()) {
            cursor = index;
            startCurrent();
        }
    }

    /** Puts a track right after the current one, starting it if nothing is loaded. */
    public void playNext(UUID track) {
        if (queue.size() >= MAX_QUEUE) {
            return;
        }
        boolean idle = playback.isStopped();
        int at = queue.isEmpty() ? 0 : Math.min(cursor + 1, queue.size());
        queue.add(at, track);
        if (idle && queue.size() == 1) {
            cursor = 0;
            startCurrent();
        }
    }

    /** Removes a queue entry. Removing the current one moves on to what follows it. */
    public void removeAt(int index) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        boolean wasCurrent = index == cursor;
        queue.remove(index);
        if (index < cursor) {
            cursor--;
        }
        if (wasCurrent) {
            if (queue.isEmpty() || cursor >= queue.size()) {
                cursor = Math.max(0, queue.size() - 1);
                stop();
            } else if (!playback.isStopped()) {
                boolean wasPaused = playback.isPaused();
                startCurrent();
                if (wasPaused) {
                    pause();
                }
            }
        }
    }

    /** Removes every mention of a track that left the library. */
    public void forget(UUID track) {
        for (int i = queue.size() - 1; i >= 0; i--) {
            if (queue.get(i).equals(track)) {
                removeAt(i);
            }
        }
    }

    public void pause() {
        if (playback.isPlaying()) {
            long now = PlaybackState.nowMs();
            playback = new PlaybackState(playback.resourceId(), now, playback.getCurrentPositionMs(now), 0f);
        }
    }

    public void resume() {
        if (playback.isPaused()) {
            playback = new PlaybackState(playback.resourceId(), PlaybackState.nowMs(), playback.positionAtAnchorMs(), 1.0f);
        }
    }

    public void stop() {
        playback = PlaybackState.STOPPED;
    }

    public void seek(long positionMs) {
        if (!playback.isStopped()) {
            playback = new PlaybackState(playback.resourceId(), PlaybackState.nowMs(), positionMs, playback.speed());
        }
    }

    /** Starts the current track from the beginning. */
    public void startCurrent() {
        UUID track = currentTrack();
        playback = track == null ? PlaybackState.STOPPED : new PlaybackState(track, PlaybackState.nowMs(), 0, 1.0f);
    }

    /** A track ended by itself: repeat decides what's next, and the queue running out stops the station. */
    public void onTrackEnded(Random random) {
        if (repeat == Repeat.ONE) {
            startCurrent();
        } else if (cursor + 1 < queue.size()) {
            cursor++;
            startCurrent();
        } else if (repeat == Repeat.ALL && !queue.isEmpty()) {
            cursor = 0;
            if (shuffle) {
                Collections.shuffle(queue, random);
            }
            startCurrent();
        } else {
            stop();
        }
    }

    /** The next button: always moves, wrapping around the queue. */
    public void next(Random random) {
        if (queue.isEmpty()) {
            return;
        }
        if (cursor + 1 >= queue.size() && shuffle) {
            Collections.shuffle(queue, random);
        }
        cursor = (cursor + 1) % queue.size();
        startCurrent();
    }

    /** The previous button: restarts a track that's well underway, or else goes back one, wrapping around. */
    public void previous() {
        if (queue.isEmpty()) {
            return;
        }
        if (playback.isStopped() || playback.getCurrentPositionMs() < RESTART_INSTEAD_OF_PREVIOUS_MS) {
            cursor = Math.floorMod(cursor - 1, queue.size());
        }
        startCurrent();
    }

    public void cycleRepeat() {
        repeat = Repeat.values()[(repeat.ordinal() + 1) % Repeat.values().length];
    }

    /** Turning shuffle on mixes what's still to come. Turning it off keeps that order. */
    public void toggleShuffle(Random random) {
        shuffle = !shuffle;
        if (shuffle) {
            shuffleUpcoming(random);
        }
    }

    private void shuffleUpcoming(Random random) {
        if (cursor + 1 < queue.size()) {
            Collections.shuffle(queue.subList(cursor + 1, queue.size()), random);
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("name", name);
        if (ownerId != null) {
            json.addProperty("ownerId", ownerId.toString());
        }
        json.addProperty("ownerName", ownerName);
        json.addProperty("shared", shared);
        JsonArray tracks = new JsonArray();
        queue.forEach(track -> tracks.add(track.toString()));
        json.add("queue", tracks);
        json.addProperty("cursor", cursor);
        json.addProperty("repeat", repeat.name());
        json.addProperty("shuffle", shuffle);
        if (playback.resourceId() != null) {
            json.addProperty("track", playback.resourceId().toString());
            json.addProperty("position", playback.getCurrentPositionMs());
            json.addProperty("speed", playback.speed());
        }
        return json;
    }

    /** Playback carries on from the saved position, anchored to now. */
    public static Station fromJson(JsonObject json) {
        Station station = new Station(
            UUID.fromString(json.get("id").getAsString()),
            json.has("name") ? json.get("name").getAsString() : "",
            json.has("ownerId") ? UUID.fromString(json.get("ownerId").getAsString()) : null,
            json.has("ownerName") ? json.get("ownerName").getAsString() : "",
            json.has("shared") && json.get("shared").getAsBoolean());
        if (json.has("queue")) {
            for (JsonElement track : json.getAsJsonArray("queue")) {
                station.queue.add(UUID.fromString(track.getAsString()));
            }
        }
        station.cursor = json.has("cursor") ? json.get("cursor").getAsInt() : 0;
        if (json.has("repeat")) {
            station.repeat = Repeat.valueOf(json.get("repeat").getAsString());
        }
        station.shuffle = json.has("shuffle") && json.get("shuffle").getAsBoolean();
        if (json.has("track")) {
            station.playback = new PlaybackState(UUID.fromString(json.get("track").getAsString()),
                PlaybackState.nowMs(), json.get("position").getAsLong(), json.get("speed").getAsFloat());
        }
        return station;
    }

    /** A new shared station that carries on from where another one is. */
    public static Station sharedCopyOf(Station from, UUID id, String name, UUID ownerId, String ownerName) {
        Station station = new Station(id, name, ownerId, ownerName, true);
        station.queue.addAll(from.queue);
        station.cursor = from.cursor;
        station.repeat = from.repeat;
        station.shuffle = from.shuffle;
        PlaybackState source = from.playback;
        if (source.resourceId() != null) {
            station.playback = new PlaybackState(source.resourceId(), PlaybackState.nowMs(), source.getCurrentPositionMs(), source.speed());
        }
        return station;
    }

    /** A speaker saved before stations existed: one track at a known position. */
    public static Station migrated(UUID track, long positionMs, float speed) {
        Station station = ownedBySpeaker();
        station.queue.add(track);
        station.playback = new PlaybackState(track, PlaybackState.nowMs(), positionMs, speed);
        return station;
    }

    public StationView view() {
        return new StationView(id, name, ownerId, ownerName, shared, List.copyOf(queue), cursor, repeat, shuffle);
    }
}
