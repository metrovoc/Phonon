package com.metrovoc.phonon.config;

import com.metrovoc.phonon.client.LiveInputManager;
import com.metrovoc.phonon.client.audio.AudioInputDevice;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

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

        // Live Input category
        ConfigCategory liveInput = builder.getOrCreateCategory(
            Component.translatable("config.phonon.category.liveInput"));

        liveInput.addEntry(entryBuilder.startBooleanToggle(
                Component.translatable("config.phonon.liveInput.enabled"),
                NeoForgeClientConfig.INSTANCE.liveInputEnabled.get())
            .setDefaultValue(PhononClientConfig.DEFAULT_LIVE_INPUT_ENABLED)
            .setTooltip(Component.translatable("config.phonon.liveInput.enabled.tooltip"))
            .setSaveConsumer(val -> NeoForgeClientConfig.INSTANCE.liveInputEnabled.set(val))
            .build());

        // Build device selector from available devices
        List<AudioInputDevice> devices = LiveInputManager.getAvailableDevices();
        String currentDevice = NeoForgeClientConfig.INSTANCE.liveInputDevice.get();

        if (!devices.isEmpty()) {
            String[] deviceNames = new String[devices.size() + 1];
            deviceNames[0] = "";  // Empty = disabled
            for (int i = 0; i < devices.size(); i++) {
                deviceNames[i + 1] = devices.get(i).name();
            }

            liveInput.addEntry(entryBuilder.startSelector(
                    Component.translatable("config.phonon.liveInput.device"),
                    deviceNames,
                    currentDevice.isEmpty() ? "" : currentDevice)
                .setDefaultValue(PhononClientConfig.DEFAULT_LIVE_INPUT_DEVICE)
                .setTooltip(Component.translatable("config.phonon.liveInput.device.tooltip"))
                .setSaveConsumer(val -> NeoForgeClientConfig.INSTANCE.liveInputDevice.set(val))
                .build());
        } else {
            liveInput.addEntry(entryBuilder.startStrField(
                    Component.translatable("config.phonon.liveInput.device"),
                    currentDevice)
                .setDefaultValue(PhononClientConfig.DEFAULT_LIVE_INPUT_DEVICE)
                .setTooltip(Component.translatable("config.phonon.liveInput.device.tooltip"))
                .setSaveConsumer(val -> NeoForgeClientConfig.INSTANCE.liveInputDevice.set(val))
                .build());
        }

        builder.setSavingRunnable(() -> {
            NeoForgeClientConfig.bind();
        });

        return builder.build();
    }
}
