package solite.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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

    /**
     * Represents a cluster of duplicate files sharing the identical SHA-256 hash.
     * The first file in the list is considered the primary original; the rest are duplicates.
     */
    public static class DuplicateGroup {
        private final String hash;
        private final long fileSize;
        private final List<Path> files;

        public DuplicateGroup(String hash, long fileSize, List<Path> files) {
            this.hash = hash;
            this.fileSize = fileSize;
            this.files = new ArrayList<>(files);
            // Sort deterministically so shallowest/alphabetical path is original
            this.files.sort(Comparator.comparing(Path::toString));
        }

        public String getHash() { return hash; }
        public long getFileSize() { return fileSize; }
        public List<Path> getFiles() { return Collections.unmodifiableList(files); }

        public Path getOriginal() {
            return files.isEmpty() ? null : files.get(0);
        }

        public List<Path> getDuplicates() {
            if (files.size() <= 1) return Collections.emptyList();
            return files.subList(1, files.size());
        }

        public long getWastedSpace() {
            return Math.max(0, (long) (files.size() - 1) * fileSize);
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
                if (Files.isDirectory(path)) {
                    String name = path.getFileName() != null ? path.getFileName().toString() : "";
                    if (!name.equalsIgnoreCase(".git") && !name.equalsIgnoreCase("Duplicates")) {
                        collectFilesRecursively(path, allFiles); // Recursive call
                    }
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
            if (!file.equals(targetFile)) {
                Action action = new MoveAction(file, targetFile);
                action.execute();
                undoStack.add(action);
            }
        }
    }

    /**
     * Automatically moves all groups into their respective extension folders in one batch.
     */
    public void moveAllGroups(List<Group> groupsToMove, Path baseDir) throws IOException {
        undoStack.clear();
        for (Group group : groupsToMove) {
            Path targetDir = baseDir.resolve(group.getName());
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            for (Path file : group.getFiles()) {
                Path targetFile = targetDir.resolve(file.getFileName());
                if (!file.equals(targetFile)) {
                    Action action = new MoveAction(file, targetFile);
                    action.execute();
                    undoStack.add(action);
                }
            }
        }
    }

    public void undoMove() throws IOException {
        for (Action action : undoStack) {
            action.undo();
        }
        undoStack.clear();
    }

    /**
     * Identifies duplicate files within the given directory and its subdirectories.
     * Uses a fast two-phase detection:
     *   Phase 1: Partition non-empty files by byte size.
     *   Phase 2: For collisions, calculate cryptographic SHA-256 hash to confirm content match.
     * Returns groups of duplicates sorted by highest wasted space first.
     */
    public List<DuplicateGroup> findDuplicates(Path dir) throws IOException {
        List<Path> allFiles = new ArrayList<>();
        try {
            collectFilesRecursively(dir, allFiles);
        } catch (IOException e) {
            throw new UnreadableFolderException("Cannot read directory: " + dir, e);
        }

        // Phase 1: Group files by non-zero file size
        Map<Long, List<Path>> bySize = allFiles.stream()
                .filter(p -> {
                    try {
                        return Files.size(p) > 0;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .collect(Collectors.groupingBy(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return -1L;
                    }
                }));

        // Phase 2: Compute SHA-256 hash for collisions
        List<DuplicateGroup> duplicates = new ArrayList<>();
        for (Map.Entry<Long, List<Path>> entry : bySize.entrySet()) {
            if (entry.getValue().size() < 2) continue;

            Map<String, List<Path>> byHash = new HashMap<>();
            for (Path p : entry.getValue()) {
                String hash = computeFileHash(p);
                if (hash != null) {
                    byHash.computeIfAbsent(hash, k -> new ArrayList<>()).add(p);
                }
            }

            for (Map.Entry<String, List<Path>> hashEntry : byHash.entrySet()) {
                if (hashEntry.getValue().size() > 1) {
                    duplicates.add(new DuplicateGroup(hashEntry.getKey(), entry.getKey(), hashEntry.getValue()));
                }
            }
        }

        // Sort groups by wasted space descending (most space-wasting duplicates first)
        duplicates.sort((g1, g2) -> Long.compare(g2.getWastedSpace(), g1.getWastedSpace()));
        return duplicates;
    }

    /**
     * Automatically moves all redundant duplicate copies into a 'Duplicates' folder under baseDir,
     * leaving one original file in its primary place.
     */
    public void tidyDuplicates(List<DuplicateGroup> duplicateGroups, Path baseDir) throws IOException {
        Path targetDir = baseDir.resolve("Duplicates");
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        undoStack.clear();

        for (DuplicateGroup group : duplicateGroups) {
            for (Path dupFile : group.getDuplicates()) {
                Path targetFile = resolveUniquePath(targetDir, dupFile.getFileName().toString());
                if (!dupFile.equals(targetFile)) {
                    Action action = new MoveAction(dupFile, targetFile);
                    action.execute();
                    undoStack.add(action);
                }
            }
        }
    }

    /**
     * Computes the SHA-256 hexadecimal hash string for a file.
     */
    public static String computeFileHash(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves a non-colliding destination file path inside targetDir.
     */
    private Path resolveUniquePath(Path targetDir, String fileName) {
        Path candidate = targetDir.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        int dot = fileName.lastIndexOf('.');
        String base = (dot > 0) ? fileName.substring(0, dot) : fileName;
        String ext = (dot > 0) ? fileName.substring(dot) : "";
        int counter = 1;
        while (Files.exists(candidate)) {
            candidate = targetDir.resolve(base + "_" + counter + ext);
            counter++;
        }
        return candidate;
    }
}
