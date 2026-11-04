package com.metrovoc.phonon.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Scrollable rows: tracks, the queue, playlists, stations and search results.
 * A hovered row shows its action icons in place of its detail text. Long names crop, and keep a tooltip.
 */
final class RowList extends GuiPort.Control {
    static final int ROW_HEIGHT = 18;
    static final int TALL_ROW_HEIGHT = 26;
    private static final int LINE_HEIGHT = 11;
    private static final int BUTTON_HEIGHT = 18;
    private static final int INSET = 2;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int ROW_TEXT_Y = 5;
    private static final int ACTION_WIDTH = 13;
    private static final int LEAD_WIDTH = 16;
    private static final int PICTURE_WIDTH = 40;
    private static final int PICTURE_HEIGHT = 22;
    private static final int CTA_FILL = 0xFF12211A;
    private static final int CTA_HOVER = 0xFF1B3226;
    private static final int HEADER_FILL = 0xFF161E26;

    record Action(String[] icon, Component label, Runnable run) {}

    /** Called with the mouse position, so a menu can open where the click was. */
    interface AtMouse {
        void run(int mouseX, int mouseY);
    }

    /** One row. Everything but the text is optional. */
    static final class Row {
        private final String text;
        @Nullable private UUID key;
        private String subtext = "";
        private String detail = "";
        private int color = SteelStyle.TEXT_DIM;
        private int subColor = SteelStyle.TEXT_MUTED;
        private boolean selected;
        @Nullable private String[] marker;
        private int markerColor;
        @Nullable private UUID head;
        private int progress = -1;
        private boolean pictureSlot;
        @Nullable private GuiPort.Picture picture;
        private List<Action> actions = List.of();
        private boolean actionsAlways;
        @Nullable private Runnable click;
        @Nullable private Runnable doubleClick;
        @Nullable private Runnable leadClick;
        @Nullable private Component tooltip;
        @Nullable private AtMouse detailClick;
        @Nullable private String[] detailIcon;
        private boolean call;
        private boolean header;
        private boolean line;
        private boolean button;
        private boolean grip;

        Row(String text) {
            this.text = text;
        }

        Row key(@Nullable UUID value) { key = value; return this; }
        Row subtext(String value, int valueColor) { subtext = value; subColor = valueColor; return this; }
        Row detail(String value) { detail = value; return this; }
        Row color(int value) { color = value; return this; }
        Row selected(boolean value) { selected = value; return this; }
        Row marker(@Nullable String[] icon, int iconColor) { marker = icon; markerColor = iconColor; return this; }
        Row head(@Nullable UUID player) { head = player; return this; }
        Row progress(int percent) { progress = percent; return this; }
        Row picture(@Nullable GuiPort.Picture value) { pictureSlot = true; picture = value; return this; }
        Row actions(List<Action> value) { actions = value; return this; }
        Row click(@Nullable Runnable value) { click = value; return this; }
        Row doubleClick(@Nullable Runnable value) { doubleClick = value; return this; }
        Row tooltip(@Nullable Component value) { tooltip = value; return this; }
        /** The call to action: what to do next, in green. */
        Row call() { call = true; color = SteelStyle.PLAYING; return this; }
        /** A heading across the list, whose actions always show. */
        Row header(@Nullable Runnable back) { header = true; actionsAlways = true; leadClick = back; color = SteelStyle.TEXT; return this; }
        /** The detail is clickable, with an icon beside it. */
        Row detailClick(AtMouse value, String[] icon) { detailClick = value; detailIcon = icon; return this; }
        /** A line of plain wrapped text. */
        Row line(int lineColor) { line = true; color = lineColor; return this; }
        /** A steel button across the row. */
        Row button(Runnable run) { button = true; click = run; color = SteelStyle.TEXT; return this; }
        /** Can be dragged by its lead space to a new place among the other such rows. */
        Row grip() { grip = true; return this; }

        private int height() {
            return line ? LINE_HEIGHT : button ? BUTTON_HEIGHT + 2 : pictureSlot || !subtext.isEmpty() ? TALL_ROW_HEIGHT : ROW_HEIGHT;
        }
    }

    private final String[] gripIcon;
    private final String[] noPictureIcon;
    private List<Row> rows = new ArrayList<>();
    private List<FormattedCharSequence> emptyMessage = List.of();
    private int scroll;
    private boolean draggingBar;
    private int draggingRow = -1;
    private int draggedFrom = -1;
    @Nullable private BiConsumer<Row, Integer> onMoved;
    private boolean blocked;

    RowList(int x, int y, int width, int height, String[] gripIcon, String[] noPictureIcon) {
        super(x, y, width, height, Component.empty());
        this.gripIcon = gripIcon;
        this.noPictureIcon = noPictureIcon;
    }

    /** Ignored during a drag, which is showing its own order. */
    void setRows(List<Row> rows, List<FormattedCharSequence> emptyMessage) {
        if (draggingRow >= 0) {
            return;
        }
        this.rows = new ArrayList<>(rows);
        this.emptyMessage = emptyMessage;
        setScroll(scroll);
    }

