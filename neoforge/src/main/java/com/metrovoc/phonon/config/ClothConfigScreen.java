package com.metrovoc.phonon.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config integration for client config GUI.
 * Only loaded if Cloth Config is present.
 */
public class ClothConfigScreen {

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("config.phonon.title"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Client config category
        ConfigCategory client = builder.getOrCreateCategory(
            Component.translatable("config.phonon.category.client"));

        client.addEntry(entryBuilder.startStrField(
                Component.translatable("config.phonon.client.cacheDirectory"),
                NeoForgeClientConfig.INSTANCE.cacheDirectory.get())
            .setDefaultValue(PhononClientConfig.DEFAULT_CACHE_DIRECTORY)
            .setTooltip(Component.translatable("config.phonon.client.cacheDirectory.tooltip"))
            .setSaveConsumer(val -> NeoForgeClientConfig.INSTANCE.cacheDirectory.set(val))
            .build());

        client.addEntry(entryBuilder.startIntSlider(
                Component.translatable("config.phonon.client.maxCacheSizeMB"),
                NeoForgeClientConfig.INSTANCE.maxCacheSizeMB.get(),
                0, 10240)
            .setDefaultValue(PhononClientConfig.DEFAULT_MAX_CACHE_SIZE_MB)
            .setTooltip(Component.translatable("config.phonon.client.maxCacheSizeMB.tooltip"))
            .setSaveConsumer(val -> NeoForgeClientConfig.INSTANCE.maxCacheSizeMB.set(val))
            .build());

        client.addEntry(entryBuilder.startDoubleField(
                Component.translatable("config.phonon.client.maxAudioDistance"),
                NeoForgeClientConfig.INSTANCE.maxAudioDistance.get())
            .setDefaultValue(PhononClientConfig.DEFAULT_MAX_AUDIO_DISTANCE)
            .setMin(16.0).setMax(256.0)
            .setTooltip(Component.translatable("config.phonon.client.maxAudioDistance.tooltip"))
            .setSaveConsumer(val -> NeoForgeClientConfig.INSTANCE.maxAudioDistance.set(val))
            .build());

        client.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.phonon.client.enableDebugLogging"),
                NeoForgeClientConfig.INSTANCE.enableDebugLogging.get())
            .setDefaultValue(PhononClientConfig.DEFAULT_ENABLE_DEBUG_LOGGING)
            .setTooltip(Component.translatable("config.phonon.client.enableDebugLogging.tooltip"))
            .setSaveConsumer(val -> NeoForgeClientConfig.INSTANCE.enableDebugLogging.set(val))
            .build());

        builder.setSavingRunnable(() -> {
            NeoForgeClientConfig.bind();
        });

        return builder.build();
    }
}
