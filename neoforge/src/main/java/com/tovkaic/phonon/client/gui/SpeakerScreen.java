package com.tovkaic.phonon.client.gui;

import com.tovkaic.phonon.audio.AudioResource;
import com.tovkaic.phonon.client.ClientAudioManager;
import com.tovkaic.phonon.menu.SpeakerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Speaker GUI screen.
 *
 * MVP: Simple list of audio resources with play buttons.
 * TODO: Add search, volume slider, pagination.
 */
public class SpeakerScreen extends AbstractContainerScreen<SpeakerMenu> {
    private static final int LIST_ITEM_HEIGHT = 24;
    private List<AudioResource> resources;
    private int scrollOffset = 0;

    public SpeakerScreen(SpeakerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 256;
        this.imageHeight = 200;
        this.resources = ClientAudioManager.getInstance().getAllResources();
    }

    @Override
    protected void init() {
        super.init();

        // Stop button
        addRenderableWidget(Button.builder(
            Component.literal("Stop"),
            btn -> menu.stopAudio()
        ).bounds(leftPos + 10, topPos + imageHeight - 30, 60, 20).build());

        // Refresh list button
        addRenderableWidget(Button.builder(
            Component.literal("Refresh"),
            btn -> {
                resources = ClientAudioManager.getInstance().getAllResources();
            }
        ).bounds(leftPos + 80, topPos + imageHeight - 30, 60, 20).build());

        // Play buttons for each audio resource (MVP: show first 5)
        int maxVisible = Math.min(5, resources.size());
        for (int i = 0; i < maxVisible; i++) {
            final int index = i;
            AudioResource resource = resources.get(i);

            addRenderableWidget(Button.builder(
                Component.literal("▶ " + resource.name()),
                btn -> menu.playAudio(resource.id(), 1.0f)
            ).bounds(
                leftPos + 10,
                topPos + 30 + i * LIST_ITEM_HEIGHT,
                imageWidth - 20,
                20
            ).build());
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Simple background
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Title
        graphics.drawString(
            font,
            "Speaker Control",
            leftPos + 10,
            topPos + 10,
            0xFFFFFF
        );

        // Resource count
        graphics.drawString(
            font,
            resources.size() + " audio files",
            leftPos + 10,
            topPos + imageHeight - 50,
            0xAAAAAA
        );
    }
}
