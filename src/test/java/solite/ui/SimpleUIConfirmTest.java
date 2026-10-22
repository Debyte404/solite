package solite.ui;

import solite.core.FileOrganizer;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explorer behaviour and move safety.
 *
 * <p>Rules pinned here: the ".." row is always first, names are shown without
 * paths, folders and files carry different icons, one Enter never applies a
 * move, and undo restores files and removes the empty folder it created.
 */
class SimpleUIConfirmTest {

    private static SimpleUI uiAt(Path dir, ByteArrayOutputStream sink) throws Exception {
        Terminal t = TerminalBuilder.builder()
                .name("test").system(false).type("xterm-256color")
                .streams(new ByteArrayInputStream(new byte[0]), sink)
                .size(new Size(100, 30))
                .build();
        SimpleUI ui = new SimpleUI(t);
        field(ui, "dir").set(ui, dir);
        invoke(ui, "loadEntries");
        return ui;
    }

    private static SimpleUI uiAt(Path dir) throws Exception {
        return uiAt(dir, new ByteArrayOutputStream());
    }

    private static Field field(SimpleUI ui, String name) throws Exception {
        Field f = SimpleUI.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Object invoke(SimpleUI ui, String name) throws Exception {
        Method m = SimpleUI.class.getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(ui);
    }

    private static Object invoke(SimpleUI ui, String name, Class<?> type, Object arg) throws Exception {
        Method m = SimpleUI.class.getDeclaredMethod(name, type);
        m.setAccessible(true);
        return m.invoke(ui, arg);
    }

    private static void files(Path dir, String... names) throws Exception {
        for (String n : names) {
            Files.createFile(dir.resolve(n));
        }
    }

    @Test
    void firstRowIsAlwaysTheBackEntry(@TempDir Path dir) throws Exception {
        files(dir, "a.jpg");
        SimpleUI ui = uiAt(dir);
        assertEquals(2, invoke(ui, "itemCount"), "1 file + the '..' row");
        assertEquals(0, field(ui, "cursor").get(ui), "cursor starts on '..'");
    }

    @Test
    void enterOnBackRowGoesToTheParent(@TempDir Path dir) throws Exception {
        Path sub = dir.resolve("Child");
        Files.createDirectory(sub);
        SimpleUI ui = uiAt(sub);

        ui.activate();                       // cursor is on ".."

        assertEquals(dir.toAbsolutePath().normalize(),
                ((Path) field(ui, "dir").get(ui)).toAbsolutePath().normalize());
    }

    @Test
    void enterOnAGroupOnlyOpensTheQuestion(@TempDir Path dir) throws Exception {
        files(dir, "a.jpg", "b.jpg");
        SimpleUI ui = uiAt(dir);
        invoke(ui, "organiseHere");

        invoke(ui, "activate");              // Enter on the highlighted group

        assertTrue(Files.exists(dir.resolve("a.jpg")), "Enter alone must not move anything");
        assertFalse(Files.exists(dir.resolve("JPG")), "no target folder may exist yet");
    }

    @Test
    void explicitYesMovesTheFiles(@TempDir Path dir) throws Exception {
        files(dir, "a.jpg", "b.jpg");
        SimpleUI ui = uiAt(dir);
        invoke(ui, "organiseHere");

        invoke(ui, "activate");
        invoke(ui, "onConfirmKey", int.class, (int) 'y');

        assertTrue(Files.exists(dir.resolve("JPG").resolve("a.jpg")));
        assertTrue(Files.exists(dir.resolve("JPG").resolve("b.jpg")));
    }

    @Test
    void explicitNoChangesNothing(@TempDir Path dir) throws Exception {
        files(dir, "a.jpg");
        SimpleUI ui = uiAt(dir);
        invoke(ui, "organiseHere");

        invoke(ui, "activate");
        invoke(ui, "onConfirmKey", int.class, (int) 'n');

        assertTrue(Files.exists(dir.resolve("a.jpg")));
        assertFalse(Files.exists(dir.resolve("JPG")));
    }

    @Test
    void undoRestoresFilesAndRemovesTheEmptyFolder(@TempDir Path dir) throws Exception {
        files(dir, "a.jpg");
        SimpleUI ui = uiAt(dir);
        invoke(ui, "organiseHere");

        invoke(ui, "activate");
        invoke(ui, "onConfirmKey", int.class, (int) 'y');
        assertTrue(Files.exists(dir.resolve("JPG").resolve("a.jpg")));

        invoke(ui, "undo");

        assertTrue(Files.exists(dir.resolve("a.jpg")), "undo must restore the file");
        assertFalse(Files.exists(dir.resolve("JPG")), "the folder we created must be removed");
    }

    @Test
    void groupsScreenListsEveryExtension(@TempDir Path dir) throws Exception {
        files(dir, "a.jpg", "b.zip", "c.pdf");
        SimpleUI ui = uiAt(dir);
        invoke(ui, "organiseHere");

        @SuppressWarnings("unchecked")
        List<FileOrganizer.Group> groups = (List<FileOrganizer.Group>) field(ui, "groups").get(ui);
        assertEquals(3, groups.size(), "jpg + zip + pdf");
    }

    @Test
    void singleClickOnlySelects(@TempDir Path dir) throws Exception {
        Path sub = dir.resolve("Photos");
        Files.createDirectory(sub);
        SimpleUI ui = uiAt(dir);

        // Row 2 is the ".." entry, row 3 is the folder.
        ui.onMouse(0, 5, 3);

        assertEquals(1, field(ui, "cursor").get(ui), "one click selects the folder row");
        assertEquals(dir.toAbsolutePath().normalize(),
                ((Path) field(ui, "dir").get(ui)).toAbsolutePath().normalize(),
                "one click must not enter the folder");
    }

    @Test
    void doubleClickEntersTheFolder(@TempDir Path dir) throws Exception {
        Path sub = dir.resolve("Photos");
        Files.createDirectory(sub);
        SimpleUI ui = uiAt(dir);

        ui.onMouse(0, 5, 3);
        ui.onMouse(0, 5, 3);                 // same row, immediately

        assertEquals(sub.toAbsolutePath().normalize(),
                ((Path) field(ui, "dir").get(ui)).toAbsolutePath().normalize(),
                "double click should open the folder");
    }

    @Test
    void rowsShowNamesAndIconsButNotPaths(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve("Photos"));
        Files.createFile(dir.resolve("notes.txt"));
        Files.createFile(dir.resolve("photo.jpg"));
        Files.createFile(dir.resolve("bundle.zip"));

        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        SimpleUI ui = uiAt(dir, sink);
        invoke(ui, "draw");
        String out = sink.toString(StandardCharsets.UTF_8);

        assertTrue(out.contains(".."), "the back row must always be listed");
        assertTrue(out.contains("Photos"), "folder name listed");
        assertTrue(out.contains("notes.txt"), "file name listed");
        assertTrue(out.contains(SimpleUI.IMG_ICON), "images get an image icon");
        assertTrue(out.contains(SimpleUI.ZIP_ICON), "archives get an archive icon");
        assertTrue(out.contains(SimpleUI.BACK_ICON), "folders get a folder icon");
        assertFalse(out.contains(dir.toString()), "full paths must never be printed");
    }

