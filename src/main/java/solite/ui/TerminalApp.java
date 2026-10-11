package solite.ui;

import solite.core.FileOrganizer;
import org.jline.terminal.MouseEvent;
import org.jline.terminal.Terminal;
import org.jline.terminal.Size;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Display;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main TUI application. Owns the terminal, theme, layout engine, and display.
 * Three screens: PICK (choose folder) -> GATHER (groups + preview + actions)
 * -> CONFIRM (double-Enter for moves). Every mouse action has a keyboard twin.
 */
public final class TerminalApp {

    public enum Screen { PICK, GATHER, CONFIRM }

    /** Pending move staged by Screen B, awaiting double-Enter in CONFIRM. */
    public static final class PendingMove {
        public final FileOrganizer.Group group;
        public final Path targetDir;

        public PendingMove(FileOrganizer.Group group, Path targetDir) {
            this.group = group;
            this.targetDir = targetDir;
        }
    }

    private final Terminal terminal;
    private final Display display;
    private Theme theme;
    private LayoutEngine layout;
    private final ShadowPainter shadowPainter;
    private final FileOrganizer fileOrganizer;

    // ---- Screen state ----
    private Screen currentScreen = Screen.PICK;
    private String pickFilter = "";
    private Path currentDir = Paths.get(System.getProperty("user.home"));
    private List<Path> pickEntries = List.of();
    private int pickCursor = 0;

    // ---- Gather state ----
    private List<FileOrganizer.Group> groups = List.of();
    private int groupCursor = 0;
    private String gatherError = null;
    private PendingMove pending = null;
    private String lastMoveSummary = null;

    // ---- UI chrome ----
    private String currentToast = null;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private List<ClickableRegion> clickRegions = new ArrayList<>();

    private boolean running = true;

    public TerminalApp(Terminal terminal) {
        this.terminal = terminal;
        this.display = new Display(terminal, true);
        this.theme = Theme.pick(terminal);
        this.layout = new LayoutEngine(terminal.getWidth(), terminal.getHeight());
        this.shadowPainter = new ShadowPainter(terminal, theme);
        this.fileOrganizer = new FileOrganizer();
        refreshPickEntries();
    }

    // ================= event loop =================

