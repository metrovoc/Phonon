package com.metrovoc.phonon.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * Volume slider widget with perceptual (quadratic) volume curve.
 * Display: 0% to 100% (linear slider position)
 * Actual volume: displayValue² (matches human hearing perception)
 */
public class VolumeSlider extends AbstractSliderButton {
    private final Consumer<Float> onChange;

    public VolumeSlider(int x, int y, int width, int height, float initialValue, Consumer<Float> onChange) {
        // Convert actual volume to display value: sqrt(actualVolume)
        super(x, y, width, height, Component.empty(), Mth.clamp(Math.sqrt(initialValue), 0.0, 1.0));
        this.onChange = onChange;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        int percent = (int) (this.value * 100);
        this.setMessage(Component.literal("Volume: " + percent + "%"));
    }

    @Override
    protected void applyValue() {
        // Apply quadratic curve: actualVolume = displayValue²
        float actualVolume = (float) (this.value * this.value);
        onChange.accept(actualVolume);
    }

    public float getVolume() {
        // Return actual volume (squared)
        return (float) (this.value * this.value);
    }

    public void setVolume(float actualVolume) {
        // Convert actual volume to display value
        this.value = Mth.clamp(Math.sqrt(actualVolume), 0.0, 1.0);
        updateMessage();
    }
}
