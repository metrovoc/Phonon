package com.metrovoc.phonon.server;

import com.metrovoc.phonon.audio.AudioLimits;
import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.config.PhononServerConfig;
import com.metrovoc.phonon.station.Playlist;
import com.metrovoc.phonon.station.Station;
import com.metrovoc.phonon.station.StationListener;
import com.metrovoc.phonon.station.StationManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Everything that changes what a speaker plays, whether a player or redstone asked. */
public final class SpeakerControl {
    private SpeakerControl() {}

    private static StationManager stations() {
        return StationManager.getInstance();
    }

    private static List<UUID> library() {
        List<UUID> ids = new ArrayList<>();
        for (AudioResource resource : AudioManager.getInstance().getAllResources()) {
            ids.add(resource.id());
        }
        return ids;
    }

    private static boolean known(@Nullable UUID track) {
        return track != null && AudioManager.getInstance().getResource(track).isPresent();
    }

    /** Whether this player may change where the speaker is tuned. */
    public static boolean canTune(ServerPlayer player, StationListener speaker) {
        return !PhononServerConfig.getOwnerOnlySpeakers()
            || PlayerPermissions.isOperator(player)
            || speaker.getOwnerId() == null
            || speaker.getOwnerId().equals(player.getUUID());
    }

    /** Whether this player may change what plays: a shared station answers to whoever runs it, not to whoever tuned in. */
    public static boolean canControl(ServerPlayer player, StationListener speaker) {
        Station station = speaker.station();
        return station.shared() && !PhononServerConfig.getAnyoneCanControlStations()
            ? ownsOrOperates(player, station.ownerId())
            : canTune(player, speaker);
    }

    /** A wire can't be asked who pulled the lever, so a station only listens to redstone on its creator's own speakers. */
    public static boolean redstoneMayControl(StationListener speaker) {
        Station station = speaker.station();
        return !station.shared() || PhononServerConfig.getAnyoneCanControlStations()
            || (station.ownerId() != null && station.ownerId().equals(speaker.getOwnerId()));
    }

    private static boolean ownsOrOperates(ServerPlayer player, @Nullable UUID owner) {
        return PlayerPermissions.isOperator(player) || player.getUUID().equals(owner);
    }

    public static boolean canAddTracks(ServerPlayer player) {
        return PhononServerConfig.getAnyoneCanAddTracks() || PlayerPermissions.isOperator(player);
    }

    /** Deleting affects every queue and playlist, so it stays with operators and whoever added the track. */
    private static boolean canDeleteTrack(ServerPlayer player, @Nullable UUID track) {
        return PlayerPermissions.isOperator(player) || AudioManager.getInstance().getResource(track)
            .map(resource -> player.getUUID().equals(resource.addedBy())).orElse(false);
    }

