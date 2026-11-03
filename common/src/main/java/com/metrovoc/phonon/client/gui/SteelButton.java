package com.metrovoc.phonon.client.gui;

import net.minecraft.network.chat.Component;

/** An icon button in the panel's steel, named by a tooltip. */
final class SteelButton extends GuiPort.Control {
    private static final int ICON_SIZE = 9;

    private final Runnable action;
    private String[] icon;

    SteelButton(int x, int y, int width, int height, Component label, String[] icon, Runnable action) {
        super(x, y, width, height, label);
        this.icon = icon;
        this.action = action;
    }

    void show(Component label, String[] icon) {
        setMessage(label);
        this.icon = icon;
    }

    @Override
    void draw(GuiPort.Canvas canvas, int mouseX, int mouseY) {
        boolean hovered = active && isMouseOver(mouseX, mouseY);
        int face = !active ? SteelStyle.STEEL_DISABLED : hovered ? SteelStyle.STEEL_HOVER : SteelStyle.STEEL;
        SteelStyle.plate(canvas, getX(), getY(), getWidth(), getHeight(), face, active);
        SteelStyle.icon(canvas, icon,
            getX() + (getWidth() - ICON_SIZE) / 2,
            getY() + (getHeight() - ICON_SIZE) / 2,
            active ? SteelStyle.TEXT : SteelStyle.TEXT_OFF);
    }

    @Override
    boolean pressed(double mouseX, double mouseY, boolean doubleClick) {
        GuiPort.playClick();
        action.run();
        return true;
    }
}
