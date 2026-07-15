package com.metrovoc.phonon.config;

import com.metrovoc.phonon.client.AudioCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Dependency-free client configuration screen. */
public final class PhononConfigScreen extends Screen {
    private static final int FIELD_WIDTH = 220;
    private static final int FIELD_HEIGHT = 20;
    private static final int ROW_HEIGHT = 42;

    private final Screen parent;
    private EditBox cacheDirectory;
    private EditBox maxCacheSize;
    private EditBox maxAudioDistance;

    public PhononConfigScreen(Screen parent) {
        super(Component.translatable("config.phonon.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (width - FIELD_WIDTH) / 2;
        int top = Math.max(38, height / 2 - 105);

        cacheDirectory = new EditBox(
            font,
            left,
            top + 14,
            FIELD_WIDTH,
            FIELD_HEIGHT,
            Component.translatable("config.phonon.client.cacheDirectory")
        );
        cacheDirectory.setMaxLength(128);
        cacheDirectory.setValue(NeoForgeClientConfig.INSTANCE.cacheDirectory.get());
        addRenderableWidget(cacheDirectory);

        maxCacheSize = numericField(
            left,
            top + ROW_HEIGHT + 14,
            Integer.toString(NeoForgeClientConfig.INSTANCE.maxCacheSizeMB.get()),
            value -> value.matches("\\d{0,5}")
        );

        maxAudioDistance = numericField(
            left,
            top + ROW_HEIGHT * 2 + 14,
            Double.toString(NeoForgeClientConfig.INSTANCE.maxAudioDistance.get()),
            value -> value.matches("\\d{0,3}(\\.\\d{0,2})?")
        );

        int buttonY = top + ROW_HEIGHT * 3 + 18;
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> saveAndClose())
            .bounds(left, buttonY, 106, FIELD_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
            .bounds(left + 114, buttonY, 106, FIELD_HEIGHT)
            .build());
    }

    private EditBox numericField(
        int x,
        int y,
        String value,
        java.util.function.Predicate<String> filter
    ) {
        EditBox field = new EditBox(font, x, y, FIELD_WIDTH, FIELD_HEIGHT, Component.empty());
        field.setFilter(filter);
        field.setValue(value);
        addRenderableWidget(field);
        return field;
    }

    private void saveAndClose() {
        String directory = cacheDirectory.getValue().trim();
        if (directory.isEmpty()) {
            directory = PhononClientConfig.DEFAULT_CACHE_DIRECTORY;
        }

        int cacheSize = parseInt(
            maxCacheSize.getValue(),
            PhononClientConfig.DEFAULT_MAX_CACHE_SIZE_MB,
            0,
            10_240
        );
        double audioDistance = parseDouble(
            maxAudioDistance.getValue(),
            PhononClientConfig.DEFAULT_MAX_AUDIO_DISTANCE,
            16.0,
            256.0
        );

        NeoForgeClientConfig.INSTANCE.cacheDirectory.set(directory);
        NeoForgeClientConfig.INSTANCE.maxCacheSizeMB.set(cacheSize);
        NeoForgeClientConfig.INSTANCE.maxAudioDistance.set(audioDistance);
        NeoForgeClientConfig.SPEC.save();
        NeoForgeClientConfig.bind();

        Minecraft minecraft = Minecraft.getInstance();
        AudioCache.getInstance().initialize(minecraft.gameDirectory.toPath());
        minecraft.gui.setScreen(parent);
    }

    private static int parseInt(String value, int fallback, int minimum, int maximum) {
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback, double minimum, double maximum) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed)
                ? Math.max(minimum, Math.min(maximum, parsed))
                : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int left = (width - FIELD_WIDTH) / 2;
        int top = Math.max(38, height / 2 - 105);
        graphics.centeredText(font, title, width / 2, top - 22, 0xFFFFFF);
        graphics.text(font, Component.translatable("config.phonon.client.cacheDirectory"),
            left, top, 0xA0A0A0);
        graphics.text(font, Component.translatable("config.phonon.client.maxCacheSizeMB"),
            left, top + ROW_HEIGHT, 0xA0A0A0);
        graphics.text(font, Component.translatable("config.phonon.client.maxAudioDistance"),
            left, top + ROW_HEIGHT * 2, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
