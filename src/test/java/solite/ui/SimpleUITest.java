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
 * The simple front end must always paint something actionable: a header naming
 * the current folder, the list, and a footer with the keys. These tests drive
 * {@code draw()} against an in-memory terminal so a blank screen or a missing
 * hint fails here instead of in front of the user.
 */
class SimpleUITest {

    private static Terminal terminal(int cols, int rows, ByteArrayOutputStream sink) throws Exception {
        return TerminalBuilder.builder()
                .name("test").system(false).type("xterm-256color")
                .streams(new ByteArrayInputStream(new byte[0]), sink)
                .size(new Size(cols, rows))
                .build();
    }

    private static String draw(int cols, int rows) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        Terminal t = terminal(cols, rows, sink);
        SimpleUI ui = new SimpleUI(t);

        Method load = SimpleUI.class.getDeclaredMethod("loadEntries");
        load.setAccessible(true);
        load.invoke(ui);

        Method draw = SimpleUI.class.getDeclaredMethod("draw");
        draw.setAccessible(true);
        draw.invoke(ui);

        return sink.toString(StandardCharsets.UTF_8);
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("\\u001b\\[[0-9;]*[A-Za-z]", "");
    }

    @Test
    void firstScreenShowsFolderAndKeys() throws Exception {
        String out = stripAnsi(draw(100, 30));
        assertTrue(out.contains("solite"), "header should name the app");
        assertTrue(out.contains("Folder"), "pick screen should say Folder");
        assertTrue(out.contains("Enter"), "footer must show the Enter action");
        assertTrue(out.contains("quit"), "footer must show how to leave");
    }

    @Test
    void paintsAtNarrowWidth() throws Exception {
        String out = draw(40, 15);
        assertFalse(out.isBlank(), "narrow terminals must still render");
    }

    @Test
    void paintsOnZeroSizedTerminal() throws Exception {
        String out = draw(0, 0);
        assertFalse(out.isBlank(), "0x0 must fall back to 80x24, not render nothing");
    }

    @Test
    void everyLineFitsTheWidth() throws Exception {
        int width = 60;
        String out = stripAnsi(draw(width, 20));
        for (String line : out.split("\r\n|\n")) {
            assertTrue(line.length() <= width,
                    "line wider than terminal (" + line.length() + " > " + width + "): [" + line + "]");
        }
    }

    @Test
    void formatSizeReadsNaturally() {
        assertEquals("512 B", SimpleUI.formatSize(512));
        assertEquals("2.0 KB", SimpleUI.formatSize(2048));
        assertEquals("5 MB", SimpleUI.formatSize(5L * 1024 * 1024));
        assertEquals("2.0 GB", SimpleUI.formatSize(2L * 1024 * 1024 * 1024));
    }
}
