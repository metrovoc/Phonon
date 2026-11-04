package com.metrovoc.phonon.client.gui;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A small list drawn over the screen. A click inside picks an item; a click outside closes it. */
final class PopupMenu {
    private static final int ROW_HEIGHT = 15;
    private static final int CAPTION_HEIGHT = 13;
    private static final int WIDTH = 130;

    record Item(String label, boolean checked, @Nullable Component tooltip, Runnable run) {}

    private final Component caption;
    private final List<Item> items;
    private final String[] checkIcon;
    private final int x;
    private final int y;

    PopupMenu(Component caption, List<Item> items, String[] checkIcon, int anchorRight, int anchorTop, int screenBottom) {
        this.caption = caption;
        this.items = items;
        this.checkIcon = checkIcon;
        this.x = anchorRight - WIDTH;
        this.y = Math.min(anchorTop, screenBottom - height());
    }

    private int height() {
        return CAPTION_HEIGHT + items.size() * ROW_HEIGHT + 2;
    }

    private int itemAt(double mouseX, double mouseY) {
        int row = (int) Math.floor((mouseY - y - CAPTION_HEIGHT) / ROW_HEIGHT);
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y + CAPTION_HEIGHT && row >= 0 && row < items.size() ? row : -1;
    }

    void draw(GuiPort.Canvas canvas, int mouseX, int mouseY) {
        canvas.fill(x - 1, y - 1, x + WIDTH + 1, y + height() + 1, SteelStyle.HIGHLIGHT);
        canvas.fill(x, y, x + WIDTH, y + height(), SteelStyle.WELL);
        canvas.text(caption, x + 4, y + 3, SteelStyle.TEXT_MUTED);
        int over = itemAt(mouseX, mouseY);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int rowY = y + CAPTION_HEIGHT + i * ROW_HEIGHT;
            if (i == over) {
                canvas.fill(x, rowY, x + WIDTH, rowY + ROW_HEIGHT, 0x20FFFFFF);
            }
            int color = item.checked() ? SteelStyle.PLAYING : SteelStyle.TEXT;
            if (item.checked()) {
                SteelStyle.icon(canvas, checkIcon, x + 4, rowY + 3, color);
            }
            String text = item.label();
            int room = WIDTH - 22;
            boolean cut = canvas.font.width(text) > room;
            if (cut) {
                text = canvas.font.plainSubstrByWidth(text, room - canvas.font.width("...")) + "...";
            }
            canvas.text(text, x + 17, rowY + 4, color);
            if (i == over) {
                if (item.tooltip() != null) {
                    canvas.tooltip(item.tooltip(), mouseX, mouseY);
                } else if (cut) {
                    canvas.tooltip(Component.literal(item.label()), mouseX, mouseY);
                }
            }
        }
    }

    /** Returns true whether it picked an item or only closed. The caller closes the menu either way. */
    void press(double mouseX, double mouseY) {
        int item = itemAt(mouseX, mouseY);
        if (item >= 0) {
            GuiPort.playClick();
            items.get(item).run().run();
        }
    }
}
