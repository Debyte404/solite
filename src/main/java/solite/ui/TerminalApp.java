package solite.ui;

import org.jline.terminal.Terminal;
import org.jline.terminal.Size;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Display;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import solite.core.FileOrganizer;

/**
 * Main TUI application. Owns the terminal, theme, layout engine, and display.
 * Phase 2: renders Pick, Gather, Confirm screens.
 */
public final class TerminalApp {

    public enum Screen { PICK, GATHER, CONFIRM }
    private Screen currentScreen = Screen.PICK;
    
    private final Terminal terminal;
    private final Display display;
    private Theme theme;
    private LayoutEngine layout;
    private final ShadowPainter shadowPainter;
    private final FileOrganizer fileOrganizer;
    
    // State
    private String pickFilter = "";
    private Path currentDir = Paths.get(System.getProperty("user.home"));
    private String currentToast = null;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private boolean running = true;

    public TerminalApp(Terminal terminal) {
        this.terminal = terminal;
        this.display = new Display(terminal, true);
        this.theme = Theme.pick(terminal);
        this.layout = new LayoutEngine(terminal.getWidth(), terminal.getHeight());
        this.shadowPainter = new ShadowPainter(terminal, theme);
        this.fileOrganizer = new FileOrganizer();
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
                case 3: // Ctrl+C
                    running = false;
                    break;
                case 'q':
                case 'Q':
                    if (currentScreen == Screen.PICK) {
                        running = false;
                    }
                    break;
                case 27: // Esc
                case 'b':
                case 'B':
                    if (currentScreen == Screen.GATHER) {
                        currentScreen = Screen.PICK;
                        repaint();
                    } else if (currentScreen == Screen.CONFIRM) {
                        currentScreen = Screen.GATHER;
                        repaint();
                    }
                    break;
                case 13: // Enter
                    if (currentScreen == Screen.PICK) {
                        currentScreen = Screen.GATHER;
                        repaint();
                    } else if (currentScreen == Screen.GATHER) {
                        currentScreen = Screen.CONFIRM;
                        repaint();
                    }
                    break;
                case 't':
                case 'T':
                    // Multithreading example for Toast as per rules
                    showToast("✓ Action applied");
                    break;
                default:
                    if (currentScreen == Screen.PICK && key >= 32 && key <= 126) {
                        pickFilter += (char) key;
                        repaint();
                    } else if (key == 127 || key == 8) { // Backspace
                        if (currentScreen == Screen.PICK && !pickFilter.isEmpty()) {
                            pickFilter = pickFilter.substring(0, pickFilter.length() - 1);
                            repaint();
                        }
                    }
                    break;
            }
        }

        scheduler.shutdownNow();

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
            String title = "● solite — " + currentScreen.name() + " mode";
            sb.append(title);
            style = AttributedStyle.DEFAULT.foreground(theme.honey).bold();
        }
        // Banner wash row 1
        else if (y == 1 && !layout.isEmergency()) {
            sb.append(buildBannerWash(width));
            style = AttributedStyle.DEFAULT.foreground(theme.wax);
        }
        // Content area
        else if (y > 1 && y < layout.height - layout.footerRows && !layout.isEmergency()) {
            if (currentScreen == Screen.PICK && y == 3) {
                String folderLine = " ┌─ Folder " + currentDir.toString();
                sb.append(folderLine);
                style = AttributedStyle.DEFAULT.foreground(theme.amber);
            } else if (currentScreen == Screen.PICK && y == 4) {
                String filterLine = " │ Filter: " + pickFilter + (pickFilter.isEmpty() ? "_" : "");
                sb.append(filterLine);
                style = AttributedStyle.DEFAULT.foreground(theme.paper);
            } else if (currentScreen == Screen.GATHER && y == 3) {
                String gatherLine = " ┌─ Groups " + currentDir.toString();
                sb.append(gatherLine);
                style = AttributedStyle.DEFAULT.foreground(theme.amber);
            }
        }
        // Footer row
        else if (y >= layout.height - layout.footerRows && !layout.isEmergency()) {
            String footer;
            if (currentToast != null) {
                footer = " " + currentToast + " ";
                style = AttributedStyle.DEFAULT.foreground(theme.leaf);
            } else {
                if (currentScreen == Screen.PICK) {
                    footer = " Type to filter · Enter select · q quit ";
                } else if (currentScreen == Screen.GATHER) {
                    footer = " b back · 1/2/3 act · u undo · ? keys ";
                } else {
                    footer = " [Enter] Yes, move · [n] No ";
                }
                style = AttributedStyle.DEFAULT.foreground(theme.ash);
            }
            sb.append(footer);
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

    /** Multithreading implementation for Toasts */
    private void showToast(String message) {
        this.currentToast = message;
        repaint();
        scheduler.schedule(() -> {
            this.currentToast = null;
            repaint();
        }, 3, TimeUnit.SECONDS);
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
