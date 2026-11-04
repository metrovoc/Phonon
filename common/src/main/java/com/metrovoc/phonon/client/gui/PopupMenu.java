package com.metrovoc.phonon.client.gui;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A small list drawn over the screen, scrolling if it's long. A click inside picks an item; any click closes it. */
final class PopupMenu {
    private static final int ROW_HEIGHT = 15;
    private static final int CAPTION_HEIGHT = 13;
    private static final int WIDTH = 130;
    private static final int MAX_VISIBLE = 9;
    private static final int BAR_WIDTH = 2;

    record Item(String label, boolean checked, @Nullable Component tooltip, Runnable run) {}

    private final Component caption;
    private final List<Item> items;
    private final String[] checkIcon;
    private final int x;
    private final int y;
    private int first;

    PopupMenu(Component caption, List<Item> items, String[] checkIcon, int anchorRight, int anchorTop, int screenBottom) {
        this.caption = caption;
        this.items = items;
        this.checkIcon = checkIcon;
        this.x = anchorRight - WIDTH;
        this.y = Math.min(anchorTop, screenBottom - height());
    }

    private int visible() {
        return Math.min(items.size(), MAX_VISIBLE);
    }

    private int height() {
        return CAPTION_HEIGHT + visible() * ROW_HEIGHT + 2;
    }

    private int itemAt(double mouseX, double mouseY) {
        int row = (int) Math.floor((mouseY - y - CAPTION_HEIGHT) / ROW_HEIGHT);
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y + CAPTION_HEIGHT && row >= 0 && row < visible() ? first + row : -1;
    }

    void scrolled(double amount) {
        first = Math.max(0, Math.min(items.size() - visible(), first - (int) Math.signum(amount)));
    }

    void draw(GuiPort.Canvas canvas, int mouseX, int mouseY) {
        canvas.fill(x - 1, y - 1, x + WIDTH + 1, y + height() + 1, SteelStyle.HIGHLIGHT);
        canvas.fill(x, y, x + WIDTH, y + height(), SteelStyle.WELL);
        canvas.text(caption, x + 4, y + 3, SteelStyle.TEXT_MUTED);
        int over = itemAt(mouseX, mouseY);
        for (int i = first; i < first + visible(); i++) {
            Item item = items.get(i);
            int rowY = y + CAPTION_HEIGHT + (i - first) * ROW_HEIGHT;
            if (i == over) {
                canvas.fill(x, rowY, x + WIDTH, rowY + ROW_HEIGHT, 0x20FFFFFF);
            }
            int color = item.checked() ? SteelStyle.PLAYING : SteelStyle.TEXT;
            if (item.checked()) {
                SteelStyle.icon(canvas, checkIcon, x + 4, rowY + 3, color);
            }
            String text = item.label();
            int room = WIDTH - 24;
            boolean cut = canvas.font.width(text) > room;
            if (cut) {
                text = canvas.font.plainSubstrByWidth(text, room - canvas.font.width("...")) + "...";
            }
            canvas.text(text, x + 17, rowY + 4, color);
            if (i == over) {
                if (cut) {
                    canvas.tooltip(Component.literal(item.label()), mouseX, mouseY);
                } else if (item.tooltip() != null) {
                    canvas.tooltip(item.tooltip(), mouseX, mouseY);
                }
            }
        }
        if (items.size() > visible()) {
            int track = visible() * ROW_HEIGHT;
            int thumb = Math.max(8, track * visible() / items.size());
            int thumbY = y + CAPTION_HEIGHT + (track - thumb) * first / (items.size() - visible());
            canvas.fill(x + WIDTH - BAR_WIDTH - 1, thumbY, x + WIDTH - 1, thumbY + thumb, SteelStyle.HIGHLIGHT);
        }
    }

    /** Picks the item under the mouse, if any. The caller closes the menu either way. */
    void press(double mouseX, double mouseY) {
        int item = itemAt(mouseX, mouseY);
        if (item >= 0) {
            GuiPort.playClick();
            items.get(item).run().run();
        }
    }
}
