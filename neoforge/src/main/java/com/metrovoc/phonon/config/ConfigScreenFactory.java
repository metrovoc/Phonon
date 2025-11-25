package com.metrovoc.phonon.config;

import com.metrovoc.phonon.Phonon;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.Optional;

/**
 * Config screen factory that optionally uses Cloth Config if available.
 */
public class ConfigScreenFactory {

    private static final String CLOTH_CONFIG_MOD_ID = "cloth_config";

    public static Optional<IConfigScreenFactory> create() {
        if (ModList.get().isLoaded(CLOTH_CONFIG_MOD_ID)) {
            Phonon.LOGGER.info("Cloth Config detected, registering config screen");
            return Optional.of((container, parent) -> ClothConfigScreen.create(parent));
        }
        Phonon.LOGGER.info("Cloth Config not found, config screen disabled");
        return Optional.empty();
    }
}
