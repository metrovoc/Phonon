package com.metrovoc.phonon.neoforge;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.PhononServerEvents;

import com.metrovoc.phonon.network.packets.SyncPermissionsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@Mod(Constants.MOD_ID)
public class PhononNeoForge {

    public PhononNeoForge(IEventBus modBus, ModContainer modContainer) {
        Phonon.init();
        PhononRegistry.register(modBus);

        // A world's settings, sent to players as they join; 26.3 renamed the type, and the name keeps the old file name.
        //? if >=26.3 {
        modContainer.registerConfig(ModConfig.Type.SYNCED, NeoForgeServerConfig.SPEC, "phonon-server.toml");
        //?} else
        /*modContainer.registerConfig(ModConfig.Type.SERVER, NeoForgeServerConfig.SPEC);*/
        modContainer.registerConfig(ModConfig.Type.CLIENT, NeoForgeClientConfig.SPEC);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            PhononNeoForgeClient.init(modBus, modContainer);
        }

        modBus.addListener(PhononRegistry::addToCreativeTabs);
        modBus.addListener(PhononNetwork::register);
        modBus.addListener(PhononNeoForge::onConfigLoad);

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
            PhononServerEvents.registerCommands(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) ->
            PhononServerEvents.serverAboutToStart(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
            PhononServerEvents.serverStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
            PhononServerEvents.serverStopping(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PhononServerEvents.playerJoined(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PhononServerEvents.playerLeft(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
            PhononServerEvents.serverTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) ->
            PhononServerEvents.entityJoined(event.getEntity()));
    }

    private static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == NeoForgeServerConfig.SPEC) {
            NeoForgeServerConfig.bind();
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                PacketDistributor.sendToAllPlayers(SyncPermissionsPacket.current());
            }
            Phonon.LOGGER.info("Server config loaded/reloaded");
        } else if (event.getConfig().getSpec() == NeoForgeClientConfig.SPEC) {
            NeoForgeClientConfig.bind();
            Phonon.LOGGER.info("Client config loaded/reloaded");
        }
    }
}
