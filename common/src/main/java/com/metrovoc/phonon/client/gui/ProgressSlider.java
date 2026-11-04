package com.metrovoc.phonon.client.gui;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Draggable progress bar for audio playback.
 */
final class ProgressSlider extends GuiPort.Control {
    private static final int BAR_HEIGHT = 4;
    private static final int KNOB_SIZE = 8;
    private static final int BAR_Y = 3;

    private final Consumer<Float> onSeek;
    private float progress;
    private long durationMs = -1;
    private long positionMs;
    private boolean dragging;

    ProgressSlider(int x, int y, int width, int height, Consumer<Float> onSeek) {
        super(x, y, width, height, Component.empty());
        this.onSeek = onSeek;
    }

    void update(long positionMs, long durationMs) {
        if (!dragging) {
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.progress = durationMs > 0 ? Math.min(1f, (float) positionMs / durationMs) : 0f;
        }
    }

    @Override
    void draw(GuiPort.Canvas canvas, int mouseX, int mouseY) {
        int barY = getY() + BAR_Y;
        canvas.fill(getX(), barY, getX() + getWidth(), barY + BAR_HEIGHT, 0x40FFFFFF);

        if (durationMs > 0) {
            int filled = (int) (getWidth() * progress);
            canvas.fill(getX(), barY, getX() + filled, barY + BAR_HEIGHT, SteelStyle.PLAYING);
            int knobX = Math.max(getX(), Math.min(getX() + filled - KNOB_SIZE / 2, getX() + getWidth() - KNOB_SIZE));
            int face = dragging || isMouseOver(mouseX, mouseY) ? SteelStyle.STEEL_HOVER : SteelStyle.STEEL;
            SteelStyle.plate(canvas, knobX, barY + BAR_HEIGHT / 2 - KNOB_SIZE / 2, KNOB_SIZE, KNOB_SIZE, face, true);
        }

        String time = SteelStyle.time(positionMs) + " / " + SteelStyle.time(durationMs);
        canvas.text(time, getX() + (getWidth() - canvas.font.width(time)) / 2,
            getY() + getHeight() - canvas.font.lineHeight, SteelStyle.TEXT_DIM);
    }

    @Override
    boolean pressed(double mouseX, double mouseY, boolean doubleClick) {
        if (durationMs <= 0) {
            return false;
        }
        dragging = true;
        moveTo(mouseX);
        return true;
    }

    @Override
    void dragged(double mouseX, double mouseY) {
        if (dragging) {
            moveTo(mouseX);
        }
    }

    @Override
    void released() {
        if (dragging) {
            dragging = false;
            onSeek.accept(progress);
        }
    }

    private void moveTo(double mouseX) {
        progress = (float) Math.max(0, Math.min(1, (mouseX - getX()) / getWidth()));
        positionMs = (long) (progress * durationMs);
    }
}
