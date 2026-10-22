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
 * The browser must always paint something usable: a title, the list starting
 * with the ".." row, and a footer with the keys. A blank screen fails here
 * instead of in front of the user.
 */
class SimpleUITest {

    private static String draw(int cols, int rows) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        Terminal t = TerminalBuilder.builder()
                .name("test").system(false).type("xterm-256color")
                .streams(new ByteArrayInputStream(new byte[0]), sink)
                .size(new Size(cols, rows))
                .build();
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
    void firstScreenShowsTitleBackRowAndKeys() throws Exception {
        String out = stripAnsi(draw(100, 30));
        assertTrue(out.contains("Current:"), "header must show the breadcrumb path");
        assertTrue(out.contains(".."), "the back row must be visible");
        assertTrue(out.contains("[Enter] Open"), "footer must show the Enter hint");
        assertTrue(out.contains("[q] Quit"), "footer must show how to quit");
        assertTrue(out.contains("[Backspace] Back"), "footer must show how to go back");
    }

    @Test
    void paintsAtNarrowWidth() throws Exception {
        assertFalse(draw(40, 15).isBlank(), "narrow terminals must still render");
    }

    @Test
    void paintsOnZeroSizedTerminal() throws Exception {
        assertFalse(draw(0, 0).isBlank(), "0x0 must fall back to 80x24");
    }

    @Test
    void everyLineFitsTheWidth() throws Exception {
        int width = 60;
        for (String line : stripAnsi(draw(width, 20)).split("\r\n|\n")) {
            int w = SimpleUI.displayWidth(line);
            assertTrue(w <= width, "line too wide (" + w + " > " + width + "): [" + line + "]");
        }
    }

    @Test
    void iconsAreChosenByType() throws Exception {
        assertEquals(SimpleUI.BACK_ICON, SimpleUI.iconFor(java.nio.file.Paths.get("C:/tmp")));
        assertEquals(SimpleUI.IMG_ICON, SimpleUI.iconFor(java.nio.file.Paths.get("photo.jpg")));
        assertEquals(SimpleUI.ZIP_ICON, SimpleUI.iconFor(java.nio.file.Paths.get("bundle.zip")));
        assertEquals(SimpleUI.AUDIO_ICON, SimpleUI.iconFor(java.nio.file.Paths.get("song.mp3")));
        assertEquals(SimpleUI.VIDEO_ICON, SimpleUI.iconFor(java.nio.file.Paths.get("clip.mp4")));
        assertEquals(SimpleUI.APP_ICON, SimpleUI.iconFor(java.nio.file.Paths.get("setup.exe")));
        assertEquals(SimpleUI.CODE_ICON, SimpleUI.iconFor(java.nio.file.Paths.get("Main.java")));
        assertEquals(SimpleUI.DOC_ICON, SimpleUI.iconFor(java.nio.file.Paths.get("resume.pdf")));
    }

    @Test
    void emojiCountAsTwoCells() {
        assertEquals(2, SimpleUI.displayWidth(SimpleUI.BACK_ICON), "an emoji occupies two cells");
        assertEquals(4, SimpleUI.displayWidth("ab" + SimpleUI.BACK_ICON), "two letters plus one emoji");
    }

    @Test
    void truncateNeverSplitsAnEmoji() {
        String s = SimpleUI.BACK_ICON + SimpleUI.BACK_ICON + "Photos";
        String cut = SimpleUI.truncate(s, 5);
        assertTrue(SimpleUI.displayWidth(cut) <= 5, "must fit the width");
        assertFalse(cut.isEmpty(), "must keep something");
    }

    @Test
    void formatSizeReadsNaturally() {
        assertEquals("512 B", SimpleUI.formatSize(512));
        assertEquals("2.0 KB", SimpleUI.formatSize(2048));
        assertEquals("5 MB", SimpleUI.formatSize(5L * 1024 * 1024));
        assertEquals("2.0 GB", SimpleUI.formatSize(2L * 1024 * 1024 * 1024));
    }
}
