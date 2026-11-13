package com.metrovoc.phonon.config;

import com.metrovoc.phonon.client.AudioCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Dependency-free client configuration screen. */
public final class PhononConfigScreen extends Screen {
    private static final int FIELD_WIDTH = 220;
    private static final int FIELD_HEIGHT = 20;
    private static final int ROW_HEIGHT = 42;

    private final Screen parent;
    private EditBox cacheDirectory;
    private EditBox maxCacheSize;

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
        cacheDirectory.setValue(PhononClientConfig.getCacheDirectory());
        addRenderableWidget(cacheDirectory);

        maxCacheSize = numericField(
            left,
            top + ROW_HEIGHT + 14,
            Integer.toString(PhononClientConfig.getMaxCacheSizeMB()),
            value -> value.matches("\\d{0,5}")
        );

        int buttonY = top + ROW_HEIGHT * 2 + 18;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> saveAndClose())
            .bounds(left, buttonY, 106, FIELD_HEIGHT)
            .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
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
        String[] lastValid = {value};
        field.setValue(value);
        field.setResponder(text -> {
            if (filter.test(text)) {
                lastValid[0] = text;
            } else {
                field.setValue(lastValid[0]);
            }
        });
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

        PhononClientConfig.save(directory, cacheSize);

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


    @Override
    //? if >=26.1 {
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    //?} else {
    /*public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    *///?}

        int left = (width - FIELD_WIDTH) / 2;
        int top = Math.max(38, height / 2 - 105);
        graphics.centeredText(font, title, width / 2, top - 22, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("config.phonon.client.cacheDirectory"),
            left, top, 0xFFA0A0A0);
        graphics.text(font, Component.translatable("config.phonon.client.maxCacheSizeMB"),
            left, top + ROW_HEIGHT, 0xFFA0A0A0);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
