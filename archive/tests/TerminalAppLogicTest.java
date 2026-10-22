package solite.ui;

import org.junit.jupiter.api.Test;

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
}
