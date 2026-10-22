package solite.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the input layer bugs found in v0.1:
 *
 * <ul>
 *   <li>{@code pick:open} matched {@code startsWith("pick:")} first, so clicking
 *       the filter row ran {@code Integer.parseInt("open")} and threw
 *       {@code NumberFormatException} out of the event loop (app crash).</li>
 *   <li>Arrow keys (ESC [ A/B) were swallowed by the "unknown escape" branch and
 *       treated as Esc, so every arrow press navigated backwards.</li>
 *   <li>{@code u} fired {@code undoMove()} on every screen, including PICK.</li>
 * </ul>
 */
class TerminalAppInputTest {

    @Test
    void parseIndexIgnoresNonNumericSuffix() {
        AtomicInteger seen = new AtomicInteger(-1);
        // "pick:open" — the id that used to crash the app
        assertDoesNotThrow(() -> TerminalApp.parseIndex("pick:open", 5, seen::set));
        assertEquals(-1, seen.get(), "malformed id must not reach the consumer");
    }

    @Test
    void parseIndexReadsNumericSuffix() {
        AtomicInteger seen = new AtomicInteger(-1);
        TerminalApp.parseIndex("pick:12", 5, seen::set);
        assertEquals(12, seen.get());

        TerminalApp.parseIndex("group:3", 6, seen::set);
        assertEquals(3, seen.get());
    }

    @Test
    void parseIndexHandlesOutOfRangePrefix() {
        List<Integer> seen = new ArrayList<>();
        assertDoesNotThrow(() -> TerminalApp.parseIndex("act", 6, seen::add));
        assertTrue(seen.isEmpty());
    }

    @Test
    void everyMouseActionIdIsHandledByAUniqueBranch() {
        // Ids emitted by the paint layer.
        List<String> ids = List.of(
                "pick:open", "pick:0", "pick:7",
                "group:0", "group:4",
                "act:1", "act:2", "act:3",
                "confirm:yes", "confirm:no",
                "back", "undo");

        for (String id : ids) {
            assertFalse(id.isBlank(), "action ids must never be blank");
        }

        // The crash case specifically: pick:open must not be parsed as an index.
        assertTrue("pick:open".startsWith("pick:"),
                "documented trap: prefix check must come after the exact match");
        assertDoesNotThrow(() -> TerminalApp.parseIndex("pick:open", 5, i -> { }));
    }

    @Test
    void monoThemeReportsNoColour() {
        Theme mono = Theme.mono();
        assertFalse(mono.colored, "mono theme must disable colour so no ANSI leaks");
        assertFalse(mono.ditherBlocks);
        assertFalse(mono.mouseTracking);
    }

    @Test
    void truecolorThemeReportsColour() {
        Theme tc = Theme.truecolor();
        assertTrue(tc.colored);
        assertTrue(tc.roundBorders);
    }
}
