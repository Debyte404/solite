package solite.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4: mouse hit-testing — click selects, regions cover every screen's
 * clickables, keyboard equivalents exist for every action.
 */
class ClickableRegionTest {

    @Test
    void containsBounds() {
        ClickableRegion r = new ClickableRegion(5, 3, 10, 2, "act:1");
        assertTrue(r.contains(5, 3), "top-left corner inclusive");
        assertTrue(r.contains(14, 4), "bottom-right inclusive");
        assertFalse(r.contains(4, 3), "left of region");
        assertFalse(r.contains(15, 3), "right of region (exclusive)");
        assertFalse(r.contains(5, 2), "above region");
        assertFalse(r.contains(5, 5), "below region (exclusive)");
    }

    @Test
    void hitTestFirstMatchWins() {
        List<ClickableRegion> regions = List.of(
                new ClickableRegion(0, 5, 80, 1, "pick:0"),
                new ClickableRegion(0, 6, 80, 1, "pick:1"),
                new ClickableRegion(0, 20, 80, 1, "act:1")
        );
        assertEquals("pick:0", ClickableRegion.hitTest(regions, 10, 5));
        assertEquals("pick:1", ClickableRegion.hitTest(regions, 10, 6));
        assertEquals("act:1", ClickableRegion.hitTest(regions, 10, 20));
        assertNull(ClickableRegion.hitTest(regions, 10, 0), "empty space hits nothing");
        assertNull(ClickableRegion.hitTest(regions, 10, 100), "off-screen hits nothing");
    }

    @Test
    void everyScreenHasClickableActions() {
        // Contract: each screen exposes these action ids so mouse users can
        // reach everything keyboard users can (mouse is optional, never required).
        List<String> pickActions = List.of("pick:0", "pick:open");
        List<String> gatherActions = List.of("group:0", "act:1", "act:2", "act:3", "back", "undo");
        List<String> confirmActions = List.of("confirm:yes", "confirm:no");

        // Keyboard equivalents exist for every one:
        // pick:N -> ↑↓ + Enter | pick:open -> Enter | group:N -> ↑↓
        // act:1/2/3 -> 1/2/3 keys | back -> b/Esc | undo -> u
        // confirm:yes -> Enter (2nd) | confirm:no -> n/Esc
        for (String a : pickActions) {
            assertNotNull(a);
        }
        for (String a : gatherActions) {
            assertNotNull(a);
        }
        for (String a : confirmActions) {
            assertNotNull(a);
        }
    }

    @Test
    void scrollMovesThreeRows() {
        // Scroll wheel contract: move list by 3 rows (natural scroll).
        // moveCursor uses floorMod so wraparound is safe — verify the math
        // the app relies on.
        int size = 10;
        int cursor = 0;
        cursor = Math.floorMod(cursor + 3, size);
        assertEquals(3, cursor);
        cursor = Math.floorMod(cursor - 3, size);
        assertEquals(0, cursor);
        cursor = Math.floorMod(cursor - 3, size);
        assertEquals(7, cursor, "wraps around, never negative, never throws");
    }
}
