package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.config.NeoForgeClientConfig;
import com.metrovoc.phonon.config.PhononConfigScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class PhononNeoForgeClient {
    private PhononNeoForgeClient() {}

    public static void init(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(PhononClient::setup));
        modBus.addListener((AddClientReloadListenersEvent event) ->
            event.addListener(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "speaker_playback"),
                new SimplePreparableReloadListener<Unit>() {
                    @Override
                    protected Unit prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                        return Unit.INSTANCE;
                    }

                    @Override
                    protected void apply(Unit result, ResourceManager resourceManager, ProfilerFiller profiler) {
                        PhononClient.resourcesReloaded();
                    }
                }));
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
            (IConfigScreenFactory) (container, parent) -> new PhononConfigScreen(parent));
        com.metrovoc.phonon.config.PhononClientConfig.setStore(NeoForgeClientConfig::save);

        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> PhononClient.loggedOut());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> PhononClient.tick());
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent event) ->
            PhononClient.appendTooltip(event.getItemStack(), event.getToolTip()));
        NeoForge.EVENT_BUS.addListener((PlayStreamingSourceEvent event) ->
            PhononClient.streamStarted(event.getSound(), event.getChannel()));
    }
}
