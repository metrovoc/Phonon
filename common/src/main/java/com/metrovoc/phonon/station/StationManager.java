package com.metrovoc.phonon.station;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the server's stations and playlists, and which loaded speakers listen to each station.
 * Shared stations and playlists are saved with the world. A private station lives in its speaker's data.
 */
public final class StationManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "phonon_stations.json";
    private static final int COMPARATOR_INTERVAL_TICKS = 20;
    private static final int SAVE_DELAY_TICKS = 20;
    public static final int MAX_SHARED_STATIONS = 256;
    public static final int MAX_PLAYLISTS = 1024;

    private static StationManager instance;

    private record SpeakerRef(ResourceKey<Level> dimension, BlockPos pos) {}

    private final Map<UUID, Station> stations = new HashMap<>();
    private final Map<UUID, Set<SpeakerRef>> listeners = new HashMap<>();
    private final Map<UUID, Playlist> playlists = new HashMap<>();
    private final Boomboxes boomboxes = new Boomboxes(this);
    private final Random random = new Random();
    @Nullable private MinecraftServer server;
    private long ticks;
    private long saveAtTick = Long.MAX_VALUE;
    private final Map<UUID, StationView> sentSharedViews = new HashMap<>();

    private StationManager() {}

    public static StationManager getInstance() {
        if (instance == null) {
            instance = new StationManager();
        }
        return instance;
    }

    public static void reset() {
        instance = null;
    }

    public Boomboxes boomboxes() {
        return boomboxes;
    }

    public Random random() {
        return random;
    }

    @Nullable
    public Station get(@Nullable UUID id) {
        return id == null ? null : stations.get(id);
    }

    public void addPrivate(Station station) {
        stations.put(station.id(), station);
    }

    public void removePrivate(Station station) {
        if (!station.shared()) {
            stations.remove(station.id());
            listeners.remove(station.id());
        }
    }

    public void attach(UUID stationId, ResourceKey<Level> dimension, BlockPos pos) {
        listeners.computeIfAbsent(stationId, id -> new HashSet<>()).add(new SpeakerRef(dimension, pos));
    }

    public void detach(UUID stationId, ResourceKey<Level> dimension, BlockPos pos) {
        Set<SpeakerRef> refs = listeners.get(stationId);
        if (refs != null) {
            refs.remove(new SpeakerRef(dimension, pos));
        }
    }

    public List<Station> sharedStations() {
        return stations.values().stream().filter(Station::shared)
            .sorted(Comparator.comparing(Station::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public List<Playlist> playlists() {
        return playlists.values().stream()
            .sorted(Comparator.comparing(Playlist::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Nullable
    public Playlist playlist(@Nullable UUID id) {
        return id == null ? null : playlists.get(id);
    }

    /** Starts a station from what's playing, so the music carries on and is now shared. */
    public Station shareAsStation(Station from, String name, UUID ownerId, String ownerName) {
        Station station = Station.sharedCopyOf(from, UUID.randomUUID(), name, ownerId, ownerName);
        stations.put(station.id(), station);
        sharedChanged();
        return station;
    }

    /** Listening speakers fall back to their own private stations. */
    public void deleteShared(Station station) {
        if (!station.shared()) {
            return;
        }
        for (SpeakerRef ref : List.copyOf(listeners.getOrDefault(station.id(), Set.of()))) {
            SpeakerBlockEntity speaker = speakerAt(ref);
            if (speaker != null) {
                speaker.tune(null);
            }
        }
        boomboxes.stationDeleted(station.id());
        stations.remove(station.id());
        listeners.remove(station.id());
        sharedChanged();
    }

    public void putPlaylist(Playlist playlist) {
        playlists.put(playlist.id(), playlist);
        sharedChanged();
    }

    public void deletePlaylist(UUID id) {
        playlists.remove(id);
        sharedChanged();
    }

    /** A track left the library: it leaves every queue and playlist too. */
    public void forgetTrack(UUID track) {
        for (Station station : List.copyOf(stations.values())) {
            if (station.queue().contains(track)) {
                station.forget(track);
                changed(station);
            }
        }
        for (Playlist playlist : List.copyOf(playlists.values())) {
            if (playlist.tracks().contains(track)) {
                playlists.put(playlist.id(), playlist.withoutTrack(track));
            }
        }
        boomboxes.forgetTrack(track);
        sharedChanged();
    }

    /** Pushes a station's state to every speaker listening and every client that needs it. */
    public void changed(Station station) {
        for (SpeakerRef ref : List.copyOf(listeners.getOrDefault(station.id(), Set.of()))) {
            SpeakerBlockEntity speaker = speakerAt(ref);
            if (speaker != null) {
                speaker.setPlayback(station.playback());
                PlatformHelper.INSTANCE.broadcastSpeakerState(speaker);
                if (!station.shared()) {
                    PlatformHelper.INSTANCE.broadcastStationView(speaker, station.view());
                }
            }
        }
        boolean heldByBoombox = boomboxes.stationChanged(station);
        if (station.shared()) {
            markDirty();
            StationView view = station.view();
            if (server != null && !view.equals(sentSharedViews.put(station.id(), view))) {
                for (var player : server.getPlayerList().getPlayers()) {
                    PlatformHelper.INSTANCE.sendStationView(player, view);
                }
            }
        } else if (heldByBoombox) {
            markDirty();
        }
    }

    private void sharedChanged() {
        markDirty();
        if (server != null) {
            PlatformHelper.INSTANCE.broadcastStations(server);
            sentSharedViews.clear();
            sharedViews().forEach(view -> sentSharedViews.put(view.id(), view));
        }
    }

    /** Coalesces edits within one second without letting continuous edits postpone a save. */
    public void markDirty() {
        if (saveAtTick == Long.MAX_VALUE) {
            saveAtTick = ticks + SAVE_DELAY_TICKS;
        }
    }

    public void tick(MinecraftServer server) {
        this.server = server;
        ticks++;
        boomboxes.tick(server);
        for (Station station : List.copyOf(stations.values())) {
            if (!station.playback().isPlaying()) {
                continue;
            }
            long duration = durationMs(station.playback().resourceId());
            if (duration > 0 && station.playback().getCurrentPositionMs() >= duration) {
                station.onTrackEnded(random);
                changed(station);
            } else if (!station.shared() || ticks % COMPARATOR_INTERVAL_TICKS == 0) {
                for (SpeakerRef ref : listeners.getOrDefault(station.id(), Set.of())) {
                    SpeakerBlockEntity speaker = speakerAt(ref);
                    if (speaker != null) {
                        if (!station.shared()) {
                            // The clock advances without a control edit. Keep its chunk dirty so an
                            // unload saves the current position instead of reusing an older snapshot.
                            speaker.getLevel().blockEntityChanged(ref.pos());
                        }
                        if (ticks % COMPARATOR_INTERVAL_TICKS == 0) {
                            speaker.updateComparators();
                        }
                    }
                }
            }
        }
        if (ticks >= saveAtTick) {
            save();
        }
    }

    @Nullable
    private SpeakerBlockEntity speakerAt(SpeakerRef ref) {
        ServerLevel level = server == null ? null : server.getLevel(ref.dimension());
        return level != null && level.isLoaded(ref.pos()) && level.getBlockEntity(ref.pos()) instanceof SpeakerBlockEntity speaker
            ? speaker : null;
    }

    public static long durationMs(@Nullable UUID track) {
        return track == null ? -1 : AudioManager.getInstance().getResource(track).map(AudioResource::durationMs).orElse(-1L);
    }

    private Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE);
    }

    public void load(MinecraftServer server) {
        this.server = server;
        Path file = file();
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("stations")) {
                try {
                    Station station = Station.fromJson(element.getAsJsonObject());
                    stations.put(station.id(), station);
                } catch (RuntimeException invalid) {
                    Phonon.LOGGER.warn("Skipping malformed station in {}", file);
                }
            }
            for (JsonElement element : root.getAsJsonArray("playlists")) {
                try {
                    Playlist playlist = Playlist.fromJson(element.getAsJsonObject());
                    playlists.put(playlist.id(), playlist);
                } catch (RuntimeException invalid) {
                    Phonon.LOGGER.warn("Skipping malformed playlist in {}", file);
                }
            }
            if (root.has("boomboxes")) {
                boomboxes.load(root.getAsJsonArray("boomboxes"));
            }
            Phonon.LOGGER.info("Loaded {} stations and {} playlists", sharedStations().size(), playlists.size());
        } catch (Exception e) {
            Phonon.LOGGER.error("Failed to load stations from {}", file, e);
        }
    }

    public void save() {
        if (server == null) {
            return;
        }
        JsonArray stationsJson = new JsonArray();
        sharedStations().forEach(station -> stationsJson.add(station.toJson()));
        JsonArray playlistsJson = new JsonArray();
        playlists().forEach(playlist -> playlistsJson.add(playlist.toJson()));
        JsonObject root = new JsonObject();
        root.add("stations", stationsJson);
        root.add("playlists", playlistsJson);
        root.add("boomboxes", boomboxes.toJson());
        Path file = file();
        Path temporary = file.resolveSibling(FILE + ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(root));
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            saveAtTick = Long.MAX_VALUE;
        } catch (IOException e) {
            saveAtTick = ticks + SAVE_DELAY_TICKS;
            Phonon.LOGGER.error("Failed to save stations", e);
        }
    }

    public List<StationView> sharedViews() {
        List<StationView> views = new ArrayList<>();
        sharedStations().forEach(station -> views.add(station.view()));
        return views;
    }
}
