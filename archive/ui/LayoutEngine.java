package solite.ui;

/**
 * Layout engine: picks a breakpoint based on terminal size and computes
 * panel geometry. Reflows live on resize.
 *
 * Breakpoints:
 *   XS  narrow:  width < 70   — one column, no shadows
 *   S   standard: 70–109      — one column + inline preview
 *   M   wide:     110–139     — two panes (55% / 45%)
 *   L   ultra:    >= 140      — two panes + far-right rail
 */
public final class LayoutEngine {

    public enum Breakpoint { XS, S, M, L }

    public final int width;
    public final int height;
    public final Breakpoint breakpoint;

    // Panel regions (computed from breakpoint)
    public final int headerRows;
    public final int footerRows;
    public final int listTop;
    public final int listBottom;
    public final int listLeft;
    public final int listRight;
    public final int previewLeft;
    public final int previewRight;
    public final int railLeft;
    public final int railRight;
    public final boolean shadows;

    public LayoutEngine(int width, int height) {
        this.width = width;
        this.height = height;
        this.breakpoint = pickBreakpoint(width);

        this.headerRows = (height < 14) ? 0 : 2;
        this.footerRows = (breakpoint == Breakpoint.XS) ? 1 : 2;
        this.listTop = headerRows;
        this.listBottom = height - footerRows;
        this.shadows = (breakpoint != Breakpoint.XS && width >= 70);

        switch (breakpoint) {
            case XS:
                this.listLeft = 1;
                this.listRight = width - 1;
                this.previewLeft = 0;
                this.previewRight = 0;
                this.railLeft = 0;
                this.railRight = 0;
                break;
            case S:
                this.listLeft = 1;
                this.listRight = width - 1;
                this.previewLeft = 0;
                this.previewRight = 0;
                this.railLeft = 0;
                this.railRight = 0;
                break;
            case M:
                this.listLeft = 1;
                this.listRight = (int) (width * 0.55);
                this.previewLeft = listRight + 1;
                this.previewRight = width - 1;
                this.railLeft = 0;
                this.railRight = 0;
                break;
            case L:
                this.listLeft = 1;
                this.listRight = (int) (width * 0.45);
                this.previewLeft = listRight + 1;
                this.previewRight = (int) (width * 0.78);
                this.railLeft = previewRight + 1;
                this.railRight = width - 1;
                break;
            default:
                throw new IllegalStateException("unknown breakpoint: " + breakpoint);
        }
    }

    private static Breakpoint pickBreakpoint(int width) {
        if (width < 70) return Breakpoint.XS;
        if (width < 110) return Breakpoint.S;
        if (width < 140) return Breakpoint.M;
        return Breakpoint.L;
    }

    /** True if the terminal is in emergency mode (tiny height). */
    public boolean isEmergency() {
        return height < 14;
    }

    /** Number of rows available for the list area. */
    public int listHeight() {
        return Math.max(0, listBottom - listTop);
    }

    /** Number of columns in the list area. */
    public int listWidth() {
        return Math.max(0, listRight - listLeft);
    }

    /** True if preview pane is visible. */
    public boolean hasPreview() {
        return breakpoint == Breakpoint.M || breakpoint == Breakpoint.L;
    }

    /** True if rail is visible. */
    public boolean hasRail() {
        return breakpoint == Breakpoint.L;
    }
}
