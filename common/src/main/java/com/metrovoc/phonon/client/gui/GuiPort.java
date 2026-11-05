package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.UUID;

/** The only GUI file that touches APIs which change between Minecraft versions. */
final class GuiPort {
    private GuiPort() {}

    static boolean isOperator() {
        var player = Minecraft.getInstance().player;
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static boolean speakerScreenOpen() {
        return Minecraft.getInstance().screen instanceof SpeakerScreen;
    }

    static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    /** A small image the server sent, held as a texture until released. */
    static final class Picture {
        private static final int MAX_SIDE = 256;
        private static int nextId;

        private final Identifier id;
        private final int width;
        private final int height;

        private Picture(Identifier id, int width, int height) {
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
                Identifier id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "search/" + nextId++);
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
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
        private final GuiGraphicsExtractor graphics;
        final Font font = Minecraft.getInstance().font;

        Canvas(GuiGraphicsExtractor graphics) {
            this.graphics = graphics;
        }

        void fill(int x0, int y0, int x1, int y1, int color) {
            graphics.fill(x0, y0, x1, y1, color);
        }

        void text(Component text, int x, int y, int color) {
            graphics.text(font, text, x, y, color);
        }

        void text(String text, int x, int y, int color) {
            graphics.text(font, text, x, y, color);
        }

        void line(FormattedCharSequence text, int x, int y, int color) {
            graphics.text(font, text, x, y, color);
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
            PlayerFaceExtractor.extractRenderState(graphics, info != null ? info.getSkin() : DefaultPlayerSkin.get(player), x, y, size);
        }

        void picture(Picture picture, int x, int y, int width, int height) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, picture.id, x, y, 0, 0, width, height,
                picture.width, picture.height, picture.width, picture.height);
        }

        void tooltip(Component text, int mouseX, int mouseY) {
            graphics.setTooltipForNextFrame(font, text, mouseX, mouseY);
        }
    }

    abstract static class Control extends AbstractWidget {
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
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            draw(new Canvas(graphics), mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return active && visible && event.button() == 0 && isMouseOver(event.x(), event.y())
                && pressed(event.x(), event.y(), doubleClick);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (event.button() != 0) {
                return false;
            }
            dragged(event.x(), event.y());
            return true;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            if (event.button() != 0) {
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
            super(menu, inventory, title, width, height);
        }

        abstract void drawPanel(Canvas canvas);

        abstract void drawOverlay(Canvas canvas, int mouseX, int mouseY);

        @Nullable
        abstract EditBox textField();

        /** Enter was pressed in the text field. */
        abstract void submitField();

        /** A menu drawn over everything gets the click first. Returns whether it was used. */
        abstract boolean overlayPressed(double mouseX, double mouseY);

        /** A menu drawn over everything gets the wheel first. Returns whether it was used. */
        abstract boolean overlayScrolled(double amount);

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return event.button() == 0 && overlayPressed(event.x(), event.y()) || super.mouseClicked(event, doubleClick);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
            drawPanel(new Canvas(graphics));
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            drawOverlay(new Canvas(graphics), mouseX, mouseY);
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            // The panel draws its own labels
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            EditBox field = textField();
            if (field != null && field.isFocused() && !event.isEscape()) {
                if (event.isConfirmation()) {
                    submitField();
                    return true;
                }
                return field.keyPressed(event);
            }
            return super.keyPressed(event);
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            EditBox field = textField();
            return field != null && field.isFocused() ? field.charTyped(event) : super.charTyped(event);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return overlayScrolled(scrollY) || getChildAt(mouseX, mouseY).filter(child -> child.mouseScrolled(mouseX, mouseY, scrollX, scrollY)).isPresent();
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (getFocused() != null && isDragging() && event.button() == 0) {
                return getFocused().mouseDragged(event, dragX, dragY);
            }
            return super.mouseDragged(event, dragX, dragY);
        }
    }
}
