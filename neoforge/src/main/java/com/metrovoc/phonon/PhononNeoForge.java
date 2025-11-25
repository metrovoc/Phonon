package com.metrovoc.phonon;

import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioPersistence;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.command.PhononCommand;
import com.metrovoc.phonon.config.ConfigScreenFactory;
import com.metrovoc.phonon.config.NeoForgeClientConfig;
import com.metrovoc.phonon.config.NeoForgeServerConfig;
import com.metrovoc.phonon.network.PhononNetwork;
import com.metrovoc.phonon.network.packets.SyncAudioResourcesPacket;
import com.metrovoc.phonon.registry.PhononRegistry;
import com.metrovoc.phonon.server.AudioTransferManager;
import com.metrovoc.phonon.server.FFmpegHelper;
import com.metrovoc.phonon.server.ServerAudioStorage;
import com.metrovoc.phonon.webui.PhononWebServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;
import java.util.UUID;

@Mod(Constants.MOD_ID)
public class PhononNeoForge {

    private net.minecraft.server.MinecraftServer currentServer;

    public PhononNeoForge(IEventBus modBus, ModContainer modContainer) {
        Phonon.init();
        PhononRegistry.register(modBus);

        // Register configs
        modContainer.registerConfig(ModConfig.Type.SERVER, NeoForgeServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, NeoForgeClientConfig.SPEC);

        // Register config screen (Cloth Config, optional) - client only
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ConfigScreenFactory.create().ifPresent(factory ->
                modContainer.registerExtensionPoint(IConfigScreenFactory.class, factory));
        }

        modBus.addListener(PhononNetwork::register);
        modBus.addListener(this::onConfigLoad);

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

        ServerAudioStorage storage = ServerAudioStorage.getInstance();
        storage.initialize(worldDir);

        Path dataFile = worldDir.resolve("phonon_audio.json");
        AudioManager manager = AudioManager.getInstance();
        manager.loadResources(AudioPersistence.load(dataFile));

        repairMissingDurations(manager, storage);

        Phonon.LOGGER.info("Loaded {} audio resources", manager.getAllResources().size());

        // Start WebUI
        currentServer = event.getServer();
        PhononWebServer.launch();
        if (PhononWebServer.getInstance() != null) {
            PhononWebServer.getInstance().setOnAddTrack(this::handleWebUiAddTrack);
            PhononWebServer.getInstance().setOnDeleteTrack(this::handleWebUiDeleteTrack);
        }
    }

    private void handleWebUiAddTrack(PhononWebServer.TrackRequest req) {
        if (currentServer == null) return;

        String url = req.url();
        String name = req.name();

        // Validate URL format (same as PhononCommand)
        if (!url.toLowerCase().endsWith(".ogg")) {
            Phonon.LOGGER.warn("WebUI: Only .ogg files supported, got: {}", url);
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Phonon.LOGGER.warn("WebUI: Invalid URL: {}", url);
            return;
        }

        UUID resourceId = UUID.randomUUID();
        ServerAudioStorage storage = ServerAudioStorage.getInstance();
        AudioManager manager = AudioManager.getInstance();

        PhononWebServer webServer = PhononWebServer.getInstance();
        if (webServer != null) {
            webServer.updateDownloadProgress(resourceId, 0, "Downloading " + name + "...");
        }

        storage.downloadAndStore(resourceId, url)
            .thenAccept(success -> {
                currentServer.execute(() -> {
                    if (webServer != null) {
                        webServer.removeDownloadProgress(resourceId);
                    }

                    if (success) {
                        long durationMs = storage.getDurationMs(resourceId);
                        AudioResource finalResource = new AudioResource(resourceId, name, url, durationMs);
                        manager.addResource(finalResource);
                        broadcastResourceList();
                        Phonon.LOGGER.info("WebUI: Added audio resource: {}", name);
                    } else {
                        Phonon.LOGGER.error("WebUI: Failed to download: {}", name);
                    }
                });
            });
    }

    private void handleWebUiDeleteTrack(UUID id) {
        if (currentServer == null) return;
        currentServer.execute(() -> {
            ServerAudioStorage.getInstance().deleteAudio(id);
            broadcastResourceList();
            Phonon.LOGGER.info("WebUI: Deleted audio resource: {}", id);
        });
    }

    private void broadcastResourceList() {
        if (currentServer == null) return;
        var packet = new SyncAudioResourcesPacket(AudioManager.getInstance().getAllResources());
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, packet);
        }
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

        ServerAudioStorage.getInstance().shutdown();
        AudioTransferManager.getInstance().shutdown();
        PhononWebServer.shutdown();
        currentServer = null;
    }

    private void onPlayerJoin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var resources = AudioManager.getInstance().getAllResources();
            var packet = new SyncAudioResourcesPacket(resources);

            PacketDistributor.sendToPlayer(player, packet);

            Phonon.LOGGER.info("Synced {} audio resources to {}", resources.size(), player.getName().getString());
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        AudioTransferManager.getInstance().tick(event.getServer());
    }

    private void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == NeoForgeServerConfig.SPEC) {
            NeoForgeServerConfig.bind();
            Phonon.LOGGER.info("Server config loaded/reloaded");
        } else if (event.getConfig().getSpec() == NeoForgeClientConfig.SPEC) {
            NeoForgeClientConfig.bind();
            Phonon.LOGGER.info("Client config loaded/reloaded");
        }
    }
}
