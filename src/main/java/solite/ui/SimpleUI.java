package solite.ui;

import solite.core.FileOrganizer;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * The simple front end: two screens and one question per screen.
 *
 * <pre>
 *   1. Pick a folder      type to filter, Enter to open
 *   2. Groups             Enter to move one group, u to undo, b back
 *   3. Confirm            y / n
 * </pre>
 *
 * <p>No mouse, no shadows, no breakpoints, no alternate screen. Every frame is
 * cleared and reprinted, so what you see is always what the state says. The
 * heavy {@link TerminalApp} is still there behind {@code solite --classic}.
 */
public final class SimpleUI {

    private enum Screen { PICK, GROUPS, CONFIRM }

    private final Terminal terminal;
    private final FileOrganizer organizer = new FileOrganizer();

    private Screen screen = Screen.PICK;
    private Path dir = Paths.get(System.getProperty("user.home"));
    private List<Path> entries = List.of();
    private String filter = "";
    private int pickCursor = 0;

    private List<FileOrganizer.Group> groups = List.of();
    private int groupCursor = 0;
    private String message = "";
    private Path lastTarget = null;

    private boolean running = true;

    public SimpleUI(Terminal terminal) {
        this(terminal, Paths.get(System.getProperty("user.home")));
    }

    /** Start in a specific folder: {@code solite D:\Downloads}. */
    public SimpleUI(Terminal terminal, Path startDir) {
        this.terminal = terminal;
        if (startDir != null && Files.isDirectory(startDir)) {
            this.dir = startDir.toAbsolutePath().normalize();
        }
    }

    public void run() {
        loadEntries();
        draw();
        boolean lastWasCr = false;
        while (running) {
            try {
                int key = terminal.reader().read();
                if (key == -1) {
                    break;
                }
                if (key == KeyInput.CTRL_C) {
                    break;
                }
                // Enter arrives as CR+LF on Windows — drop the second byte so
                // one press is one action.
                if (KeyInput.isDuplicateEndOfLine(key, lastWasCr)) {
                    lastWasCr = false;
                    continue;
                }
                lastWasCr = (key == KeyInput.CR);

                if (key == KeyInput.ESC) {   // arrow keys or a real Esc
                    handleEscape();
                } else {
                    handleKey(key);
                }
                if (running) {
                    draw();
                }
            } catch (IOException e) {
                break;
            }
        }
        terminal.writer().print("\u001b[0m\n");
        terminal.writer().flush();
    }

    // ---------------- input ----------------

    private void handleEscape() throws IOException {
        var reader = terminal.reader();
        if (!reader.ready()) {
            back();
            return;
        }
        int c1 = reader.read();
        if (c1 == '[') {
            if (!reader.ready()) {
                back();
                return;
            }
            int c2 = reader.read();
            if (c2 == 'A') {
                move(-1);
            } else if (c2 == 'B') {
                move(1);
            } else {
                back();
            }
        } else {
            back();
        }
    }

    private void handleKey(int key) {
        if (key == 13 || key == 10) {
            onEnter();
            return;
        }
        switch (screen) {
            case PICK:
                onPickKey(key);
                break;
            case GROUPS:
                onGroupsKey(key);
                break;
            case CONFIRM:
                onConfirmKey(key);
                break;
            default:
                break;
        }
    }

    private void onPickKey(int key) {
        boolean typing = !filter.isEmpty();
        if (key == 127 || key == 8) {
            if (typing) {
                filter = filter.substring(0, filter.length() - 1);
                pickCursor = 0;
                loadEntries();
            } else {
                up();       // backspace with an empty filter = one level up
            }
            return;
        }
        if (key == 'k') {
            if (!typing) {
                move(-1);
                return;
            }
        }
        if (key == 'j') {
            if (!typing) {
                move(1);
                return;
            }
        }
        if (key == 'q' && !typing) {
            running = false;
            return;
        }
        if (key >= 32 && key <= 126) {
            filter += (char) key;
            pickCursor = 0;
            loadEntries();
        }
    }

