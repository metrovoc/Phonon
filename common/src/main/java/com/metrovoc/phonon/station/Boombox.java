package com.metrovoc.phonon.station;

import com.google.gson.JsonObject;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.platform.PlatformHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A boombox's state, kept by the server under the id its item carries, so it survives whatever happens to the stack.
 * It plays while something has it. With nobody holding it, its own station stops where it was until it turns up again.
 */
public final class Boombox implements StationListener {
    private static final float DEFAULT_VOLUME = 0.5f;
    /** Long enough for a creative player's cursor, which the server can't see. */
    private static final int MISSING_GRACE_TICKS = 100;
    private static final double HEAR_RANGE_SQUARED = 128.0 * 128.0;
    private static final double OUT_OF_RANGE_SQUARED = 144.0 * 144.0;

    private final UUID id;
    private final StationManager stations;
    private final Set<UUID> hearing = new HashSet<>();
    private Station ownStation;
    /** The own station as it stood when the boombox was last had, and playback resumes from. */
    private JsonObject stored;
    @Nullable private UUID tunedTo;
    private boolean resumeOwn;
    private float volume = DEFAULT_VOLUME;
    @Nullable private Entity holder;
    private boolean present;
    private int missingTicks;

    Boombox(UUID id, StationManager stations, Station ownStation) {
        this.id = id;
        this.stations = stations;
        this.ownStation = ownStation;
        this.stored = ownStation.toJson();
    }

    public UUID id() {
        return id;
    }

    public boolean present() {
        return present;
    }

    public boolean playing() {
        return present && station().playback().isPlaying();
    }

    /** Whether this player has it right now. */
    public boolean heldBy(Entity entity) {
        return present && holder == entity;
    }

    @Override
    public Receiver receiver() {
        return new Receiver.Boombox(id);
    }

    @Override
    public Station station() {
        Station tuned = stations.get(tunedTo);
        return tuned != null ? tuned : ownStation;
    }

    Station ownStation() {
        return ownStation;
    }

    @Nullable
    UUID tunedTo() {
        return tunedTo;
    }

    /** Whoever holds it runs it. */
    @Override
    @Nullable
    public UUID getOwnerId() {
        return holder instanceof ServerPlayer player ? player.getUUID() : null;
    }

    @Override
    public void tune(@Nullable UUID stationId) {
        boolean wasOnOwn = tunedTo == null;
        tunedTo = stations.get(stationId) != null ? stationId : null;
        if (tunedTo != null && wasOnOwn) {
            resumeOwn = ownStation.playback().isPlaying();
            ownStation.pause();
        } else if (tunedTo == null && resumeOwn) {
            ownStation.resume();
            resumeOwn = false;
        }
        stations.changed(station());
        stations.save();
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public void setVolume(float volume) {
        this.volume = Float.isFinite(volume) ? Math.max(0.0f, Math.min(1.0f, volume)) : DEFAULT_VOLUME;
        stations.save();
    }

    @Override
    public void broadcast(Object packet) {
        forEachHearing(player -> PlatformHelper.INSTANCE.sendToPlayer(player, packet));
    }

    private void forEachHearing(java.util.function.Consumer<ServerPlayer> action) {
        MinecraftServer server = holder == null ? null : holder.level().getServer();
        if (server == null) {
            return;
        }
        for (UUID uuid : hearing) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                action.accept(player);
            }
        }
    }

    void sendStateToHearing() {
        PlatformHelper.INSTANCE.broadcastSpeakerState(this);
        if (!station().shared()) {
            PlatformHelper.INSTANCE.broadcastStationView(this, station().view());
        }
    }

    /** A track left the library while nobody has this boombox. */
    void forgetWhileAway(UUID track) {
        Station station = Station.fromJson(stored);
        station.forget(track);
        stored = station.toJson();
    }

    void playerLeft(UUID player) {
        hearing.remove(player);
    }

    /** Runs each tick, with whatever has this boombox now, or null. */
    void tick(MinecraftServer server, @Nullable Entity found) {
        if (found == null) {
            if (present && ++missingTicks > MISSING_GRACE_TICKS) {
                suspend();
            }
            if (present) {
                reconcileHearing(server, false);
            }
            return;
        }
        missingTicks = 0;
        if (!present) {
            present = true;
            ownStation = Station.fromJson(stored);
            stations.addPrivate(ownStation);
        }
        boolean moved = found != holder;
        holder = found;
        reconcileHearing(server, moved);
    }

    /** Tells each player whose hearing changed, and everyone hearing when it changed hands. */
    private void reconcileHearing(MinecraftServer server, boolean moved) {
        Entity at = holder;
        if (at == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean heard = hearing.contains(player.getUUID());
            boolean inRange = player == at || player.level() == at.level()
                && player.distanceToSqr(at) < (heard ? OUT_OF_RANGE_SQUARED : HEAR_RANGE_SQUARED);
            if (inRange && !heard) {
                hearing.add(player.getUUID());
                PlatformHelper.INSTANCE.sendBoomboxPlace(player, id, at.getId(), at.position());
                PlatformHelper.INSTANCE.sendStationView(player, station().view());
                PlatformHelper.INSTANCE.sendListenerState(player, this);
            } else if (!inRange && heard) {
                hearing.remove(player.getUUID());
                PlatformHelper.INSTANCE.sendBoomboxGone(player, id);
            } else if (inRange && moved) {
                PlatformHelper.INSTANCE.sendBoomboxPlace(player, id, at.getId(), at.position());
            }
        }
    }

    private void suspend() {
        forEachHearing(player -> PlatformHelper.INSTANCE.sendBoomboxGone(player, id));
        hearing.clear();
        holder = null;
        present = false;
        stored = ownStation.toJson();
        stations.removePrivate(ownStation);
        stations.save();
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("volume", volume);
        json.addProperty("resumeOwn", resumeOwn);
        if (tunedTo != null) {
            json.addProperty("tunedTo", tunedTo.toString());
        }
        json.add("station", present ? ownStation.toJson() : stored);
        return json;
    }

    static Boombox fromJson(JsonObject json, StationManager stations) {
        Boombox boombox = new Boombox(UUID.fromString(json.get("id").getAsString()), stations,
            Station.fromJson(json.getAsJsonObject("station")));
        boombox.volume = json.has("volume") ? json.get("volume").getAsFloat() : DEFAULT_VOLUME;
        boombox.tunedTo = json.has("tunedTo") ? UUID.fromString(json.get("tunedTo").getAsString()) : null;
        boombox.resumeOwn = json.has("resumeOwn") && json.get("resumeOwn").getAsBoolean();
        return boombox;
    }
}
