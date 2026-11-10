package com.metrovoc.phonon.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** The config button in Mod Menu, when it's installed. */
public final class PhononModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PhononConfigScreen::new;
    }
}
