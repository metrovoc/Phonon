package com.metrovoc.phonon.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * Steel volume slider. Reports during a drag for real-time feedback, and on release for persistence.
 */
final class VolumeSlider extends GuiPort.Control {
    private static final int HANDLE_WIDTH = 6;

    private final Consumer<Float> onChange;
    private final Runnable onCommit;
    private float value;
    private boolean dragging;

    VolumeSlider(int x, int y, int width, int height, float value, Consumer<Float> onChange, Runnable onCommit) {
        super(x, y, width, height, Component.empty());
        this.onChange = onChange;
        this.onCommit = onCommit;
        setValue(value);
    }

    /** Follows changes from other players, unless this one is holding the handle. */
    void follow(float volume) {
        if (!dragging) {
            setValue(volume);
        }
    }

    private void setValue(float volume) {
        value = Mth.clamp(volume, 0.0f, 1.0f);
        setMessage(Component.translatable("gui.phonon.speaker.volume", Math.round(value * 100)));
    }

    private void moveTo(double mouseX) {
        setValue((float) ((mouseX - getX() - HANDLE_WIDTH / 2.0) / (getWidth() - HANDLE_WIDTH)));
        onChange.accept(value);
    }

    @Override
    void draw(GuiPort.Canvas canvas, int mouseX, int mouseY) {
        SteelStyle.plate(canvas, getX(), getY(), getWidth(), getHeight(), SteelStyle.WELL, false);
        canvas.fill(getX(), getY(), getX() + getWidth(), getY() + 1, SteelStyle.SHADOW);
        int handle = getX() + Math.round(value * (getWidth() - HANDLE_WIDTH));
        canvas.fill(getX(), getY() + getHeight() - 1, handle, getY() + getHeight(), active ? SteelStyle.PLAYING : SteelStyle.TEXT_OFF);
        int face = !active ? SteelStyle.STEEL_DISABLED : dragging || isMouseOver(mouseX, mouseY) ? SteelStyle.STEEL_HOVER : SteelStyle.STEEL;
        SteelStyle.plate(canvas, handle, getY(), HANDLE_WIDTH, getHeight(), face, active);
        canvas.text(getMessage(), getX() + (getWidth() - canvas.font.width(getMessage())) / 2,
            getY() + (getHeight() - canvas.font.lineHeight) / 2 + 1, SteelStyle.TEXT);
    }

    @Override
    boolean pressed(double mouseX, double mouseY, boolean doubleClick) {
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
            GuiPort.playClick();
            onCommit.run();
        }
    }
}
