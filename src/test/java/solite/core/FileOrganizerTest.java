package solite.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileOrganizerTest {

    private FileOrganizer organizer;

    @BeforeEach
    void setUp() {
        organizer = new FileOrganizer();
    }

    @Test
    void testGroupFiles(@TempDir Path tempDir) throws IOException {
        // Create mixed extension files
        Files.createFile(tempDir.resolve("image1.jpg"));
        Files.createFile(tempDir.resolve("image2.jpg"));
        Files.createFile(tempDir.resolve("doc1.pdf"));
        Files.createFile(tempDir.resolve("noextension"));
        
        // Create subdirectory
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        Files.createFile(subDir.resolve("image3.jpg"));

        List<FileOrganizer.Group> groups = organizer.groupFiles(tempDir);

        assertEquals(3, groups.size(), "Should find 3 groups: JPG, PDF, Unknown");
        
        boolean foundJpg = false;
        boolean foundPdf = false;
        boolean foundUnknown = false;
        
        for (FileOrganizer.Group group : groups) {
            if (group.getName().equals("JPG")) {
                assertEquals(3, group.getFiles().size());
                foundJpg = true;
            } else if (group.getName().equals("PDF")) {
                assertEquals(1, group.getFiles().size());
                foundPdf = true;
            } else if (group.getName().equals("Unknown")) {
                assertEquals(1, group.getFiles().size());
                foundUnknown = true;
            }
        }
        
        assertTrue(foundJpg, "Should have grouped JPG files");
        assertTrue(foundPdf, "Should have grouped PDF files");
        assertTrue(foundUnknown, "Should have grouped unknown extension files");
    }

    @Test
    void testGroupFilesCaseInsensitiveAndDeterministic(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("pic1.PNG"));
        Files.createFile(tempDir.resolve("pic2.png"));
        Files.createFile(tempDir.resolve("pic3.Png"));
        Files.createFile(tempDir.resolve("data.CSV"));
        Files.createFile(tempDir.resolve("doc.txt"));

        List<FileOrganizer.Group> groups = organizer.groupFiles(tempDir);

        assertEquals(3, groups.size(), "Should merge mixed cases into 3 groups (CSV, PNG, TXT)");
        assertEquals("CSV", groups.get(0).getName(), "Groups must be deterministically sorted alphabetically");
        assertEquals("PNG", groups.get(1).getName());
        assertEquals("TXT", groups.get(2).getName());
        assertEquals(3, groups.get(1).getFiles().size(), "All 3 PNG variations should merge into one PNG group");
    }

    @Test
    void testMoveFilesAndUndo(@TempDir Path tempDir) throws IOException {
        Path sourceDir = tempDir.resolve("source");
        Path targetDir = tempDir.resolve("target");
        Files.createDirectory(sourceDir);
        
        Path file1 = sourceDir.resolve("test1.txt");
        Path file2 = sourceDir.resolve("test2.txt");
        Files.createFile(file1);
        Files.createFile(file2);
        
        List<Path> filesToMove = Arrays.asList(file1, file2);
        
        organizer.moveFiles(filesToMove, targetDir, false);
        
        // Assert moved
        assertFalse(Files.exists(file1), "Original file1 should not exist after move");
        assertFalse(Files.exists(file2), "Original file2 should not exist after move");
        assertTrue(Files.exists(targetDir.resolve("test1.txt")), "Target file1 should exist");
        assertTrue(Files.exists(targetDir.resolve("test2.txt")), "Target file2 should exist");
        
        organizer.undoMove();
        
        // Assert undone
        assertTrue(Files.exists(file1), "Original file1 should exist after undo");
        assertTrue(Files.exists(file2), "Original file2 should exist after undo");
        assertFalse(Files.exists(targetDir.resolve("test1.txt")), "Target file1 should not exist after undo");
        assertFalse(Files.exists(targetDir.resolve("test2.txt")), "Target file2 should not exist after undo");
    }

    @Test
    void testMoveAllGroupsAndUndo(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("photo.png");
        Path file2 = tempDir.resolve("paper.pdf");
        Path file3 = tempDir.resolve("notes.txt");
        Files.createFile(file1);
        Files.createFile(file2);
        Files.createFile(file3);

        List<FileOrganizer.Group> groups = organizer.groupFiles(tempDir);
        assertEquals(3, groups.size(), "Should have 3 groups");

        organizer.moveAllGroups(groups, tempDir);

        assertFalse(Files.exists(file1), "photo.png moved");
        assertFalse(Files.exists(file2), "paper.pdf moved");
        assertFalse(Files.exists(file3), "notes.txt moved");

        assertTrue(Files.exists(tempDir.resolve("PNG").resolve("photo.png")), "PNG/photo.png exists");
        assertTrue(Files.exists(tempDir.resolve("PDF").resolve("paper.pdf")), "PDF/paper.pdf exists");
        assertTrue(Files.exists(tempDir.resolve("TXT").resolve("notes.txt")), "TXT/notes.txt exists");

        organizer.undoMove();

        assertTrue(Files.exists(file1), "photo.png restored");
        assertTrue(Files.exists(file2), "paper.pdf restored");
        assertTrue(Files.exists(file3), "notes.txt restored");
    }

    @Test
    void testFindAndTidyDuplicates(@TempDir Path tempDir) throws IOException {
        // Create 2 duplicates of file A
        Path f1 = tempDir.resolve("doc1.txt");
        Path f2 = tempDir.resolve("doc1_copy.txt");
        Files.writeString(f1, "identical content A");
        Files.writeString(f2, "identical content A");

        // Create 3 duplicates of file B (one in a subfolder)
        Path sub = tempDir.resolve("sub");
        Files.createDirectory(sub);
        Path f3 = tempDir.resolve("pic1.png");
        Path f4 = tempDir.resolve("pic2.png");
        Path f5 = sub.resolve("pic3.png");
        Files.writeString(f3, "image bytes XYZ");
        Files.writeString(f4, "image bytes XYZ");
        Files.writeString(f5, "image bytes XYZ");

        // Unique file C
        Path f6 = tempDir.resolve("unique.log");
        Files.writeString(f6, "completely unique content");

        List<FileOrganizer.DuplicateGroup> dups = organizer.findDuplicates(tempDir);
        assertEquals(2, dups.size(), "Should find 2 duplicate groups");

        // Group 1: 3 files (2 duplicates)
        FileOrganizer.DuplicateGroup picGroup = dups.stream()
                .filter(g -> g.getFiles().size() == 3)
                .findFirst().orElse(null);
        assertNotNull(picGroup);
        assertEquals(2, picGroup.getDuplicates().size());
        assertNotNull(picGroup.getOriginal());

        // Group 2: 2 files (1 duplicate)
        FileOrganizer.DuplicateGroup docGroup = dups.stream()
                .filter(g -> g.getFiles().size() == 2)
                .findFirst().orElse(null);
        assertNotNull(docGroup);
        assertEquals(1, docGroup.getDuplicates().size());

        // Tidy duplicates into Duplicates/
        organizer.tidyDuplicates(dups, tempDir);

        Path dupsDir = tempDir.resolve("Duplicates");
        assertTrue(Files.exists(dupsDir), "Duplicates directory created");

        // Originals must still exist
        assertTrue(Files.exists(picGroup.getOriginal()), "pic original exists");
        assertTrue(Files.exists(docGroup.getOriginal()), "doc original exists");
        assertTrue(Files.exists(f6), "unique file untouched");

        // Duplicates moved
        for (Path dup : picGroup.getDuplicates()) {
            assertFalse(Files.exists(dup), "Duplicate copy moved from original path");
        }
        for (Path dup : docGroup.getDuplicates()) {
            assertFalse(Files.exists(dup), "Duplicate copy moved from original path");
        }

        // Test undo
        organizer.undoMove();
        for (Path dup : picGroup.getDuplicates()) {
            assertTrue(Files.exists(dup), "Duplicate copy restored by undo");
        }
        for (Path dup : docGroup.getDuplicates()) {
            assertTrue(Files.exists(dup), "Duplicate copy restored by undo");
        }
    }
}
