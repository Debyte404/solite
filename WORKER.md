# Solite — Contributor Handoff

> Read this before touching code. It describes what actually ships today.

## Project Overview

**Solite** is a file organiser with a terminal UI, written in Java. You browse
your folders like a normal file manager, press `o` to group the current folder
by file type, and confirm the move with `y`.

- Repo: https://github.com/Debyte404/solite
- Branch: work on `Shiv-Branch` (local branch name `shiv`), not `main`
- Stack: Java 17, Gradle 9.2.0, JLine 3.30.0, JUnit 5
- Platform: Windows (primary), Linux/macOS (secondary)

## Current State (v0.2)

One front end: `ui/SimpleUI` — a plain explorer. No themes, no shadows, no
dither, no alternate screen.

### Files
- `Main.java` — entry point; JLine terminal setup, dumb-terminal guard, `solite [path]`
- `ui/SimpleUI.java` — the explorer (398 lines): browse, groups, confirm
- `ui/KeyInput.java` — Enter handling; drops the LF of a CRLF pair on Windows
- `core/FileOrganizer.java` — `listEntries(dir, filter)`, recursive `groupFiles`,
  `moveFiles` via an Action stack, `undoMove` (one level deep)
- `core/Action.java`, `core/MoveAction.java` — command pattern, cross-filesystem
  copy fallback, `move_log.txt` audit log
- `core/MoveFailedException.java`, `core/UnreadableFolderException.java`
- `scripts/solite`, `scripts/solite.cmd` — launchers for `PATH`
- `archive/` — the previous DESIGN.md UI (TerminalApp, Theme, LayoutEngine,
  ShadowPainter, ClickableRegion) plus its tests. **Not compiled.** Kept for
  reference only; do not extend it.
- `DESIGN.md` — the design spec for the archived UI. Historical, not the
  current behaviour.

### Build

```bash
./gradlew build         # compile + test
./gradlew test          # tests only
./gradlew installCli    # build cli/solite.jar for the `solite` command
```

**`./gradlew run` does not work.** Gradle hands the JVM a pipe rather than a
terminal, JLine falls back to a dumb terminal, and solite exits with code 2.
Install the CLI and run `solite` instead.

### Keys

```
↑ / ↓            move
Enter / →        open the selected folder
Backspace / ←    go to the parent folder
..               the first row of every listing, Enter on it goes up
o                group the current folder by extension
Enter (groups)   ask about moving that group
y / n            apply / cancel the move
u                undo the last move
q                quit
```

Mouse: single click selects, a second click on the same row within 450ms opens
(or asks, on the groups screen). Wheel scrolls three rows. Every mouse action
has a keyboard equivalent.

### Tests (29, `./gradlew test` green)

- `core/FileOrganizerTest` — grouping on a mixed-extension temp dir, move + undo roundtrip
- `ui/KeyInputTest` — one CRLF pair produces exactly one Enter
- `ui/SimpleUITest` — paint at 100x30, 60x20, 40x15 and 0x0; every line fits the width;
  emoji counted as two cells; icon chosen per extension; size formatting
- `ui/SimpleUIConfirmTest` — `..` is always row 0; Enter on a group never moves files;
  `y` moves, `n` changes nothing; undo restores files and deletes the empty folder;
  Backspace/BS go up; single click selects and a double click opens

## Architecture Rules

1. **JLine only** — no other UI framework, no curses wrappers
2. **No absolute paths in the list** — the path belongs in the breadcrumb header
3. **Names are truncated before styling** — count terminal cells, not chars, so
   emoji never push a row past the edge
4. **A move needs an explicit `y`** — Enter must never apply one, so a stray
   keypress can't reorganise anything. `KeyInput` drops the CRLF second byte for
   the same reason: one keypress must be one action
5. **Mouse is optional** — every action has a keyboard equivalent
6. **Undo is one level deep** — the last move only
7. **`core/` is the backend** — the UI never calls `Files.move` directly, it
   goes through `FileOrganizer`

## How to Contribute

1. Read this file, then `ui/SimpleUI.java` — it is one screen at a time
2. Keep `SimpleUI` under 400 lines; if it grows, split by screen, don't bolt on
3. Run `./gradlew build` before committing — 29 tests must stay green
4. Commit as Debyte404: `git config user.name "Debyte404"` and
   `git config user.email "ankit.byte.404@gmail.com"`
5. Push to `Shiv-Branch`, never `main`

## Known Gaps

- `moveFiles` takes an `overwrite` flag that is never used; an existing target
  file is overwritten silently through the copy fallback
- `undo` has no cross-filesystem fallback, so undoing a cross-volume move can fail
- `move_log.txt` is written to the process working directory, so it lands in
  whatever folder you launched from
- No wide-character (CJK) width handling beyond emoji
