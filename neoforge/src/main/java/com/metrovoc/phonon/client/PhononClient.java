package com.metrovoc.phonon.client;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class PhononClient {
    private PhononClient() {}

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var gameDir = Minecraft.getInstance().gameDirectory.toPath();
            com.metrovoc.phonon.client.AudioCache.getInstance().initialize(gameDir);

            // Register GUI opener
            com.metrovoc.phonon.block.SpeakerBlock.setGuiOpener(pos -> {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(new com.metrovoc.phonon.client.gui.SpeakerScreen(
                    new com.metrovoc.phonon.menu.SpeakerMenu(0, mc.player.getInventory(), pos),
                    mc.player.getInventory(),
                    net.minecraft.network.chat.Component.literal("Speaker")
                ));
            });
        });
    }

    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        com.metrovoc.phonon.client.ClientSpeakerManager.getInstance().clear();
        com.metrovoc.phonon.client.ClientAudioManager.getInstance().setResources(java.util.List.of());
    }
}
