package com.metrovoc.phonon.client.audio;

import com.metrovoc.phonon.Phonon;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

/**
 * Muffles one OpenAL source, by turning down its high frequencies.
 * Does nothing on devices without the EFX extension.
 */
public final class LowPass {
    private static Boolean supported;

    private final int source;
    private int filter;
    private float lastGainHf = -1;

    public LowPass(int source) {
        this.source = source;
    }

    private static boolean supported() {
        if (supported == null) {
            long device = ALC10.alcGetContextsDevice(ALC10.alcGetCurrentContext());
            supported = device != 0 && ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX");
            Phonon.LOGGER.info("Sound muffling {}", supported ? "enabled" : "unavailable on this audio device");
        }
        return supported;
    }

    /** Whether muffling has been checked and works on the current sound device. */
    public static boolean isActive() {
        return Boolean.TRUE.equals(supported);
    }

    /** Forgets what the last sound device supported, for after a sound engine restart. */
    public static void resetSupport() {
        supported = null;
    }

    /** 1 keeps every frequency; values toward 0 leave only the low end. */
    public void set(float gainHf) {
        if (Math.abs(gainHf - lastGainHf) < 0.01f || !supported()) {
            return;
        }
        if (filter == 0) {
            filter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, 1.0f);
        }
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, gainHf);
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
        AL10.alGetError();
        lastGainHf = gainHf;
    }

    public float gainHf() {
        return lastGainHf;
    }

    public void release() {
        if (filter != 0) {
            EXTEfx.alDeleteFilters(filter);
            AL10.alGetError();
            filter = 0;
        }
    }
}
