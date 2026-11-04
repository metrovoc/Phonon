package com.metrovoc.phonon.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Scrollable rows: tracks, the queue, playlists or stations.
 * A hovered row shows its action icons in place of its detail text.
 */
final class RowList extends GuiPort.Control {
    static final int ROW_HEIGHT = 18;
    private static final int INSET = 2;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int ROW_TEXT_Y = 5;
    private static final int ACTION_WIDTH = 13;
    private static final int LEAD_WIDTH = 16;

    record Action(String[] icon, Component label, Runnable run) {}

    /** One row. Key identifies it for scrolling and double clicks; head shows a player's face in the lead space. */
    record Row(
        @Nullable UUID key, String text, String detail, int color, boolean selected,
        @Nullable String[] marker, int markerColor, @Nullable UUID head, int progress,
        List<Action> actions, @Nullable Runnable click, @Nullable Runnable doubleClick
    ) {}

    private List<Row> rows = List.of();
    private List<FormattedCharSequence> emptyMessage = List.of();
    private int scroll;
    private boolean draggingBar;

    RowList(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    void setRows(List<Row> rows, List<FormattedCharSequence> emptyMessage) {
        this.rows = rows;
        this.emptyMessage = emptyMessage;
        setScroll(scroll);
    }

    int scroll() {
        return scroll;
    }

    void setScroll(int value) {
        scroll = Math.max(0, Math.min(value, maxScroll()));
    }

    void reveal(@Nullable UUID key) {
        for (int i = 0; i < rows.size(); i++) {
            if (key != null && key.equals(rows.get(i).key())) {
                int top = i * ROW_HEIGHT;
                if (top < scroll) {
                    setScroll(top);
                } else if (top + ROW_HEIGHT > scroll + viewHeight()) {
                    setScroll(top + ROW_HEIGHT - viewHeight());
                }
                return;
            }
        }
    }

    private int viewHeight() {
        return getHeight() - INSET * 2;
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * ROW_HEIGHT - viewHeight());
    }

    private boolean scrollable() {
        return maxScroll() > 0;
    }

    private int rowWidth() {
        return getWidth() - INSET * 2 - (scrollable() ? SCROLLBAR_WIDTH + 1 : 0);
    }

    private int rowIndexAt(double mouseX, double mouseY) {
        int left = getX() + INSET;
        if (mouseX < left || mouseX >= left + rowWidth()) {
            return -1;
        }
        int offset = (int) mouseY - getY() - INSET;
        int index = offset < 0 || offset >= viewHeight() ? -1 : (offset + scroll) / ROW_HEIGHT;
        return index < rows.size() ? index : -1;
    }

    /** Which action of a row the mouse is over, counted from the right edge, or -1. */
    private int actionAt(Row row, double mouseX) {
        int right = getX() + INSET + rowWidth();
        int index = (int) ((right - 2 - mouseX) / ACTION_WIDTH);
        return mouseX < right - 2 && index >= 0 && index < row.actions().size() ? index : -1;
    }

    @Override
    void draw(GuiPort.Canvas canvas, int mouseX, int mouseY) {
        int x = getX();
        int y = getY();
        canvas.fill(x, y, x + getWidth(), y + getHeight(), SteelStyle.WELL);

        if (rows.isEmpty()) {
            int lineY = y + 10;
            for (FormattedCharSequence line : emptyMessage) {
                canvas.line(line, x + 8, lineY, SteelStyle.TEXT_DIM);
                lineY += canvas.font.lineHeight + 2;
            }
            return;
        }

        int hovered = isMouseOver(mouseX, mouseY) ? rowIndexAt(mouseX, mouseY) : -1;
        Component tooltip = null;
        canvas.clip(x + INSET, y + INSET, x + getWidth() - INSET, y + getHeight() - INSET);
        for (int i = scroll / ROW_HEIGHT; i < rows.size(); i++) {
            int rowY = y + INSET + i * ROW_HEIGHT - scroll;
            if (rowY >= y + getHeight()) {
                break;
            }
            Component rowTooltip = drawRow(canvas, rows.get(i), x + INSET, rowY, i == hovered, mouseX);
            if (i == hovered) {
                tooltip = rowTooltip;
            }
        }
        canvas.unclip();

        if (scrollable()) {
            int barX = x + getWidth() - INSET - SCROLLBAR_WIDTH;
            int barY = y + INSET;
            int thumbHeight = Math.max(16, viewHeight() * viewHeight() / (rows.size() * ROW_HEIGHT));
            int thumbY = barY + scroll * (viewHeight() - thumbHeight) / maxScroll();
            canvas.fill(barX, barY, barX + SCROLLBAR_WIDTH, barY + viewHeight(), 0xFF000000);
            SteelStyle.plate(canvas, barX, thumbY, SCROLLBAR_WIDTH, thumbHeight, SteelStyle.STEEL, true);
        }

        if (tooltip != null) {
            canvas.tooltip(tooltip, mouseX, mouseY);
        }
    }

