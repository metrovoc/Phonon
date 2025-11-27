package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.client.audio.PhononAudioEngine;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class PhononClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var gameDir = Minecraft.getInstance().gameDirectory.toPath();
            com.metrovoc.phonon.client.AudioCache.getInstance().initialize(gameDir);
            com.metrovoc.phonon.client.AudioReceiver.getInstance().initialize(gameDir);

            PhononAudioEngine.getInstance().init();

            NeoForge.EVENT_BUS.addListener(PhononClient::onClientTick);

            com.metrovoc.phonon.block.SpeakerBlock.setGuiOpener(pos -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(new com.metrovoc.phonon.client.gui.SpeakerScreen(
                    new com.metrovoc.phonon.menu.SpeakerMenu(0, mc.player.getInventory(), pos),
                    mc.player.getInventory(),
                    net.minecraft.network.chat.Component.literal("Speaker")
                ));
            });
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        PhononAudioEngine.getInstance().tick();
    }
}
