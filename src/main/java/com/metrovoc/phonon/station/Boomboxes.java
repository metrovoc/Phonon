package com.metrovoc.phonon.station;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.item.BoomboxItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Finds every boombox each tick, in inventories, on cursors and on the ground, and keeps its sound following it. */
public final class Boomboxes {
    private final StationManager stations;
    private final Map<UUID, Boombox> all = new HashMap<>();
    private final Set<ItemEntity> onGround = new HashSet<>();
    private final Map<UUID, Entity> found = new HashMap<>();

    Boomboxes(StationManager stations) {
        this.stations = stations;
    }

    @Nullable
    public Boombox get(@Nullable UUID id) {
        return id == null ? null : all.get(id);
    }

    /** A boombox left on the ground stays there rather than despawning with its music. */
    public void entityJoined(Entity entity) {
        if (entity instanceof ItemEntity item && item.getItem().getItem() instanceof BoomboxItem) {
            item.setUnlimitedLifetime();
            onGround.add(item);
        }
    }

    public void playerLeft(UUID player) {
        all.values().forEach(boombox -> boombox.playerLeft(player));
    }

    void tick(MinecraftServer server) {
        found.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                see(player.getInventory().getItem(slot), player);
            }
            see(player.containerMenu.getCarried(), player);
        }
        onGround.removeIf(Entity::isRemoved);
        for (ItemEntity item : onGround) {
            see(item.getItem(), item);
        }
        for (Boombox boombox : all.values()) {
            boombox.tick(server, found.get(boombox.id()));
        }
    }

    private void see(ItemStack stack, Entity holder) {
        if (!(stack.getItem() instanceof BoomboxItem)) {
            return;
        }
        UUID id = BoomboxItem.assignId(stack);
        Boombox boombox = all.computeIfAbsent(id, key -> new Boombox(key, stations, Station.ownedBySpeaker()));
        found.putIfAbsent(id, holder);
        if (BoomboxItem.showPlaying(stack, boombox.playing()) && holder instanceof ItemEntity item) {
            item.setItem(stack.copy());
        }
    }

    /** Returns whether a boombox is on this station's own, so it needs saving. */
    boolean stationChanged(Station station) {
        boolean own = false;
        for (Boombox boombox : all.values()) {
            own |= boombox.ownStation() == station;
            if (boombox.present() && boombox.station() == station) {
                boombox.sendStateToHearing();
            }
        }
        return own;
    }

    void stationDeleted(UUID station) {
        for (Boombox boombox : all.values()) {
            if (station.equals(boombox.tunedTo())) {
                boombox.tune(null);
            }
        }
    }

    /** Boomboxes nobody has right now aren't among the running stations. */
    void forgetTrack(UUID track) {
        for (Boombox boombox : all.values()) {
            if (!boombox.present()) {
                boombox.forgetWhileAway(track);
            }
        }
    }

    void load(JsonArray json) {
        for (JsonElement element : json) {
            try {
                Boombox boombox = Boombox.fromJson(element.getAsJsonObject(), stations);
                all.put(boombox.id(), boombox);
            } catch (RuntimeException invalid) {
                Phonon.LOGGER.warn("Skipping a malformed boombox");
            }
        }
    }

    JsonArray toJson() {
        JsonArray json = new JsonArray();
        all.values().forEach(boombox -> json.add(boombox.toJson()));
        return json;
    }
}
