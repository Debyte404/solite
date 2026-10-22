package solite.ui;

import solite.core.FileOrganizer;
import org.jline.terminal.Terminal;
import org.jline.terminal.Size;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Display;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Main TUI application. Owns the terminal, theme, layout engine, and display.
 * Three screens: PICK (choose folder) -> GATHER (groups + preview + actions)
 * -> CONFIRM (double-Enter for moves). Every mouse action has a keyboard twin.
 */
public final class TerminalApp {

    public enum Screen { PICK, GATHER, CONFIRM }

    /** Pending move staged by Screen B, Quick Tidy, or Duplicate Tidy, awaiting confirmation in CONFIRM. */
    public static final class PendingMove {
        public enum Mode { SINGLE_GROUP, ALL_GROUPS, DUPLICATES }

        public final Mode mode;
        public final boolean isAll;
        public final List<FileOrganizer.Group> allGroups;
        public final FileOrganizer.Group group;
        public final List<FileOrganizer.DuplicateGroup> duplicateGroups;
        public final Path targetDir;

        public static PendingMove single(FileOrganizer.Group group, Path targetDir) {
            return new PendingMove(Mode.SINGLE_GROUP, false, List.of(group), group, List.of(), targetDir);
        }

        public static PendingMove all(List<FileOrganizer.Group> groups, Path targetDir) {
            return new PendingMove(Mode.ALL_GROUPS, true, groups, null, List.of(), targetDir);
        }

        public static PendingMove duplicates(List<FileOrganizer.DuplicateGroup> duplicateGroups, Path targetDir) {
            return new PendingMove(Mode.DUPLICATES, false, List.of(), null, duplicateGroups, targetDir);
        }

        public PendingMove(FileOrganizer.Group group, Path targetDir) {
            this(Mode.SINGLE_GROUP, false, List.of(group), group, List.of(), targetDir);
        }

        private PendingMove(Mode mode, boolean isAll, List<FileOrganizer.Group> allGroups,
                            FileOrganizer.Group group, List<FileOrganizer.DuplicateGroup> duplicateGroups, Path targetDir) {
            this.mode = mode;
            this.isAll = isAll;
            this.allGroups = allGroups;
            this.group = group;
            this.duplicateGroups = duplicateGroups;
            this.targetDir = targetDir;
        }

        public int getTotalFiles() {
            if (mode == Mode.DUPLICATES) {
                return duplicateGroups.stream().mapToInt(dg -> dg.getDuplicates().size()).sum();
            }
            if (isAll) {
                return allGroups.stream().mapToInt(g -> g.getFiles().size()).sum();
            }
            return group != null ? group.getFiles().size() : 0;
        }

        public long getTotalSize() {
            if (mode == Mode.DUPLICATES) {
                return duplicateGroups.stream().mapToLong(FileOrganizer.DuplicateGroup::getWastedSpace).sum();
            }
            if (isAll) {
                return allGroups.stream().mapToLong(FileOrganizer.Group::getTotalSize).sum();
            }
            return group != null ? group.getTotalSize() : 0;
        }
    }

    /** Navigation item model for Screen A (PICK). */
    public static class PickItem {
        public enum Type {
            QUICK_TIDY_ALL,
            TIDY_CURRENT,
            FIND_DUPLICATES,
            GO_UP,
            DRIVE,
            DIRECTORY,
            FILE
        }

        public final Type type;
        public final Path path;
        public final String displayName;
        public final long size;

        public PickItem(Type type, Path path, String displayName, long size) {
            this.type = type;
            this.path = path;
            this.displayName = displayName;
            this.size = size;
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
    private boolean showHelp = false;

    // ---- Pick state ----
    private String pickFilter = "";
    private Path currentDir = Paths.get(System.getProperty("user.home"));
    private List<PickItem> pickItems = new ArrayList<>();
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
        int w = terminal.getWidth();
        int h = terminal.getHeight();
        if (w <= 0 || h <= 0) {
            String cols = System.getenv("COLUMNS");
            String lines = System.getenv("LINES");
            try {
                if (cols != null && !cols.isBlank()) w = Integer.parseInt(cols.trim());
            } catch (NumberFormatException ignored) {}
            try {
                if (lines != null && !lines.isBlank()) h = Integer.parseInt(lines.trim());
            } catch (NumberFormatException ignored) {}
        }
        if (w <= 0) w = 80;
        if (h <= 0) h = 24;
        this.display = new Display(terminal, true);
        this.display.resize(h, w);
        this.theme = Theme.pick(terminal);
        this.layout = new LayoutEngine(w, h);
        this.shadowPainter = new ShadowPainter(terminal, theme);
        this.fileOrganizer = new FileOrganizer();
        refreshPickEntries();
    }

    // ================= event loop =================

