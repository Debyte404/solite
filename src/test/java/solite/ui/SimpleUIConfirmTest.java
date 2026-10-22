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
 * Safety tests for the simplify flow.
 *
 * <p>The first release had a CRLF bug that made one Enter press count twice, so
 * selecting a group both opened the confirm screen <em>and</em> applied the
 * move — files were reorganised without the user ever answering the question.
 * These tests pin the rule: nothing moves until an explicit {@code y}.
 */
class SimpleUIConfirmTest {

    private static SimpleUI uiPointedAt(Path dir) throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        Terminal t = TerminalBuilder.builder()
                .name("test").system(false).type("xterm-256color")
                .streams(new ByteArrayInputStream(new byte[0]), sink)
                .size(new Size(100, 30))
                .build();

        SimpleUI ui = new SimpleUI(t);

        Field dirField = SimpleUI.class.getDeclaredField("dir");
        dirField.setAccessible(true);
        dirField.set(ui, dir);

        invoke(ui, "loadEntries");
        invoke(ui, "loadGroups");

        Field screen = SimpleUI.class.getDeclaredField("screen");
        screen.setAccessible(true);
        // Pretend the user has opened the folder and is looking at the groups.
        Object groupsScreen = Class.forName("solite.ui.SimpleUI$Screen").getEnumConstants()[1];
        screen.set(ui, groupsScreen);

        return ui;
    }

    private static Object invoke(SimpleUI ui, String name) throws Exception {
        Method m = SimpleUI.class.getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(ui);
    }

    private static Object invoke(SimpleUI ui, String name, Class<?> argType, Object arg) throws Exception {
        Method m = SimpleUI.class.getDeclaredMethod(name, argType);
        m.setAccessible(true);
        return m.invoke(ui, arg);
    }

    private static Path makeFiles(Path dir, String... names) throws Exception {
        for (String n : names) {
            Files.createFile(dir.resolve(n));
        }
        return dir;
    }

    @Test
    void enterOnAGroupOnlyOpensTheQuestion(@TempDir Path dir) throws Exception {
        makeFiles(dir, "a.jpg", "b.jpg");
        SimpleUI ui = uiPointedAt(dir);

        invoke(ui, "onEnter");   // user presses Enter on the group

        assertTrue(Files.exists(dir.resolve("a.jpg")), "Enter alone must not move anything");
        assertTrue(Files.exists(dir.resolve("b.jpg")), "Enter alone must not move anything");
        assertFalse(Files.exists(dir.resolve("JPG")), "no target folder may be created yet");
    }

    @Test
    void explicitYesMovesTheFiles(@TempDir Path dir) throws Exception {
        makeFiles(dir, "a.jpg", "b.jpg");
        SimpleUI ui = uiPointedAt(dir);

        invoke(ui, "onEnter");
        invoke(ui, "onConfirmKey", int.class, (int) 'y');

        assertTrue(Files.exists(dir.resolve("JPG")), "target folder should exist after y");
        assertTrue(Files.exists(dir.resolve("JPG").resolve("a.jpg")), "a.jpg should be moved");
        assertTrue(Files.exists(dir.resolve("JPG").resolve("b.jpg")), "b.jpg should be moved");
    }

    @Test
    void explicitNoLeavesEverythingAlone(@TempDir Path dir) throws Exception {
        makeFiles(dir, "a.jpg");
        SimpleUI ui = uiPointedAt(dir);

        invoke(ui, "onEnter");
        invoke(ui, "onConfirmKey", int.class, (int) 'n');

        assertTrue(Files.exists(dir.resolve("a.jpg")), "n must not move anything");
        assertFalse(Files.exists(dir.resolve("JPG")), "n must not create the target folder");
    }

    @Test
    void undoPutsTheFilesBack(@TempDir Path dir) throws Exception {
        makeFiles(dir, "a.jpg");
        SimpleUI ui = uiPointedAt(dir);

        invoke(ui, "onEnter");
        invoke(ui, "onConfirmKey", int.class, (int) 'y');
        assertTrue(Files.exists(dir.resolve("JPG").resolve("a.jpg")));

        invoke(ui, "undo");

        assertTrue(Files.exists(dir.resolve("a.jpg")), "undo must restore the original file");
        assertFalse(Files.exists(dir.resolve("JPG").resolve("a.jpg")), "target copy should be gone");
        assertFalse(Files.exists(dir.resolve("JPG")), "the empty folder we created should be removed");
    }

    @Test
    void groupsScreenListsEveryExtension(@TempDir Path dir) throws Exception {
        makeFiles(dir, "a.jpg", "b.zip", "c.pdf");
        SimpleUI ui = uiPointedAt(dir);

        Field gf = SimpleUI.class.getDeclaredField("groups");
        gf.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<FileOrganizer.Group> groups = (List<FileOrganizer.Group>) gf.get(ui);

        assertEquals(3, groups.size(), "jpg + zip + pdf = 3 groups");
    }
}
