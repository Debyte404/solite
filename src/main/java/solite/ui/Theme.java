package solite.ui;

import org.jline.utils.AttributedStyle;

/**
 * Theme object: holds the palette + border set + dither chars for the current
 * terminal capability. Pick once at startup via Theme.pick(terminal).
 *
 * Palette (Hermes yellow):
 *   ink    #0B0A06  background
 *   honey  #FFC800  titles, selected row, focused border
 *   amber  #8A6D00  secondary text, unfocused borders
 *   wax    #4A3D10  hairlines, gutters, inactive
 *   paper  #F5EDD6  file names, body
 *   ash    #9A917A  meta text (sizes, dates)
 *   leaf   #9DBE00  done check only
 *   ember  #FF5C00  warning only
 */
public final class Theme {

    public final int honey, amber, wax, paper, ash, leaf, ember;
    public final boolean roundBorders, ditherBlocks, mouseTracking;
    public final boolean colored;
    public final AttributedStyle styleHoney, styleAmber, styleWax, stylePaper, styleAsh;

    private Theme(int honey, int amber, int wax, int paper, int ash, int leaf, int ember,
                  boolean roundBorders, boolean ditherBlocks, boolean mouseTracking,
                  boolean colored) {
        this.honey = honey;
        this.amber = amber;
        this.wax = wax;
        this.paper = paper;
        this.ash = ash;
        this.leaf = leaf;
        this.ember = ember;
        this.roundBorders = roundBorders;
        this.ditherBlocks = ditherBlocks;
        this.mouseTracking = mouseTracking;
        this.colored = colored;

        this.styleHoney = AttributedStyle.DEFAULT.foreground(honey).bold();
        this.styleAmber = AttributedStyle.DEFAULT.foreground(amber);
        this.styleWax = AttributedStyle.DEFAULT.foreground(wax);
        this.stylePaper = AttributedStyle.DEFAULT.foreground(paper);
        this.styleAsh = AttributedStyle.DEFAULT.foreground(ash);
    }

    /**
     * Pick the best theme for the given terminal.
     * Detects truecolor → 256 → 16 → mono via COLORTERM + terminal type.
     */
    public static Theme pick(org.jline.terminal.Terminal terminal) {
        String type = terminal.getType();
        String colorTerm = System.getenv("COLORTERM");
        String term = System.getenv("TERM");

        // Truecolor: COLORTERM=truecolor or 24bit, or modern terminals
        if ("truecolor".equals(colorTerm) || "24bit".equals(colorTerm)) {
            return truecolor();
        }
        if (type != null && (type.contains("wt") || type.contains("wezterm")
                || type.contains("ghostty") || type.contains("kitty")
                || type.contains("alacritty") || type.contains("foot")
                || type.contains("xterm-256color") || type.contains("screen-256color")
                || type.contains("tmux-256color") || type.contains("rxvt-unicode-256color"))) {
            // Modern terminals that report 256color TERM are usually truecolor-capable
            return truecolor();
        }
        if (type != null && (type.equals("xterm") || type.equals("screen")
                || type.startsWith("vt100") || type.startsWith("vt220"))) {
            return v16();
        }

        // Fallback: 256 if TERM has 256, else 16, else mono
        if (term != null && term.contains("256color")) {
            return v256();
        }
        return v16();
    }

    /** Truecolor (24-bit) — full Hermes palette. */
    public static Theme truecolor() {
        return new Theme(
                0xFFC800, // honey
                0x8A6D00, // amber
                0x4A3D10, // wax
                0xF5EDD6, // paper
                0x9A917A, // ash
                0x9DBE00, // leaf
                0xFF5C00, // ember
                true,     // roundBorders
                true,     // ditherBlocks (░ enhancement)
                true,     // mouseTracking
                true      // colored
        );
    }

    /** 256-color — nearest palette entries. */
    public static Theme v256() {
        return new Theme(
                220,  // honey  #FFC800
                136,  // amber  #8A6D00
                58,   // wax    #4A3D10
                230,  // paper  #F5EDD6
                246,  // ash    #9A917A
                148,  // leaf   #9DBE00
                202,  // ember  #FF5C00
                true, // roundBorders
                false, // ditherBlocks (no ░ on 256)
                true,  // mouseTracking
                true   // colored
        );
    }

    /** 16-color — legacy terminals. */
    public static Theme v16() {
        return new Theme(
                AttributedStyle.YELLOW,     // honey (bold applied via style)
                AttributedStyle.YELLOW,     // amber
                AttributedStyle.BLACK,      // wax
                AttributedStyle.WHITE,      // paper
                AttributedStyle.WHITE,      // ash
                AttributedStyle.GREEN,      // leaf
                AttributedStyle.RED,        // ember
                false,                      // roundBorders (square only)
                false,                      // ditherBlocks
                false,                      // mouseTracking (flaky on legacy)
                true                        // colored
        );
    }

    /** Mono — no colors, layout + markers only. */
    public static Theme mono() {
        return new Theme(
                AttributedStyle.WHITE,
                AttributedStyle.WHITE,
                AttributedStyle.WHITE,
                AttributedStyle.WHITE,
                AttributedStyle.WHITE,
                AttributedStyle.WHITE,
                AttributedStyle.WHITE,
                false,
                false,
                false,
                false       // not colored
        );
    }
}
