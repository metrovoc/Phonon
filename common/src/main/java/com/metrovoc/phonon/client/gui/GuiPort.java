package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.UUID;

/** The only GUI file that touches APIs which change between Minecraft versions. */
final class GuiPort {
    private static final long DOUBLE_CLICK_MS = 250;

    private GuiPort() {}

    static boolean isOperator() {
        var player = Minecraft.getInstance().player;
        return player != null && player.hasPermissions(2);
    }

    static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    /** A small image the server sent, held as a texture until released. */
    static final class Picture {
        private static final int MAX_SIDE = 256;
        private static int nextId;

        private final ResourceLocation id;
        private final int width;
        private final int height;

        private Picture(ResourceLocation id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }

        /** Null when the bytes aren't a picture of a sensible size. */
        @Nullable
        static Picture read(byte[] png) {
            try {
                NativeImage image = NativeImage.read(png);
                if (image.getWidth() > MAX_SIDE || image.getHeight() > MAX_SIDE) {
                    image.close();
                    return null;
                }
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "search/" + nextId++);
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
                return new Picture(id, image.getWidth(), image.getHeight());
            } catch (IOException invalid) {
                return null;
            }
        }

        void release() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    static final class Canvas {
        private final GuiGraphics graphics;
        final Font font = Minecraft.getInstance().font;

        Canvas(GuiGraphics graphics) {
            this.graphics = graphics;
        }

        void fill(int x0, int y0, int x1, int y1, int color) {
            graphics.fill(x0, y0, x1, y1, color);
        }

        void text(Component text, int x, int y, int color) {
            graphics.drawString(font, text, x, y, color);
        }

        void text(String text, int x, int y, int color) {
            graphics.drawString(font, text, x, y, color);
        }

        void line(FormattedCharSequence text, int x, int y, int color) {
            graphics.drawString(font, text, x, y, color);
        }

        void clip(int x0, int y0, int x1, int y1) {
            graphics.enableScissor(x0, y0, x1, y1);
        }

        void unclip() {
            graphics.disableScissor();
        }

        /** A player's face, from their skin if they're online and the default for their ID if not. */
        void head(UUID player, int x, int y, int size) {
            var connection = Minecraft.getInstance().getConnection();
            var info = connection == null ? null : connection.getPlayerInfo(player);
            PlayerFaceRenderer.draw(graphics, info != null ? info.getSkin() : DefaultPlayerSkin.get(player), x, y, size);
        }

        void picture(Picture picture, int x, int y, int width, int height) {
            graphics.blit(picture.id, x, y, width, height, 0, 0, picture.width, picture.height, picture.width, picture.height);
        }

        void tooltip(Component text, int mouseX, int mouseY) {
            graphics.renderTooltip(font, text, mouseX, mouseY);
        }
    }

    abstract static class Control extends AbstractWidget {
        private long lastPress;

        Control(int x, int y, int width, int height, Component message) {
            super(x, y, width, height, message);
        }

        abstract void draw(Canvas canvas, int mouseX, int mouseY);

        /** Returns whether the press was used. */
        boolean pressed(double mouseX, double mouseY, boolean doubleClick) {
            return true;
        }

        void dragged(double mouseX, double mouseY) {}

        void released() {}

        boolean scrolled(double amount) {
            return false;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            draw(new Canvas(graphics), mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) {
                return false;
            }
            long now = System.currentTimeMillis();
            boolean doubleClick = now - lastPress < DOUBLE_CLICK_MS;
            lastPress = now;
            return pressed(mouseX, mouseY, doubleClick);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (button != 0) {
                return false;
            }
            dragged(mouseX, mouseY);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            released();
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return scrolled(scrollY);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    abstract static class PanelScreen extends AbstractContainerScreen<SpeakerMenu> {
        PanelScreen(SpeakerMenu menu, Inventory inventory, Component title, int width, int height) {
            super(menu, inventory, title);
            this.imageWidth = width;
            this.imageHeight = height;
        }

        abstract void drawPanel(Canvas canvas);

        abstract void drawOverlay(Canvas canvas, int mouseX, int mouseY);

        @Nullable
        abstract EditBox textField();

        /** Enter was pressed in the text field. */
        abstract void submitField();

        @Override
        protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
            drawPanel(new Canvas(graphics));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            drawOverlay(new Canvas(graphics), mouseX, mouseY);
        }

        @Override
        protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
            // The panel draws its own labels
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            EditBox field = textField();
            if (field != null && field.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    submitField();
                    return true;
                }
                return field.keyPressed(key, scanCode, modifiers);
            }
            return super.keyPressed(key, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char character, int modifiers) {
            EditBox field = textField();
            return field != null && field.isFocused() ? field.charTyped(character, modifiers) : super.charTyped(character, modifiers);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (getFocused() != null && isDragging() && button == 0) {
                return getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return getChildAt(mouseX, mouseY).filter(child -> child.mouseScrolled(mouseX, mouseY, scrollX, scrollY)).isPresent();
        }
    }
}
