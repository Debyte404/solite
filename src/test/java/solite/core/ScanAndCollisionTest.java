package solite.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smart scanning: hidden and system entries must never be grouped or moved,
 * and a target file must never be silently overwritten.
 */
class ScanAndCollisionTest {

    private static Map<String, Integer> counts(List<FileOrganizer.Group> groups) {
        return groups.stream().collect(Collectors.toMap(
                FileOrganizer.Group::getName, g -> g.getFiles().size()));
    }

    private static FileOrganizer.Group group(List<FileOrganizer.Group> groups, String name) {
        return groups.stream().filter(g -> g.getName().equals(name)).findFirst().orElse(null);
    }

    // ---------- smart scanning ----------

    @Test
    void skipsDotFilesAndDotFolders(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("keep.jpg"));
        Files.createFile(dir.resolve(".DS_Store"));
        Files.createFile(dir.resolve(".gitignore"));

        Path git = Files.createDirectory(dir.resolve(".git"));
        Files.createFile(git.resolve("HEAD"));

        List<FileOrganizer.Group> groups = new FileOrganizer().groupFiles(dir);

        assertEquals(1, groups.size(), "only the real file should be grouped");
        assertEquals("JPG", groups.get(0).getName());
        assertEquals(1, groups.get(0).getFiles().size());
    }

    @Test
    void skipsWindowsShellNoise(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("keep.pdf"));
        Files.createFile(dir.resolve("desktop.ini"));
        Files.createFile(dir.resolve("Thumbs.db"));

        List<FileOrganizer.Group> groups = new FileOrganizer().groupFiles(dir);

        assertEquals(1, groups.size());
        assertEquals("PDF", groups.get(0).getName());
    }

    @Test
    void skipsNoiseFoldersEntirely(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("a.mp3"));
        for (String noise : List.of("node_modules", "build", "target", "__pycache__")) {
            Path p = Files.createDirectory(dir.resolve(noise));
            Files.createFile(p.resolve("junk." + noise));
        }

        List<FileOrganizer.Group> groups = new FileOrganizer().groupFiles(dir);

        assertEquals(1, groups.size(), "noise folders must not contribute files");
        assertEquals("MP3", groups.get(0).getName());
    }

    @Test
    void stillRecursesIntoRealSubfolders(@TempDir Path dir) throws IOException {
        Path sub = Files.createDirectory(dir.resolve("College"));
        Files.createFile(sub.resolve("notes.pdf"));

        List<FileOrganizer.Group> groups = new FileOrganizer().groupFiles(dir);

        assertEquals(1, groups.size());
        assertEquals(1, groups.get(0).getFiles().size(), "recursion must still work");
    }

    @Test
    void theBrowserListingAlsoHidesHiddenEntries(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("keep.jpg"));
        Files.createFile(dir.resolve(".DS_Store"));
        Files.createDirectory(dir.resolve(".git"));
        Files.createFile(dir.resolve("desktop.ini"));

        List<Path> listed = new FileOrganizer().listEntries(dir, "");

        assertEquals(1, listed.size(), "the browser should not offer hidden/system entries");
        assertEquals("keep.jpg", listed.get(0).getFileName().toString());
    }

    @Test
    void isSkippedKnowsTheRules(@TempDir Path dir) throws IOException {
        assertTrue(FileOrganizer.isSkipped(dir.resolve(".git")));
        assertTrue(FileOrganizer.isSkipped(dir.resolve(".DS_Store")));
        assertTrue(FileOrganizer.isSkipped(dir.resolve("desktop.ini")));
        assertTrue(FileOrganizer.isSkipped(dir.resolve("Thumbs.db")));
        assertFalse(FileOrganizer.isSkipped(dir.resolve("holiday.jpg")));
    }

    // ---------- collision handling ----------

    @Test
    void freeNameLeavesAFreeTargetAlone(@TempDir Path dir) {
        Path target = dir.resolve("report.pdf");
        assertEquals(target, MoveAction.freeName(target));
    }

    @Test
    void freeNameAddsOneForTheFirstCollision(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("report.pdf"));
        assertEquals(dir.resolve("report (1).pdf"), MoveAction.freeName(dir.resolve("report.pdf")));
    }

    @Test
    void freeNameKeepsCounting(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("report.pdf"));
        Files.createFile(dir.resolve("report (1).pdf"));
        assertEquals(dir.resolve("report (2).pdf"), MoveAction.freeName(dir.resolve("report.pdf")));
    }

    @Test
    void freeNameHandlesFilesWithoutAnExtension(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("README"));
        assertEquals(dir.resolve("README (1)"), MoveAction.freeName(dir.resolve("README")));
    }

    @Test
    void freeNameOnlySplitsTheLastDot(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("archive.tar.gz"));
        assertEquals(dir.resolve("archive.tar (1).gz"),
                MoveAction.freeName(dir.resolve("archive.tar.gz")));
    }

    @Test
    void movingOntoAnExistingFileRenamesInsteadOfOverwriting(@TempDir Path dir) throws IOException {
        Path target = Files.createDirectory(dir.resolve("PDF"));
        Path existing = target.resolve("notes.pdf");
        Files.writeString(existing, "OLD DATA");

        Path source = dir.resolve("notes.pdf");
        Files.writeString(source, "NEW DATA");

        FileOrganizer organizer = new FileOrganizer();
        organizer.moveFiles(List.of(source), target);

        assertEquals("OLD DATA", Files.readString(existing), "the existing file must be untouched");
        assertEquals("NEW DATA", Files.readString(target.resolve("notes (1).pdf")),
                "the incoming file must land under a free name");
        assertFalse(Files.exists(source));
    }

    @Test
    void undoAfterACollisionRestoresTheIncomingFile(@TempDir Path dir) throws IOException {
        Path target = Files.createDirectory(dir.resolve("PDF"));
        Files.writeString(target.resolve("notes.pdf"), "OLD");

        Path source = dir.resolve("notes.pdf");
        Files.writeString(source, "NEW");

        FileOrganizer organizer = new FileOrganizer();
        organizer.moveFiles(List.of(source), target);
        organizer.undoMove();

        assertTrue(Files.exists(source), "the moved file must come back");
        assertEquals("NEW", Files.readString(source));
        assertFalse(Files.exists(target.resolve("notes (1).pdf")), "the renamed copy should be gone");
        assertEquals("OLD", Files.readString(target.resolve("notes.pdf")), "the original stays put");
    }

    @Test
    void overwriteFlagDoesReplace(@TempDir Path dir) throws IOException {
        Path target = Files.createDirectory(dir.resolve("PDF"));
        Path existing = target.resolve("notes.pdf");
        Files.writeString(existing, "OLD");

        Path source = dir.resolve("notes.pdf");
        Files.writeString(source, "NEW");

        new FileOrganizer().moveFiles(List.of(source), target, true);

        assertEquals("NEW", Files.readString(existing), "overwrite=true is an explicit replace");
    }

    @Test
    void actualTargetIsReported(@TempDir Path dir) throws IOException {
        Path target = Files.createDirectory(dir.resolve("PDF"));
        Files.createFile(target.resolve("a.pdf"));
        Path source = dir.resolve("a.pdf");
        Files.createFile(source);

        MoveAction action = new MoveAction(source, target.resolve("a.pdf"));
        action.execute();

        assertEquals(target.resolve("a (1).pdf").toAbsolutePath().normalize(),
                action.getActualTarget().toAbsolutePath().normalize());
        assertEquals(target.resolve("a.pdf").toAbsolutePath().normalize(),
                action.getIntendedTarget().toAbsolutePath().normalize());
    }
}