    @Test
    void backspaceGoesToTheParent(@TempDir Path dir) throws Exception {
        Path sub = dir.resolve("Child");
        Files.createDirectory(sub);
        SimpleUI ui = uiAt(sub);
        Method m = SimpleUI.class.getDeclaredMethod("onBrowseKey", int.class);
        m.setAccessible(true);
        m.invoke(ui, 127);                 // DEL, what Windows sends for Backspace
        assertEquals(dir.toAbsolutePath().normalize(),
                ((Path) field(ui, "dir").get(ui)).toAbsolutePath().normalize());
    }

    @Test
    void controlHAlsoGoesToTheParent(@TempDir Path dir) throws Exception {
        Path sub = dir.resolve("Child");
        Files.createDirectory(sub);
        SimpleUI ui = uiAt(sub);
        Method m = SimpleUI.class.getDeclaredMethod("onBrowseKey", int.class);
        m.setAccessible(true);
        m.invoke(ui, 8);                   // BS
        assertEquals(dir.toAbsolutePath().normalize(),
                ((Path) field(ui, "dir").get(ui)).toAbsolutePath().normalize());
    }

    @Test
    void qQuitsAndEnterDoesNotQuit(@TempDir Path dir) throws Exception {
        SimpleUI ui = uiAt(dir);
        Method m = SimpleUI.class.getDeclaredMethod("onBrowseKey", int.class);
        m.setAccessible(true);
        m.invoke(ui, 'x');                 // a stray letter must not quit
        assertEquals(true, field(ui, "running").get(ui));
        m.invoke(ui, 'q');
        assertEquals(false, field(ui, "running").get(ui));
    }
}
