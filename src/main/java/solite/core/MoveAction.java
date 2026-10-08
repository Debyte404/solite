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
    private static final String LOG_FILE = "move_log.txt";

    public MoveAction(Path source, Path target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public void execute() throws MoveFailedException {
        try {
            if (!Files.exists(target.getParent())) {
                Files.createDirectories(target.getParent());
            }
            try {
                Files.move(source, target);
            } catch (IOException e) {
                // Fallback to byte stream copy if move fails (e.g. across mount points)
                copyFallbackByteStream();
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
                Files.move(target, source);
                logAction("UNDONE");
            }
        } catch (IOException e) {
            throw new MoveFailedException("Failed to undo move from " + target + " to " + source, e);
        }
    }

    private void copyFallbackByteStream() throws IOException {
        try (FileInputStream in = new FileInputStream(source.toFile());
             FileOutputStream out = new FileOutputStream(target.toFile())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        Files.delete(source);
    }
    
    private void logAction(String actionType) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            writer.printf("[%s] %s: %s -> %s%n", LocalDateTime.now(), actionType, source, target);
        } catch (IOException e) {
            System.err.println("Failed to log action: " + e.getMessage());
        }
    }
}
