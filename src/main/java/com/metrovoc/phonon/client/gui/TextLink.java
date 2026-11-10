package com.metrovoc.phonon.client.gui;

import net.minecraft.network.chat.Component;

/** A small underlined word that acts like a button, for a next step that shouldn't take up a button's room. */
final class TextLink extends GuiPort.Control {
    private final Runnable action;

    TextLink(int x, int y, int width, int height, Component label, Runnable action) {
        super(x, y, width, height, label);
        this.action = action;
    }

    @Override
    void draw(GuiPort.Canvas canvas, int mouseX, int mouseY) {
        int color = isMouseOver(mouseX, mouseY) ? SteelStyle.PLAYING : SteelStyle.TEXT_DIM;
        canvas.text(getMessage(), getX(), getY(), color);
        canvas.fill(getX(), getY() + canvas.font.lineHeight, getX() + canvas.font.width(getMessage()), getY() + canvas.font.lineHeight + 1, color);
    }

    @Override
    boolean pressed(double mouseX, double mouseY, boolean doubleClick) {
        GuiPort.playClick();
        action.run();
        return true;
    }
}
