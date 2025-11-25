package com.metrovoc.phonon;

import com.metrovoc.phonon.audio.AudioManager;
import com.metrovoc.phonon.audio.AudioPersistence;
import com.metrovoc.phonon.audio.AudioResource;
import com.metrovoc.phonon.command.PhononCommand;
import com.metrovoc.phonon.network.PhononNetwork;
import com.metrovoc.phonon.registry.PhononRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

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
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        PhononCommand.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        Path dataFile = event.getServer()
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("phonon_audio.json");

        AudioManager.getInstance().loadResources(AudioPersistence.load(dataFile));

        Phonon.LOGGER.info("Loaded {} audio resources", AudioManager.getInstance().getAllResources().size());
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
    }

    private void onPlayerJoin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            // Send all audio resources to joining player
            var resources = AudioManager.getInstance().getAllResources();
            var packet = new com.metrovoc.phonon.network.packets.SyncAudioResourcesPacket(resources);

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);

            Phonon.LOGGER.info("Synced {} audio resources to {}", resources.size(), player.getName().getString());

            // TODO: Send all active Speaker states
        }
    }
}
