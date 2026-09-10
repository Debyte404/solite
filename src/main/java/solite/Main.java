package solite;

import solite.ui.TerminalApp;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Entry point for solite. Sets up the JLine terminal and launches the TUI app.
 */
public final class Main {

    private Main() {
        // utility class
    }

    public static void main(String[] args) {
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .jansi(true)
                    .build();

            terminal.enterRawMode();

            TerminalApp app = new TerminalApp(terminal);
            app.run();
        } catch (Exception e) {
            System.err.println("solite failed to start: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