    @Nullable
    private Component drawRow(GuiPort.Canvas canvas, Row row, int x, int y, boolean hovered, int mouseX) {
        int right = x + rowWidth();
        if (row.selected()) {
            canvas.fill(x, y, right, y + ROW_HEIGHT, SteelStyle.TEXT_DIM);
            canvas.fill(x + 1, y + 1, right - 1, y + ROW_HEIGHT - 1, 0xFF20262B);
        } else if (hovered) {
            canvas.fill(x, y, right, y + ROW_HEIGHT, 0x20FFFFFF);
        }
        if (row.progress() >= 0) {
            canvas.fill(x, y + ROW_HEIGHT - 2, x + (right - x) * row.progress() / 100, y + ROW_HEIGHT - 1, SteelStyle.PLAYING);
        }
        if (row.head() != null) {
            canvas.head(row.head(), x + 4, y + 5, 8);
        } else if (row.marker() != null) {
            SteelStyle.icon(canvas, row.marker(), x + 4, y + 4, row.markerColor());
        }

        boolean showActions = hovered && !row.actions().isEmpty();
        int detailWidth = showActions ? row.actions().size() * ACTION_WIDTH : canvas.font.width(row.detail()) + 8;
        int textWidth = rowWidth() - LEAD_WIDTH - detailWidth;
        String text = row.text();
        boolean cut = canvas.font.width(text) > textWidth;
        if (cut) {
            text = canvas.font.plainSubstrByWidth(text, Math.max(0, textWidth - canvas.font.width("..."))) + "...";
        }
        int color = hovered && !row.selected() && row.color() == SteelStyle.TEXT_DIM ? 0xFFE0E0E0 : row.color();
        canvas.text(text, x + LEAD_WIDTH, y + ROW_TEXT_Y, color);

        Component tooltip = cut ? Component.literal(row.text()) : null;
        if (showActions) {
            int over = actionAt(row, mouseX);
            for (int i = 0; i < row.actions().size(); i++) {
                int iconX = right - 2 - (i + 1) * ACTION_WIDTH + 2;
                SteelStyle.icon(canvas, row.actions().get(i).icon(), iconX, y + 4, i == over ? SteelStyle.PLAYING : SteelStyle.TEXT_DIM);
            }
            if (over >= 0) {
                tooltip = row.actions().get(over).label();
            }
        } else {
            canvas.text(row.detail(), right - canvas.font.width(row.detail()) - 4, y + ROW_TEXT_Y, SteelStyle.TEXT_MUTED);
        }
        return tooltip;
    }

    @Override
    boolean pressed(double mouseX, double mouseY, boolean doubleClick) {
        if (scrollable() && mouseX >= getX() + getWidth() - INSET - SCROLLBAR_WIDTH) {
            draggingBar = true;
            scrollTo(mouseY);
            return true;
        }
        int index = rowIndexAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        Row row = rows.get(index);
        int action = actionAt(row, mouseX);
        if (action >= 0) {
            GuiPort.playClick();
            row.actions().get(action).run().run();
            return true;
        }
        if (row.click() != null) {
            row.click().run();
        }
        if (doubleClick && row.doubleClick() != null) {
            row.doubleClick().run();
        }
        return true;
    }

    @Override
    void dragged(double mouseX, double mouseY) {
        if (draggingBar) {
            scrollTo(mouseY);
        }
    }

    @Override
    void released() {
        draggingBar = false;
    }

    private void scrollTo(double mouseY) {
        double fraction = (mouseY - getY() - INSET) / viewHeight();
        setScroll((int) (fraction * maxScroll()));
    }

    @Override
    boolean scrolled(double amount) {
        setScroll(scroll - (int) (amount * ROW_HEIGHT));
        return true;
    }
}
