package solite.ui;

import org.jline.terminal.Terminal;
import org.jline.terminal.Size;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Display;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main TUI application. Owns the terminal, theme, layout engine, and display.
 * Phase 1: renders the frame (banner + panel + shadow), handles resize + quit.
 */
public final class TerminalApp {

    private final Terminal terminal;
    private final Display display;
    private Theme theme;
    private LayoutEngine layout;
    private final ShadowPainter shadowPainter;

    private boolean running = true;

    public TerminalApp(Terminal terminal) {
        this.terminal = terminal;
        this.display = new Display(terminal, true);
        this.theme = Theme.pick(terminal);
        this.layout = new LayoutEngine(terminal.getWidth(), terminal.getHeight());
        this.shadowPainter = new ShadowPainter(terminal, theme);
    }

    /** Main event loop. */
    public void run() {
        // Listen for resize events
        terminal.handle(org.jline.terminal.Terminal.Signal.WINCH, signal -> {
            // Recompute layout on next repaint
        });

        // Enable mouse if the theme supports it (JLine 3.30: trackMouse, not setMouseTracking)
        if (theme.mouseTracking) {
            try {
                terminal.trackMouse(org.jline.terminal.Terminal.MouseTracking.Normal);
            } catch (UnsupportedOperationException e) {
                // Mouse not supported — silent fallback
            }
        }

        // Initial paint
        repaint();

        // Event loop: read keys
        while (running) {
            int key = readKey();
            switch (key) {
                case 'q':
                case 'Q':
                case 3: // Ctrl+C
                    running = false;
                    break;
                default:
                    // Phase 1: ignore other keys
                    break;
            }
        }

        // Cleanup
        try {
            terminal.close();
        } catch (IOException e) {
            // ignore on close
        }
    }

    /** Repaint the entire frame. */
    private void repaint() {
        // Check for resize
        Size size = terminal.getSize();
        if (size != null && (size.getColumns() != layout.width || size.getRows() != layout.height)) {
            layout = new LayoutEngine(size.getColumns(), size.getRows());
        }

        List<AttributedString> lines = new ArrayList<>();
        int width = layout.width;
        int height = layout.height;

        // Build each row as an AttributedString
        for (int y = 0; y < height; y++) {
            lines.add(buildRow(y, width));
        }

        display.update(lines, 0);
    }

    /** Build a single row of the screen. */
    private AttributedString buildRow(int y, int width) {
        StringBuilder sb = new StringBuilder();
        AttributedStyle style = AttributedStyle.DEFAULT;

        // Banner row 0
        if (y == 0 && !layout.isEmergency()) {
            String title = "● solite — smart file organizer";
            sb.append(title);
            style = AttributedStyle.DEFAULT.foreground(theme.honey).bold();
        }
        // Banner wash row 1
        else if (y == 1 && !layout.isEmergency()) {
            sb.append(buildBannerWash(width));
            style = AttributedStyle.DEFAULT.foreground(theme.wax);
        }
        // Footer row
        else if (y >= layout.height - layout.footerRows && !layout.isEmergency()) {
            String footer = (layout.breakpoint == LayoutEngine.Breakpoint.XS)
                    ? " ↑↓ move · Enter open · q quit "
                    : " ↑↓ move · Enter open · q quit · ? keys ";
            sb.append(footer);
            style = AttributedStyle.DEFAULT.foreground(theme.ash);
        }

        // Pad to width
        while (sb.length() < width) {
            sb.append(' ');
        }

        return new AttributedString(sb.toString(), style);
    }

    /** Build the dithered banner wash string. */
    private String buildBannerWash(int width) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        int gap = 2;
        while (pos < width) {
            sb.append('·');
            pos += gap;
            gap++;
        }
        return sb.toString();
    }

    /** Read a single key (blocking). Returns -1 on EOF. */
    private int readKey() {
        try {
            return terminal.reader().read();
        } catch (IOException e) {
            running = false;
            return -1;
        }
    }
}
