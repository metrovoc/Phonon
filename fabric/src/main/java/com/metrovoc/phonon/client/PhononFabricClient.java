package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.config.FabricConfig;
import com.metrovoc.phonon.config.PhononClientConfig;
import com.metrovoc.phonon.network.PhononFabricNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
        PhononFabricNetwork.registerClientHandlers();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> PhononClient.setup());
        ClientTickEvents.END_CLIENT_TICK.register(client -> PhononClient.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PhononClient.loggedOut());
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> PhononClient.appendTooltip(stack, lines));
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "speaker_playback"),
            new SimplePreparableReloadListener<Unit>() {
                @Override
                protected Unit prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                    return Unit.INSTANCE;
                }

                @Override
                protected void apply(Unit result, ResourceManager resourceManager, ProfilerFiller profiler) {
                    PhononClient.resourcesReloaded();
                }
            });
    }
}
