package solite.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileOrganizer {

    // Nested class demonstrating encapsulation, objects, and classes
    public static class Group {
        private final String name;
        private final List<Path> files;

        public Group(String name, List<Path> files) {
            this.name = name;
            this.files = files;
        }

        public String getName() { return name; }
        public List<Path> getFiles() { return files; }

        public long getTotalSize() {
            return files.stream().mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        }
    }

    // Access control and encapsulation
    private final List<UndoState> undoStack = new ArrayList<>();

    // Inner record/class for state
    private static final class UndoState {
        private final Path originalPath;
        private final Path movedPath;

        public UndoState(Path originalPath, Path movedPath) {
            this.originalPath = originalPath;
            this.movedPath = movedPath;
        }
    }

    /**
     * Lists children, sorts dirs first.
     */
    public List<Path> listEntries(Path dir, String filter) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> filter == null || filter.isEmpty() || p.getFileName().toString().toLowerCase().contains(filter.toLowerCase()))
                    .sorted((p1, p2) -> {
                        boolean isDir1 = Files.isDirectory(p1);
                        boolean isDir2 = Files.isDirectory(p2);
                        if (isDir1 && !isDir2) return -1;
                        if (!isDir1 && isDir2) return 1;
                        return p1.getFileName().compareTo(p2.getFileName());
                    })
                    .collect(Collectors.toList());
        }
    }

    /**
     * Groups files by extension in the given directory and its subdirectories (demonstrates recursion).
     */
    public List<Group> groupFiles(Path dir) throws IOException {
        List<Path> allFiles = new ArrayList<>();
        // Exception handling
        try {
            collectFilesRecursively(dir, allFiles);
        } catch (IOException e) {
            System.err.println("Error reading directory: " + e.getMessage());
            throw e;
        }

        Map<String, List<Path>> grouped = allFiles.stream()
                .collect(Collectors.groupingBy(this::getExtension));

        return grouped.entrySet().stream()
                .map(e -> new Group(e.getKey().isEmpty() ? "Unknown" : e.getKey().toUpperCase(), e.getValue()))
                .collect(Collectors.toList());
    }

    // Recursion implementation
    private void collectFilesRecursively(Path dir, List<Path> allFiles) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.collect(Collectors.toList())) {
                if (Files.isDirectory(path)) {
                    collectFilesRecursively(path, allFiles); // Recursive call
                } else if (Files.isRegularFile(path)) {
                    allFiles.add(path);
                }
            }
        }
    }

    private String getExtension(Path p) {
        String name = p.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1);
        }
        return "";
    }

    // Overloading example
    public void moveFiles(List<Path> files, Path targetDir) throws IOException {
        moveFiles(files, targetDir, false);
    }

    // Method overloading and file I/O
    public void moveFiles(List<Path> files, Path targetDir, boolean overwrite) throws IOException {
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        
        undoStack.clear(); // One level deep undo as per rules
        
        for (Path file : files) {
            Path targetFile = targetDir.resolve(file.getFileName());
            Files.move(file, targetFile);
            undoStack.add(new UndoState(file, targetFile));
        }
    }

    public void undoMove() throws IOException {
        for (UndoState state : undoStack) {
            if (Files.exists(state.movedPath)) {
                Files.move(state.movedPath, state.originalPath);
            }
        }
        undoStack.clear();
    }
}
