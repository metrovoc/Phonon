package com.metrovoc.phonon.config;

import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.Optional;

/**
 * Config screen factory backed only by vanilla widgets.
 */
public class ConfigScreenFactory {

    public static Optional<IConfigScreenFactory> create() {
        return Optional.of((container, parent) -> new PhononConfigScreen(parent));
    }
}
