package solite;

import solite.ui.TerminalApp;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

/**
 * Entry point for solite. Sets up the JLine terminal and launches the TUI app.
 *
 * <p>If the host is not an interactive terminal (piped output, dumb console,
 * CI), the app explains that and exits with code 2 instead of silently
 * "succeeding" while doing nothing — DESIGN.md §8.
 */
public final class Main {

    private Main() {
        // utility class
    }

    public static void main(String[] args) {
        Terminal terminal = null;
        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .jansi(true)
                    .build();

            String type = terminal.getType();
            boolean dumb = terminal instanceof org.jline.terminal.impl.DumbTerminal
                    || type == null
                    || type.startsWith("dumb")
                    || type.startsWith("unknown");

            if (dumb) {
                System.err.println("solite: no interactive terminal detected (type=" + type + ").");
                System.err.println("solite: run it directly in Windows Terminal, git-bash or mintty.");
                System.err.println("solite: the TUI cannot drive a piped or dumb terminal.");
                System.exit(2);
            }

            terminal.enterRawMode();

            TerminalApp app = new TerminalApp(terminal);
            app.run();
        } catch (Exception e) {
            System.err.println("solite failed to start: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (terminal != null) {
                try {
                    terminal.close();
                } catch (IOException ignored) {
                    // nothing useful to do on close
                }
            }
        }
    }
}
