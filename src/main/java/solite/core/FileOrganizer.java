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
    private final List<Action> undoStack = new ArrayList<>();

    /**
     * Lists children, sorts dirs first.
     */
    public List<Path> listEntries(Path dir, String filter) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> !isSkipped(p))
                    .filter(p -> filter == null || filter.isEmpty() || p.getFileName().toString().toLowerCase().contains(filter.toLowerCase()))
                    .sorted((p1, p2) -> {
                        boolean isDir1 = Files.isDirectory(p1);
                        boolean isDir2 = Files.isDirectory(p2);
                        if (isDir1 && !isDir2) return -1;
                        if (!isDir1 && isDir2) return 1;
                        return p1.getFileName().compareTo(p2.getFileName());
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UnreadableFolderException("Cannot read directory: " + dir, e);
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
            throw new UnreadableFolderException("Cannot read directory: " + dir, e);
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
                if (isSkipped(path)) {
                    continue;   // never walk into .git, node_modules, hidden system files
                }
                if (Files.isDirectory(path)) {
                    if (isNoiseFolder(path)) {
                        continue;   // build output, node_modules, caches
                    }
                    collectFilesRecursively(path, allFiles); // Recursive call
                } else if (Files.isRegularFile(path)) {
                    allFiles.add(path);
                }
            }
        }
    }

    /**
     * Hidden and system entries that must not be counted or moved: dotfiles
     * (.git, .DS_Store), Windows shell noise, and anything the OS flags hidden.
     */
    public static boolean isSkipped(Path path) {
        Path name = path.getFileName();
        if (name == null) {
            return false;
        }
        String n = name.toString();
        if (n.isEmpty()) {
            return false;
        }
        if (n.charAt(0) == '.') {
            return true;
        }
        if (n.equalsIgnoreCase("desktop.ini") || n.equalsIgnoreCase("thumbs.db")
                || n.equalsIgnoreCase("$RECYCLE.BIN") || n.equalsIgnoreCase("System Volume Information")) {
            return true;
        }
        try {
            return Files.isHidden(path);
        } catch (IOException e) {
            return false;
        }
    }

    /** Folders never worth scanning for files to organise. */
    private static boolean isNoiseFolder(Path path) {
        Path name = path.getFileName();
        if (name == null) {
            return false;
        }
        String n = name.toString();
        return n.equals("node_modules") || n.equals("build") || n.equals("target")
                || n.equals("__pycache__") || n.equals(".gradle");
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

        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        for (Path file : files) {
            // Never move a file into the folder it already lives in.
            Path parent = file.toAbsolutePath().normalize().getParent();
            if (parent != null && parent.equals(normalizedTarget)) {
                continue;
            }
            Path targetFile = targetDir.resolve(file.getFileName());
            Action action = new MoveAction(file, targetFile, overwrite);
            action.execute();
            undoStack.add(action);
        }
    }

    public void undoMove() throws IOException {
        for (Action action : undoStack) {
            action.undo();
        }
        undoStack.clear();
    }
}
