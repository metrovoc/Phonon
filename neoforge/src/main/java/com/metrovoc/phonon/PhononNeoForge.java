package com.metrovoc.phonon;

import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioPersistence;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.client.ClientSpeakerManager;
import com.metrovoc.phonon.client.PhononClient;
import com.metrovoc.phonon.platform.PlatformHelper;
import com.metrovoc.phonon.command.PhononCommand;
import com.metrovoc.phonon.config.ConfigScreenFactory;
import com.metrovoc.phonon.config.NeoForgeClientConfig;
import com.metrovoc.phonon.config.NeoForgeServerConfig;
import com.metrovoc.phonon.network.PhononNetwork;
import com.metrovoc.phonon.network.packets.SyncAudioResourcesPacket;
import com.metrovoc.phonon.registry.PhononRegistry;
import com.metrovoc.phonon.server.AudioTransferManager;
import com.metrovoc.phonon.server.ServerAudioStorage;
import com.metrovoc.phonon.server.ServerSpeakerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;

@Mod(Constants.MOD_ID)
public class PhononNeoForge {

    private long serverTickCount = 0;

    public PhononNeoForge(IEventBus modBus, ModContainer modContainer) {
        Phonon.init();
        PhononRegistry.register(modBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, NeoForgeServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, NeoForgeClientConfig.SPEC);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modBus.addListener(PhononClient::onClientSetup);
            NeoForge.EVENT_BUS.addListener(PhononClient::onClientLogout);
            ConfigScreenFactory.create().ifPresent(factory ->
                modContainer.registerExtensionPoint(IConfigScreenFactory.class, factory));
            modBus.addListener(this::onRegisterClientReloadListeners);
        }

        modBus.addListener(PhononNetwork::register);
        modBus.addListener(this::onConfigLoad);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
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

        serverTickCount = 0;

        // 设置 speaker 停止回调
        ServerSpeakerManager.getInstance().setStopCallback((level, pos, speaker) -> {
            long now = PlaybackState.nowMs();
            var packet = com.metrovoc.phonon.network.packets.SyncSpeakerStatePacket.snapshot(
                pos, PlaybackState.STOPPED, speaker.getVolume(), now
            );
            PlatformHelper.INSTANCE.sendToAllTracking(level, pos, packet);
        });

        Phonon.LOGGER.info("Loaded {} audio resources", manager.getAllResources().size());
    }

    private void repairMissingDurations(AudioManager manager, ServerAudioStorage storage) {
        int repaired = 0;
        for (AudioResource resource : manager.getAllResources()) {
            if (resource.durationMs() <= 0 && storage.hasAudio(resource.id())) {
                long duration = storage.getDurationMs(resource.id());
                if (duration > 0) {
                    manager.updateResource(new AudioResource(
                        resource.id(), resource.name(), resource.url(), duration, resource.sizeBytes()
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

        ServerAudioStorage.reset();
        AudioTransferManager.reset();
        ServerSpeakerManager.reset();
    }

    private void onPlayerJoin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 只同步音频资源列表，speaker 状态由 BlockEntity 自动同步
            var resources = AudioManager.getInstance().getAllResources();
            var packet = new SyncAudioResourcesPacket(resources);
            PacketDistributor.sendToPlayer(player, packet);
            Phonon.LOGGER.info("Synced {} audio resources to {}", resources.size(), player.getName().getString());
        }
    }

    private void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AudioTransferManager.getInstance().cancelPlayerTransfers(player.getUUID());
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        serverTickCount++;
        AudioTransferManager.getInstance().tick(event.getServer());
        ServerSpeakerManager.getInstance().tick(event.getServer(), serverTickCount);
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

    /**
     * 注册客户端资源重载监听器 (F3+T 恢复)。
     */
    private void onRegisterClientReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "speaker_playback"),
            new SimplePreparableReloadListener<Unit>() {
                @Override
                protected Unit prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                    return Unit.INSTANCE;
                }

                @Override
                protected void apply(Unit result, ResourceManager resourceManager, ProfilerFiller profiler) {
                    ClientSpeakerManager.getInstance().onResourcesReloaded();
                    Phonon.LOGGER.debug("Resource reload detected, restoring speaker playback");
                }
            });
    }
}
