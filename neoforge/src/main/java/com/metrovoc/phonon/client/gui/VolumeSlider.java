package com.metrovoc.phonon.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * Volume slider widget.
 * Range: 0.0 to 1.0, displayed as 0% to 100%.
 * Calls onChange during drag for real-time feedback, onCommit on release for persistence.
 */
public class VolumeSlider extends AbstractSliderButton {
    private final Consumer<Float> onChange;
    private final Runnable onCommit;

    public VolumeSlider(int x, int y, int width, int height, float initialValue,
                        Consumer<Float> onChange, Runnable onCommit) {
        super(x, y, width, height, Component.empty(), Mth.clamp(initialValue, 0.0, 1.0));
        this.onChange = onChange;
        this.onCommit = onCommit;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        int percent = (int) (this.value * 100);
        this.setMessage(Component.literal("Volume: " + percent + "%"));
    }

    @Override
    protected void applyValue() {
        onChange.accept((float) this.value);
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        super.onRelease(event);
        onCommit.run();
    }

    public float getVolume() {
        return (float) this.value;
    }

    public void setVolume(float volume) {
        this.value = Mth.clamp(volume, 0.0, 1.0);
        updateMessage();
    }
}
