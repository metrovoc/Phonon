package com.metrovoc.phonon.fabric;

import com.metrovoc.phonon.client.PhononClient;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.config.PhononClientConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
//? if >=1.21.9
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;

public final class PhononFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricConfig.loadClient();
        PhononClientConfig.setStore(FabricConfig::saveClient);
        PhononFabricClientNetwork.register();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> PhononClient.setup());
        //? if <1.21.4 {
        /*// Before 1.21.4 an item model picks its variant through a registered property.
        net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry.register(
            PhononFabricRegistry.boombox, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "playing"),
            (stack, level, entity, seed) -> com.metrovoc.phonon.item.BoomboxItem.isShownPlaying(stack) ? 1 : 0);
        *///?}
        ClientTickEvents.END_CLIENT_TICK.register(client -> PhononClient.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PhononClient.loggedOut());
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> PhononClient.appendTooltip(stack, lines));
        Identifier reloadId = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "speaker_playback");
        //? if >=1.21.9 {
        var reloaded = new SimplePreparableReloadListener<Unit>() {
            @Override
            protected Unit prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return Unit.INSTANCE;
            }

            @Override
            protected void apply(Unit result, ResourceManager resourceManager, ProfilerFiller profiler) {
                PhononClient.resourcesReloaded();
            }
        };
        //?}
        //? if >=26.1 {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(reloadId, reloaded);
        //?} else if >=1.21.9 {
        /*ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(reloadId, reloaded);
        *///?} else {
        /*net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                @Override
                public Identifier getFabricId() {
                    return reloadId;
                }

                @Override
                public void onResourceManagerReload(ResourceManager resourceManager) {
                    PhononClient.resourcesReloaded();
                }
            });
        *///?}
    }
}
