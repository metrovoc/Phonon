package com.metrovoc.phonon;

import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioPersistence;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.command.PhononCommand;
import com.metrovoc.phonon.network.packets.SyncAudioResourcesPacket;
import com.metrovoc.phonon.network.packets.SyncStationsPacket;
import com.metrovoc.phonon.platform.PlatformHelper;
import com.metrovoc.phonon.server.AddFlow;
import com.metrovoc.phonon.server.AudioLibrary;
import com.metrovoc.phonon.server.AudioTools;
import com.metrovoc.phonon.server.AudioTransferManager;
import com.metrovoc.phonon.server.ServerAudioStorage;
import com.metrovoc.phonon.server.TitleSearch;
import com.metrovoc.phonon.server.ToolsFlow;
import com.metrovoc.phonon.station.StationManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/** Server lifecycle, shared by every loader; each loader calls these from its own events. */
public final class PhononServerEvents {
    private PhononServerEvents() {}

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        PhononCommand.register(dispatcher);
    }

    /** Before any chunk loads, so speakers tuned to a shared station find it. */
    public static void serverAboutToStart(MinecraftServer server) {
        StationManager.getInstance().load(server);
    }

    public static void serverStarted(MinecraftServer server) {
        Path worldDir = server.getWorldPath(LevelResource.ROOT);

        AudioTools.initialize(server.getServerDirectory());

        ServerAudioStorage storage = ServerAudioStorage.getInstance();
        storage.initialize(worldDir);

        AudioManager manager = AudioManager.getInstance();
        manager.loadResources(AudioPersistence.load(AudioLibrary.dataFile(server)));

        repairMissingDurations(manager, storage);

        ToolsFlow.autoUpdate();

        Phonon.LOGGER.info("Loaded {} audio resources", manager.getAllResources().size());
        AudioLibrary.importMusicFolder(server, (file, failure) -> {
            if (failure == null) {
                Phonon.LOGGER.info("Imported {} from the music folder", file);
            }
        });
    }

    private static void repairMissingDurations(AudioManager manager, ServerAudioStorage storage) {
        int repaired = 0;
        for (AudioResource resource : manager.getAllResources()) {
            if (resource.durationMs() <= 0 && storage.hasAudio(resource.id())) {
                long duration = storage.getDurationMs(resource.id());
                if (duration > 0) {
                    manager.updateResource(resource.withDurationMs(duration));
                    repaired++;
                }
            }
        }
        if (repaired > 0) {
            Phonon.LOGGER.info("Repaired duration for {} audio resources", repaired);
        }
    }

    public static void serverStopping(MinecraftServer server) {
        Path dataFile = AudioLibrary.dataFile(server);

        try {
            AudioPersistence.save(dataFile, AudioManager.getInstance().getAllResources());
            Phonon.LOGGER.info("Saved audio resources");
        } catch (Exception e) {
            Phonon.LOGGER.error("Failed to save audio resources", e);
        }

        ServerAudioStorage.reset();
        AudioTransferManager.reset();
        StationManager.getInstance().save();
        StationManager.reset();
        AddFlow.clear();
    }

    public static void playerJoined(ServerPlayer player) {
        // Only the library is sent here; speakers sync through their block entities.
        var resources = AudioManager.getInstance().getAllResources();
        PlatformHelper platform = PlatformHelper.INSTANCE;
        platform.sendToPlayer(player, new SyncAudioResourcesPacket(resources));
        platform.sendToPlayer(player, SyncStationsPacket.current());
        platform.sendPermissions(player);
        ToolsFlow.noticeIfStale(player);
        Phonon.LOGGER.info("Synced {} audio resources to {}", resources.size(), player.getName().getString());
    }

    public static void playerLeft(ServerPlayer player) {
        AudioTransferManager.getInstance().cancelPlayerTransfers(player.getUUID());
        StationManager.getInstance().boomboxes().playerLeft(player.getUUID());
        TitleSearch.playerLeft(player.getUUID());
    }

    public static void entityJoined(Entity entity) {
        if (!entity.level().isClientSide()) {
            StationManager.getInstance().boomboxes().entityJoined(entity);
        }
    }

    public static void serverTick(MinecraftServer server) {
        AudioTransferManager.getInstance().tick(server);
        StationManager.getInstance().tick(server);
    }
}