    private void onGroupsKey(int key) {
        switch (key) {
            case 'u':
            case 'U':
                undo();
                return;
            case 'b':
            case 'B':
                back();
                return;
            case 'q':
            case 'Q':
                running = false;
                return;
            case 'k':
                move(-1);
                return;
            case 'j':
                move(1);
                return;
            default:
                break;
        }
    }

    private void onConfirmKey(int key) {
        if (key == 'y' || key == 'Y') {
            applyMove();
        } else if (key == 'n' || key == 'N' || key == 27) {
            screen = Screen.GROUPS;
            message = "Cancelled. Nothing was moved.";
        }
    }

    private void onEnter() {
        if (screen == Screen.PICK) {
            openSelected();
        } else if (screen == Screen.GROUPS) {
            if (!groups.isEmpty()) {
                screen = Screen.CONFIRM;
                message = "";
            }
        }
        // On CONFIRM, Enter deliberately does nothing: only an explicit "y"
        // applies a move, so a stray Enter can never destroy anything.
    }

    private void move(int delta) {
        if (screen == Screen.PICK) {
            if (!entries.isEmpty()) {
                pickCursor = Math.floorMod(pickCursor + delta, entries.size());
            }
        } else if (screen == Screen.GROUPS) {
            if (!groups.isEmpty()) {
                groupCursor = Math.floorMod(groupCursor + delta, groups.size());
            }
        }
    }

    // ---------------- actions ----------------

    private void loadEntries() {
        try {
            entries = organizer.listEntries(dir, filter);
            if (entries.isEmpty()) {
                pickCursor = 0;
            } else if (pickCursor >= entries.size()) {
                pickCursor = entries.size() - 1;
            }
            message = "";
        } catch (IOException e) {
            entries = List.of();
            pickCursor = 0;
            message = "Can't read this folder (permission).";
        }
    }

    private void openSelected() {
        if (entries.isEmpty()) {
            message = "Nothing here to tidy.";
            return;
        }
        Path sel = entries.get(Math.min(pickCursor, entries.size() - 1));
        dir = Files.isDirectory(sel) ? sel : (sel.getParent() != null ? sel.getParent() : dir);
        filter = "";
        loadGroups();
        if (!groups.isEmpty()) {
            screen = Screen.GROUPS;
        }
    }

    private void up() {
        Path parent = dir.getParent();
        if (parent != null) {
            dir = parent;
            filter = "";
            pickCursor = 0;
            loadEntries();
        }
    }

    private void back() {
        if (screen == Screen.PICK) {
            up();
        } else if (screen == Screen.GROUPS) {
            screen = Screen.PICK;
            filter = "";
            loadEntries();
        } else {
            screen = Screen.GROUPS;
        }
    }

    private void loadGroups() {
        try {
            groups = organizer.groupFiles(dir);
            groupCursor = 0;
            if (groups.isEmpty()) {
                message = "No files to organize here. Pick another folder.";
                screen = Screen.PICK;
            }
        } catch (IOException e) {
            groups = List.of();
            message = "Can't read this folder (permission).";
            screen = Screen.PICK;
        }
    }

    private FileOrganizer.Group selectedGroup() {
        if (groups.isEmpty()) {
            return null;
        }
        return groups.get(Math.min(groupCursor, groups.size() - 1));
    }

    private void applyMove() {
        FileOrganizer.Group g = selectedGroup();
        if (g == null) {
            screen = Screen.GROUPS;
            return;
        }
        Path target = dir.resolve(g.getName());
        int n = g.getFiles().size();
        String size = formatSize(g.getTotalSize());
        try {
            organizer.moveFiles(g.getFiles(), target);
            lastTarget = target;
            message = "Moved " + n + " files into " + g.getName() + "/ (" + size + "). u = undo";
            loadGroups();
            screen = groups.isEmpty() ? Screen.PICK : Screen.GROUPS;
        } catch (IOException e) {
            message = "Move failed: " + e.getMessage();
            screen = Screen.GROUPS;
        }
    }

    private void undo() {
        try {
            organizer.undoMove();
            removeEmptyTarget();
            message = "Undone. Files are back.";
            loadGroups();
            if (groups.isEmpty()) {
                screen = Screen.PICK;
            }
        } catch (IOException e) {
            message = "Undo failed: " + e.getMessage();
        }
    }

