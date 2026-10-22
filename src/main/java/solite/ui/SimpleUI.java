package solite.ui;

import solite.core.FileOrganizer;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/** A normal-feeling file explorer: names only, emoji icons, ".." row on top. */
public final class SimpleUI {
    private enum Screen { BROWSE, GROUPS, CONFIRM }
    private static final int LIST_TOP = 2;
    private static final int FOOTER_ROWS = 2;
    private static final long DOUBLE_CLICK_MS = 450;
    private static final String CANCELLED = "Cancelled. Nothing was moved.";
    static final String BACK_ICON = "\uD83D\uDCC1";
    static final String DOC_ICON = "\uD83D\uDCC4";
    static final String IMG_ICON = "\uD83D\uDDBC";
    static final String ZIP_ICON = "\uD83D\uDCE6";
    static final String AUDIO_ICON = "\uD83C\uDFB5";
    static final String VIDEO_ICON = "\uD83C\uDFAC";
    static final String CODE_ICON = "\uD83D\uDCDC";
    static final String APP_ICON = "\u2699";
    private final Terminal terminal;
    private final FileOrganizer organizer = new FileOrganizer();
    private Screen screen = Screen.BROWSE;
    private Path dir = Paths.get(System.getProperty("user.home"));
    private List<Path> entries = List.of();
    private int cursor = 0;
    private List<FileOrganizer.Group> groups = List.of();
    private int groupCursor = 0;
    private String message = "";
    private Path lastTarget;
    private long lastClickAt = 0;
    private int lastClickRow = -1;
    private boolean running = true;
    public SimpleUI(Terminal terminal) {
        this(terminal, Paths.get(System.getProperty("user.home")));
    }
    public SimpleUI(Terminal terminal, Path startDir) {
        this.terminal = terminal;
        if (startDir != null && Files.isDirectory(startDir)) {
            this.dir = startDir.toAbsolutePath().normalize();
        }
    }
    public void run() {
        try {
            terminal.trackMouse(Terminal.MouseTracking.Normal);
            terminal.writer().print("\u001b[?1006h");
            terminal.writer().flush();
        } catch (Exception ignored) {
            // no mouse: every action still has a key
        }
        loadEntries();
        draw();
        boolean lastWasCr = false;
        while (running) {
            try {
                int key = terminal.reader().read();
                if (key == -1 || key == KeyInput.CTRL_C) break;
                if (KeyInput.isDuplicateEndOfLine(key, lastWasCr)) { lastWasCr = false; continue; }
                lastWasCr = key == KeyInput.CR;
                if (key == KeyInput.ESC) handleEscape();
                else if (KeyInput.isEnter(key)) activate();
                else if (screen == Screen.BROWSE) onBrowseKey(key);
                else if (screen == Screen.GROUPS) onGroupsKey(key);
                else onConfirmKey(key);
                if (running) draw();
            } catch (IOException e) {
                break;
            }
        }
        terminal.writer().print("\u001b[?1000l\u001b[?1006l\u001b[0m\n");
        terminal.writer().flush();
    }
    private void handleEscape() throws IOException {
        Reader r = terminal.reader();
        if (!r.ready()) { goUp(); return; }
        int c1 = r.read();
        if (c1 == 'O') { if (r.ready()) arrow(r.read()); return; }
        if (c1 != '[' || !r.ready()) { goUp(); return; }
        int c2 = r.read();
        if (c2 >= 'A' && c2 <= 'D') { arrow(c2); return; }
        if (c2 == 'M' && r.ready()) {
            int b = r.read() - 32;
            onMouse(b, Math.max(0, r.read() - 33), Math.max(0, r.read() - 33));
        } else if (c2 == '<') {
            sgrMouse(r);
        } else {
            goUp();
        }
    }
    private void sgrMouse(Reader r) throws IOException {
        StringBuilder buf = new StringBuilder();
        while (r.ready()) {
            int ch = r.read();
            if (ch == 'm') return;
            if (ch == 'M') {
                String[] p = buf.toString().split(";");
                if (p.length != 3) return;
                try {
                    onMouse(Integer.parseInt(p[0].trim()),
                            Math.max(0, Integer.parseInt(p[1].trim()) - 1),
                            Math.max(0, Integer.parseInt(p[2].trim()) - 1));
                } catch (NumberFormatException ignored) {
                    // malformed report
                }
                return;
            }
            buf.append((char) ch);
        }
    }
    private void arrow(int c) {
        switch (c) {
            case 'A' -> move(-1);
            case 'B' -> move(1);
            case 'C' -> activate();
            case 'D' -> goUp();
            default -> { }
        }
    }
    /** Single click selects; a second click on the same row within 450ms activates. */
    void onMouse(int button, int x, int y) {
        if ((button & 64) != 0) { move((button & 1) != 0 ? 3 : -3); return; }
        if ((button & 3) != 0) return;
        if (screen == Screen.CONFIRM) {
            if (y == confirmRow()) { if (x < cols() / 2) applyMove(); else { screen = Screen.GROUPS; message = CANCELLED; } }
            return;
        }
        int index = y - LIST_TOP;
        if (index < 0 || index >= visibleRows()) return;
        if (screen == Screen.BROWSE) {
            if (index >= itemCount()) return;
            cursor = index;
        } else {
            if (index >= groups.size()) return;
            groupCursor = index;
        }
        long now = System.currentTimeMillis();
        boolean twice = index == lastClickRow && now - lastClickAt < DOUBLE_CLICK_MS;
        lastClickRow = index;
        lastClickAt = now;
        if (twice) activate();
    }
    private void onBrowseKey(int key) {
        if (key == 127 || key == 8) { goUp(); return; }
        switch (key) {
            case 'k' -> move(-1);
            case 'j' -> move(1);
            case 'o', 'O' -> organiseHere();
            case 'q', 'Q' -> running = false;
            default -> { }
        }
    }
    private void onGroupsKey(int key) {
        switch (key) {
            case 'u', 'U' -> undo();
            case 'b', 'B' -> { screen = Screen.BROWSE; loadEntries(); }
            case 'q', 'Q' -> running = false;
            case 'k' -> move(-1);
            case 'j' -> move(1);
            default -> { }
        }
    }
    private void onConfirmKey(int key) {
        if (key == 'y' || key == 'Y') applyMove();
        else if (key == 'n' || key == 'N') { screen = Screen.GROUPS; message = CANCELLED; }
    }
    void activate() {
        if (screen == Screen.CONFIRM) return;
        if (screen == Screen.GROUPS) {
            if (!groups.isEmpty()) { screen = Screen.CONFIRM; message = ""; }
            return;
        }
        if (cursor == 0) { goUp(); return; }
        Path p = entries.get(cursor - 1);
        if (Files.isDirectory(p)) open(p);
        else message = "That is a file - press o to organise this folder.";
    }
    private void open(Path p) {
        dir = p; cursor = 0; message = "";
        loadEntries();
    }
    private void goUp() {
        if (screen == Screen.CONFIRM) { screen = Screen.GROUPS; message = CANCELLED; return; }
        if (screen == Screen.GROUPS) { screen = Screen.BROWSE; loadEntries(); return; }
        Path parent = dir.getParent();
        if (parent == null) { message = "Already at the top of this drive."; return; }
        open(parent);
    }
    private void move(int delta) {
        int size = screen == Screen.BROWSE ? itemCount() : groups.size();
        if (size == 0) return;
        if (screen == Screen.BROWSE) cursor = Math.floorMod(cursor + delta, size);
        else groupCursor = Math.floorMod(groupCursor + delta, size);
    }
    /** Row 0 is always the ".." entry. */
    int itemCount() {
        return entries.size() + 1;
    }
    void loadEntries() {
        try {
            entries = organizer.listEntries(dir, "");
            if (cursor >= itemCount()) cursor = Math.max(0, itemCount() - 1);
            message = "";
        } catch (IOException e) {
            entries = List.of(); cursor = 0;
            message = "Can't read this folder (permission).";
        }
    }
    void organiseHere() {
        loadEntries();
        try {
            groups = organizer.groupFiles(dir);
            groupCursor = 0;
            if (groups.isEmpty()) { message = "No files to organise here. Open another folder."; return; }
            screen = Screen.GROUPS;
            message = "";
        } catch (IOException e) {
            groups = List.of(); message = "Can't read this folder (permission).";
        }
    }
    private void reloadGroups() {
        try {
            groups = organizer.groupFiles(dir);
            groupCursor = Math.min(groupCursor, Math.max(0, groups.size() - 1));
        } catch (IOException e) {
            groups = List.of();
        }
    }
    FileOrganizer.Group selectedGroup() {
        return groups.isEmpty() ? null : groups.get(Math.min(groupCursor, groups.size() - 1));
    }
    void applyMove() {
        FileOrganizer.Group g = selectedGroup();
        if (g == null) { screen = Screen.GROUPS; return; }
        Path target = dir.resolve(g.getName());
        int n = g.getFiles().size(); String size = formatSize(g.getTotalSize());
        try {
            organizer.moveFiles(g.getFiles(), target);
            lastTarget = target;
            message = "Moved " + n + " files into " + g.getName() + "/ (" + size + ").  u undo";
            reloadGroups();
            screen = groups.isEmpty() ? Screen.BROWSE : Screen.GROUPS;
            loadEntries();
        } catch (IOException e) {
            message = "Move failed: " + e.getMessage();
            screen = Screen.GROUPS;
        }
    }
    private void cancel() {
        screen = Screen.GROUPS;
        message = "Cancelled. Nothing was moved.";
    }
    void undo() {
        try {
            organizer.undoMove();
            if (lastTarget != null) {
                try (var s = Files.list(lastTarget)) { if (s.findAny().isEmpty()) Files.delete(lastTarget); }
                catch (IOException ignored) { /* empty folder left behind is harmless */ }
                lastTarget = null;
            }
            message = "Undone. Files are back.";
            reloadGroups();
            if (groups.isEmpty()) { screen = Screen.BROWSE; loadEntries(); }
        } catch (IOException e) {
            message = "Undo failed: " + e.getMessage();
        }
    }
    int cols() { int w = terminal.getWidth(); return w > 0 ? w : 80; }
    int rows() { int h = terminal.getHeight(); return h > 0 ? h : 24; }
    int visibleRows() { return Math.max(1, rows() - LIST_TOP - FOOTER_ROWS); }
    int confirmRow() { return Math.max(LIST_TOP, rows() - FOOTER_ROWS - 3); }
    void draw() {
        int width = cols();
        int listRows = visibleRows();
        StringBuilder sb = new StringBuilder("\u001b[H\u001b[2J");
        sb.append("\u001b[1m").append(breadcrumb(width)).append("\u001b[0m\n");
        sb.append(truncate("-".repeat(width), width)).append('\n');
        if (screen == Screen.BROWSE) drawBrowse(sb, width, listRows);
        else if (screen == Screen.GROUPS) drawGroups(sb, width, listRows);
        else drawConfirm(sb, width);
        sb.append('\n').append(truncate(message.isBlank() ? " " : "  " + message, width)).append('\n');
        sb.append(truncate(cheatSheet(), width));
        terminal.writer().print(sb);
        terminal.writer().flush();
    }
    /** "Current: ~/Downloads/College" - home shortened to ~, forward slashes. */
    String breadcrumb(int width) {
        String h = System.getProperty("user.home").replace('\\', '/');
        String p = dir.toAbsolutePath().toString().replace('\\', '/');
        if (p.equals(h)) p = "~";
        else if (p.startsWith(h + "/")) p = "~" + p.substring(h.length());
        return truncate("Current: " + p, width);
    }
    private String cheatSheet() {
        return switch (screen) {
            case BROWSE -> "  [\u2191/\u2193] Move   [Enter] Open   [Backspace] Back   [o] Organise   [q] Quit";
            case GROUPS -> "  [\u2191/\u2193] Move   [Enter] Move group   [u] Undo   [Backspace] Back   [q] Quit";
            case CONFIRM -> "  [y] Yes, move   [n] No";
        };
    }
    private void drawBrowse(StringBuilder sb, int width, int listRows) {
        for (int i = 0; i < listRows; i++) {
            if (i >= itemCount()) { sb.append('\n'); continue; }
            String content = i == 0
                    ? BACK_ICON + " .."
                    : iconFor(entries.get(i - 1)) + " " + nameOf(entries.get(i - 1));
            sb.append(row(i == cursor, content, width)).append('\n');
        }
    }
    private void drawGroups(StringBuilder sb, int width, int listRows) {
        for (int i = 0; i < listRows; i++) {
            if (i >= groups.size()) { sb.append('\n'); continue; }
            FileOrganizer.Group g = groups.get(i);
            String content = BACK_ICON + " " + pad(g.getName(), 8)
                    + pad(g.getFiles().size() + " files", 12) + formatSize(g.getTotalSize());
            sb.append(row(i == groupCursor, content, width)).append('\n');
        }
    }
    private void drawConfirm(StringBuilder sb, int width) {
        FileOrganizer.Group g = selectedGroup();
        for (int i = LIST_TOP; i < confirmRow() - 2; i++) sb.append('\n');
        if (g == null) return;
        sb.append(truncate("   " + g.getFiles().size() + " files  ->  " + g.getName() + "/", width)).append('\n');
        sb.append(truncate("   frees about " + formatSize(g.getTotalSize()), width)).append('\n');
        sb.append('\n').append(truncate("   [y] Yes, move        [n] No", width)).append('\n');
    }
    /** One row: truncated before styling, so escape codes never eat the width. */
    private static String row(boolean selected, String content, int width) {
        String body = truncate(content, Math.max(1, width - 2));
        return selected ? "\u001b[7m> " + body + "\u001b[0m" : "  " + body;
    }
    private static String nameOf(Path p) {
        return p.getFileName() != null ? p.getFileName().toString() : p.toString();
    }
    static String iconFor(Path p) {
        if (Files.isDirectory(p)) return BACK_ICON;
        String n = nameOf(p);
        int dot = n.lastIndexOf('.');
        String ext = (dot > 0 && dot < n.length() - 1) ? n.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return switch (ext) {
            case "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic", "tif", "tiff" -> IMG_ICON;
            case "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "cab" -> ZIP_ICON;
            case "mp3", "wav", "flac", "aac", "m4a", "ogg", "wma", "opus" -> AUDIO_ICON;
            case "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "mpg", "mpeg" -> VIDEO_ICON;
            case "exe", "msi", "bat", "cmd", "sh", "app", "jar", "dll" -> APP_ICON;
            case "java", "py", "js", "ts", "jsx", "tsx", "c", "cpp", "h", "hpp", "cs", "html",
                 "css", "json", "xml", "yml", "yaml", "md", "sql", "txt", "log" -> CODE_ICON;
            default -> DOC_ICON;
        };
    }
    static String truncate(String s, int width) {
        if (width <= 0) return "";
        if (displayWidth(s) <= width) return s;
        StringBuilder out = new StringBuilder(); int used = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int w = charWidth(cp);
            if (used + w > width) break;
            out.appendCodePoint(cp);
            used += w;
            i += Character.charCount(cp);
        }
        return out.toString();
    }
    /** Terminal cells used by a string (emoji take two). */
    static int displayWidth(String s) {
        int total = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            total += charWidth(cp);
            i += Character.charCount(cp);
        }
        return total;
    }
    private static int charWidth(int cp) {
        if (cp == 0xFE0F || cp == 0x200D) return 0;
        return (cp > 0xFFFF && Character.getType(cp) == Character.OTHER_SYMBOL) ? 2 : 1;
    }
    private static String pad(String s, int width) {
        int gap = width - displayWidth(s);
        return gap <= 0 ? s : s + " ".repeat(gap);
    }
    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.0f MB", mb);
        return String.format("%.1f GB", mb / 1024.0);
    }
}
