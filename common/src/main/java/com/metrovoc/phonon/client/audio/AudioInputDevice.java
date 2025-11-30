package com.metrovoc.phonon.client.audio;

import javax.sound.sampled.Mixer;

/**
 * Represents an audio input device available on the system.
 */
public record AudioInputDevice(String name, String description, Mixer.Info mixerInfo) {

    @Override
    public String toString() {
        return name;
    }
}
