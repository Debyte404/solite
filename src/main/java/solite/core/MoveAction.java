package solite.core;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * One file move, with enough state to undo it.
 *
 * <p>Collision policy: a target that already exists is never overwritten. The
 * move picks a free name instead — {@code report.pdf} becomes
 * {@code report (1).pdf}, then {@code report (2).pdf}. The name actually used is
 * remembered so {@link #undo()} puts the file back exactly where it came from.
 *
 * <p>The {@code overwrite} flag exists for the API contract and is honoured, but
 * the default path is always "rename, never lose data".
 */
public class MoveAction implements Action {

    private final Path source;
    private final Path intended;
    private final boolean overwrite;
    private Path actual;
    private static final String LOG_FILE = "move_log.txt";

    public MoveAction(Path source, Path target) {
        this(source, target, false);
    }

    public MoveAction(Path source, Path target, boolean overwrite) {
        this.source = source;
        this.intended = target;
        this.actual = target;
        this.overwrite = overwrite;
    }

    @Override
    public void execute() throws MoveFailedException {
        try {
            if (intended.getParent() != null && !Files.exists(intended.getParent())) {
                Files.createDirectories(intended.getParent());
            }
            actual = overwrite ? intended : freeName(intended);
            try {
                Files.move(source, actual);
            } catch (IOException e) {
                // Fallback to byte stream copy if move fails (e.g. across mount points)
                copyBytes(source, actual);
                Files.delete(source);
            }
            logAction("MOVED", actual);
        } catch (IOException e) {
            throw new MoveFailedException("Failed to move " + source + " to " + intended, e);
        }
    }

    @Override
    public void undo() throws MoveFailedException {
        try {
            if (!Files.exists(actual)) {
                return;   // already gone: undo becomes a no-op
            }
            if (Files.exists(source)) {
                throw new MoveFailedException("Cannot undo: " + source + " already exists");
            }
            try {
                Files.move(actual, source);
            } catch (IOException e) {
                copyBytes(actual, source);
                Files.delete(actual);
            }
            logAction("UNDONE", actual);
        } catch (IOException e) {
            throw new MoveFailedException("Failed to undo move from " + actual + " to " + source, e);
        }
    }

    /** Where the file ended up. Differs from the requested target on a collision. */
    public Path getActualTarget() {
        return actual;
    }

    public Path getSource() {
        return source;
    }

    public Path getIntendedTarget() {
        return intended;
    }

    /**
     * First free name for {@code target}: {@code name.ext}, {@code name (1).ext},
     * {@code name (2).ext} … so an existing file is never clobbered.
     */
    public static Path freeName(Path target) {
        if (!Files.exists(target)) {
            return target;
        }
        String name = target.getFileName().toString();
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; i < 10000; i++) {
            Path candidate = target.resolveSibling(base + " (" + i + ")" + ext);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return target.resolveSibling(base + " (" + System.nanoTime() + ")" + ext);
    }

    private static void copyBytes(Path from, Path to) throws IOException {
        try (FileInputStream in = new FileInputStream(from.toFile());
             FileOutputStream out = new FileOutputStream(to.toFile())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    private void logAction(String actionType, Path destination) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            writer.printf("[%s] %s: %s -> %s%n", LocalDateTime.now(), actionType, source, destination);
        } catch (IOException e) {
            System.err.println("Failed to log action: " + e.getMessage());
        }
    }
}
