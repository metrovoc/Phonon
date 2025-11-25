package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Constants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class PhononClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            AudioCache.getInstance().initialize(Minecraft.getInstance().gameDirectory.toPath());

            // Register GUI opener
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
}

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
class PhononClientTick {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // SoundManager handles audio lifecycle automatically
        // No manual tick needed when using proper SoundInstance
    }
}
