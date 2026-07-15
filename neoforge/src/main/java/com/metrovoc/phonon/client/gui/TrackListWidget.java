package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.audio.AudioResource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Scrollable list of audio tracks.
 * Uses Minecraft's ObjectSelectionList for native scrolling behavior.
 */
public class TrackListWidget extends ObjectSelectionList<TrackListWidget.Entry> {
    private final Consumer<AudioResource> onSelect;
    private final Consumer<AudioResource> onPlay;

    public TrackListWidget(Minecraft mc, int width, int height, int y, int itemHeight,
                           Consumer<AudioResource> onSelect, Consumer<AudioResource> onPlay) {
        super(mc, width, height, y, itemHeight);
        this.onSelect = onSelect;
        this.onPlay = onPlay;
    }

    public void updateEntries(List<AudioResource> resources) {
        clearEntries();
        for (AudioResource resource : resources) {
            addEntry(new Entry(resource));
        }
    }

    @Override
    public int getRowWidth() {
        return this.width - 12;
    }

    @Override
    protected int scrollBarX() {
        return this.getX() + this.width - 6;
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> {
        private final AudioResource resource;
        private long lastClickTime;

        public Entry(AudioResource resource) {
            this.resource = resource;
        }

        public AudioResource getResource() {
            return resource;
        }

        @Override
        public Component getNarration() {
            return Component.literal(resource.name());
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            int textColor = isSelected() ? 0xFFFFFF : (hovered ? 0xE0E0E0 : 0xA0A0A0);

            if (hovered || isSelected()) {
                graphics.fill(left, top, left + width, top + height,
                    isSelected() ? 0x40FFFFFF : 0x20FFFFFF);
            }

            // Track name
            String name = resource.name();
            if (minecraft.font.width(name) > width - 50) {
                name = minecraft.font.plainSubstrByWidth(name, width - 55) + "...";
            }
            graphics.drawString(minecraft.font, name, left + 4, top + 4, textColor);

            // Duration (right-aligned)
            String duration = formatDuration(resource.durationMs());
            int durationWidth = minecraft.font.width(duration);
            graphics.drawString(minecraft.font, duration, left + width - durationWidth - 4, top + 4, 0x808080);

        }

        private boolean isSelected() {
            return TrackListWidget.this.getSelected() == this;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                long now = System.currentTimeMillis();
                TrackListWidget.this.setSelected(this);
                onSelect.accept(resource);

                // Double-click to play
                if (now - lastClickTime < 400) {
                    onPlay.accept(resource);
                }
                lastClickTime = now;
                return true;
            }
            return false;
        }

        private String formatDuration(long ms) {
            if (ms < 0) return "--:--";
            long seconds = ms / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
        }
    }
}
