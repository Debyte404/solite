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

Then put `bin/solite` (bash) and `bin/solite.cmd` (cmd/PowerShell) on your `PATH`.
Once installed:

```
solite                      # launches the TUI in the current terminal
```

The launcher resolves the jar in this order: `$SOLITE_JAR`, `./solite.jar`
next to the script, `<repo>/cli/solite.jar`, then
`build/libs/solite-0.1.0-all.jar`.

**The TUI needs a real TTY.** Piped or dumb terminals exit with code 2 and an
explanatory message — it will not pretend to work.

## Commands

```
./gradlew build        compile + test
./gradlew test         run tests only
./gradlew installCli   build the standalone jar for the `solite` command
./gradlew run          launch via gradle (inherits the caller's terminal)
```

(On Windows: `gradlew.bat`)

## Keyboard

- `↑ ↓ / j k` move · `Home / End` jump to first / last
- `Enter` confirm / open · `→` same as Enter · `←` / `Esc` back
- Screen A: type to filter · `Esc` clears the filter · `q` quits (only with an empty filter)
- Screen B: `1` move · `2` peek · `3` or `s` skip · `u` undo · `b` back
- Screen C: `Enter` applies (double-Enter rule) · `n` / `Esc` cancels · `u` undo
- `Ctrl+C` always quits cleanly

Mouse: single click selects a row, click on an action row fires it, scroll wheel
moves 3 rows. Both X10 (`ESC [ M`) and SGR (`ESC [ <`) reports are parsed, so it
works in Windows Terminal as well as mintty.

## Status

v0.1 — three screens (Pick → Gather → Confirm), mouse, responsive layout,
44 tests green.
