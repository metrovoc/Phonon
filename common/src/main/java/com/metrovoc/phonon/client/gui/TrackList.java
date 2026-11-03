package com.metrovoc.phonon.client.gui;

import com.metrovoc.phonon.audio.AudioResource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Scrollable track list that marks the selected and the playing track.
 */
final class TrackList extends GuiPort.Control {
    static final int ROW_HEIGHT = 18;
    private static final int INSET = 2;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int ROW_TEXT_Y = 5;
    private static final String[] NOTE = {
        "...#####.", "...#...#.", "...#...#.", "...#...#.", "...#...#.",
        ".###.###.", "####.####", "####.####", ".##...##."
    };

    private final Consumer<AudioResource> onSelect;
    private final Consumer<AudioResource> onPlay;
    private List<AudioResource> tracks = List.of();
    private List<FormattedCharSequence> emptyMessage = List.of();
    @Nullable private UUID selected;
    @Nullable private UUID playing;
    private int playingColor = SteelStyle.PLAYING;
    private int scroll;
    private boolean draggingBar;

    TrackList(int x, int y, int width, int height, Consumer<AudioResource> onSelect, Consumer<AudioResource> onPlay) {
        super(x, y, width, height, Component.empty());
        this.onSelect = onSelect;
        this.onPlay = onPlay;
    }

    void setTracks(List<AudioResource> tracks) {
        this.tracks = tracks;
        setScroll(scroll);
    }

    /** Shown in place of the rows when there are none. */
    void setEmptyMessage(List<FormattedCharSequence> lines) {
        emptyMessage = lines;
    }

    void setSelected(@Nullable UUID id) {
        selected = id;
    }

    void setPlaying(@Nullable UUID id, int color) {
        playing = id;
        playingColor = color;
    }

    int scroll() {
        return scroll;
    }

    void setScroll(int value) {
        scroll = Math.max(0, Math.min(value, maxScroll()));
    }

