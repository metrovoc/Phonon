package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
//? if >=26.1 {
import net.minecraft.client.gui.components.PlayerFaceExtractor;
//?} else
/*import net.minecraft.client.gui.components.PlayerFaceRenderer;*/
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
//? if >=1.21.9 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
//? if >=1.21.6 {
import net.minecraft.client.renderer.RenderPipelines;
//?} else if >=1.21.2
/*import net.minecraft.client.renderer.RenderType;*/
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
//? if >=1.21.11
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

    //? if <1.21.9
    /*private static final long DOUBLE_CLICK_MS = 250;*/

    static boolean isOperator() {
        var player = Minecraft.getInstance().player;
        //? if >=1.21.11 {
        return player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        //?} else
        /*return player != null && player.hasPermissions(2);*/
    }

    static boolean speakerScreenOpen() {
        return Minecraft.getInstance().gui.screen() instanceof SpeakerScreen;
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
                //? if >=1.21.5 {
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
                //?} else
                /*Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));*/
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
            //? if >=26.1 {
            PlayerFaceExtractor.extractRenderState(graphics, info != null ? info.getSkin() : DefaultPlayerSkin.get(player), x, y, size);
            //?} else
            /*PlayerFaceRenderer.draw(graphics, info != null ? info.getSkin() : DefaultPlayerSkin.get(player), x, y, size);*/
        }

        void picture(Picture picture, int x, int y, int width, int height) {
            //? if >=1.21.6 {
            graphics.blit(RenderPipelines.GUI_TEXTURED, picture.id, x, y, 0, 0, width, height,
                picture.width, picture.height, picture.width, picture.height);
            //?} else if >=1.21.2 {
            /*graphics.blit(RenderType::guiTextured, picture.id, x, y, 0, 0, width, height,
                picture.width, picture.height, picture.width, picture.height);
            *///?} else {
            /*graphics.blit(picture.id, x, y, width, height, 0, 0, picture.width, picture.height, picture.width, picture.height);
            *///?}
        }

        void tooltip(Component text, int mouseX, int mouseY) {
            //? if >=1.21.6 {
            graphics.setTooltipForNextFrame(font, text, mouseX, mouseY);
            //?} else
            /*graphics.renderTooltip(font, text, mouseX, mouseY);*/
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
        //? if >=26.1 {
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        //?} else
        /*protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {*/
            draw(new Canvas(graphics), mouseX, mouseY);
        }

        //? if >=1.21.9 {
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return active && visible && event.button() == InputConstants.MOUSE_BUTTON_LEFT && isMouseOver(event.x(), event.y())
                && pressed(event.x(), event.y(), doubleClick);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
            }
            dragged(event.x(), event.y());
            return true;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
            }
            released();
            return true;
        }
        //?} else {
        /*private long lastPress;

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!active || !visible || button != InputConstants.MOUSE_BUTTON_LEFT || !isMouseOver(mouseX, mouseY)) {
                return false;
            }
            long now = System.currentTimeMillis();
            boolean doubleClick = now - lastPress < DOUBLE_CLICK_MS;
            lastPress = now;
            return pressed(mouseX, mouseY, doubleClick);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (button != InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
            }
            dragged(mouseX, mouseY);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button != InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
            }
            released();
            return true;
        }
        *///?}

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
            //? if >=26.1 {
            super(menu, inventory, title, width, height);
            //?} else {
            /*super(menu, inventory, title);
            imageWidth = width;
            imageHeight = height;
            *///?}
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

        //? if >=1.21.9 {
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return event.button() == InputConstants.MOUSE_BUTTON_LEFT && overlayPressed(event.x(), event.y()) || super.mouseClicked(event, doubleClick);
        }
        //?} else {
        /*@Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return button == InputConstants.MOUSE_BUTTON_LEFT && overlayPressed(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }
        *///?}

        //? if >=26.1 {
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
        //?} else {
        /*@Override
        protected void renderBg(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
            drawPanel(new Canvas(graphics));
        }

        @Override
        public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            drawOverlay(new Canvas(graphics), mouseX, mouseY);
        }
        *///?}

        @Override
        //? if >=26.1 {
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        //?} else
        /*protected void renderLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {*/
            // The panel draws its own labels
        }

        //? if >=1.21.9 {
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
        //?} else {
        /*@Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            EditBox field = textField();
            if (field != null && field.isFocused() && key != InputConstants.KEY_ESCAPE) {
                if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
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
        *///?}

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return overlayScrolled(scrollY) || getChildAt(mouseX, mouseY).filter(child -> child.mouseScrolled(mouseX, mouseY, scrollX, scrollY)).isPresent();
        }

        //? if >=1.21.9 {
        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (getFocused() != null && isDragging() && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                return getFocused().mouseDragged(event, dragX, dragY);
            }
            return super.mouseDragged(event, dragX, dragY);
        }
        //?} else {
        /*@Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (getFocused() != null && isDragging() && button == InputConstants.MOUSE_BUTTON_LEFT) {
                return getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        *///?}
    }
}