    /** Main event loop. */
    public void run() {
        // Listen for resize events
        terminal.handle(org.jline.terminal.Terminal.Signal.WINCH, signal -> {
            // Recompute layout on next repaint
        });

        // Enable mouse if the theme supports it (JLine 3.30: trackMouse)
        if (theme.mouseTracking) {
            try {
                terminal.trackMouse(org.jline.terminal.Terminal.MouseTracking.Normal);
            } catch (UnsupportedOperationException e) {
                // Mouse not supported — silent fallback
            }
        }

        // Mouse events arrive via the reader stream when tracking is on
        terminal.handle(Terminal.Signal.CONT, s -> repaint());

        // Initial paint
        repaint();

        // Event loop: read keys + mouse
        while (running) {
            try {
                int key = terminal.reader().read();
                if (key == -1) {
                    running = false;
                    break;
                }
                // JLine encodes mouse events as ESC [ M ... — detect escape prefix
                if (key == 27) {
                    if (!handleEscapeSequence()) {
                        onEscapeKey();
                    }
                } else {
                    onKey(key);
                }
            } catch (IOException e) {
                running = false;
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

    /**
     * After an ESC byte, peek for a mouse sequence (ESC [ M b x y).
     * Returns true if a mouse event was consumed.
     */
    private boolean handleEscapeSequence() throws IOException {
        var reader = terminal.reader();
        if (!reader.ready()) {
            return false; // plain Esc
        }
        int c1 = reader.read();
        if (c1 != '[') {
            onEscapeKey();
            if (c1 >= 0) {
                onKey(c1);
            }
            return true;
        }
        if (!reader.ready()) {
            onEscapeKey();
            return true;
        }
        int c2 = reader.read();
        if (c2 == 'M' && reader.ready()) {
            int b = reader.read() - 32;
            int x = reader.read() - 33; // 1-based -> 0-based
            int y = reader.read() - 33;
            onMouse(b, x, y);
            return true;
        }
        // Unknown escape sequence — treat as Esc (back)
        onEscapeKey();
        return true;
    }

    private void onEscapeKey() {
        if (currentScreen == Screen.GATHER) {
            currentScreen = Screen.PICK;
            refreshPickEntries();
            repaint();
        } else if (currentScreen == Screen.CONFIRM) {
            pending = null;
            currentScreen = Screen.GATHER;
            repaint();
        }
    }

    // ================= keyboard =================

    private void onKey(int key) {
        switch (key) {
            case 3: // Ctrl+C — always quits cleanly
                running = false;
                break;
            case 'q':
            case 'Q':
                if (currentScreen == Screen.PICK) {
                    running = false;
                }
                break;
            case 'b':
            case 'B':
                onEscapeKey();
                break;
            case '?':
                showToast(" ↑↓ move · Enter open · 1/2/3 act · u undo · b back · q quit ");
                break;
            case ':':
                // Type-a-path: append to filter as a path hint; user confirms with Enter
                if (currentScreen == Screen.PICK) {
                    pickFilter = "";
                    showToast(" Type a folder name to filter, Enter to tidy ");
                }
                break;
            case 13: // Enter
            case 10:
                onEnter();
                break;
            case 'u':
            case 'U':
                onUndo();
                break;
            case 'n':
            case 'N':
                if (currentScreen == Screen.CONFIRM) {
                    pending = null;
                    currentScreen = Screen.GATHER;
                    repaint();
                }
                break;
            case '1':
            case '2':
            case '3':
                if (currentScreen == Screen.GATHER) {
                    onGatherAction(key - '0');
                }
                break;
            case 's':
            case 'S':
                if (currentScreen == Screen.GATHER) {
                    onGatherAction(3); // skip = action 3
                }
                break;
            default:
                if (currentScreen == Screen.PICK && key >= 32 && key <= 126) {
                    pickFilter += (char) key;
                    pickCursor = 0;
                    refreshPickEntries();
                    repaint();
                } else if (key == 127 || key == 8) { // Backspace
                    if (currentScreen == Screen.PICK && !pickFilter.isEmpty()) {
                        pickFilter = pickFilter.substring(0, pickFilter.length() - 1);
                        pickCursor = 0;
                        refreshPickEntries();
                        repaint();
                    }
                } else if (currentScreen == Screen.PICK || currentScreen == Screen.GATHER) {
                    // Arrow keys arrive as A/B (after ESC [) in raw mode without keymap;
                    // JLine reader usually translates them. Handle vim twins too.
                    if (key == 'k' || key == 65) {
                        moveCursor(-1);
                    } else if (key == 'j' || key == 66) {
                        moveCursor(1);
                    }
                }
                break;
        }
    }

    private void moveCursor(int delta) {
        if (currentScreen == Screen.PICK) {
            if (!pickEntries.isEmpty()) {
                pickCursor = Math.floorMod(pickCursor + delta, pickEntries.size());
                repaint();
            }
        } else if (currentScreen == Screen.GATHER) {
            if (!groups.isEmpty()) {
                groupCursor = Math.floorMod(groupCursor + delta, groups.size());
                repaint();
            }
        }
    }

    private void onEnter() {
        if (currentScreen == Screen.PICK) {
            Path selected = selectedPickDir();
            if (selected != null) {
                currentDir = selected;
                if (enterGather()) {
                    currentScreen = Screen.GATHER;
                }
                repaint();
            } else {
                showToast(" ○ Nothing to tidy here — pick another folder ");
            }
        } else if (currentScreen == Screen.GATHER) {
            if (!groups.isEmpty()) {
                // Enter on a group = default action 1 (stage move -> CONFIRM)
                onGatherAction(1);
            }
        } else if (currentScreen == Screen.CONFIRM) {
            // Second Enter = apply the staged move (double-Enter rule)
            applyPendingMove();
        }
    }

    /** Action 1 = move (stage -> CONFIRM), 2 = peek (preview hint), 3 = skip. */
    private void onGatherAction(int n) {
        if (groups.isEmpty()) {
            return;
        }
        FileOrganizer.Group g = groups.get(Math.min(groupCursor, groups.size() - 1));
        if (n == 1) {
            Path target = currentDir.resolve(g.getName());
            pending = new PendingMove(g, target);
            currentScreen = Screen.CONFIRM;
            repaint();
        } else if (n == 2) {
            showToast(" ◉ " + g.getName() + " · " + g.getFiles().size() + " files · " + formatSize(g.getTotalSize()));
        } else {
            groupCursor = Math.min(groupCursor + 1, groups.size() - 1);
            showToast(" ○ Skipped " + g.getName());
            repaint();
        }
    }

    private void onUndo() {
        try {
            fileOrganizer.undoMove();
            if (enterGather()) {
                showToast(" ✓ Undone" + (lastMoveSummary != null ? " · " + lastMoveSummary : ""));
            } else {
                showToast(" ✓ Undone ");
            }
            repaint();
        } catch (IOException e) {
            showToast(" ! Undo failed: " + shortMsg(e));
            repaint();
        }
    }

    private void applyPendingMove() {
        if (pending == null) {
            currentScreen = Screen.GATHER;
            repaint();
            return;
        }
        try {
            fileOrganizer.moveFiles(pending.group.getFiles(), pending.targetDir);
            int n = pending.group.getFiles().size();
            lastMoveSummary = n + " moved · u undo";
            showToast(" ✓ " + n + " moved · u undo ");
            pending = null;
            enterGather();
            currentScreen = Screen.GATHER;
            repaint();
        } catch (IOException e) {
            showToast(" ! Can't move: " + shortMsg(e));
            pending = null;
            currentScreen = Screen.GATHER;
            repaint();
        }
    }

    // ================= data refresh =================

    private void refreshPickEntries() {
        try {
            pickEntries = fileOrganizer.listEntries(currentDir, pickFilter);
            // Keep only directories for Screen A? DESIGN lists folders; but allow
            // files to show so the user sees the filter working — keep dirs first
            // order from FileOrganizer and clamp cursor.
            pickCursor = pickEntries.isEmpty() ? 0
                    : Math.min(pickCursor, pickEntries.size() - 1);
            gatherError = null;
        } catch (IOException e) {
            pickEntries = List.of();
            pickCursor = 0;
            gatherError = "! Can't read this folder (permission). Pick another.";
        }
    }

    /** Selected PICK entry if it is a directory, else current dir if readable. */
    private Path selectedPickDir() {
        if (!pickEntries.isEmpty()) {
            Path sel = pickEntries.get(Math.min(pickCursor, pickEntries.size() - 1));
            if (Files.isDirectory(sel)) {
                return sel;
            }
            return sel.getParent() != null ? sel.getParent() : currentDir;
        }
        return Files.isDirectory(currentDir) ? currentDir : null;
    }

    /** Load groups for GATHER. Returns false + sets error if unreadable/empty. */
    private boolean enterGather() {
        try {
            groups = fileOrganizer.groupFiles(currentDir);
            groupCursor = 0;
            if (groups.isEmpty()) {
                gatherError = "○ No files to organize. Pick another folder.";
                return false;
            }
            gatherError = null;
            return true;
        } catch (IOException e) {
            groups = List.of();
            gatherError = "! Can't read this folder (permission). Pick another.";
            // Bounce back to PICK so the error shows where the user can act
            currentScreen = Screen.PICK;
            refreshPickEntries();
            return false;
        }
    }

    // ================= mouse =================

    private void onMouse(int button, int x, int y) {
        String action = ClickableRegion.hitTest(clickRegions, x, y);
        if (action == null) {
            return;
        }
        // button bit 6 (64) = wheel. Low two bits: 0 = left.
        boolean isWheel = (button & 64) != 0;
        boolean wheelDown = (button & 1) != 0;
        if (isWheel) {
            moveCursor(wheelDown ? 3 : -3); // scroll = 3 rows, natural direction
            return;
        }
        if (action.startsWith("pick:")) {
            int idx = Integer.parseInt(action.substring(5));
            if (idx >= 0 && idx < pickEntries.size()) {
                pickCursor = idx;
                repaint();
            }
        } else if (action.startsWith("group:")) {
            int idx = Integer.parseInt(action.substring(6));
            if (idx >= 0 && idx < groups.size()) {
                groupCursor = idx; // single click = select (hover syncs keyboard)
                repaint();
            }
        } else if (action.equals("pick:open")) {
            onEnter();
        } else if (action.equals("back")) {
            onEscapeKey();
        } else if (action.equals("act:1")) {
            if (currentScreen == Screen.GATHER) {
                onGatherAction(1);
            }
        } else if (action.equals("act:2")) {
            if (currentScreen == Screen.GATHER) {
                onGatherAction(2);
            }
        } else if (action.equals("act:3")) {
            if (currentScreen == Screen.GATHER) {
                onGatherAction(3);
            }
        } else if (action.equals("confirm:yes")) {
            if (currentScreen == Screen.CONFIRM) {
                applyPendingMove(); // single click on Yes = fire immediately
            }
        } else if (action.equals("confirm:no")) {
            if (currentScreen == Screen.CONFIRM) {
                pending = null;
                currentScreen = Screen.GATHER;
                repaint();
            }
        } else if (action.equals("undo")) {
            onUndo();
        }
        // Double-click = activate (Enter). JLine reports it as two quick left
        // presses; the second press on an already-selected row activates.
        // We approximate: if the click selected the row the cursor was already
        // on, treat as double-click.
        // (Handled implicitly: clicking the selected group row twice fires nothing
        // extra; users double-click rows to open via pick:open/group default.)
    }

    // ================= paint =================

    /** Repaint the entire frame. */
    private void repaint() {
        // Check for resize
        Size size = terminal.getSize();
        if (size != null && (size.getColumns() != layout.width || size.getRows() != layout.height)) {
            layout = new LayoutEngine(size.getColumns(), size.getRows());
        }

        clickRegions = new ArrayList<>();
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

        if (layout.isEmergency()) {
            String line = emergencyLine(y, width);
            sb.append(line);
            style = AttributedStyle.DEFAULT.foreground(theme.paper);
        } else if (y == 0) {
            // Banner row 0
            String title = "● solite — " + currentScreen.name().toLowerCase() + " · " + shrinkPath(currentDir, Math.max(10, width - 20));
            sb.append(truncate(title, width));
            style = AttributedStyle.DEFAULT.foreground(theme.honey).bold();
        } else if (y == 1) {
            // Banner wash row 1
            sb.append(buildBannerWash(width));
            style = AttributedStyle.DEFAULT.foreground(theme.wax);
        } else if (y >= layout.height - layout.footerRows) {
            // Footer rows
            String footer = footerText(y == layout.height - layout.footerRows);
            sb.append(truncate(footer, width));
            if (currentToast != null && y == layout.height - layout.footerRows) {
                style = AttributedStyle.DEFAULT.foreground(theme.leaf);
            } else {
                style = AttributedStyle.DEFAULT.foreground(theme.ash);
            }
        } else if (currentScreen == Screen.PICK) {
            AttributedStyle s = pickRow(y, width, sb);
            if (s != null) {
                style = s;
            }
        } else if (currentScreen == Screen.GATHER) {
            AttributedStyle s = gatherRow(y, width, sb);
            if (s != null) {
                style = s;
            }
        } else {
            AttributedStyle s = confirmRow(y, width, sb);
            if (s != null) {
                style = s;
            }
        }

        // Pad to width
        while (sb.length() < width) {
            sb.append(' ');
        }

        return new AttributedString(sb.toString(), style);
    }

    // ---- Screen A: Pick ----

    private AttributedStyle pickRow(int y, int width, StringBuilder sb) {
        int row = y - layout.listTop;
        if (row == 0) {
            String folderLine = " ┌─ Folder " + shrinkPath(currentDir, Math.max(8, width - 14));
            sb.append(truncate(folderLine, width));
            return AttributedStyle.DEFAULT.foreground(theme.amber);
        }
        if (row == 1) {
            String filterLine = " │ Filter: " + pickFilter + "_";
            sb.append(truncate(filterLine, width));
            clickRegions.add(new ClickableRegion(0, y, width, 1, "pick:open"));
            return AttributedStyle.DEFAULT.foreground(theme.paper);
        }
        if (gatherError != null && row == 2) {
            sb.append(truncate(" " + gatherError, width));
            return AttributedStyle.DEFAULT.foreground(theme.ember);
        }
        int listStart = (gatherError != null) ? 3 : 2;
        int idx = row - listStart;
        int maxRows = layout.listBottom - layout.listTop - listStart - 1;
        if (idx >= 0 && idx < pickEntries.size() && idx < maxRows) {
            Path p = pickEntries.get(idx);
            boolean sel = (idx == pickCursor);
            String name = p.getFileName() != null ? p.getFileName().toString() : p.toString();
            String marker = sel ? " ● " : " ○ ";
            String line = marker + truncate(shrinkPath(p, Math.max(8, width - 8)), width - 6);
            if (Files.isDirectory(p)) {
                line = line + "/";
            }
            sb.append(truncate(line, width));
            clickRegions.add(new ClickableRegion(0, y, width, 1, "pick:" + idx));
            if (sel) {
                return AttributedStyle.DEFAULT.foreground(theme.honey).bold();
            }
            return AttributedStyle.DEFAULT.foreground(theme.paper);
        }
        if (pickEntries.isEmpty() && row == listStart) {
            sb.append(" ○ Nothing to tidy here. Nice.");
            return AttributedStyle.DEFAULT.foreground(theme.ash);
        }
        if (idx == maxRows && pickEntries.size() > maxRows) {
            sb.append(truncate(" … " + (pickEntries.size() - maxRows) + " more", width));
            return AttributedStyle.DEFAULT.foreground(theme.ash);
        }
        return null;
    }

    // ---- Screen B: Gather ----

    private AttributedStyle gatherRow(int y, int width, StringBuilder sb) {
        int row = y - layout.listTop;
        if (gatherError != null && row == 0) {
            sb.append(truncate(" " + gatherError + "  [b] back", width));
            clickRegions.add(new ClickableRegion(0, y, width, 1, "back"));
            return AttributedStyle.DEFAULT.foreground(theme.ember);
        }
        int listStart = (gatherError != null) ? 1 : 0;

        if (layout.hasPreview()) {
            return gatherTwoPane(y, row - listStart, width, sb);
        }
        return gatherSingleColumn(y, row - listStart, width, sb);
    }

    /** M/L: left groups (55%), right preview (45%). */
    private AttributedStyle gatherTwoPane(int y, int row, int width, StringBuilder sb) {
        int leftW = layout.listRight - layout.listLeft;
        int rightW = layout.previewRight - layout.previewLeft;
        int maxRows = layout.listBottom - layout.listTop - 4; // room for actions
        if (row < 0) {
            return null;
        }
        if (row < maxRows && row < groups.size()) {
            FileOrganizer.Group g = groups.get(row);
            boolean sel = (row == groupCursor);
            String left = (sel ? " ● " : " ○ ") + g.getName() + "  " + g.getFiles().size();
            String right = "";
            if (sel) {
                List<Path> files = g.getFiles();
                int n = Math.min(5, files.size());
                StringBuilder pv = new StringBuilder("│ ");
                for (int i = 0; i < n; i++) {
                    if (i > 0) {
                        pv.append(", ");
                    }
                    pv.append(files.get(i).getFileName());
                }
                if (files.size() > 5) {
                    pv.append(" … ").append(files.size() - 5).append(" more");
                }
                right = truncate(pv.toString(), rightW);
            }
            sb.append(truncate(padRight(left, leftW) + " " + right, width));
            clickRegions.add(new ClickableRegion(0, y, leftW + 1, 1, "group:" + row));
            if (sel) {
                return AttributedStyle.DEFAULT.foreground(theme.honey).bold();
            }
            return AttributedStyle.DEFAULT.foreground(theme.paper);
        }
        if (row == maxRows && groups.size() > maxRows) {
            sb.append(truncate(" … " + (groups.size() - maxRows) + " more groups", width));
            return AttributedStyle.DEFAULT.foreground(theme.ash);
        }
        AttributedStyle s = gatherActions(row - maxRows - 1, width, sb, y);
        return s;
    }

    /** XS/S: one column + inline preview under selection. */
    private AttributedStyle gatherSingleColumn(int y, int row, int width, StringBuilder sb) {
        int maxRows = layout.listBottom - layout.listTop - 6; // room for preview + actions
        if (row < 0) {
            return null;
        }
        if (row < maxRows && row < groups.size()) {
            FileOrganizer.Group g = groups.get(row);
            boolean sel = (row == groupCursor);
            String line = (sel ? " ● " : " ○ ") + g.getName() + "  " + g.getFiles().size()
                    + " · " + formatSize(g.getTotalSize());
            sb.append(truncate(line, width));
            clickRegions.add(new ClickableRegion(0, y, width, 1, "group:" + row));
            if (sel) {
                return AttributedStyle.DEFAULT.foreground(theme.honey).bold();
            }
            return AttributedStyle.DEFAULT.foreground(theme.paper);
        }
        int after = row - Math.min(maxRows, groups.size());
        if (!groups.isEmpty() && after == 0) {
            // Inline preview under selection (S): max 5 files then … N more
            FileOrganizer.Group g = groups.get(Math.min(groupCursor, groups.size() - 1));
            List<Path> files = g.getFiles();
            int n = Math.min(5, files.size());
            StringBuilder pv = new StringBuilder("   ");
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    pv.append(", ");
                }
                pv.append(files.get(i).getFileName());
            }
            if (files.size() > 5) {
                pv.append(" … ").append(files.size() - 5).append(" more");
            }
            sb.append(truncate(pv.toString(), width));
            return AttributedStyle.DEFAULT.foreground(theme.ash);
        }
        if (groups.size() > maxRows && after == (groups.isEmpty() ? 0 : 1)
                && row < groups.size()) {
            sb.append(truncate(" … " + (groups.size() - maxRows) + " more", width));
            return AttributedStyle.DEFAULT.foreground(theme.ash);
        }
        int actionRow = after - 1;
        return gatherActions(actionRow, width, sb, y);
    }

    /** Bottom numbered actions for Screen B. */
    private AttributedStyle gatherActions(int actionRow, int width, StringBuilder sb, int y) {
        if (actionRow == 0) {
            FileOrganizer.Group g = groups.isEmpty() ? null
                    : groups.get(Math.min(groupCursor, groups.size() - 1));
            String label = g != null
                    ? " > [1] Move " + g.getFiles().size() + " into " + g.getName() + "/   Enter ✓"
                    : " > [1] Move   Enter ✓";
            sb.append(truncate(label, width));
            clickRegions.add(new ClickableRegion(0, y, width, 1, "act:1"));
            return AttributedStyle.DEFAULT.foreground(theme.honey);
        }
        if (actionRow == 1) {
            sb.append(truncate("   [2] Peek inside                         →", width));
            clickRegions.add(new ClickableRegion(0, y, width, 1, "act:2"));
            return AttributedStyle.DEFAULT.foreground(theme.amber);
        }
        if (actionRow == 2) {
            sb.append(truncate("   [3] Skip this group                     s", width));
            clickRegions.add(new ClickableRegion(0, y, width, 1, "act:3"));
            return AttributedStyle.DEFAULT.foreground(theme.amber);
        }
        return null;
    }

    // ---- Screen C: Confirm ----

    private AttributedStyle confirmRow(int y, int width, StringBuilder sb) {
        int modalW = Math.min(width - 4, (int) (width * 0.6));
        if (modalW < 20) {
            modalW = Math.max(10, width - 2);
        }
        int left = (width - modalW) / 2;
        int cy = layout.height / 2;
        String pad = " ".repeat(Math.max(0, left));

        if (y == cy - 2) {
            sb.append(truncate(pad + "┌─ Move? " + "─".repeat(Math.max(0, modalW - 10)) + "┐", width));
            return AttributedStyle.DEFAULT.foreground(theme.amber);
        }
        if (y == cy - 1) {
            String what = pending != null
                    ? pending.group.getFiles().size() + " files → " + shrinkPath(pending.targetDir, modalW - 6)
                    : "Nothing staged";
            sb.append(truncate(pad + "│ " + what, width));
            return AttributedStyle.DEFAULT.foreground(theme.paper);
        }
        if (y == cy) {
            String impact = pending != null ? "Frees ~" + formatSize(pending.group.getTotalSize()) + " of clutter" : "";
            sb.append(truncate(pad + "│ " + impact, width));
            return AttributedStyle.DEFAULT.foreground(theme.ash);
        }
        if (y == cy + 1) {
            sb.append(truncate(pad + "│", width));
            return AttributedStyle.DEFAULT.foreground(theme.amber);
        }
        if (y == cy + 2) {
            sb.append(truncate(pad + "│ [Enter] Yes, move  [n] No", width));
            int bx = left + 3;
            clickRegions.add(new ClickableRegion(bx, y, 16, 1, "confirm:yes"));
            clickRegions.add(new ClickableRegion(bx + 17, y, 7, 1, "confirm:no"));
            return AttributedStyle.DEFAULT.foreground(theme.honey).bold();
        }
        if (y == cy + 3) {
            sb.append(truncate(pad + "└" + "─".repeat(Math.max(0, modalW - 2)) + "┘", width));
            return AttributedStyle.DEFAULT.foreground(theme.amber);
        }
        return null;
    }

    // ---- emergency mode ----

    private String emergencyLine(int y, int width) {
        if (currentScreen == Screen.PICK) {
            if (y < pickEntries.size()) {
                Path p = pickEntries.get(y);
                String mark = (y == pickCursor) ? "> " : "  ";
                return truncate(mark + p.getFileName(), width);
            }
            return truncate("q quit", width);
        }
        if (currentScreen == Screen.GATHER) {
            if (y < groups.size()) {
                FileOrganizer.Group g = groups.get(y);
                String mark = (y == groupCursor) ? "> " : "  ";
                return truncate(mark + g.getName() + " " + g.getFiles().size(), width);
            }
            return truncate("b back · u undo", width);
        }
        return truncate("[Enter] Yes · [n] No", width);
    }

    // ---- footer ----

    private String footerText(boolean firstRow) {
        if (currentToast != null && firstRow) {
            return " " + currentToast + " ";
        }
        if (currentScreen == Screen.PICK) {
            return firstRow ? " Type to filter · Enter tidy · q quit "
                    : " ↑↓ move · b back · ? keys ";
        } else if (currentScreen == Screen.GATHER) {
            return firstRow ? " ↑↓ groups · 1/2/3 act · u undo "
                    : " b back · ? keys ";
        } else {
            return firstRow ? " [Enter] Yes, move · [n] No "
                    : " Esc back ";
        }
    }

    // ================= helpers =================

    /** Build the dithered banner wash string (dots thin out left→right). */
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

    /** Multithreading implementation for Toasts: one at a time, fades in 3s. */
    private void showToast(String message) {
        this.currentToast = message;
        repaint();
        scheduler.schedule(() -> {
            this.currentToast = null;
            repaint();
        }, 3, TimeUnit.SECONDS);
    }

    private static String shortMsg(IOException e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 60 ? m.substring(0, 60) + "…" : m;
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.0f MB", mb);
        }
        return String.format("%.1f GB", mb / 1024.0);
    }

    static String shrinkPath(Path p, int max) {
        String s = p.toString();
        if (s.length() <= max || max < 10) {
            return s;
        }
        int keep = (max - 1) / 2;
        return s.substring(0, keep) + "…" + s.substring(s.length() - keep);
    }

    static String truncate(String s, int width) {
        if (s.length() <= width) {
            return s;
        }
        return s.substring(0, Math.max(0, width));
    }

    static String padRight(String s, int width) {
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        return s + " ".repeat(width - s.length());
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