    void reveal(@Nullable UUID id) {
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).id().equals(id)) {
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
        return Math.max(0, tracks.size() * ROW_HEIGHT - viewHeight());
    }

    private boolean scrollable() {
        return maxScroll() > 0;
    }

    private int rowWidth() {
        return getWidth() - INSET * 2 - (scrollable() ? SCROLLBAR_WIDTH + 1 : 0);
    }

    @Nullable
    private AudioResource rowAt(double mouseX, double mouseY) {
        int left = getX() + INSET;
        if (mouseX < left || mouseX >= left + rowWidth()) {
            return null;
        }
        int offset = (int) mouseY - getY() - INSET;
        int index = offset < 0 || offset >= viewHeight() ? -1 : (offset + scroll) / ROW_HEIGHT;
        return index >= 0 && index < tracks.size() ? tracks.get(index) : null;
    }

    @Override
    void draw(GuiPort.Canvas canvas, int mouseX, int mouseY) {
        int x = getX();
        int y = getY();
        canvas.fill(x, y, x + getWidth(), y + getHeight(), SteelStyle.WELL);

        if (tracks.isEmpty()) {
            int lineY = y + 10;
            for (FormattedCharSequence line : emptyMessage) {
                canvas.line(line, x + 8, lineY, SteelStyle.TEXT_DIM);
                lineY += canvas.font.lineHeight + 2;
            }
            return;
        }

        boolean inside = isMouseOver(mouseX, mouseY);
        canvas.clip(x + INSET, y + INSET, x + getWidth() - INSET, y + getHeight() - INSET);
        int first = scroll / ROW_HEIGHT;
        for (int i = first; i < tracks.size(); i++) {
            int rowY = y + INSET + i * ROW_HEIGHT - scroll;
            if (rowY >= y + getHeight()) {
                break;
            }
            AudioResource track = tracks.get(i);
            drawRow(canvas, track, x + INSET, rowY, inside && rowAt(mouseX, mouseY) == track);
        }
        canvas.unclip();

        if (scrollable()) {
            int barX = x + getWidth() - INSET - SCROLLBAR_WIDTH;
            int barY = y + INSET;
            int thumbHeight = Math.max(16, viewHeight() * viewHeight() / (tracks.size() * ROW_HEIGHT));
            int thumbY = barY + scroll * (viewHeight() - thumbHeight) / maxScroll();
            canvas.fill(barX, barY, barX + SCROLLBAR_WIDTH, barY + viewHeight(), 0xFF000000);
            SteelStyle.plate(canvas, barX, thumbY, SCROLLBAR_WIDTH, thumbHeight, SteelStyle.STEEL, true);
        }

        AudioResource hovered = inside ? rowAt(mouseX, mouseY) : null;
        if (hovered != null && canvas.font.width(hovered.name()) > nameWidth(canvas, hovered)) {
            canvas.tooltip(Component.literal(hovered.name()), mouseX, mouseY);
        }
    }

    private int nameWidth(GuiPort.Canvas canvas, AudioResource track) {
        return rowWidth() - 18 - canvas.font.width(SteelStyle.time(track.durationMs())) - 8;
    }

    private void drawRow(GuiPort.Canvas canvas, AudioResource track, int x, int y, boolean hovered) {
        int right = x + rowWidth();
        boolean isSelected = track.id().equals(selected);
        boolean isPlaying = track.id().equals(playing);
        if (isSelected) {
            canvas.fill(x, y, right, y + ROW_HEIGHT, SteelStyle.TEXT_DIM);
            canvas.fill(x + 1, y + 1, right - 1, y + ROW_HEIGHT - 1, 0xFF20262B);
        } else if (hovered) {
            canvas.fill(x, y, right, y + ROW_HEIGHT, 0x20FFFFFF);
        }
        if (isPlaying) {
            SteelStyle.icon(canvas, NOTE, x + 4, y + 4, playingColor);
        }

        int color = isPlaying ? playingColor : isSelected ? SteelStyle.TEXT : hovered ? 0xFFE0E0E0 : SteelStyle.TEXT_DIM;
        String name = track.name();
        int width = nameWidth(canvas, track);
        if (canvas.font.width(name) > width) {
            name = canvas.font.plainSubstrByWidth(name, width - canvas.font.width("...")) + "...";
        }
        canvas.text(name, x + 16, y + ROW_TEXT_Y, color);

        String time = SteelStyle.time(track.durationMs());
        canvas.text(time, right - canvas.font.width(time) - 4, y + ROW_TEXT_Y, SteelStyle.TEXT_MUTED);
    }

    @Override
    boolean pressed(double mouseX, double mouseY, boolean doubleClick) {
        if (scrollable() && mouseX >= getX() + getWidth() - INSET - SCROLLBAR_WIDTH) {
            draggingBar = true;
            scrollTo(mouseY);
            return true;
        }
        AudioResource row = rowAt(mouseX, mouseY);
        if (row == null) {
            return false;
        }
        onSelect.accept(row);
        if (doubleClick) {
            onPlay.accept(row);
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

    /** The track after the given one, wrapping at the ends. */
    @Nullable
    AudioResource neighbor(@Nullable UUID id, int direction) {
        if (tracks.isEmpty()) {
            return null;
        }
        List<UUID> ids = new ArrayList<>();
        for (AudioResource track : tracks) {
            ids.add(track.id());
        }
        int index = ids.indexOf(id);
        if (index < 0) {
            return tracks.get(direction > 0 ? 0 : tracks.size() - 1);
        }
        return tracks.get(Math.floorMod(index + direction, tracks.size()));
    }

    boolean isEmpty() {
        return tracks.isEmpty();
    }

    boolean contains(@Nullable UUID id) {
        return tracks.stream().anyMatch(track -> Objects.equals(track.id(), id));
    }
}