    private static void deny(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("phonon.permission.denied"));
    }

    /** Runs what a player's screen asked for, after checking they may. */
    public static void handle(
        ServerPlayer player, StationListener speaker, SpeakerAction action,
        @Nullable UUID target, @Nullable UUID second, int number, String text
    ) {
        switch (action) {
            case CREATE_PLAYLIST, ADD_TO_PLAYLIST, REMOVE_FROM_PLAYLIST, DELETE_PLAYLIST, RENAME_PLAYLIST, MOVE_IN_PLAYLIST -> {
                handlePlaylist(player, action, target, second, number, text);
                return;
            }
            case ADD_LINK -> {
                if (canAddTracks(player)) {
                    AddFlow.addFromText(player.createCommandSourceStack(), text);
                } else {
                    deny(player);
                }
                return;
            }
            case ADD_RESULT -> {
                if (canAddTracks(player)) {
                    AddFlow.addResult(player.createCommandSourceStack(), text);
                } else {
                    deny(player);
                }
                return;
            }
            case DELETE_STATION -> {
                Station shared = stations().get(target);
                if (shared == null || !shared.shared() || !ownsOrOperates(player, shared.ownerId())) {
                    deny(player);
                } else {
                    stations().deleteShared(shared);
                }
                return;
            }
            case DELETE_TRACK -> {
                if (canDeleteTrack(player, target)) {
                    AudioManager.getInstance().getResource(target)
                        .ifPresent(resource -> AudioLibrary.remove(player.level().getServer(), resource));
                } else {
                    deny(player);
                }
                return;
            }
            default -> {
            }
        }

        boolean tuning = action == SpeakerAction.CREATE_STATION || action == SpeakerAction.TUNE;
        if (!(tuning ? canTune(player, speaker) : canControl(player, speaker))) {
            deny(player);
            return;
        }
        Station station = speaker.station();
        switch (action) {
            case PLAY_TRACK -> {
                if (known(target)) {
                    List<UUID> ids = library();
                    station.playFrom(ids, ids.indexOf(target), stations().random());
                }
            }
            case PLAY_NEXT -> {
                if (known(target)) {
                    station.playNext(target);
                }
            }
            case JUMP_TO -> station.jumpTo(number);
            case REMOVE_FROM_QUEUE -> station.removeAt(number);
            case PAUSE -> station.pause();
            case RESUME -> station.resume();
            case STOP -> station.stop();
            case NEXT -> step(station, 1);
            case PREVIOUS -> step(station, -1);
            case CYCLE_REPEAT -> station.cycleRepeat();
            case TOGGLE_SHUFFLE -> station.toggleShuffle(stations().random());
            case PLAY_PLAYLIST -> {
                Playlist playlist = stations().playlist(target);
                if (playlist != null && !playlist.tracks().isEmpty()) {
                    station.playFrom(playlist.tracks(), number, stations().random());
                }
            }
            case CREATE_STATION -> {
                String name = cleanName(text);
                Station existing = Station.named(stations().sharedStations(), name);
                if (existing != null) {
                    speaker.tune(existing.id());
                } else if (!name.isEmpty() && stations().sharedStations().size() < StationManager.MAX_SHARED_STATIONS) {
                    Station created = stations().shareAsStation(station, name, player.getUUID(), player.getName().getString());
                    if (!station.shared()) {
                        station.stop();
                    }
                    speaker.tune(created.id());
                }
                return;
            }
            case TUNE -> {
                speaker.tune(target);
                return;
            }
            default -> {
                return;
            }
        }
        stations().changed(station);
    }

    private static void handlePlaylist(
        ServerPlayer player, SpeakerAction action, @Nullable UUID target, @Nullable UUID second, int number, String text
    ) {
        if (action == SpeakerAction.CREATE_PLAYLIST) {
            String name = cleanName(text);
            if (!name.isEmpty() && stations().playlists().size() < StationManager.MAX_PLAYLISTS) {
                stations().putPlaylist(new Playlist(UUID.randomUUID(), name, player.getUUID(), player.getName().getString(),
                    known(second) ? List.of(second) : List.of()));
            }
            return;
        }
        Playlist playlist = stations().playlist(target);
        if (playlist == null) {
            return;
        }
        if (!ownsOrOperates(player, playlist.ownerId())) {
            deny(player);
            return;
        }
        switch (action) {
            case ADD_TO_PLAYLIST -> {
                if (known(second)) {
                    stations().putPlaylist(playlist.withTrack(second));
                }
            }
            case REMOVE_FROM_PLAYLIST -> stations().putPlaylist(playlist.withoutTrack(second));
            case DELETE_PLAYLIST -> stations().deletePlaylist(playlist.id());
            case RENAME_PLAYLIST -> {
                String name = cleanName(text);
                if (!name.isEmpty()) {
                    stations().putPlaylist(playlist.renamed(name));
                }
            }
            case MOVE_IN_PLAYLIST -> stations().putPlaylist(playlist.withTrackAt(second, number));
            default -> {
            }
        }
    }

    private static String cleanName(String text) {
        String name = text.strip();
        return name.substring(0, Math.min(name.length(), Station.MAX_NAME_CHARS));
    }

    private static void step(Station station, int direction) {
        if (station.queue().isEmpty()) {
            List<UUID> ids = library();
            if (!ids.isEmpty()) {
                station.playFrom(ids, 0, stations().random());
            }
        } else if (direction > 0) {
            station.next(stations().random());
        } else {
            station.previous();
        }
    }

    // Redstone

    /** Pauses when playing; otherwise resumes, or starts the queue, or starts the library. */
    public static void toggle(StationListener speaker) {
        Station station = speaker.station();
        if (station.playback().isPlaying()) {
            station.pause();
        } else if (station.playback().isPaused()) {
            station.resume();
        } else if (station.currentTrack() != null) {
            station.startCurrent();
        } else {
            step(station, 1);
        }
        stations().changed(station);
    }

    public static void step(StationListener speaker, int direction) {
        Station station = speaker.station();
        step(station, direction);
        stations().changed(station);
    }

    public static void stop(StationListener speaker) {
        Station station = speaker.station();
        station.stop();
        stations().changed(station);
    }

    public static void seek(StationListener speaker, long positionMs) {
        Station station = speaker.station();
        long durationMs = StationManager.durationMs(station.playback().resourceId());
        long position = Math.max(0, positionMs);
        if (durationMs > 0) {
            position = Math.min(position, durationMs - 1);
        }
        station.seek(position);
        stations().changed(station);
    }
}
