package com.metrovoc.phonon.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Draggable progress bar for audio playback.
 */
public class ProgressSlider extends AbstractWidget {
    private static final int BAR_HEIGHT = 4;
    private static final int KNOB_SIZE = 8;

    private final Consumer<Float> onSeek;
    private float progress = 0f;
    private long durationMs = -1;
    private long positionMs = 0;
    private boolean dragging = false;

    public ProgressSlider(int x, int y, int width, int height, Consumer<Float> onSeek) {
        super(x, y, width, height, Component.empty());
        this.onSeek = onSeek;
    }

    public void update(long positionMs, long durationMs) {
        if (!dragging) {
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.progress = durationMs > 0 ? Math.min(1f, (float) positionMs / durationMs) : 0f;
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int barY = getY() + (height - BAR_HEIGHT) / 2;

        // Background track
        graphics.fill(getX(), barY, getX() + width, barY + BAR_HEIGHT, 0x40FFFFFF);

        // Progress fill
        if (durationMs > 0) {
            int progressWidth = (int) (width * progress);
            graphics.fill(getX(), barY, getX() + progressWidth, barY + BAR_HEIGHT, 0xFF40FF40);

            // Knob
            int knobX = getX() + progressWidth - KNOB_SIZE / 2;
            int knobY = getY() + (height - KNOB_SIZE) / 2;
            graphics.fill(knobX, knobY, knobX + KNOB_SIZE, knobY + KNOB_SIZE,
                dragging || isHovered ? 0xFFFFFFFF : 0xFFCCCCCC);
        }

        // Time text below
        String timeText = formatTime(positionMs) + " / " + (durationMs > 0 ? formatTime(durationMs) : "--:--");
        int textWidth = net.minecraft.client.Minecraft.getInstance().font.width(timeText);
        graphics.text(net.minecraft.client.Minecraft.getInstance().font,
            timeText, getX() + (width - textWidth) / 2, getY() + height - 8, 0xA0A0A0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0 && durationMs > 0 && isMouseOver(event.x(), event.y())) {
            dragging = true;
            updateFromMouse(event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && event.button() == 0) {
            updateFromMouse(event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == 0) {
            dragging = false;
            onSeek.accept(progress);
            return true;
        }
        return false;
    }

    private void updateFromMouse(double mouseX) {
        float newProgress = (float) Math.max(0, Math.min(1, (mouseX - getX()) / width));
        this.progress = newProgress;
        this.positionMs = (long) (newProgress * durationMs);
    }

    private String formatTime(long ms) {
        if (ms < 0) return "--:--";
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
