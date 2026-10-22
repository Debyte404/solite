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
```

## Commands

```
./gradlew build        compile + test
./gradlew run          launch the TUI
./gradlew packageExe   build standalone Windows exe (build/dist/solite/solite.exe)
./gradlew test         run tests only
```

(On Windows: `gradlew.bat`, `.\solite.bat`, or `.\solite.exe`)

## Running

Launch the standalone executable directly:
```powershell
.\solite.exe
```

Or run via Gradle:
```powershell
.\gradlew.bat run
```

## Navigation & Controls

### Screen A: Directory Navigator (Pick)
- **`↑` / `↓` / `k` / `j`**: Move cursor between files, folders, parent `..`, and drive switches.
- **`Enter`**:
  - On `! [Quick Tidy: Auto-Organize All]`: Instantly stages full automatic organization of all files.
  - On `* [Tidy: Review Groups]`: Enters group-by-group review mode.
  - On a folder: Enters and browses into that directory.
  - On `..`: Navigates up to the parent directory.
  - On a Drive (`[Drive C:\]`): Switches to that drive.
- **`a`**: Quick Tidy shortcut — automatically organizes all files in the current folder at once.
- **`Space`**: Organizes the current folder.
- **`Backspace`**: Deletes character if filter is typed; if filter is empty, navigates up to parent directory (`..`).
- **`Left Arrow` / `b`**: Navigates up to parent directory.
- **`Right Arrow`**: Enters selected folder.
- **`PageUp` / `PageDown` / `Home` / `End`**: Fast scroll & jump through large directories.
- **Search & Path Jump**: Start typing any folder name to filter, or type an absolute path (e.g. `D:\Photos` or `c:`) to jump anywhere on your computer.
- **`?`**: Toggles the in-app Help & Keymap modal.
- **`q` or `Ctrl+C`**: Clean quit — restores terminal screen, cursor, and mouse state instantly.

### Screen B: File Group Organizer (Gather)
- **`↑` / `↓`**: Select file group (e.g. PNG Images, PDF Documents).
- **`1` or `Enter`**: Stage move files into corresponding folder.
- **`4` or `a`**: Quick Tidy — automatically organizes ALL groups at once.
- **`2` or `→`**: Peek file names in the selected group.
- **`3` or `s`**: Skip group.
- **`u`**: Undo last file organization action.
- **`b` or `Esc`**: Return to directory navigator.

### Screen C: Confirmation (Confirm)
- **`Enter`**: Confirm and execute the file moves.
- **`n` or `Esc`**: Cancel and return to group list.

## Status

Fully functional terminal user interface with Hermes-yellow aesthetic, whole-filesystem navigation, smooth viewport scrolling, pure keyboard navigation, undo stack, and clean terminal restoration on quit.
