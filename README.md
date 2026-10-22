# solite

A smart file organizer with a TUI (terminal user interface), written in Java.

## Stack

- **Java 17** (LTS)
- **Gradle 9.2.0** — build system (wrapper included; no global install needed)
- **JLine 3** — terminal UI: line editing, ANSI rendering
- **Gson** — JSON persistence for the file index
- **JUnit 5** — testing

## Layout

```
src/main/java/solite/   main source
src/test/java/solite/   tests
build.gradle            build script
cli/                    installed fat jar (created by ./gradlew installCli)
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

## Usage — simple mode (default)

Two screens, one question each. No mouse, no colours, no surprises.

```
solite                  start in your home folder
solite D:\Downloads     start in a specific folder
```

**1. Pick** — where to tidy

```
↑ ↓            move
a..z           type to filter  (Dow → Downloads)
Enter          open this folder
Backspace      go up one level (when the filter is empty)
q              quit
```

**2. Groups** — what it found

```
↑ ↓            move
Enter          ask to move this group
u              undo the last move
b              back to the folder list
q              quit
```

**3. Confirm** — nothing has moved yet

```
y    move the files
n    cancel, change nothing
```

Only `y` applies a move. `Enter` deliberately does nothing on this screen, so a
stray keypress can never reorganise your files.

## Usage — classic mode

`solite --classic` runs the full DESIGN.md UI (three screens, mouse, dithered
shadows, responsive panes). That is the one the design document describes.

## Commands

```
./gradlew build        compile + test
./gradlew test         run tests only
./gradlew installCli   build the standalone jar for the `solite` command
./gradlew run          launch via gradle (inherits the caller's terminal)
```

(On Windows: `gradlew.bat`)

## Status

v0.1 — simple flow (Pick → Groups → Confirm) plus the classic UI behind
`--classic`. 65 tests green.

