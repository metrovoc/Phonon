package com.metrovoc.phonon;

import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioPersistence;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.command.PhononCommand;
import com.metrovoc.phonon.network.PhononNetwork;
import com.metrovoc.phonon.network.packets.SyncAudioResourcesPacket;
import com.metrovoc.phonon.registry.PhononRegistry;
import com.metrovoc.phonon.server.AudioTransferManager;
import com.metrovoc.phonon.server.FFmpegHelper;
import com.metrovoc.phonon.server.ServerAudioStorage;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;

@Mod(Constants.MOD_ID)
public class PhononNeoForge {

    public PhononNeoForge(IEventBus modBus) {
        Phonon.init();
        PhononRegistry.register(modBus);

        modBus.addListener(PhononNetwork::register);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        PhononCommand.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        Path worldDir = event.getServer()
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);

        // Initialize server audio storage
        ServerAudioStorage storage = ServerAudioStorage.getInstance();
        storage.initialize(worldDir);

        // Load audio resources
        Path dataFile = worldDir.resolve("phonon_audio.json");
        AudioManager manager = AudioManager.getInstance();
        manager.loadResources(AudioPersistence.load(dataFile));

        // Repair resources with missing duration
        repairMissingDurations(manager, storage);

        Phonon.LOGGER.info("Loaded {} audio resources", manager.getAllResources().size());
    }

    private void repairMissingDurations(AudioManager manager, ServerAudioStorage storage) {
        if (!FFmpegHelper.isAvailable()) {
            return;
        }

        int repaired = 0;
        for (AudioResource resource : manager.getAllResources()) {
            if (resource.durationMs() <= 0 && storage.hasAudio(resource.id())) {
                long duration = storage.getDurationMs(resource.id());
                if (duration > 0) {
                    manager.updateResource(new AudioResource(
                        resource.id(), resource.name(), resource.url(), duration
                    ));
                    repaired++;
                }
            }
        }
        if (repaired > 0) {
            Phonon.LOGGER.info("Repaired duration for {} audio resources", repaired);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        Path dataFile = event.getServer()
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("phonon_audio.json");

        try {
            AudioPersistence.save(dataFile, AudioManager.getInstance().getAllResources());
            Phonon.LOGGER.info("Saved audio resources");
        } catch (Exception e) {
            Phonon.LOGGER.error("Failed to save audio resources", e);
        }

        // Shutdown managers
        ServerAudioStorage.getInstance().shutdown();
        AudioTransferManager.getInstance().shutdown();
    }

    private void onPlayerJoin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Send all audio resources to joining player
            var resources = AudioManager.getInstance().getAllResources();
            var packet = new SyncAudioResourcesPacket(resources);

            PacketDistributor.sendToPlayer(player, packet);

            Phonon.LOGGER.info("Synced {} audio resources to {}", resources.size(), player.getName().getString());
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        // Process audio transfers with flow control
        AudioTransferManager.getInstance().tick(event.getServer());
    }
}
