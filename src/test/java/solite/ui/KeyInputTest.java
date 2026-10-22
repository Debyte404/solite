package solite.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the CRLF double-Enter bug.
 *
 * <p>Windows terminals send Enter as two bytes, CR then LF. Both front ends
 * treated each of them as a separate Enter, so one keypress fired two actions.
 * On the simplify flow that meant pressing Enter on a group opened the confirm
 * screen and immediately applied the move — a single keystroke moving files.
 */
class KeyInputTest {

    @Test
    void lineFeedAfterCarriageReturnIsDropped() {
        assertTrue(KeyInput.isDuplicateEndOfLine(KeyInput.LF, true),
                "LF following CR is the redundant half of one Enter press");
    }

    @Test
    void lineFeedOnItsOwnIsARealEnter() {
        assertFalse(KeyInput.isDuplicateEndOfLine(KeyInput.LF, false),
                "a bare LF (some terminals) must still work as Enter");
    }

    @Test
    void carriageReturnIsNeverADuplicate() {
        assertFalse(KeyInput.isDuplicateEndOfLine(KeyInput.CR, true));
        assertFalse(KeyInput.isDuplicateEndOfLine(KeyInput.CR, false));
    }

    @Test
    void otherKeysAreNeverDuplicates() {
        assertFalse(KeyInput.isDuplicateEndOfLine('y', true));
        assertFalse(KeyInput.isDuplicateEndOfLine(KeyInput.ESC, true));
    }

    @Test
    void aSingleCrlfPairYieldsExactlyOneEnter() {
        // Simulate the byte stream Windows sends for one Enter press.
        int[] stream = {KeyInput.CR, KeyInput.LF};
        int enters = 0;
        boolean lastWasCr = false;
        for (int key : stream) {
            if (KeyInput.isDuplicateEndOfLine(key, lastWasCr)) {
                lastWasCr = false;
                continue;
            }
            lastWasCr = (key == KeyInput.CR);
            if (KeyInput.isEnter(key)) {
                enters++;
            }
        }
        assertEquals(1, enters, "one Enter press must produce one action");
    }

    @Test
    void twoCrlfPairsYieldTwoEnters() {
        int[] stream = {KeyInput.CR, KeyInput.LF, KeyInput.CR, KeyInput.LF};
        int enters = 0;
        boolean lastWasCr = false;
        for (int key : stream) {
            if (KeyInput.isDuplicateEndOfLine(key, lastWasCr)) {
                lastWasCr = false;
                continue;
            }
            lastWasCr = (key == KeyInput.CR);
            if (KeyInput.isEnter(key)) {
                enters++;
            }
        }
        assertEquals(2, enters, "two Enter presses must still produce two actions");
    }
}
