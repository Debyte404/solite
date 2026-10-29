package solite.core;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class MoveAction implements Action {

    private final Path source;
    private final Path target;
    private final Path logFile;

    public MoveAction(Path source, Path target) {
        this(source, target, defaultLogFile(target));
    }

    public MoveAction(Path source, Path target, Path logFile) {
        this.source = source;
        this.target = target;
        this.logFile = logFile;
    }

    private static Path defaultLogFile(Path target) {
        if (target != null && target.getParent() != null) {
            return target.getParent().resolve("move_log.txt");
        }
        return Path.of("move_log.txt");
    }

    @Override
    public void execute() throws MoveFailedException {
        try {
            if (target.getParent() != null && !Files.exists(target.getParent())) {
                Files.createDirectories(target.getParent());
            }
            try {
                Files.move(source, target);
            } catch (IOException e) {
                copyFallback(source, target);
            }
            logAction("MOVED");
        } catch (IOException e) {
            throw new MoveFailedException("Failed to move " + source + " to " + target, e);
        }
    }

    @Override
    public void undo() throws MoveFailedException {
        try {
            if (Files.exists(target)) {
                if (source.getParent() != null && !Files.exists(source.getParent())) {
                    Files.createDirectories(source.getParent());
                }
                try {
                    Files.move(target, source);
                } catch (IOException e) {
                    copyFallback(target, source);
                }
                logAction("UNDONE");
            }
        } catch (IOException e) {
            throw new MoveFailedException("Failed to undo move from " + target + " to " + source, e);
        }
    }

    private void copyFallback(Path src, Path dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src.toFile());
             FileOutputStream out = new FileOutputStream(dst.toFile())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        Files.delete(src);
    }

    private void logAction(String actionType) {
        try {
            if (logFile.getParent() != null) {
                Files.createDirectories(logFile.getParent());
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile.toFile(), true))) {
                writer.printf("[%s] %s: %s -> %s%n", LocalDateTime.now(), actionType, source, target);
            }
        } catch (IOException e) {
            System.err.println("Failed to log action: " + e.getMessage());
        }
    }
}
