package solite.ui;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

/**
 * Shadow painter: draws dithered shadow strips along the right + bottom edges
 * of a panel. Two passes — L2 mist (near) then L1 dust (far). Uses only
 * `· : .` glyphs for compatibility.
 *
 * Density ladder:
 *   L1 dust:  `·` on every 4th cell  — ~12% — far shadow edge
 *   L2 mist:  `·` checkerboard        — ~25% — near shadow
 *   L3 grain: `:` mixed with `·`      — ~40% — max density (panel underlay)
 */
public final class ShadowPainter {

    private final Terminal terminal;
    private final Theme theme;

    public ShadowPainter(Terminal terminal, Theme theme) {
        this.terminal = terminal;
        this.theme = theme;
    }

    /**
     * Paint a shadow for a panel at (x, y) with given width and height.
     * Caller must ensure the panel itself is drawn first.
     *
     * @param x      left edge of panel
     * @param y      top edge of panel
     * @param w      panel width
     * @param h      panel height
     * @param theme  current theme (for wax color)
     */
    public void paintShadow(int x, int y, int w, int h, Theme theme) {
        if (w < 4 || h < 2) return;

        AttributedStyle waxStyle = AttributedStyle.DEFAULT.foreground(theme.wax);

        // Pass 1: L2 mist — checkerboard strip along right edge (+2 col, +1 row)
        // and bottom edge (+1 col, +2 row)
        int offset2Right = x + w + 2;
        int offset2Bottom = y + h + 1;
        for (int row = y + 1; row < y + h + 1; row++) {
            // Right edge strip
            if (isCheckerboard(offset2Right, row)) {
                drawChar(offset2Right, row, '·', waxStyle);
            }
            // Bottom edge strip
            if (isCheckerboard(x + 1 + (row - y), offset2Bottom)) {
                drawChar(x + 1 + (row - y), offset2Bottom, '·', waxStyle);
            }
        }

        // Pass 2: L1 dust — every 4th cell, further out (+3 col, +2 row)
        int offset3Right = x + w + 3;
        int offset3Bottom = y + h + 2;
        for (int row = y + 2; row < y + h + 2; row++) {
            if ((row % 4) == 0) {
                drawChar(offset3Right, row, '·', waxStyle);
            }
        }
        for (int col = x + 2; col < x + w + 2; col++) {
            if ((col % 4) == 0) {
                drawChar(col, offset3Bottom, '·', waxStyle);
            }
        }
    }

    /**
     * Paint a banner wash: a row of L1 dust dots fading left→right.
     * Drops the title into the "Hermes glow".
     *
     * @param y         row to paint the wash on (below the title)
     * @param width     terminal width
     */
    public void paintBannerWash(int y, int width, Theme theme) {
        AttributedStyle waxStyle = AttributedStyle.DEFAULT.foreground(theme.wax);
        // Dots thin out: positions 0,2,5,9,14,20,... (increasing gap)
        int pos = 0;
        int gap = 2;
        while (pos < width) {
            drawChar(pos, y, '·', waxStyle);
            pos += gap;
            gap++; // gap grows → dots thin out → fade
        }
    }

    /** Checkerboard test: true if (x+y) is even. */
    private boolean isCheckerboard(int x, int y) {
        return ((x + y) & 1) == 0;
    }

    /** Draw a single attributed char at viewport coords (clamped to screen). */
    private void drawChar(int x, int y, char c, AttributedStyle style) {
        if (x < 0 || y < 0 || x >= terminal.getWidth() || y >= terminal.getHeight()) {
            return;
        }
        AttributedString ac = new AttributedString(String.valueOf(c), style);
        // Use terminal.puts with cursor_address capability for positioning
        try {
            terminal.puts(org.jline.utils.InfoCmp.Capability.cursor_address, y, x);
            terminal.writer().print(ac.toAnsi(terminal));
        } catch (Exception e) {
            // ignore draw failures on incompatible terminals
        }
    }
}
