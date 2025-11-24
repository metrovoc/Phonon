package com.tovkaic.phonon;

import com.tovkaic.phonon.audio.AudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod class.
 * Common initialization entry point.
 */
public class Phonon {
    public static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    public static void init() {
        LOGGER.info("Initializing {}", Constants.MOD_NAME);
        AudioManager.getInstance();
    }
}
