package com.metrovoc.phonon.client.gui;

/** Colors taken from the Speaker's steel texture and the panel, shared by every control. */
final class SteelStyle {
    static final int PANEL = 0xE0101820;
    static final int PANEL_EDGE = 0xFF2A3540;
    static final int DIVIDER = 0x40FFFFFF;
    static final int WELL = 0xFF090E13;
    static final int STEEL = 0xFF38393D;
    static final int STEEL_HOVER = 0xFF4B5053;
    static final int STEEL_DISABLED = 0xFF232427;
    static final int HIGHLIGHT = 0xFF676A6F;
    static final int SHADOW = 0xFF15191C;
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT_DIM = 0xFFA0A0A0;
    static final int TEXT_MUTED = 0xFF808080;
    static final int TEXT_OFF = 0xFF606060;
    static final int PLAYING = 0xFF40FF40;
    static final int PAUSED = 0xFFFFFF40;

    private SteelStyle() {}

    static void plate(GuiPort.Canvas canvas, int x, int y, int width, int height, int face, boolean raised) {
        int right = x + width;
        int bottom = y + height;
        canvas.fill(x, y, right, bottom, face);
        if (raised) {
            canvas.fill(x, y, right, y + 1, HIGHLIGHT);
            canvas.fill(x, y, x + 1, bottom, HIGHLIGHT);
            canvas.fill(x, bottom - 1, right, bottom, SHADOW);
            canvas.fill(right - 1, y, right, bottom, SHADOW);
        }
    }

    static void icon(GuiPort.Canvas canvas, String[] rows, int x, int y, int color) {
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (rows[row].charAt(column) == '#') {
                    canvas.fill(x + column, y + row, x + column + 1, y + row + 1, color);
                }
            }
        }
    }

    static String time(long ms) {
        if (ms < 0) return "--:--";
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }
}