    /** Main event loop with alternate buffer and clean quit cleanup. */
    public void run() {
        // 1. Enter alternate screen buffer, hide cursor, clear screen
        try {
            terminal.puts(org.jline.utils.InfoCmp.Capability.enter_ca_mode);
            terminal.puts(org.jline.utils.InfoCmp.Capability.cursor_invisible);
        } catch (Exception ignored) {}
        terminal.writer().print("\u001b[?1049h\u001b[2J\u001b[H\u001b[?25l");
        terminal.flush();

        try {
            // Listen for resize events
            terminal.handle(Terminal.Signal.WINCH, signal -> repaint());
            terminal.handle(Terminal.Signal.CONT, s -> repaint());

            // Disable mouse/trackpad tracking for reliable keyboard-only control
            try {
                terminal.trackMouse(Terminal.MouseTracking.Off);
            } catch (Exception ignored) {}
            terminal.writer().print("\u001b[?1000l\u001b[?1002l\u001b[?1003l\u001b[?1006l");

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
        } finally {
            scheduler.shutdownNow();

            // Disable mouse tracking
            try {
                terminal.trackMouse(Terminal.MouseTracking.Off);
            } catch (Exception ignored) {}
            terminal.writer().print("\u001b[?1000l\u001b[?1002l\u001b[?1003l\u001b[?1006l");

            // Restore cursor, exit alternate screen mode
            try {
                terminal.puts(org.jline.utils.InfoCmp.Capability.cursor_visible);
                terminal.puts(org.jline.utils.InfoCmp.Capability.exit_ca_mode);
            } catch (Exception ignored) {}
            terminal.writer().print("\u001b[?25h\u001b[?1049l");
            terminal.writer().flush();

            try {
                terminal.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * After an ESC byte, peek for arrow keys, mouse sequences, or specials.
     * Returns true if a multi-byte sequence was consumed.
     */
    private boolean handleEscapeSequence() throws IOException {
        var reader = terminal.reader();
        int c1 = reader.read(60L);
        if (c1 < 0) {
            return false; // plain Esc
        }
        if (c1 == 'O') {
            int c2 = reader.read(60L);
            if (c2 == 'A') { moveCursor(-1); return true; }
            if (c2 == 'B') { moveCursor(1); return true; }
            if (c2 == 'C') {
                if (currentScreen == Screen.PICK) onEnter();
                else if (currentScreen == Screen.GATHER) onGatherAction(2);
                return true;
            }
            if (c2 == 'D') { onEscapeKey(); return true; }
            onEscapeKey();
            return true;
        }
        if (c1 != '[') {
            onEscapeKey();
            if (c1 >= 0) {
                onKey(c1);
            }
            return true;
        }
        int c2 = reader.read(60L);
        if (c2 < 0) {
            onEscapeKey();
            return true;
        }
        // Arrow keys: ESC [ A (Up), ESC [ B (Down), ESC [ C (Right), ESC [ D (Left)
        if (c2 == 'A') {
            moveCursor(-1);
            return true;
        }
        if (c2 == 'B') {
            moveCursor(1);
            return true;
        }
        if (c2 == 'C') {
            if (currentScreen == Screen.PICK) {
                onEnter();
            } else if (currentScreen == Screen.GATHER) {
                onGatherAction(2);
            }
            return true;
        }
        if (c2 == 'D') {
            onEscapeKey();
            return true;
        }
        if (c2 == 'H') {
            if (currentScreen == Screen.PICK) { pickCursor = 0; repaint(); }
            else if (currentScreen == Screen.GATHER) { groupCursor = 0; repaint(); }
            return true;
        }
        if (c2 == 'F') {
            if (currentScreen == Screen.PICK && !pickItems.isEmpty()) { pickCursor = pickItems.size() - 1; repaint(); }
            else if (currentScreen == Screen.GATHER && !groups.isEmpty()) { groupCursor = groups.size() - 1; repaint(); }
            return true;
        }
        // Ignore residual X10 mouse sequences: ESC [ M b x y
        if (c2 == 'M') {
            reader.read(60L);
            reader.read(60L);
            reader.read(60L);
            return true;
        }
        // Ignore residual SGR mouse sequences: ESC [ < ... M / m
        if (c2 == '<') {
            while (true) {
                int c = reader.read(60L);
                if (c < 0 || c == 'M' || c == 'm') break;
            }
            return true;
        }
        // PageUp (~5), PageDown (~6), Home (~1), End (~4)
        if (c2 >= '0' && c2 <= '9') {
            StringBuilder num = new StringBuilder().append((char) c2);
            while (true) {
                int c = reader.read(60L);
                if (c < 0 || c == '~') break;
                num.append((char) c);
            }
            try {
                int code = Integer.parseInt(num.toString());
                if (code == 5) moveCursor(-5); // PageUp
                else if (code == 6) moveCursor(5); // PageDown
                else if (code == 1) { // Home
                    if (currentScreen == Screen.PICK) { pickCursor = 0; repaint(); }
                    else if (currentScreen == Screen.GATHER) { groupCursor = 0; repaint(); }
                } else if (code == 4) { // End
                    if (currentScreen == Screen.PICK && !pickItems.isEmpty()) { pickCursor = pickItems.size() - 1; repaint(); }
                    else if (currentScreen == Screen.GATHER && !groups.isEmpty()) { groupCursor = groups.size() - 1; repaint(); }
                }
            } catch (NumberFormatException ignored) {}
            return true;
        }
        // Unknown escape sequence — treat as Esc (back)
        onEscapeKey();
        return true;
    }

    private void onEscapeKey() {
        if (showHelp) {
            showHelp = false;
            repaint();
            return;
        }
        if (currentScreen == Screen.CONFIRM) {
            Screen backTo = (pending != null && (pending.isAll || pending.mode == PendingMove.Mode.DUPLICATES))
                    ? Screen.PICK : Screen.GATHER;
            pending = null;
            currentScreen = backTo;
            repaint();
        } else if (currentScreen == Screen.GATHER) {
            currentScreen = Screen.PICK;
            refreshPickEntries();
            repaint();
        } else if (currentScreen == Screen.PICK) {
            if (!pickFilter.isEmpty()) {
                pickFilter = "";
                pickCursor = 0;
                refreshPickEntries();
                repaint();
            } else {
                // If filter empty, go up to parent directory
                Path parent = currentDir.toAbsolutePath().getParent();
                if (parent != null) {
                    currentDir = parent;
                    pickCursor = 0;
                    refreshPickEntries();
                    repaint();
                }
            }
        }
    }

    // ================= keyboard =================

    private void onKey(int key) {
        if (key == 3) { // Ctrl+C — always quits cleanly
            running = false;
            return;
        }

        if (showHelp) {
            if (key == 'q' || key == 'Q') {
                running = false;
                return;
            }
            showHelp = false;
            repaint();
            return;
        }

        switch (key) {
            case 13: // Enter
            case 10:
                onEnter();
                break;
            case 127: // Backspace
            case 8:
                if (currentScreen == Screen.PICK) {
                    if (!pickFilter.isEmpty()) {
                        pickFilter = pickFilter.substring(0, pickFilter.length() - 1);
                        pickCursor = 0;
                        refreshPickEntries();
                        repaint();
                    } else {
                        // Go up to parent directory
                        Path parent = currentDir.toAbsolutePath().getParent();
                        if (parent != null) {
                            currentDir = parent;
                            pickCursor = 0;
                            refreshPickEntries();
                            repaint();
                        }
                    }
                }
                break;
            case '?':
                showHelp = !showHelp;
                repaint();
                break;
            case 'q':
            case 'Q':
                if (currentScreen == Screen.PICK) {
                    if (pickFilter.isEmpty()) {
                        running = false;
                    } else {
                        typeChar((char) key);
                    }
                } else {
                    running = false;
                }
                break;
            case 'b':
            case 'B':
                if (currentScreen == Screen.PICK) {
                    if (pickFilter.isEmpty()) {
                        onEscapeKey(); // go up
                    } else {
                        typeChar((char) key);
                    }
                } else {
                    onEscapeKey(); // back
                }
                break;
            case 'u':
            case 'U':
                if (currentScreen == Screen.PICK) {
                    if (pickFilter.isEmpty()) {
                        onUndo();
                    } else {
                        typeChar((char) key);
                    }
                } else {
                    onUndo();
                }
                break;
            case ' ':
                if (currentScreen == Screen.PICK) {
                    if (pickFilter.isEmpty()) {
                        startTidy();
                    } else {
                        typeChar(' ');
                    }
                }
                break;
            case 'a':
            case 'A':
                if (currentScreen == Screen.GATHER) {
                    onGatherAction(4); // Quick tidy all
                } else if (currentScreen == Screen.PICK) {
                    typeChar((char) key);
                }
                break;
            case 'n':
            case 'N':
                if (currentScreen == Screen.CONFIRM) {
                    Screen backTo = (pending != null && (pending.isAll || pending.mode == PendingMove.Mode.DUPLICATES))
                            ? Screen.PICK : Screen.GATHER;
                    pending = null;
                    currentScreen = backTo;
                    repaint();
                } else if (currentScreen == Screen.PICK) {
                    typeChar((char) key);
                }
                break;
            case '1':
            case '2':
            case '3':
                if (currentScreen == Screen.GATHER) {
                    onGatherAction(key - '0');
                } else if (currentScreen == Screen.PICK) {
                    typeChar((char) key);
                }
                break;
            case '4':
                if (currentScreen == Screen.GATHER) {
                    onGatherAction(4);
                } else if (currentScreen == Screen.PICK) {
                    typeChar((char) key);
                }
                break;
            case 's':
            case 'S':
                if (currentScreen == Screen.GATHER) {
                    onGatherAction(3); // skip
                } else if (currentScreen == Screen.PICK) {
                    typeChar((char) key);
                }
                break;
            default:
                if (currentScreen == Screen.PICK && key >= 32 && key <= 126) {
                    typeChar((char) key);
                } else if (currentScreen == Screen.PICK || currentScreen == Screen.GATHER) {
                    if (key == 'k' || key == 65) {
                        moveCursor(-1);
                    } else if (key == 'j' || key == 66) {
                        moveCursor(1);
                    }
                }
                break;
        }
    }

    private void typeChar(char c) {
        pickFilter += c;
        pickCursor = 0;
        refreshPickEntries();
        repaint();
    }

    private void moveCursor(int delta) {
        if (showHelp) {
            return;
        }
        if (currentScreen == Screen.PICK) {
            if (!pickItems.isEmpty()) {
                pickCursor = Math.floorMod(pickCursor + delta, pickItems.size());
                repaint();
            }
        } else if (currentScreen == Screen.GATHER) {
            if (!groups.isEmpty()) {
                groupCursor = Math.floorMod(groupCursor + delta, groups.size());
                repaint();
            }
        }
    }

    private void startTidy() {
        if (!pickItems.isEmpty()) {
            PickItem item = pickItems.get(Math.min(pickCursor, pickItems.size() - 1));
            if (item.type == PickItem.Type.DIRECTORY) {
                currentDir = item.path;
                pickFilter = "";
                refreshPickEntries();
            }
        }
        if (enterGather()) {
            currentScreen = Screen.GATHER;
        }
        repaint();
    }

    private void startQuickTidy() {
        try {
            groups = fileOrganizer.groupFiles(currentDir);
            if (groups.isEmpty()) {
                showToast(" ○ No files to organize in this folder ");
                return;
            }
            pending = PendingMove.all(groups, currentDir);
            currentScreen = Screen.CONFIRM;
            repaint();
        } catch (Exception e) {
            showToast(" ! Cannot read folder: " + shortMsg(e instanceof IOException ? (IOException) e : new IOException(e)));
        }
    }

    private void startFindDuplicates() {
        try {
            List<FileOrganizer.DuplicateGroup> dups = fileOrganizer.findDuplicates(currentDir);
            if (dups.isEmpty()) {
                showToast(" ○ No duplicate files found in this folder ");
                return;
            }
            Path dupsDir = currentDir.resolve("Duplicates");
            pending = PendingMove.duplicates(dups, dupsDir);
            currentScreen = Screen.CONFIRM;
            repaint();
        } catch (Exception e) {
            showToast(" ! Scan failed: " + shortMsg(e instanceof IOException ? (IOException) e : new IOException(e)));
        }
    }

    private void onEnter() {
        if (showHelp) {
            showHelp = false;
            repaint();
            return;
        }
        if (currentScreen == Screen.PICK) {
            if (!pickItems.isEmpty()) {
                PickItem item = pickItems.get(Math.min(pickCursor, pickItems.size() - 1));
                switch (item.type) {
                    case QUICK_TIDY_ALL:
                        startQuickTidy();
                        break;
                    case TIDY_CURRENT:
                        if (enterGather()) {
                            currentScreen = Screen.GATHER;
                        }
                        break;
                    case FIND_DUPLICATES:
                        startFindDuplicates();
                        break;
                    case GO_UP:
                    case DRIVE:
                    case DIRECTORY:
                        currentDir = item.path;
                        pickFilter = "";
                        pickCursor = 0;
                        refreshPickEntries();
                        break;
                    case FILE:
                        if (enterGather()) {
                            currentScreen = Screen.GATHER;
                        }
                        break;
                }
                repaint();
            } else {
                showToast(" ○ Nothing to tidy here — pick another folder ");
            }
        } else if (currentScreen == Screen.GATHER) {
            if (!groups.isEmpty()) {
                onGatherAction(1);
            }
        } else if (currentScreen == Screen.CONFIRM) {
            applyPendingMove();
        }
    }

    /** Action 1 = move (stage -> CONFIRM), 2 = peek, 3 = skip, 4 = quick tidy all. */
    private void onGatherAction(int n) {
        if (groups.isEmpty()) {
            return;
        }
        if (n == 4) {
            startQuickTidy();
            return;
        }
        FileOrganizer.Group g = groups.get(Math.min(groupCursor, groups.size() - 1));
        if (n == 1) {
            Path target = currentDir.resolve(g.getName());
            pending = PendingMove.single(g, target);
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
            refreshPickEntries();
            if (currentScreen == Screen.GATHER) {
                enterGather();
            }
            showToast(" ✓ Undone" + (lastMoveSummary != null ? " · " + lastMoveSummary : ""));
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
            if (pending.mode == PendingMove.Mode.DUPLICATES) {
                fileOrganizer.tidyDuplicates(pending.duplicateGroups, currentDir);
                int n = pending.getTotalFiles();
                long sz = pending.getTotalSize();
                lastMoveSummary = n + " duplicates moved to Duplicates/ (" + formatSize(sz) + " cleaned) · u undo";
                showToast(" ✓ " + n + " duplicates moved to Duplicates/ (" + formatSize(sz) + ") · u undo ");
                pending = null;
                refreshPickEntries();
                currentScreen = Screen.PICK;
                repaint();
            } else if (pending.isAll) {
                fileOrganizer.moveAllGroups(pending.allGroups, currentDir);
                int n = pending.getTotalFiles();
                int gCount = pending.allGroups.size();
                lastMoveSummary = n + " files organized into " + gCount + " folders · u undo";
                showToast(" ✓ " + n + " files organized into " + gCount + " folders · u undo ");
                pending = null;
                refreshPickEntries();
                currentScreen = Screen.PICK;
                repaint();
            } else {
                fileOrganizer.moveFiles(pending.group.getFiles(), pending.targetDir);
                int n = pending.group.getFiles().size();
                lastMoveSummary = n + " moved · u undo";
                showToast(" ✓ " + n + " moved · u undo ");
                pending = null;
                enterGather();
                currentScreen = Screen.GATHER;
                repaint();
            }
        } catch (IOException e) {
            showToast(" ! Can't move: " + shortMsg(e));
            pending = null;
            currentScreen = Screen.PICK;
            repaint();
        }
    }

    // ================= data refresh =================

    private void refreshPickEntries() {
        List<PickItem> items = new ArrayList<>();
        gatherError = null;

        // 1. Direct path jump or drive switch if typed in filter
        if (pickFilter != null && !pickFilter.isBlank()) {
            String trimmed = pickFilter.trim();
            // Single letter shortcut for drive (e.g. "c", "d")
            if (trimmed.length() == 1 && Character.isLetter(trimmed.charAt(0))) {
                Path drivePath = Paths.get(trimmed.toUpperCase() + ":\\");
                if (Files.exists(drivePath)) {
                    items.add(new PickItem(PickItem.Type.DRIVE, drivePath,
                            "[Drive " + drivePath + "]", 0));
                }
            }
            try {
                Path direct = Paths.get(trimmed);
                if (!direct.isAbsolute()) {
                    direct = currentDir.resolve(direct).normalize();
                }
                if (Files.exists(direct) && Files.isDirectory(direct)) {
                    items.add(new PickItem(PickItem.Type.DIRECTORY, direct,
                            "> Go to: " + direct.toAbsolutePath(), 0));
                }
            } catch (Exception ignored) {}
        }

        // 2. Quick Tidy All, Review Groups, and Find Duplicates for current folder
        String currentName = currentDir.getFileName() != null ? currentDir.getFileName().toString() : currentDir.toString();
        items.add(new PickItem(PickItem.Type.QUICK_TIDY_ALL, currentDir,
                "! [Quick Tidy: Auto-Organize All] (" + currentName + ")", 0));
        items.add(new PickItem(PickItem.Type.TIDY_CURRENT, currentDir,
                "* [Tidy: Review Groups] (" + currentName + ")", 0));
        items.add(new PickItem(PickItem.Type.FIND_DUPLICATES, currentDir,
                "= [Find & Tidy Duplicates] (" + currentName + ")", 0));

        // 3. Parent navigation
        Path parent = currentDir.toAbsolutePath().getParent();
        if (parent != null) {
            items.add(new PickItem(PickItem.Type.GO_UP, parent,
                    ".. (Up to: " + shrinkPath(parent, 28) + ")", 0));
        }

        // 4. Drive switchers (always visible when at drive root)
        if (parent == null) {
            File[] roots = File.listRoots();
            if (roots != null) {
                for (File root : roots) {
                    Path rootPath = root.toPath();
                    if (!rootPath.equals(currentDir.toAbsolutePath())) {
                        items.add(new PickItem(PickItem.Type.DRIVE, rootPath,
                                "[Drive " + root.toString() + "]", 0));
                    }
                }
            }
        }

        // 5. Directory contents
        try {
            List<Path> entries = fileOrganizer.listEntries(currentDir, pickFilter);
            for (Path p : entries) {
                try {
                    if (Files.isDirectory(p)) {
                        String name = p.getFileName() != null ? p.getFileName().toString() : p.toString();
                        items.add(new PickItem(PickItem.Type.DIRECTORY, p, name + "/", 0));
                    } else {
                        String name = p.getFileName() != null ? p.getFileName().toString() : p.toString();
                        long sz = 0;
                        try { sz = Files.size(p); } catch (Exception ignored) {}
                        items.add(new PickItem(PickItem.Type.FILE, p, name, sz));
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            gatherError = "Permission denied or unreadable folder. Pick another.";
        }

        this.pickItems = items;
        this.pickEntries = items.stream().map(it -> it.path).collect(Collectors.toList());
        if (pickItems.isEmpty()) {
            pickCursor = 0;
        } else {
            pickCursor = Math.max(0, Math.min(pickCursor, pickItems.size() - 1));
        }
    }

    /** Load groups for GATHER. Returns false + sets error if unreadable/empty. */
    private boolean enterGather() {
        try {
            groups = fileOrganizer.groupFiles(currentDir);
            groupCursor = 0;
            if (groups.isEmpty()) {
                gatherError = "Nothing to organize in this folder (no loose files).";
                showToast(" ○ Nothing to organize in this folder ");
                return false;
            }
            gatherError = null;
            return true;
        } catch (Exception e) {
            groups = List.of();
            gatherError = "Cannot read folder: " + shortMsg(e instanceof IOException ? (IOException) e : new IOException(e));
            showToast(" ! " + gatherError);
            currentScreen = Screen.PICK;
            refreshPickEntries();
            return false;
        }
    }

    // ================= mouse (disabled) =================

    private void onMouse(int button, int x, int y) {
        // Mouse and trackpad tracking disabled for pure, reliable keyboard control
    }

    // ================= paint =================

    /** Repaint the entire frame. */
    private void repaint() {
        // Check for resize
        Size size = terminal.getSize();
        int curW = (size != null && size.getColumns() > 0) ? size.getColumns() : layout.width;
        int curH = (size != null && size.getRows() > 0) ? size.getRows() : layout.height;
        if (curW <= 0) curW = 80;
        if (curH <= 0) curH = 24;
        if (curW != layout.width || curH != layout.height) {
            layout = new LayoutEngine(curW, curH);
            display.clear();
        }

        clickRegions = new ArrayList<>();
        List<AttributedString> lines = new ArrayList<>();
        int width = layout.width;
        int height = layout.height;

        // Build each row as an AttributedString.
        // On the terminal's very bottom row (y == height - 1), pad to (width - 1) instead of width.
        // This avoids writing to the bottom-right cell which triggers auto-wrap / auto-scroll and
        // duplicates the footer row in Windows terminal/conhost.
        for (int y = 0; y < height; y++) {
            int rowW = (y == height - 1) ? Math.max(0, width - 1) : width;
            lines.add(buildRow(y, rowW));
        }

        display.resize(height, width);
        display.update(lines, 0);
    }

    /** Build a single row of the screen. */
    private AttributedString buildRow(int y, int width) {
        if (showHelp) {
            return helpRow(y, width);
        } else if (layout.isEmergency()) {
            return new AttributedString(emergencyLine(y, width), AttributedStyle.DEFAULT.foreground(theme.paper));
        } else if (y == 0) {
            // Banner row 0
            String scr = currentScreen.name().toLowerCase();
            String title = " ● SOLITE — " + scr + " · " + shrinkPath(currentDir, Math.max(10, width - 24));
            return new AttributedString(padRight(truncate(title, width), width), theme.styleHoney);
        } else if (y == 1) {
            // Banner wash row 1
            return new AttributedString(padRight(truncate(buildBannerWash(width), width), width), theme.styleWax);
        } else if (y >= layout.height - layout.footerRows) {
            // Footer rows
            boolean isTopFooter = (y == layout.height - layout.footerRows);
            String footer = footerText(isTopFooter);
            AttributedStyle style = (currentToast != null && isTopFooter)
                    ? AttributedStyle.DEFAULT.foreground(theme.leaf).bold()
                    : theme.styleAsh;
            return new AttributedString(padRight(truncate(footer, width), width), style);
        } else if (currentScreen == Screen.PICK) {
            return pickRow(y, width);
        } else if (currentScreen == Screen.GATHER) {
            return gatherRow(y, width);
        } else {
            return confirmRow(y, width);
        }
    }

    // ---- Box formatting helpers (guarantees pixel-perfect border alignment) ----

    private static String formatBoxTop(String title, int width) {
        int innerW = width - 4;
        if (innerW < 4) return truncate("┌" + "─".repeat(Math.max(0, width - 2)) + "┐", width);
        String label = (title != null && !title.isEmpty()) ? " " + title + " " : "";
        if (label.length() > innerW - 2) {
            label = truncate(label, innerW - 2);
        }
        int dashCount = innerW - label.length();
        return " ┌" + label + "─".repeat(Math.max(0, dashCount)) + "┐ ";
    }

    private static String formatBoxDivider(String label, int width) {
        int innerW = width - 4;
        if (innerW < 4) return truncate("├" + "─".repeat(Math.max(0, width - 2)) + "┤", width);
        String lbl = (label != null && !label.isEmpty()) ? " " + label + " " : "";
        if (lbl.length() > innerW - 2) {
            lbl = truncate(lbl, innerW - 2);
        }
        int dashCount = innerW - lbl.length();
        return " ├" + lbl + "─".repeat(Math.max(0, dashCount)) + "┤ ";
    }

    private static String formatBoxBottom(int width) {
        int innerW = width - 4;
        return " └" + "─".repeat(Math.max(0, innerW)) + "┘ ";
    }

    // ---- Screen A: Pick ----

    private AttributedString pickRow(int y, int width) {
        int row = y - layout.listTop;
        int innerW = width - 4;

        if (row == 0) {
            String dirStr = shrinkPath(currentDir, Math.max(6, innerW - 14));
            return new AttributedString(formatBoxTop("Folder: " + dirStr, width), theme.styleAmber);
        }
        if (row == 1) {
            String filterPrompt = pickFilter.isEmpty()
                    ? " Filter: _ (type to filter, Enter: open/action, Bksp: up)"
                    : " Filter: " + pickFilter + "_ (Esc: clear, Enter: open)";
            clickRegions.add(new ClickableRegion(0, y, width, 1, "pick:open"));
            AttributedStringBuilder asb = new AttributedStringBuilder();
            asb.style(theme.styleAmber).append(" │");
            asb.style(theme.stylePaper).append(padRight(truncate(filterPrompt, innerW), innerW));
            asb.style(theme.styleAmber).append("│ ");
            return asb.toAttributedString();
        }
        if (row == 2) {
            String count = "Items: " + (pickItems.isEmpty() ? "0" : (pickCursor + 1) + "/" + pickItems.size());
            return new AttributedString(formatBoxDivider(count, width), theme.styleWax);
        }

        int listStart = 3;
        if (gatherError != null) {
            if (row == 3) {
                AttributedStringBuilder asb = new AttributedStringBuilder();
                asb.style(theme.styleAmber).append(" │");
                asb.style(AttributedStyle.DEFAULT.foreground(theme.ember).bold())
                   .append(padRight(truncate(" ! " + gatherError, innerW), innerW));
                asb.style(theme.styleAmber).append("│ ");
                return asb.toAttributedString();
            }
            listStart = 4;
        }

        int maxRows = layout.listBottom - layout.listTop - listStart - 1;
        int viewportHeight = Math.max(1, maxRows);
        int totalItems = pickItems.size();

        int firstVisible = 0;
        if (totalItems > viewportHeight) {
            firstVisible = Math.max(0, Math.min(pickCursor - viewportHeight / 2, totalItems - viewportHeight));
        }

        int screenRow = row - listStart;
        if (screenRow >= 0 && screenRow < viewportHeight) {
            int itemIndex = firstVisible + screenRow;
            if (itemIndex < totalItems) {
                PickItem item = pickItems.get(itemIndex);
                boolean sel = (itemIndex == pickCursor);

                clickRegions.add(new ClickableRegion(0, y, width, 1, "pick:" + itemIndex));

                AttributedStringBuilder asb = new AttributedStringBuilder();
                asb.style(theme.styleAmber).append(" │");

                if (sel) {
                    // Full row background highlight, no side arrow
                    String content;
                    if (item.type == PickItem.Type.FILE && item.size > 0) {
                        String sz = formatSize(item.size);
                        int spaceForName = Math.max(8, innerW - sz.length() - 3);
                        String truncatedName = truncate(item.displayName, spaceForName);
                        int pad = innerW - 2 - truncatedName.length() - sz.length();
                        content = "  " + truncatedName + " ".repeat(Math.max(1, pad)) + sz;
                    } else {
                        content = "  " + item.displayName;
                    }
                    asb.style(theme.styleFocus).append(padRight(truncate(content, innerW), innerW));
                } else {
                    // Unselected: styled by item type, no arrow
                    switch (item.type) {
                        case QUICK_TIDY_ALL:
                            asb.style(AttributedStyle.DEFAULT.foreground(theme.leaf).bold())
                               .append(padRight(truncate("  " + item.displayName, innerW), innerW));
                            break;
                        case TIDY_CURRENT:
                            asb.style(AttributedStyle.DEFAULT.foreground(theme.amber).bold())
                               .append(padRight(truncate("  " + item.displayName, innerW), innerW));
                            break;
                        case FIND_DUPLICATES:
                            asb.style(AttributedStyle.DEFAULT.foreground(theme.ember).bold())
                               .append(padRight(truncate("  " + item.displayName, innerW), innerW));
                            break;
                        case GO_UP:
                        case DRIVE:
                            asb.style(theme.styleAmber)
                               .append(padRight(truncate("  " + item.displayName, innerW), innerW));
                            break;
                        case DIRECTORY:
                            asb.style(theme.stylePaper)
                               .append(padRight(truncate("  " + item.displayName, innerW), innerW));
                            break;
                        case FILE:
                        default:
                            String sz = item.size > 0 ? formatSize(item.size) : "";
                            int spaceForName = Math.max(8, innerW - sz.length() - 3);
                            String truncatedName = truncate(item.displayName, spaceForName);
                            int pad = innerW - 2 - truncatedName.length() - sz.length();
                            asb.style(theme.stylePaper).append("  ").append(truncatedName);
                            asb.style(theme.styleAsh).append(" ".repeat(Math.max(1, pad))).append(sz);
                            break;
                    }
                }

                asb.style(theme.styleAmber).append("│ ");
                return asb.toAttributedString();
            } else if (totalItems == 0 && screenRow == 0) {
                AttributedStringBuilder asb = new AttributedStringBuilder();
                asb.style(theme.styleAmber).append(" │");
                asb.style(theme.styleAsh).append(padRight("   ○ (Folder is empty or nothing matches filter)", innerW));
                asb.style(theme.styleAmber).append("│ ");
                return asb.toAttributedString();
            }
        }

        if (row == listStart + viewportHeight) {
            return new AttributedString(formatBoxBottom(width), theme.styleAmber);
        }

        return new AttributedString(" ".repeat(width), AttributedStyle.DEFAULT);
    }

    // ---- Screen B: Gather ----

    private AttributedString gatherRow(int y, int width) {
        int row = y - layout.listTop;
        if (gatherError != null && row == 0) {
            clickRegions.add(new ClickableRegion(0, y, width, 1, "back"));
            String line = truncate(" " + gatherError + "  [b] back", width);
            return new AttributedString(padRight(line, width), AttributedStyle.DEFAULT.foreground(theme.ember));
        }
        int listStart = (gatherError != null) ? 1 : 0;

        if (layout.hasPreview()) {
            return gatherTwoPane(y, row - listStart, width);
        }
        return gatherSingleColumn(y, row - listStart, width);
    }

    /** M/L: left groups (55%), right preview (45%). */
    private AttributedString gatherTwoPane(int y, int row, int width) {
        int leftW = layout.listRight - layout.listLeft;
        int rightW = layout.previewRight - layout.previewLeft;
        int maxRows = layout.listBottom - layout.listTop - 5; // room for actions including quick tidy

        if (row < 0) {
            return new AttributedString(" ".repeat(width), AttributedStyle.DEFAULT);
        }

        int totalGroups = groups.size();
        int firstVisibleGroup = 0;
        if (totalGroups > maxRows) {
            firstVisibleGroup = Math.max(0, Math.min(groupCursor - maxRows / 2, totalGroups - maxRows));
        }

        if (row < maxRows) {
            int groupIndex = firstVisibleGroup + row;
            if (groupIndex < totalGroups) {
                FileOrganizer.Group g = groups.get(groupIndex);
                boolean sel = (groupIndex == groupCursor);
                String left = "  " + g.getName() + "  " + g.getFiles().size()
                        + " files · " + formatSize(g.getTotalSize());
                String right = "";
                if (sel) {
                    List<Path> files = g.getFiles();
                    int n = Math.min(6, files.size());
                    StringBuilder pv = new StringBuilder("│ ");
                    for (int i = 0; i < n; i++) {
                        if (i > 0) pv.append(", ");
                        pv.append(files.get(i).getFileName());
                    }
                    if (files.size() > 6) {
                        pv.append(" … +").append(files.size() - 6).append(" more");
                    }
                    right = truncate(pv.toString(), rightW);
                }

                clickRegions.add(new ClickableRegion(0, y, leftW + 1, 1, "group:" + groupIndex));

                AttributedStringBuilder asb = new AttributedStringBuilder();
                if (sel) {
                    asb.style(theme.styleFocus).append(padRight(truncate(left, leftW), leftW));
                } else {
                    asb.style(theme.stylePaper).append(padRight(truncate(left, leftW), leftW));
                }
                asb.style(theme.styleWax).append(" ");
                asb.style(theme.styleAsh).append(padRight(right, rightW));
                return asb.toAttributedString();
            }
        }

        if (row == maxRows) {
            String info = (totalGroups > maxRows)
                    ? " ─ Groups: " + (groupCursor + 1) + "/" + totalGroups + " [▲▼ scroll] " + "─".repeat(Math.max(0, width - 35))
                    : " ─" + "─".repeat(Math.max(0, width - 4));
            return new AttributedString(padRight(truncate(info, width), width), theme.styleWax);
        }

        return gatherActions(row - maxRows - 1, width, y);
    }

    /** XS/S: one column + inline preview under selection. */
    private AttributedString gatherSingleColumn(int y, int row, int width) {
        int maxRows = layout.listBottom - layout.listTop - 7; // room for preview + actions
        if (row < 0) return new AttributedString(" ".repeat(width), AttributedStyle.DEFAULT);

        int totalGroups = groups.size();
        int firstVisibleGroup = 0;
        if (totalGroups > maxRows) {
            firstVisibleGroup = Math.max(0, Math.min(groupCursor - maxRows / 2, totalGroups - maxRows));
        }

        if (row < maxRows) {
            int groupIndex = firstVisibleGroup + row;
            if (groupIndex < totalGroups) {
                FileOrganizer.Group g = groups.get(groupIndex);
                boolean sel = (groupIndex == groupCursor);
                String line = "  " + g.getName() + "  " + g.getFiles().size()
                        + " files · " + formatSize(g.getTotalSize());

                clickRegions.add(new ClickableRegion(0, y, width, 1, "group:" + groupIndex));

                if (sel) {
                    return new AttributedString(padRight(truncate(line, width), width), theme.styleFocus);
                }
                return new AttributedString(padRight(truncate(line, width), width), theme.stylePaper);
            }
        }

        if (row == maxRows) {
            // Inline preview under selection
            if (!groups.isEmpty()) {
                FileOrganizer.Group g = groups.get(Math.min(groupCursor, groups.size() - 1));
                List<Path> files = g.getFiles();
                int n = Math.min(5, files.size());
                StringBuilder pv = new StringBuilder("   Preview: ");
                for (int i = 0; i < n; i++) {
                    if (i > 0) pv.append(", ");
                    pv.append(files.get(i).getFileName());
                }
                if (files.size() > 5) {
                    pv.append(" … +").append(files.size() - 5).append(" more");
                }
                return new AttributedString(padRight(truncate(pv.toString(), width), width), theme.styleAsh);
            }
        }

        if (row == maxRows + 1) {
            return new AttributedString(padRight(truncate(" ─" + "─".repeat(Math.max(0, width - 4)), width), width), theme.styleWax);
        }

        int actionRow = row - maxRows - 2;
        return gatherActions(actionRow, width, y);
    }

    /** Bottom numbered actions for Screen B. */
    private AttributedString gatherActions(int actionRow, int width, int y) {
        if (actionRow == 0) {
            FileOrganizer.Group g = groups.isEmpty() ? null
                    : groups.get(Math.min(groupCursor, groups.size() - 1));
            String label = g != null
                    ? "  [1] Move " + g.getFiles().size() + " files into " + g.getName() + "/       [Enter ✓]"
                    : "  [1] Move files                                   [Enter ✓]";
            clickRegions.add(new ClickableRegion(0, y, width, 1, "act:1"));
            return new AttributedString(padRight(truncate(label, width), width), theme.styleHoney);
        }
        if (actionRow == 1) {
            String label = "  [2] Peek inside files                           [→]";
            clickRegions.add(new ClickableRegion(0, y, width, 1, "act:2"));
            return new AttributedString(padRight(truncate(label, width), width), theme.styleAmber);
        }
        if (actionRow == 2) {
            String label = "  [3] Skip this group                             [s]";
            clickRegions.add(new ClickableRegion(0, y, width, 1, "act:3"));
            return new AttributedString(padRight(truncate(label, width), width), theme.styleAmber);
        }
        if (actionRow == 3) {
            int totalFiles = groups.stream().mapToInt(g -> g.getFiles().size()).sum();
            String label = "  [4] Quick Tidy ALL (" + totalFiles + " files in " + groups.size() + " groups)      [a]";
            clickRegions.add(new ClickableRegion(0, y, width, 1, "act:4"));
            return new AttributedString(padRight(truncate(label, width), width), AttributedStyle.DEFAULT.foreground(theme.leaf).bold());
        }
        return new AttributedString(" ".repeat(width), AttributedStyle.DEFAULT);
    }

    // ---- Screen C: Confirm ----

    private AttributedString confirmRow(int y, int width) {
        int modalW = Math.min(width - 4, Math.max(50, (int) (width * 0.72)));
        int left = Math.max(1, (width - modalW) / 2);
        int cy = layout.height / 2;
        String pad = " ".repeat(left);

        if (pending != null && pending.mode == PendingMove.Mode.DUPLICATES) {
            int totalDupFiles = pending.getTotalFiles();
            long totalSpace = pending.getTotalSize();
            if (y == cy - 3) {
                int dashes = Math.max(0, modalW - 28);
                String line = pad + "┌─ Tidy Duplicate Files " + "─".repeat(dashes) + "┐";
                return new AttributedString(padRight(truncate(line, width), width), theme.styleAmber);
            }
            if (y == cy - 2) {
                String what = pad + "│  Found " + totalDupFiles + " duplicate files across "
                        + pending.duplicateGroups.size() + " sets (" + formatSize(totalSpace) + " redundant space):";
                return new AttributedString(padRight(truncate(what, width), width), theme.stylePaper);
            }
            if (y == cy - 1) {
                StringBuilder sample = new StringBuilder();
                for (int i = 0; i < Math.min(4, pending.duplicateGroups.size()); i++) {
                    if (i > 0) sample.append(", ");
                    FileOrganizer.DuplicateGroup dg = pending.duplicateGroups.get(i);
                    Path orig = dg.getOriginal();
                    String name = orig != null ? orig.getFileName().toString() : "file";
                    sample.append(name).append(" (").append(dg.getDuplicates().size()).append(" dups)");
                }
                if (pending.duplicateGroups.size() > 4) {
                    sample.append(" … +").append(pending.duplicateGroups.size() - 4).append(" more");
                }
                String line = pad + "│  " + sample;
                return new AttributedString(padRight(truncate(line, width), width), theme.styleHoney);
            }
            if (y == cy) {
                String impact = pad + "│  Moves duplicates into Duplicates/ (keeps 1 original intact · u undo)";
                return new AttributedString(padRight(truncate(impact, width), width), theme.styleAsh);
            }
            if (y == cy + 1) {
                int dashes = Math.max(0, modalW - 4);
                String line = pad + "│  " + "─".repeat(dashes);
                return new AttributedString(padRight(truncate(line, width), width), theme.styleWax);
            }
            if (y == cy + 2) {
                String line = pad + "│  [Enter] Yes, move duplicates to Duplicates/    [n/Esc] Cancel";
                int bx = left + 3;
                clickRegions.add(new ClickableRegion(bx, y, 46, 1, "confirm:yes"));
                clickRegions.add(new ClickableRegion(bx + 48, y, 16, 1, "confirm:no"));
                return new AttributedString(padRight(truncate(line, width), width), theme.styleHoney);
            }
            if (y == cy + 3) {
                int dashes = Math.max(0, modalW - 2);
                String line = pad + "└" + "─".repeat(dashes) + "┘";
                return new AttributedString(padRight(truncate(line, width), width), theme.styleAmber);
            }
            return new AttributedString(" ".repeat(width), AttributedStyle.DEFAULT);
        }

        if (pending != null && pending.isAll) {
            if (y == cy - 3) {
                int dashes = Math.max(0, modalW - 28);
                String line = pad + "┌─ Quick Tidy: Organize All " + "─".repeat(dashes) + "┐";
                return new AttributedString(padRight(truncate(line, width), width), theme.styleAmber);
            }
            if (y == cy - 2) {
                String what = pad + "│  Automatically organize " + pending.getTotalFiles() + " files into "
                        + pending.allGroups.size() + " extension folders:";
                return new AttributedString(padRight(truncate(what, width), width), theme.stylePaper);
            }
            if (y == cy - 1) {
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < Math.min(8, pending.allGroups.size()); i++) {
                    if (i > 0) names.append(", ");
                    names.append(pending.allGroups.get(i).getName()).append("/");
                }
                if (pending.allGroups.size() > 8) {
                    names.append(" … +").append(pending.allGroups.size() - 8).append(" more");
                }
                String line = pad + "│  " + names;
                return new AttributedString(padRight(truncate(line, width), width), theme.styleHoney);
            }
            if (y == cy) {
                String impact = pad + "│  Frees ~" + formatSize(pending.getTotalSize()) + " of directory clutter";
                return new AttributedString(padRight(truncate(impact, width), width), theme.styleAsh);
            }
            if (y == cy + 1) {
                int dashes = Math.max(0, modalW - 4);
                String line = pad + "│  " + "─".repeat(dashes);
                return new AttributedString(padRight(truncate(line, width), width), theme.styleWax);
            }
            if (y == cy + 2) {
                String line = pad + "│  [Enter] Yes, organize all files    [n/Esc] Cancel";
                int bx = left + 3;
                clickRegions.add(new ClickableRegion(bx, y, 32, 1, "confirm:yes"));
                clickRegions.add(new ClickableRegion(bx + 34, y, 16, 1, "confirm:no"));
                return new AttributedString(padRight(truncate(line, width), width), theme.styleHoney);
            }
            if (y == cy + 3) {
                int dashes = Math.max(0, modalW - 2);
                String line = pad + "└" + "─".repeat(dashes) + "┘";
                return new AttributedString(padRight(truncate(line, width), width), theme.styleAmber);
            }
            return new AttributedString(" ".repeat(width), AttributedStyle.DEFAULT);
        }

        // Single group move
        if (y == cy - 3) {
            int dashes = Math.max(0, modalW - 22);
            String line = pad + "┌─ Confirm Move " + "─".repeat(dashes) + "┐";
            return new AttributedString(padRight(truncate(line, width), width), theme.styleAmber);
        }
        if (y == cy - 2) {
            String what = pending != null
                    ? pad + "│  Move " + pending.group.getFiles().size() + " files (" + formatSize(pending.group.getTotalSize()) + ") into:"
                    : pad + "│  No move pending";
            return new AttributedString(padRight(truncate(what, width), width), theme.stylePaper);
        }
        if (y == cy - 1) {
            String target = pending != null ? pad + "│  " + shrinkPath(pending.targetDir, modalW - 8) + "/" : pad + "│";
            return new AttributedString(padRight(truncate(target, width), width), theme.styleHoney);
        }
        if (y == cy) {
            String impact = pending != null ? pad + "│  Frees ~" + formatSize(pending.group.getTotalSize()) + " of clutter" : pad + "│";
            return new AttributedString(padRight(truncate(impact, width), width), theme.styleAsh);
        }
        if (y == cy + 1) {
            int dashes = Math.max(0, modalW - 4);
            String line = pad + "│  " + "─".repeat(dashes);
            return new AttributedString(padRight(truncate(line, width), width), theme.styleWax);
        }
        if (y == cy + 2) {
            String line = pad + "│  [Enter] Yes, move files    [n/Esc] Cancel";
            int bx = left + 3;
            clickRegions.add(new ClickableRegion(bx, y, 22, 1, "confirm:yes"));
            clickRegions.add(new ClickableRegion(bx + 24, y, 16, 1, "confirm:no"));
            return new AttributedString(padRight(truncate(line, width), width), theme.styleHoney);
        }
        if (y == cy + 3) {
            int dashes = Math.max(0, modalW - 2);
            String line = pad + "└" + "─".repeat(dashes) + "┘";
            return new AttributedString(padRight(truncate(line, width), width), theme.styleAmber);
        }
        return new AttributedString(" ".repeat(width), AttributedStyle.DEFAULT);
    }

    // ---- Help Modal Overlay ----

    private AttributedString helpRow(int y, int width) {
        int modalW = 64;
        int left = Math.max(1, (width - modalW) / 2);
        int cy = layout.height / 2;
        String pad = " ".repeat(left);

        int startY = Math.max(1, cy - 10);
        int lineIdx = y - startY;

        String[] helpLines = {
                "┌─ Solite Help & Instructions " + "─".repeat(32) + "┐",
                "│                                                              │",
                "│  DIRECTORY NAVIGATOR (Screen A: Pick)                        │",
                "│  - Up / Down or j / k Move cursor up and down                │",
                "│  - Enter              Open folder / run selected action      │",
                "│  - Space              Tidy: review file groups               │",
                "│  - Backspace or Left  Go up to parent directory (..)         │",
                "│  - Type any text      Filter items or type path to jump      │",
                "│  - Esc                Clear filter / dismiss dialog          │",
                "│                                                              │",
                "│  FILE GROUP ORGANIZER (Screen B: Gather)                     │",
                "│  - 1 or Enter         Stage move files into group folder     │",
                "│  - 4 or a             Quick Tidy: auto-organize ALL groups   │",
                "│  - 2 or Right         Peek file list inside group            │",
                "│  - 3 or s             Skip this group                        │",
                "│  - u                  Undo last move                         │",
                "│  - b or Esc           Back to directory navigator            │",
                "│                                                              │",
                "│  GENERAL & SYSTEM                                            │",
                "│  - ?                  Toggle this help screen                │",
                "│  - q or Ctrl+C        Clean exit (clears terminal cleanly)   │",
                "│  - Home / End        Jump to first / last item              │",
                "│                                                              │",
                "│  [ Press ? or Esc or Enter to close ]                        │",
                "└" + "─".repeat(62) + "┘"
        };

        if (lineIdx >= 0 && lineIdx < helpLines.length) {
            String raw = helpLines[lineIdx];
            String line = pad + raw;
            AttributedStyle style;
            if (lineIdx == 0 || lineIdx == helpLines.length - 1) {
                style = theme.styleAmber;
            } else if (raw.contains("DIRECTORY") || raw.contains("FILE GROUP") || raw.contains("GENERAL")) {
                style = theme.styleHoney;
            } else if (raw.contains("Press ?")) {
                style = AttributedStyle.DEFAULT.foreground(theme.leaf).bold();
            } else {
                style = theme.stylePaper;
            }
            return new AttributedString(padRight(truncate(line, width), width), style);
        }

        // Outside modal: dim background
        return new AttributedString(padRight("·".repeat(width), width), theme.styleWax);
    }

    // ---- emergency mode ----

    private String emergencyLine(int y, int width) {
        if (currentScreen == Screen.PICK) {
            if (y < pickItems.size()) {
                PickItem p = pickItems.get(y);
                String mark = (y == pickCursor) ? "> " : "  ";
                return truncate(mark + p.displayName, width);
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
            return firstRow ? " [Enter] Select/Run   [Space] Tidy   [Bksp] Up   [u] Undo   [?] Help   [q] Quit "
                    : " [↑/↓] Move   [PgUp/PgDn] Scroll   [Home/End] First/Last   Type to filter ";
        } else if (currentScreen == Screen.GATHER) {
            return firstRow ? " [1/Enter] Move Group   [4/a] Quick Tidy All   [2/→] Peek   [3/s] Skip   [u] Undo "
                    : " [↑/↓] Select Group   [b/Esc] Back to navigator   [?] Help   [q] Quit ";
        } else {
            return firstRow ? " [Enter] Yes, move files   [n/Esc] Cancel "
                    : " Esc: Back ";
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

    public static String formatSize(long bytes) {
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

    public static String shrinkPath(Path p, int max) {
        String s = p.toString();
        if (s.length() <= max || max < 10) {
            return s;
        }
        int keep = (max - 1) / 2;
        return s.substring(0, keep) + "…" + s.substring(s.length() - keep);
    }

    public static String truncate(String s, int width) {
        if (s.length() <= width) {
            return s;
        }
        return s.substring(0, Math.max(0, width));
    }

    public static String padRight(String s, int width) {
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        return s + " ".repeat(width - s.length());
    }
}
