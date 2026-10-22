package solite.ui;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fuzzy filtering: typing filters the list immediately, Backspace trims the
 * filter before it walks up a folder, and Esc clears the filter first.
 */
class SimpleUIFilterTest {

    private static SimpleUI uiAt(Path dir) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        Terminal t = TerminalBuilder.builder()
                .name("test").system(false).type("xterm-256color")
                .streams(new ByteArrayInputStream(new byte[0]), sink)
                .size(new Size(100, 30))
                .build();
        SimpleUI ui = new SimpleUI(t);
        Field f = SimpleUI.class.getDeclaredField("dir");
        f.setAccessible(true);
        f.set(ui, dir);
        ui.loadEntries();
        return ui;
    }

    private static void key(SimpleUI ui, int k) throws Exception {
        Method m = SimpleUI.class.getDeclaredMethod("onBrowseKey", int.class);
        m.setAccessible(true);
        m.invoke(ui, k);
    }

    private static String filter(SimpleUI ui) throws Exception {
        Field f = SimpleUI.class.getDeclaredField("filter");
        f.setAccessible(true);
        return (String) f.get(ui);
    }

    private static int itemCount(SimpleUI ui) {
        return ui.itemCount();
    }

    @Test
    void typingFiltersTheListImmediately(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("holiday.jpg"));
        Files.createFile(dir.resolve("notes.pdf"));
        Files.createFile(dir.resolve("song.mp3"));
        SimpleUI ui = uiAt(dir);

        assertEquals(4, itemCount(ui));          // 3 files + ".."
        key(ui, 's');
        key(ui, 'o');
        assertEquals("so", filter(ui));
        assertEquals(2, itemCount(ui), "\"..\" plus song.mp3 only");
    }

    @Test
    void filterIsCaseInsensitive(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("Holiday.JPG"));
        SimpleUI ui = uiAt(dir);
        key(ui, 'h');
        assertEquals(2, itemCount(ui));
    }

    @Test
    void backspaceTrimsTheFilterBeforeLeaving(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("abc.txt"));
        SimpleUI ui = uiAt(dir);

        key(ui, 'a');
        key(ui, 'b');
        key(ui, 127);

        assertEquals("a", filter(ui), "one character comes off");
        Field d = SimpleUI.class.getDeclaredField("dir");
        d.setAccessible(true);
        assertEquals(dir.toAbsolutePath().normalize(),
                ((Path) d.get(ui)).toAbsolutePath().normalize(),
                "still in the same folder");
    }

    @Test
    void backspaceOnAnEmptyFilterGoesUp(@TempDir Path dir) throws Exception {
        Path sub = Files.createDirectory(dir.resolve("Child"));
        SimpleUI ui = uiAt(sub);

        key(ui, 127);

        Field d = SimpleUI.class.getDeclaredField("dir");
        d.setAccessible(true);
        assertEquals(dir.toAbsolutePath().normalize(),
                ((Path) d.get(ui)).toAbsolutePath().normalize());
    }

    @Test
    void escapeClearsTheFilterFirst(@TempDir Path dir) throws Exception {
        Path sub = Files.createDirectory(dir.resolve("Child"));
        Files.createFile(sub.resolve("a.txt"));
        SimpleUI ui = uiAt(sub);

        key(ui, 'a');
        assertEquals("a", filter(ui));

        Method esc = SimpleUI.class.getDeclaredMethod("escapeOut");
        esc.setAccessible(true);
        esc.invoke(ui);

        assertEquals("", filter(ui), "Esc clears the filter");
        assertEquals(2, itemCount(ui), "and the full list is back");
    }

    @Test
    void openingAFolderResetsTheFilter(@TempDir Path dir) throws Exception {
        Path sub = Files.createDirectory(dir.resolve("Child"));
        Files.createFile(sub.resolve("a.txt"));
        SimpleUI ui = uiAt(dir);

        key(ui, 'c');                       // filter down to Child
        Field c = SimpleUI.class.getDeclaredField("cursor");
        c.setAccessible(true);
        c.set(ui, 1);                       // row 0 is "..", row 1 is Child
        ui.activate();

        assertEquals("", filter(ui), "entering a folder must clear the filter");
    }

    @Test
    void theFilterLineIsPainted(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("holiday.jpg"));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        Terminal t = TerminalBuilder.builder()
                .name("test").system(false).type("xterm-256color")
                .streams(new ByteArrayInputStream(new byte[0]), sink)
                .size(new Size(100, 30)).build();
        SimpleUI ui = new SimpleUI(t);
        Field f = SimpleUI.class.getDeclaredField("dir");
        f.setAccessible(true);
        f.set(ui, dir);
        ui.loadEntries();
        key(ui, 'h');

        Method draw = SimpleUI.class.getDeclaredMethod("draw");
        draw.setAccessible(true);
        draw.invoke(ui);

        String out = sink.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("filter: h"), "the active filter must be visible");
    }

    @Test
    void confirmScreenStatesTheCountAndSize(@TempDir Path dir) throws Exception {
        Files.writeString(Files.createFile(dir.resolve("a.jpg")), "1234567890");
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        Terminal t = TerminalBuilder.builder()
                .name("test").system(false).type("xterm-256color")
                .streams(new ByteArrayInputStream(new byte[0]), sink)
                .size(new Size(100, 30)).build();
        SimpleUI ui = new SimpleUI(t);
        Field f = SimpleUI.class.getDeclaredField("dir");
        f.setAccessible(true);
        f.set(ui, dir);
        ui.loadEntries();
        ui.organiseHere();
        ui.activate();                      // open the confirm screen

        Method draw = SimpleUI.class.getDeclaredMethod("draw");
        draw.setAccessible(true);
        draw.invoke(ui);

        String out = sink.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("Move 1 files into JPG/"), "confirm must state the count and target");
        assertTrue(out.contains("frees about"), "confirm must state the reclaimed size");
        assertTrue(out.contains("[y] Yes, move"), "confirm must offer the explicit yes");
        assertTrue(out.contains("[n] No"), "confirm must offer the cancel");
    }

    @Test
    void clickingUsesTheRowUnderThePointerWithAFilter(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("holiday.jpg"));
        Files.createFile(dir.resolve("harbour.jpg"));
        Files.createFile(dir.resolve("notes.pdf"));
        SimpleUI ui = uiAt(dir);

        key(ui, 'h');                       // list: .., harbour.jpg, holiday.jpg
        ui.onMouse(0, 3, 4);                // row 3 is the filter line, row 4 is the 2nd item

        Field c = SimpleUI.class.getDeclaredField("cursor");
        c.setAccessible(true);
        assertEquals(1, c.get(ui), "the click must land on the row it covers");
    }
}
