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
}
