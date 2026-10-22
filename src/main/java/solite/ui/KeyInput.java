package solite.ui;

/**
 * Small input helpers shared by both front ends.
 */
public final class KeyInput {

    public static final int CTRL_C = 3;
    public static final int ESC = 27;
    public static final int CR = 13;
    public static final int LF = 10;

    private KeyInput() {
        // utility class
    }

    /**
     * Windows terminals send Enter as CR + LF. Treating both bytes as Enter
     * fires every action twice — which on the confirm screen means a single
     * Enter press both opens and applies a move. The LF must be dropped when
     * it directly follows a CR.
     *
     * @param key          the byte just read
     * @param previousWasCr whether the previous byte was a carriage return
     * @return true when this byte is the redundant half of a CRLF pair
     */
    public static boolean isDuplicateEndOfLine(int key, boolean previousWasCr) {
        return key == LF && previousWasCr;
    }

    /** True for the bytes that mean "Enter" to the user. */
    public static boolean isEnter(int key) {
        return key == CR || key == LF;
    }
}
