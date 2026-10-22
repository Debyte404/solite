package solite.ui;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4: pure-logic unit tests for TerminalApp helpers.
 * Rendering needs a live terminal; these cover the logic underneath it.
 */
class TerminalAppLogicTest {

    @Test
    void formatSizeBytes() {
        assertEquals("0 B", TerminalApp.formatSize(0));
        assertEquals("512 B", TerminalApp.formatSize(512));
        assertEquals("1023 B", TerminalApp.formatSize(1023));
    }

    @Test
    void formatSizeKiloAndMega() {
        assertEquals("1.0 KB", TerminalApp.formatSize(1024));
        assertEquals("410 MB", TerminalApp.formatSize(410L * 1024 * 1024),
                "DESIGN example: frees ~410 MB");
    }

    @Test
    void formatSizeGiga() {
        assertEquals("1.5 GB", TerminalApp.formatSize((long) (1.5 * 1024 * 1024 * 1024)));
    }

    @Test
    void shrinkPathShortUnchanged() {
        // Short paths pass through untouched (platform separators preserved).
        String shortPath = Paths.get("Photos", "2024").toString();
        assertEquals(shortPath, TerminalApp.shrinkPath(Paths.get("Photos", "2024"), 60));
    }

    @Test
    void shrinkPathEllipsisMiddle() {
        String shrunk = TerminalApp.shrinkPath(
                Paths.get("D:/very/long/path/to/vacation-photos-2024"), 20);
        assertTrue(shrunk.contains("…"), "middle ellipsis per DESIGN reflow checklist");
        assertTrue(shrunk.length() <= 21, "fits budget, was: " + shrunk);
    }

    @Test
    void truncateNeverExceedsWidth() {
        assertEquals("abc", TerminalApp.truncate("abcdef", 3));
        assertEquals("abc", TerminalApp.truncate("abc", 5), "short strings untouched");
        assertEquals("", TerminalApp.truncate("abc", 0));
    }

    @Test
    void padRightFills() {
        assertEquals("ab   ", TerminalApp.padRight("ab", 5));
        assertEquals("abcde", TerminalApp.padRight("abcdef", 5), "long strings cut");
    }

    @Test
    void screenEnumOrder() {
        // Flow contract: PICK -> GATHER -> CONFIRM, back via b/Esc.
        TerminalApp.Screen[] screens = TerminalApp.Screen.values();
        assertEquals(3, screens.length, "DESIGN: 3 screens only");
        assertEquals(TerminalApp.Screen.PICK, screens[0]);
        assertEquals(TerminalApp.Screen.GATHER, screens[1]);
        assertEquals(TerminalApp.Screen.CONFIRM, screens[2]);
    }

    @Test
    void initializeDumbTerminalUsesFallbackDimensions() throws Exception {
        org.jline.terminal.Terminal term = org.jline.terminal.TerminalBuilder.builder()
                .dumb(true)
                .build();
        TerminalApp app = new TerminalApp(term);
        assertNotNull(app);
    }

    @Test
    void runExitsCleanlyOnEof() throws Exception {
        org.jline.terminal.Terminal term = org.jline.terminal.TerminalBuilder.builder()
                .dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .build();
        TerminalApp app = new TerminalApp(term);
        assertDoesNotThrow(app::run);
    }

    @Test
    void runExitsCleanlyOnQKey() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        org.jline.terminal.Terminal term = org.jline.terminal.TerminalBuilder.builder()
                .dumb(true)
                .streams(new ByteArrayInputStream("q".getBytes(StandardCharsets.UTF_8)), out)
                .build();
        TerminalApp app = new TerminalApp(term);
        assertDoesNotThrow(app::run);
        String output = out.toString(StandardCharsets.UTF_8);
        // Verify alternate buffer enter and clean exit escape sequences
        assertTrue(output.contains("\u001b[?1049h"), "Enters alternate screen buffer on start");
        assertTrue(output.contains("\u001b[?1049l"), "Leaves alternate screen buffer on quit");
        assertTrue(output.contains("\u001b[?25h"), "Restores cursor visibility on quit");
    }

    @Test
    void runTogglesHelpModalOnQuestionMark() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        org.jline.terminal.Terminal term = org.jline.terminal.TerminalBuilder.builder()
                .dumb(true)
                .streams(new ByteArrayInputStream("?q".getBytes(StandardCharsets.UTF_8)), out)
                .build();
        TerminalApp app = new TerminalApp(term);
        assertDoesNotThrow(app::run);
        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Solite Help & Instructions"), "Renders help modal on ? key");
    }

    @Test
    void pickItemModelTypes() {
        TerminalApp.PickItem quick = new TerminalApp.PickItem(
                TerminalApp.PickItem.Type.QUICK_TIDY_ALL, Paths.get("."), "Quick Tidy", 0);
        TerminalApp.PickItem tidy = new TerminalApp.PickItem(
                TerminalApp.PickItem.Type.TIDY_CURRENT, Paths.get("."), "Tidy", 0);
        TerminalApp.PickItem dups = new TerminalApp.PickItem(
                TerminalApp.PickItem.Type.FIND_DUPLICATES, Paths.get("."), "Duplicates", 0);
        TerminalApp.PickItem up = new TerminalApp.PickItem(
                TerminalApp.PickItem.Type.GO_UP, Paths.get(".."), "..", 0);
        TerminalApp.PickItem drive = new TerminalApp.PickItem(
                TerminalApp.PickItem.Type.DRIVE, Paths.get("C:\\"), "C:\\", 0);
        TerminalApp.PickItem dir = new TerminalApp.PickItem(
                TerminalApp.PickItem.Type.DIRECTORY, Paths.get("photos"), "photos/", 0);
        TerminalApp.PickItem file = new TerminalApp.PickItem(
                TerminalApp.PickItem.Type.FILE, Paths.get("test.txt"), "test.txt", 1024);

        assertEquals(TerminalApp.PickItem.Type.QUICK_TIDY_ALL, quick.type);
        assertEquals(TerminalApp.PickItem.Type.TIDY_CURRENT, tidy.type);
        assertEquals(TerminalApp.PickItem.Type.FIND_DUPLICATES, dups.type);
        assertEquals(TerminalApp.PickItem.Type.GO_UP, up.type);
        assertEquals(TerminalApp.PickItem.Type.DRIVE, drive.type);
        assertEquals(TerminalApp.PickItem.Type.DIRECTORY, dir.type);
        assertEquals(TerminalApp.PickItem.Type.FILE, file.type);
        assertEquals(1024, file.size);
    }
}
