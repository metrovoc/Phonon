package com.metrovoc.phonon.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * Volume slider widget.
 * Range: 0.0 to 1.0, displayed as 0% to 100%.
 */
public class VolumeSlider extends AbstractSliderButton {
    private final Consumer<Float> onChange;

    public VolumeSlider(int x, int y, int width, int height, float initialValue, Consumer<Float> onChange) {
        super(x, y, width, height, Component.empty(), Mth.clamp(initialValue, 0.0, 1.0));
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
        onChange.accept((float) this.value);
    }

    public float getVolume() {
        return (float) this.value;
    }

    public void setVolume(float volume) {
        this.value = Mth.clamp(volume, 0.0, 1.0);
        updateMessage();
    }
}
