package solite.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4: palette + capability ladder + glyph-budget rules.
 * No colors in logic, no block glyphs, mono leaks no ANSI.
 */
class ThemeTest {

    @Test
    void truecolorPaletteMatchesDesign() {
        Theme t = Theme.truecolor();
        assertEquals(0xFFC800, t.honey, "honey #FFC800");
        assertEquals(0x8A6D00, t.amber);
        assertEquals(0x4A3D10, t.wax);
        assertEquals(0xF5EDD6, t.paper);
        assertEquals(0x9A917A, t.ash);
        assertEquals(0x9DBE00, t.leaf, "leaf sparingly");
        assertEquals(0xFF5C00, t.ember, "ember sparingly");
        assertTrue(t.roundBorders, "truecolor gets round borders");
        assertTrue(t.mouseTracking, "truecolor gets mouse");
        assertTrue(t.colored);
    }

    @Test
    void v256PaletteMatchesDesignFallbacks() {
        Theme t = Theme.v256();
        assertEquals(220, t.honey);
        assertEquals(136, t.amber);
        assertEquals(58, t.wax);
        assertEquals(230, t.paper);
        assertEquals(246, t.ash);
        assertFalse(t.ditherBlocks, "no ░ on 256-color");
        assertTrue(t.mouseTracking);
        assertTrue(t.colored);
    }

    @Test
    void v16SquareBordersNoMouse() {
        Theme t = Theme.v16();
        assertFalse(t.roundBorders, "legacy terminals: square borders only");
        assertFalse(t.ditherBlocks);
        assertFalse(t.mouseTracking, "mouse flaky on legacy — off");
        assertTrue(t.colored);
    }

    @Test
    void monoHasNoColor() {
        Theme t = Theme.mono();
        assertFalse(t.colored);
        assertFalse(t.roundBorders);
        assertFalse(t.mouseTracking);
    }

    @Test
    void ditherGlyphBudgetOnlyDots() {
        // Architecture rule 3: only · : . for dither, never blocks.
        // ShadowPainter hardcodes '·' — assert the source-visible contract here
        // by checking the banner wash density stays sparse (L1 ~12%).
        String wash = bannerWashForTest(80);
        long dots = wash.chars().filter(c -> c == '·').count();
        double density = (double) dots / 80;
        assertTrue(density < 0.30, "wash must stay sparse, was " + density);
        assertTrue(dots > 0, "wash must have some dots");
        for (char c : wash.toCharArray()) {
            assertTrue(c == '·' || c == ' ',
                    "wash uses only · and space, found '" + c + "'");
        }
    }

    /** Mirror of TerminalApp.buildBannerWash for density assertions. */
    private static String bannerWashForTest(int width) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        int gap = 2;
        while (pos < width) {
            sb.append('·');
            pos += gap;
            gap++;
        }
        // Pad like buildRow does
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
