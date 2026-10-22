# solite

A smart file organizer with a TUI (terminal user interface), written in Java.

## Stack

- **Java 17** (LTS)
- **Gradle 9.2.0** — build system (wrapper included; no global install needed)
- **JLine 3** — terminal UI: line editing, ANSI rendering
- **JUnit 5** — testing

## Layout

```
src/main/java/solite/   main source
src/test/java/solite/   tests
build.gradle            build script
scripts/                solite launchers (bash + cmd) for your PATH
cli/                    installed fat jar (created by ./gradlew installCli)
archive/                the old DESIGN.md UI — not compiled, kept for reference
```

## Install as a CLI command

Like `opencode` or `antigravity`: one command, no gradle in the loop.

```
./gradlew installCli        # builds build/libs/solite-0.1.0-all.jar -> cli/solite.jar
./gradlew fatJar            # just the standalone jar
```

Then put `scripts/solite` (bash) and `scripts/solite.cmd` (cmd/PowerShell) on your
`PATH` — copy both into a folder that is already on it, e.g. `~/bin`:

```
cp scripts/solite scripts/solite.cmd ~/bin/
```

Once installed:

```
solite                      # launches the TUI in the current terminal
```

The TUI draws **inside the terminal you launch it from** — it fills the window
rather than opening a new one.

The launcher resolves the jar in this order: `$SOLITE_JAR`, `./solite.jar`
next to the script, `<repo>/cli/solite.jar`, then
`build/libs/solite-0.1.0-all.jar`.

**The TUI needs a real TTY.** Piped or dumb terminals exit with code 2 and an
explanatory message — it will not pretend to work.

## Usage

```
solite                  start in your home folder
solite D:\Downloads     start in a specific folder
```

The window shows the current path, the list, and a key cheat sheet.

```
Current: ~/Downloads/College
--------------------------------------------------
> 📁 ..
  📁 Documents
  📦 photos.zip
  🖼 holiday.jpg
  📄 notes.txt
--------------------------------------------------
  [↑/↓] Move   [Enter] Open   [Backspace] Back   [o] Organise   [q] Quit
```

- Names only, never full paths; the path lives in the header.
- `..` is always the first row, so going back never needs a shortcut.
- Icons: folder, document, image, archive, audio, video, code, program.
- Mouse: click selects, double click opens. Wheel scrolls.
- `o` groups the current folder by extension; `Enter` on a group asks first,
  and only `y` applies the move. `u` undoes the last move.

## Commands

```
./gradlew build        compile + test
./gradlew test         run tests only
./gradlew installCli   build the standalone jar for the `solite` command
./gradlew run          does NOT work for the TUI (see note below)
```

**`./gradlew run` will not launch the TUI.** Gradle gives the app a pipe, not a
terminal, so JLine reports a dumb terminal and solite exits with code 2:

```
WARNING: Unable to create a system terminal, creating a dumb terminal
solite: no interactive terminal detected (type=dumb).
```

Use `solite` (or `./gradlew installCli` first). `./gradlew build` and
`./gradlew test` work normally.

(On Windows: `gradlew.bat`)

## Status

v0.2 — one explorer: names only, emoji icons, breadcrumb, `..` row on top,
double-click, grouped moves with an explicit `y` to confirm. 26 tests green.

The earlier DESIGN.md UI (themes, dithered shadows, four breakpoints, mouse
regions) is kept in `archive/` — not compiled, kept for reference.

