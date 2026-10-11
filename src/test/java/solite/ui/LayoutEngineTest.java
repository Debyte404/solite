package solite.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4: breakpoint matrix + geometry + resize fuzz.
 * Sizes from WORKER.md: 60/80/110/150 x 14/24/40.
 */
class LayoutEngineTest {

    @ParameterizedTest(name = "width {0} -> {1}")
    @CsvSource({
            "60,  XS",
            "69,  XS",
            "70,  S",
            "80,  S",
            "109, S",
            "110, M",
            "139, M",
            "140, L",
            "150, L",
            "200, L"
    })
    void breakpointMatrix(int width, LayoutEngine.Breakpoint expected) {
        LayoutEngine layout = new LayoutEngine(width, 24);
        assertEquals(expected, layout.breakpoint, "width=" + width);
    }

    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({
            "60, 14",
            "80, 24",
            "110, 24",
            "150, 40",
            "80, 40",
            "150, 14"
    })
    void geometrySane(int width, int height) {
        LayoutEngine layout = new LayoutEngine(width, height);
        assertTrue(layout.listBottom > layout.listTop, "list area must have height");
        assertTrue(layout.listRight > layout.listLeft, "list area must have width");
        assertTrue(layout.listHeight() >= 0);
        assertTrue(layout.listWidth() >= 0);
        if (layout.breakpoint == LayoutEngine.Breakpoint.XS) {
            assertEquals(1, layout.footerRows, "XS footer is 1 row");
            assertFalse(layout.shadows, "XS has no shadows");
            assertFalse(layout.hasPreview(), "XS has no preview pane");
            assertFalse(layout.hasRail(), "XS has no rail");
        } else {
            assertEquals(2, layout.footerRows, "S/M/L footer is 2 rows");
        }
        if (layout.breakpoint == LayoutEngine.Breakpoint.M) {
            assertTrue(layout.hasPreview(), "M has preview");
            assertFalse(layout.hasRail(), "M has no rail");
            assertEquals((int) (width * 0.55), layout.listRight, "M left pane is 55%");
        }
        if (layout.breakpoint == LayoutEngine.Breakpoint.L) {
            assertTrue(layout.hasPreview(), "L has preview");
            assertTrue(layout.hasRail(), "L has rail");
        }
    }

    @Test
    void emergencyModeBelow14Rows() {
        assertTrue(new LayoutEngine(100, 13).isEmergency());
        assertTrue(new LayoutEngine(100, 10).isEmergency());
        assertFalse(new LayoutEngine(100, 14).isEmergency());
        assertFalse(new LayoutEngine(100, 24).isEmergency());
        // Emergency: no header rows, footer still present
        LayoutEngine tiny = new LayoutEngine(100, 10);
        assertEquals(0, tiny.headerRows);
    }

    @Test
    void resizeFuzzSelectionStable() {
        // Random sizes must never throw and must keep sane geometry.
        Random rng = new Random(42);
        for (int i = 0; i < 200; i++) {
            int w = 20 + rng.nextInt(200);
            int h = 5 + rng.nextInt(60);
            assertDoesNotThrow(() -> {
                LayoutEngine layout = new LayoutEngine(w, h);
                assertTrue(layout.listHeight() >= 0);
                assertTrue(layout.listWidth() >= 0);
                assertNotNull(layout.breakpoint);
            }, "size " + w + "x" + h + " must not throw");
        }
    }

    @Test
    void tinyWindowNeverCrashes() {
        // Extreme small sizes: 1x1 up to 10x10
        for (int w = 1; w <= 10; w++) {
            for (int h = 1; h <= 10; h++) {
                LayoutEngine layout = new LayoutEngine(w, h);
                assertNotNull(layout.breakpoint);
                assertTrue(layout.listHeight() >= 0);
            }
        }
    }
}