    /** Undo puts files back but leaves the folder we created behind — tidy it up. */
    private void removeEmptyTarget() {
        if (lastTarget == null) {
            return;
        }
        try {
            if (Files.isDirectory(lastTarget)) {
                try (var s = Files.list(lastTarget)) {
                    if (s.findAny().isEmpty()) {
                        Files.delete(lastTarget);
                    }
                }
            }
            lastTarget = null;
        } catch (IOException ignored) {
            // best effort: leaving an empty folder behind is harmless
        }
    }

    // ---------------- paint ----------------

    private int cols() {
        int w = terminal.getWidth();
        return w > 0 ? w : 80;
    }

    private int rows() {
        int h = terminal.getHeight();
        return h > 0 ? h : 24;
    }

    private void draw() {
        int width = cols();
        int height = rows();
        StringBuilder sb = new StringBuilder("\u001b[H\u001b[2J");
        int body = height - 3;              // header + two footer lines

        sb.append(bold(truncate("solite  " + dir, width))).append("\r\n");

        if (screen == Screen.PICK) {
            sb.append(truncate("Folder: type to filter  Enter open  Backspace up  q quit", width)).append("\r\n");
            for (int i = 0; i < body && i < entries.size(); i++) {
                Path p = entries.get(i);
                String name = p.getFileName() != null ? p.getFileName().toString() : p.toString();
                String mark = (i == pickCursor) ? "> " : "  ";
                String suffix = Files.isDirectory(p) ? "/" : "";
                sb.append(truncate(mark + name + suffix, width)).append("\r\n");
            }
            if (entries.isEmpty()) {
                sb.append("  (nothing here)\r\n");
            }
        } else if (screen == Screen.GROUPS) {
            sb.append(truncate("Groups   Enter move  u undo  b back  q quit", width)).append("\r\n");
            for (int i = 0; i < body && i < groups.size(); i++) {
                FileOrganizer.Group g = groups.get(i);
                String mark = (i == groupCursor) ? "> " : "  ";
                String line = mark + pad(g.getName(), 10) + pad(g.getFiles().size() + " files", 12)
                        + formatSize(g.getTotalSize());
                sb.append(truncate(line, width)).append("\r\n");
            }
            FileOrganizer.Group sel = selectedGroup();
            if (sel != null && body > 2) {
                sb.append("\r\n").append(truncate("  " + preview(sel, width - 2), width)).append("\r\n");
            }
        } else {
            sb.append(truncate("Confirm   y = yes   n = no", width)).append("\r\n");
            FileOrganizer.Group g = selectedGroup();
            if (g != null) {
                sb.append("\r\n");
                sb.append(truncate("  " + g.getFiles().size() + " files -> " + dir.resolve(g.getName()), width)).append("\r\n");
                sb.append(truncate("  frees ~" + formatSize(g.getTotalSize()), width)).append("\r\n");
                sb.append("\r\n");
                sb.append(truncate("  Move them?  [y] yes   [n] no", width)).append("\r\n");
            }
        }

        // footer
        sb.append("\r\n");
        sb.append(truncate(message.isBlank() ? " " : " " + message, width)).append("\r\n");
        sb.append(truncate(" " + hint(), width));

        terminal.writer().print(sb);
        terminal.writer().flush();
    }

    private String hint() {
        return switch (screen) {
            case PICK -> "up/down move - type to filter - Enter open - q quit";
            case GROUPS -> "up/down move - Enter move this group - u undo - b back";
            case CONFIRM -> "y to move - n to cancel";
        };
    }

    private String preview(FileOrganizer.Group g, int max) {
        List<Path> files = g.getFiles();
        StringBuilder sb = new StringBuilder();
        int n = Math.min(3, files.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(files.get(i).getFileName());
        }
        if (files.size() > n) {
            sb.append(" … ").append(files.size() - n).append(" more");
        }
        return truncate(sb.toString(), max);
    }

    // ---------------- helpers ----------------

    private static String bold(String s) {
        return "\u001b[1m" + s + "\u001b[0m";
    }

    private static String truncate(String s, int width) {
        if (s.length() <= width) {
            return s;
        }
        return s.substring(0, Math.max(0, width));
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        return s + " ".repeat(width - s.length());
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
}