    /** Reports a dragged row and its new place among the draggable rows. */
    void setOnMoved(BiConsumer<Row, Integer> listener) {
        onMoved = listener;
    }

    UUID keyOf(Row row) {
        return row.key;
    }

    /** While a menu is open over the list, the list doesn't react to the mouse. */
    void setBlocked(boolean value) {
        blocked = value;
    }

    int scroll() {
        return scroll;
    }

    void setScroll(int value) {
        scroll = Math.max(0, Math.min(value, maxScroll()));
    }

    void reveal(@Nullable UUID key) {
        int top = 0;
        for (Row row : rows) {
            if (key != null && key.equals(row.key)) {
                if (top < scroll) {
                    setScroll(top);
                } else if (top + row.height() > scroll + viewHeight()) {
                    setScroll(top + row.height() - viewHeight());
                }
                return;
            }
            top += row.height();
        }
    }

    private int viewHeight() {
        return getHeight() - INSET * 2;
    }

    private int contentHeight() {
        int total = 0;
        for (Row row : rows) {
            total += row.height();
        }
        return total;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - viewHeight());
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
        if (offset < 0 || offset >= viewHeight()) {
            return -1;
        }
        int top = -scroll;
        for (int i = 0; i < rows.size(); i++) {
            top += rows.get(i).height();
            if (offset < top) {
                return i;
            }
        }
        return -1;
    }

    /** Which action of a row the mouse is over, counted from the right edge, or -1. */
    private int actionAt(Row row, double mouseX) {
        int right = getX() + INSET + rowWidth();
        int index = (int) ((right - 2 - mouseX) / ACTION_WIDTH);
        return mouseX < right - 2 && index >= 0 && index < row.actions.size() ? index : -1;
    }

    private boolean overDetail(Row row, double mouseX) {
        return row.detailClick != null && mouseX >= getX() + INSET + rowWidth() - 90;
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

        int hovered = !blocked && isMouseOver(mouseX, mouseY) ? rowIndexAt(mouseX, mouseY) : -1;
        Component tooltip = null;
        canvas.clip(x + INSET, y + INSET, x + getWidth() - INSET, y + getHeight() - INSET);
        int rowY = y + INSET - scroll;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (rowY >= y + getHeight()) {
                break;
            }
            if (rowY + row.height() > y) {
                Component rowTooltip = drawRow(canvas, row, x + INSET, rowY, i == hovered || i == draggingRow, mouseX);
                if (i == hovered) {
                    tooltip = rowTooltip;
                }
            }
            rowY += row.height();
        }
        canvas.unclip();

        if (scrollable()) {
            int barX = x + getWidth() - INSET - SCROLLBAR_WIDTH;
            int barY = y + INSET;
            int thumbHeight = Math.max(16, viewHeight() * viewHeight() / contentHeight());
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
        int height = row.height();
        if (row.line) {
            canvas.text(row.text, x + 6, y + 1, row.color);
            return null;
        }
        if (row.button) {
            int face = hovered ? SteelStyle.STEEL_HOVER : SteelStyle.STEEL;
            SteelStyle.plate(canvas, x + 6, y + 1, rowWidth() - 12, BUTTON_HEIGHT, face, true);
            canvas.text(row.text, x + (rowWidth() - canvas.font.width(row.text)) / 2, y + 6, row.color);
            return row.tooltip;
        }
        if (row.selected) {
            canvas.fill(x, y, right, y + height, SteelStyle.TEXT_DIM);
            canvas.fill(x + 1, y + 1, right - 1, y + height - 1, 0xFF20262B);
        } else if (row.call) {
            canvas.fill(x, y, right, y + height, hovered ? CTA_HOVER : CTA_FILL);
        } else if (row.header) {
            canvas.fill(x, y, right, y + height, HEADER_FILL);
        } else if (hovered) {
            canvas.fill(x, y, right, y + height, 0x20FFFFFF);
        }
        if (row.progress >= 0) {
            canvas.fill(x, y + height - 2, x + (right - x) * row.progress / 100, y + height - 1, SteelStyle.PLAYING);
        }

        int lead = LEAD_WIDTH;
        if (row.pictureSlot) {
            lead = PICTURE_WIDTH + 6;
            int picY = y + (height - PICTURE_HEIGHT) / 2;
            if (row.picture != null) {
                canvas.picture(row.picture, x + 2, picY, PICTURE_WIDTH, PICTURE_HEIGHT);
            } else {
                canvas.fill(x + 2, picY, x + 2 + PICTURE_WIDTH, picY + PICTURE_HEIGHT, 0xFF000000);
                SteelStyle.icon(canvas, noPictureIcon, x + 2 + (PICTURE_WIDTH - 9) / 2, picY + (PICTURE_HEIGHT - 9) / 2, SteelStyle.TEXT_OFF);
            }
        } else if (row.grip) {
            SteelStyle.icon(canvas, gripIcon, x + 4, y + (height - 9) / 2, SteelStyle.TEXT_OFF);
        } else if (row.head != null) {
            if (row.marker != null) {
                SteelStyle.icon(canvas, row.marker, x + 4, y + (height - 9) / 2, row.markerColor);
                lead = LEAD_WIDTH + 11;
                canvas.head(row.head, x + LEAD_WIDTH, y + (height - 8) / 2, 8);
            } else {
                canvas.head(row.head, x + 4, y + (height - 8) / 2, 8);
            }
        } else if (row.marker != null) {
            SteelStyle.icon(canvas, row.marker, x + 4, y + (height - 9) / 2, row.markerColor);
        }

        boolean showActions = !row.actions.isEmpty() && (hovered || row.actionsAlways);
        int detailWidth = showActions ? row.actions.size() * ACTION_WIDTH
            : row.detail.isEmpty() ? 4 : canvas.font.width(row.detail) + 8 + (row.detailIcon != null ? 10 : 0);
        int textWidth = rowWidth() - lead - detailWidth;
        String text = row.text;
        boolean cut = canvas.font.width(text) > textWidth;
        if (cut) {
            text = canvas.font.plainSubstrByWidth(text, Math.max(0, textWidth - canvas.font.width("..."))) + "...";
        }
        int color = hovered && !row.selected && row.color == SteelStyle.TEXT_DIM ? 0xFFE0E0E0 : row.color;
        boolean two = !row.subtext.isEmpty();
        int textY = two ? y + 4 : y + (height - canvas.font.lineHeight) / 2 + 1;
        canvas.text(text, x + lead, textY, color);
        if (two) {
            String sub = row.subtext;
            if (canvas.font.width(sub) > textWidth) {
                sub = canvas.font.plainSubstrByWidth(sub, Math.max(0, textWidth - canvas.font.width("..."))) + "...";
            }
            canvas.text(sub, x + lead, y + 15, row.subColor);
        }

        Component tooltip = row.tooltip != null ? row.tooltip : cut ? Component.literal(row.text) : null;
        if (showActions) {
            int over = hovered ? actionAt(row, mouseX) : -1;
            for (int i = 0; i < row.actions.size(); i++) {
                int iconX = right - 2 - (i + 1) * ACTION_WIDTH + 2;
                SteelStyle.icon(canvas, row.actions.get(i).icon(), iconX, y + (height - 9) / 2, i == over ? SteelStyle.PLAYING : SteelStyle.TEXT_DIM);
            }
            if (over >= 0) {
                tooltip = row.actions.get(over).label();
            }
        } else if (!row.detail.isEmpty()) {
            boolean link = row.detailClick != null;
            int detailColor = link && hovered && overDetail(row, mouseX) ? SteelStyle.PLAYING : link ? SteelStyle.TEXT_DIM : SteelStyle.TEXT_MUTED;
            int iconRoom = row.detailIcon != null ? 10 : 0;
            int detailX = right - canvas.font.width(row.detail) - 4 - iconRoom;
            canvas.text(row.detail, detailX, y + (height - canvas.font.lineHeight) / 2 + 1, detailColor);
            if (row.detailIcon != null) {
                SteelStyle.icon(canvas, row.detailIcon, right - 12, y + (height - 9) / 2, detailColor);
            }
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
        if (row.grip && mouseX < getX() + INSET + LEAD_WIDTH) {
            draggingRow = index;
            draggedFrom = index;
            return true;
        }
        if (row.header && row.leadClick != null && mouseX < getX() + INSET + LEAD_WIDTH) {
            GuiPort.playClick();
            row.leadClick.run();
            return true;
        }
        if (row.detailClick != null && overDetail(row, mouseX)) {
            GuiPort.playClick();
            row.detailClick.run((int) mouseX, (int) mouseY);
            return true;
        }
        int action = actionAt(row, mouseX);
        if (action >= 0) {
            GuiPort.playClick();
            row.actions.get(action).run().run();
            return true;
        }
        if (row.button || row.call) {
            GuiPort.playClick();
        }
        if (row.click != null) {
            row.click.run();
        }
        if (doubleClick && row.doubleClick != null) {
            row.doubleClick.run();
        }
        return true;
    }

    @Override
    void dragged(double mouseX, double mouseY) {
        if (draggingBar) {
            scrollTo(mouseY);
        } else if (draggingRow >= 0) {
            int target = rowIndexAt(getX() + INSET + 1, mouseY);
            if (target >= 0 && target != draggingRow && rows.get(target).grip) {
                rows.add(target, rows.remove(draggingRow));
                draggingRow = target;
            }
        }
    }

    @Override
    void released() {
        draggingBar = false;
        if (draggingRow >= 0) {
            int place = 0;
            for (int i = 0; i < draggingRow; i++) {
                place += rows.get(i).grip ? 1 : 0;
            }
            Row moved = rows.get(draggingRow);
            boolean changed = draggingRow != draggedFrom;
            draggingRow = -1;
            draggedFrom = -1;
            if (changed && onMoved != null) {
                onMoved.accept(moved, place);
            }
        }
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
