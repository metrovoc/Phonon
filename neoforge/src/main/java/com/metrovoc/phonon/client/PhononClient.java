package com.metrovoc.phonon.client;

import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.item.BoomboxItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;

import java.util.UUID;

public final class PhononClient {
    private PhononClient() {}

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            var gameDir = Minecraft.getInstance().gameDirectory.toPath();
            com.metrovoc.phonon.client.AudioCache.getInstance().initialize(gameDir);

            net.minecraft.client.renderer.item.ItemProperties.register(com.metrovoc.phonon.registry.PhononRegistry.BOOMBOX_ITEM.get(),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.metrovoc.phonon.Constants.MOD_ID, "playing"),
                (stack, level, entity, seed) -> stack.has(com.metrovoc.phonon.registry.PhononRegistry.BOOMBOX_PLAYING.get()) ? 1 : 0);

            // Register GUI opener
            com.metrovoc.phonon.block.SpeakerBlock.setGuiOpener(receiver -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(new com.metrovoc.phonon.client.gui.SpeakerScreen(
                    new com.metrovoc.phonon.menu.SpeakerMenu(0, mc.player.getInventory(), receiver),
                    mc.player.getInventory(),
                    net.minecraft.network.chat.Component.translatable(
                        receiver instanceof Receiver.Boombox ? "item.phonon.boombox" : "block.phonon.speaker")
                ));
            });
        });
    }

    /** Says what a boombox is playing, on its tooltip. */
    public static void onTooltip(ItemTooltipEvent event) {
        UUID id = event.getItemStack().getItem() instanceof BoomboxItem ? BoomboxItem.idOf(event.getItemStack()) : null;
        if (id == null) {
            return;
        }
        var state = ClientSpeakerManager.getInstance().getSpeakerState(new Receiver.Boombox(id)).orElse(PlaybackState.STOPPED);
        Component line = ClientAudioManager.getInstance().getResource(state.resourceId())
            .filter(resource -> state.isPlaying())
            .map(resource -> (Component) Component.literal(resource.name()))
            .orElse(Component.translatable("item.phonon.boombox.off"));
        event.getToolTip().add(line.copy().withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable("item.phonon.boombox.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        com.metrovoc.phonon.audio.PlaybackState.setClockPaused(Minecraft.getInstance().isPaused());
        com.metrovoc.phonon.client.audio.AudioPlayer.getInstance().tick();
    }

    public static void onStreamStart(PlayStreamingSourceEvent event) {
        if (event.getSound() instanceof com.metrovoc.phonon.client.audio.SpeakerSoundInstance sound) {
            sound.attachChannel(event.getChannel());
        }
    }

    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        com.metrovoc.phonon.audio.PlaybackState.setClockPaused(false);
        com.metrovoc.phonon.client.ClientSpeakerManager.getInstance().clear();
        com.metrovoc.phonon.client.ClientStations.getInstance().clear();
        com.metrovoc.phonon.client.gui.ClientSearch.getInstance().clear();
        com.metrovoc.phonon.client.ClientAudioManager.getInstance().setResources(java.util.List.of());
    }
}
