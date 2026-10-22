package solite.ui;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the "blank screen" bug in v0.1.
 *
 * <p>JLine's {@code Display} keeps its own copy of the terminal size and paints
 * <em>nothing at all</em> until {@code Display.resize(columns, rows)} has been
 * called. {@code TerminalApp} created the Display and then called
 * {@code update()} directly, so the app entered its event loop and sat there
 * with a completely empty screen — no error, no output, just blank.
 *
 * <p>This test drives the real paint path against a terminal of known size and
 * asserts that a frame is actually produced.
 */
class TerminalAppPaintTest {

    /** Build a terminal bound to in-memory streams with a fixed size. */
    private static Terminal terminalOf(int cols, int rows, ByteArrayOutputStream sink) throws Exception {
        return TerminalBuilder.builder()
                .name("test")
                .system(false)
                .type("xterm-256color")
                .streams(new ByteArrayInputStream(new byte[0]), sink)
                .size(new Size(cols, rows))
                .build();
    }

    private static String paint(int cols, int rows) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        Terminal terminal = terminalOf(cols, rows, sink);
        TerminalApp app = new TerminalApp(terminal);

        Method repaint = TerminalApp.class.getDeclaredMethod("repaint");
        repaint.setAccessible(true);
        repaint.invoke(app);

        return sink.toString(StandardCharsets.UTF_8);
    }

    @Test
    void repaintWritesAFrameInsteadOfNothing() throws Exception {
        String frame = paint(100, 30);
        assertFalse(frame.isEmpty(),
                "Display was never resized — this is the blank-screen bug: repaint wrote 0 bytes");
        assertTrue(frame.length() > 200, "a 100x30 frame should be substantial, got " + frame.length() + " bytes");
    }

    @Test
    void firstFrameContainsTheBannerAndFolderList() throws Exception {
        String frame = paint(100, 30);
        String plain = frame.replaceAll("\\u001b\\[[0-9;()A-Za-z]*", "");

        assertTrue(plain.contains("solite"), "banner should name the app");
        assertTrue(plain.contains("Folder"), "Screen A should draw the Folder panel");
        assertTrue(plain.contains("Filter"), "Screen A should draw the filter line");
    }

    @Test
    void narrowTerminalStillPaints() throws Exception {
        String frame = paint(60, 20);
        assertFalse(frame.isEmpty(), "XS breakpoint must still render");
    }

    @Test
    void emergencyModeStillPaints() throws Exception {
        String frame = paint(80, 12);
        assertFalse(frame.isEmpty(), "emergency mode (height < 14) must still render");
    }

    @Test
    void zeroSizedTerminalFallsBackInsteadOfPaintingNothing() throws Exception {
        // Some hosts report 0x0 before the first resize event.
        String frame = paint(0, 0);
        assertFalse(frame.isEmpty(), "0x0 terminal must fall back to 80x24, not render blank");
    }
}
