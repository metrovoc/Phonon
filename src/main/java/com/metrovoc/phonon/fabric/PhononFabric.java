package com.metrovoc.phonon.fabric;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.PhononServerEvents;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;

public final class PhononFabric implements ModInitializer {
    private static MinecraftServer server;

    /** The running server, or null. */
    public static MinecraftServer server() {
        return server;
    }

    @Override
    public void onInitialize() {
        Phonon.init();
        PhononFabricRegistry.register();
        PhononFabricNetwork.registerTypes();

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
            PhononServerEvents.registerCommands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(starting -> {
            server = starting;
            FabricConfig.loadServer();
            PhononServerEvents.serverAboutToStart(starting);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(PhononServerEvents::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(PhononServerEvents::serverStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> PhononServerEvents.playerJoined(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, left) -> PhononServerEvents.playerLeft(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(PhononServerEvents::serverTick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> PhononServerEvents.entityJoined(entity));
    }
}
