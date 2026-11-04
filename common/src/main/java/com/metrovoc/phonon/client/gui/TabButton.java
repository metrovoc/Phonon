package com.metrovoc.phonon.client.gui;

import net.minecraft.network.chat.Component;

/** A text tab along the top of the panel, underlined when open. */
final class TabButton extends GuiPort.Control {
    private final Runnable action;
    private boolean open;

    TabButton(int x, int y, int width, int height, Component label, Runnable action) {
        super(x, y, width, height, label);
        this.action = action;
    }

    void setOpen(boolean open) {
        this.open = open;
    }

    @Override
    void draw(GuiPort.Canvas canvas, int mouseX, int mouseY) {
        canvas.text(getMessage(), getX(), getY() + 1,
            open || isMouseOver(mouseX, mouseY) ? SteelStyle.TEXT : SteelStyle.TEXT_MUTED);
        if (open) {
            canvas.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), SteelStyle.PLAYING);
        }
    }

    @Override
    boolean pressed(double mouseX, double mouseY, boolean doubleClick) {
        GuiPort.playClick();
        action.run();
        return true;
    }
}
