package solite.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A clickable rectangle on screen, rebuilt on every repaint/reflow.
 * Stores viewport coords (x, y, w, h) plus the action id to fire.
 * Every mouse action has a keyboard equivalent — mouse is never required.
 */
public final class ClickableRegion {

    public final int x;
    public final int y;
    public final int w;
    public final int h;
    public final String action;

    public ClickableRegion(int x, int y, int w, int h, String action) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.action = action;
    }

    /** True if the 0-based viewport point is inside this region. */
    public boolean contains(int px, int py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /** First region containing the point, or null. Pure logic — unit testable. */
    public static String hitTest(List<ClickableRegion> regions, int px, int py) {
        for (ClickableRegion r : regions) {
            if (r.contains(px, py)) {
                return r.action;
            }
        }
        return null;
    }
}
