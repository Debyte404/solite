package solite.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Batch moving, directory sorting, filtering, and one-level undo. */
class FileOrganizerMoreTest {

    @Test
    void moveCreatesTheTargetFolder(@TempDir Path dir) throws IOException {
        Path file = Files.createFile(dir.resolve("a.jpg"));
        Path target = dir.resolve("JPG");

        assertFalse(Files.exists(target));
        new FileOrganizer().moveFiles(List.of(file), target);

        assertTrue(Files.isDirectory(target), "the target folder must be created");
        assertTrue(Files.exists(target.resolve("a.jpg")));
    }

    @Test
    void moveCreatesNestedTargetFolders(@TempDir Path dir) throws IOException {
        Path file = Files.createFile(dir.resolve("a.jpg"));
        Path target = dir.resolve("Photos").resolve("2024");

        new FileOrganizer().moveFiles(List.of(file), target);

        assertTrue(Files.exists(target.resolve("a.jpg")));
    }

    @Test
    void movingManyFilesAtOnce(@TempDir Path dir) throws IOException {
        Path a = Files.createFile(dir.resolve("a.jpg"));
        Path b = Files.createFile(dir.resolve("b.jpg"));
        Path c = Files.createFile(dir.resolve("c.jpg"));
        Path target = dir.resolve("JPG");

        new FileOrganizer().moveFiles(List.of(a, b, c), target);

        assertEquals(3, Files.list(target).count());
        assertFalse(Files.exists(a));
        assertFalse(Files.exists(b));
        assertFalse(Files.exists(c));
    }

    @Test
    void undoRevertsExactlyTheLastBatch(@TempDir Path dir) throws IOException {
        Path a = Files.createFile(dir.resolve("a.jpg"));
        Path b = Files.createFile(dir.resolve("b.jpg"));
        Path target = dir.resolve("JPG");

        FileOrganizer organizer = new FileOrganizer();
        organizer.moveFiles(List.of(a, b), target);
        organizer.undoMove();

        assertTrue(Files.exists(a), "a.jpg must be back");
        assertTrue(Files.exists(b), "b.jpg must be back");
        assertFalse(Files.exists(target.resolve("a.jpg")));
    }

    @Test
    void secondMoveDiscardsTheFirstUndo(@TempDir Path dir) throws IOException {
        Path a = Files.createFile(dir.resolve("a.jpg"));
        Path b = Files.createFile(dir.resolve("b.zip"));

        FileOrganizer organizer = new FileOrganizer();
        organizer.moveFiles(List.of(a), dir.resolve("JPG"));
        organizer.moveFiles(List.of(b), dir.resolve("ZIP"));
        organizer.undoMove();

        assertTrue(Files.exists(b), "the last move is undone");
        assertFalse(Files.exists(a), "the earlier move is not: undo is one level deep");
    }

    @Test
    void undoWithNothingToUndoIsHarmless(@TempDir Path dir) {
        assertDoesNotThrow(() -> new FileOrganizer().undoMove());
    }

    @Test
    void movingAFileIntoItsOwnFolderIsSkipped(@TempDir Path dir) throws IOException {
        Path target = Files.createDirectory(dir.resolve("JPG"));
        Path inside = Files.createFile(target.resolve("a.jpg"));

        FileOrganizer organizer = new FileOrganizer();
        organizer.moveFiles(List.of(inside), target);

        assertTrue(Files.exists(inside), "the file must stay exactly where it was");
        assertEquals(1, Files.list(target).count(), "no renamed duplicate may appear");
    }

    @Test
    void listEntriesPutsFoldersFirst(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("aa.txt"));
        Files.createDirectory(dir.resolve("zz_folder"));

        List<Path> listed = new FileOrganizer().listEntries(dir, "");

        assertEquals("zz_folder", listed.get(0).getFileName().toString(),
                "folders sort before files");
        assertEquals("aa.txt", listed.get(1).getFileName().toString());
    }

    @Test
    void listEntriesFiltersByName(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("holiday.jpg"));
        Files.createFile(dir.resolve("notes.pdf"));

        List<Path> listed = new FileOrganizer().listEntries(dir, "holi");

        assertEquals(1, listed.size());
        assertEquals("holiday.jpg", listed.get(0).getFileName().toString());
    }

    @Test
    void listEntriesFilterIgnoresCase(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("Holiday.JPG"));
        assertEquals(1, new FileOrganizer().listEntries(dir, "holiday").size());
    }

    @Test
    void listEntriesWithEmptyFilterReturnsEverything(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("a.txt"));
        Files.createFile(dir.resolve("b.txt"));
        assertEquals(2, new FileOrganizer().listEntries(dir, "").size());
        assertEquals(2, new FileOrganizer().listEntries(dir, null).size());
    }

    @Test
    void groupFilesBucketsFilesWithoutAnExtension(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("LICENSE"));
        List<FileOrganizer.Group> groups = new FileOrganizer().groupFiles(dir);

        assertEquals(1, groups.size());
        assertEquals("Unknown", groups.get(0).getName());
    }

    @Test
    void groupFilesUppercasesTheGroupName(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("a.JpG"));
        List<FileOrganizer.Group> groups = new FileOrganizer().groupFiles(dir);

        assertEquals("JPG", groups.get(0).getName());
    }

    @Test
    void groupTotalSizeAddsUp(@TempDir Path dir) throws IOException {
        Files.writeString(Files.createFile(dir.resolve("a.txt")), "12345");
        Files.writeString(Files.createFile(dir.resolve("b.txt")), "123");

        FileOrganizer.Group g = new FileOrganizer().groupFiles(dir).get(0);

        assertEquals(8, g.getTotalSize());
    }

    @Test
    void emptyFolderGroupsToNothing(@TempDir Path dir) throws IOException {
        assertTrue(new FileOrganizer().groupFiles(dir).isEmpty());
    }
}
