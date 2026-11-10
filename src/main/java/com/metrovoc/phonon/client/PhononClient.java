package com.metrovoc.phonon.client;

import com.metrovoc.phonon.Phonon;
import com.metrovoc.phonon.audio.PlaybackState;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.block.SpeakerBlock;
import com.metrovoc.phonon.client.audio.AudioPlayer;
import com.metrovoc.phonon.client.audio.SpeakerSoundInstance;
import com.metrovoc.phonon.client.gui.ClientSearch;
import com.metrovoc.phonon.client.gui.SpeakerScreen;
import com.metrovoc.phonon.item.BoomboxItem;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/** Client lifecycle, shared by every loader; each loader calls these from its own events. */
public final class PhononClient {
    private PhononClient() {}

    /** Once the game window exists. */
    public static void setup() {
        AudioCache.getInstance().initialize(Minecraft.getInstance().gameDirectory.toPath());

        SpeakerBlock.setGuiOpener(receiver -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(new SpeakerScreen(
                new SpeakerMenu(0, mc.player.getInventory(), receiver),
                mc.player.getInventory(),
                Component.translatable(receiver instanceof Receiver.Boombox ? "item.phonon.boombox" : "block.phonon.speaker")
            ));
        });
    }

    /** Says what a boombox is playing, on its tooltip. */
    public static void appendTooltip(ItemStack stack, List<Component> lines) {
        UUID id = stack.getItem() instanceof BoomboxItem ? BoomboxItem.idOf(stack) : null;
        if (id == null) {
            return;
        }
        var state = ClientSpeakerManager.getInstance().getSpeakerState(new Receiver.Boombox(id)).orElse(PlaybackState.STOPPED);
        Component line = ClientAudioManager.getInstance().getResource(state.resourceId())
            .filter(resource -> state.isPlaying())
            .map(resource -> (Component) Component.literal(resource.name()))
            .orElse(Component.translatable("item.phonon.boombox.off"));
        lines.add(line.copy().withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.phonon.boombox.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void tick() {
        PlaybackState.setClockPaused(Minecraft.getInstance().isPaused());
        AudioPlayer.getInstance().tick();
    }

    /** The sound engine has given a sound its OpenAL source. */
    public static void streamStarted(SoundInstance sound, Channel channel) {
        if (sound instanceof SpeakerSoundInstance speaker) {
            speaker.attachChannel(channel);
        }
    }

    public static void loggedOut() {
        PlaybackState.setClockPaused(false);
        ClientSpeakerManager.getInstance().clear();
        ClientStations.getInstance().clear();
        ClientSearch.getInstance().clear();
        ClientAudioManager.getInstance().setResources(List.of());
    }

    /** After F3+T, so speakers that were playing play again. */
    public static void resourcesReloaded() {
        ClientSpeakerManager.getInstance().onResourcesReloaded();
        Phonon.LOGGER.debug("Resource reload detected, restoring speaker playback");
    }
}
