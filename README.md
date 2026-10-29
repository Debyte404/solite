# solite

[![Java 17](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Gradle 9.2.0](https://img.shields.io/badge/Gradle-9.2.0-02303A.svg)](https://gradle.org)
[![JUnit 5](https://img.shields.io/badge/Tests-Passing-brightgreen.svg)](src/test/java/solite)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)]()

> **A high-performance, Hermes-yellow terminal file organizer and duplicate cleaner, written in modern Java 17.**

Solite brings calm, intentional order to cluttered folders. Designed around the *Hermes-yellow* aesthetic—a dark stage illuminated by a single warm lamp—Solite delivers an intuitive, distraction-free terminal experience with instant keyboard navigation, two-phase duplicate detection, transactional moves, and one-key undo.

---

## Highlights

- ⚡ **1-Key Quick Tidy**  
  Instantly organize an entire folder of chaotic files into clean, extension-based directories (`PNG/`, `PDF/`, `TXT/`, etc.) in a single keystroke.
- 🔍 **Cryptographic Duplicate Detection**  
  Two-phase duplicate detection: O(N) partitioning by byte size, followed by SHA-256 cryptographic hashing to confirm true content matches. Automatically identifies wasted storage and groups duplicates by highest space savings.
- 🧭 **Whole-Filesystem Navigator & Path Jump**  
  Freely navigate drives (`C:\`, `D:\`), switch roots, drill down into nested directories, filter names in real time, or type any absolute/relative path to jump anywhere on your system.
- 🛡️ **Transactional Safety & 1-Key Undo (`u`)**  
  Built on the **Command Design Pattern** (`Action` / `MoveAction`). Moves are atomic with an automatic byte-stream fallback across drives/mount points. Made a mistake? Press `u` to instantly roll back changes to their original locations.
- 📝 **Per-Folder Audit Trail**  
  Every operation logs timestamped records (`MOVED`, `UNDONE`) directly to `move_log.txt` inside the organized directory—keeping an immutable, transparent record of all actions.
- 🎨 **Adaptive Hermes-Yellow TUI Engine**  
  Reflows dynamically across 4 responsive breakpoints (`XS`, `S`, `M`, `L`) with an emergency mode for micro-windows (<14 rows). Supports Truecolor (24-bit), 256-color, 16-color, and monochrome environments with zero conhost line-wrapping glitches.
- 📦 **Zero-Dependency Windows Executable**  
  Ships with a standalone `solite.exe` bundled with a custom trimmed runtime via `jpackage`—no global Java installation required.

---

## Quick Start

### 1. Launch via Standalone Executable (Windows)
Run the self-contained executable directly from PowerShell or Command Prompt:
```powershell
.\solite.exe
```

Or use the smart launcher batch script (which automatically detects executables, distribution builds, or Gradle):
```powershell
.\solite.bat
```

### 2. Run via Gradle Wrapper (Cross-Platform)
No build tools need to be installed globally; the included Gradle wrapper handles all dependencies:
```bash
# Windows
.\gradlew.bat run

# Linux / macOS
./gradlew run
```

---

## Controls & Keybindings

Solite features a strict **one screen = one decision** workflow across three dedicated screens. Press `?` at any point to open the in-app help modal.

### Screen A: Directory Navigator (Pick)
*Browse your filesystem, filter directories, or initiate tidying.*

| Key | Action |
| :--- | :--- |
| `↑` / `↓` or `k` / `j` | Move cursor up / down |
| `Enter` | Select item (browse directory, switch drive, or run highlighted action) |
| `Space` | Tidy selected folder (opens group review in Screen B) |
| `Backspace` | Erase search character; if search is empty, go up to parent directory (`..`) |
| `Left Arrow` / `b` | Navigate up to parent directory (`..`) |
| `Right Arrow` | Enter selected folder |
| `PageUp` / `PageDown` | Fast scroll 5 items at a time |
| `Home` / `End` | Jump directly to the first / last item in the list |
| *Type any letters* | Live type-to-filter search (e.g., type `photo` or full path `D:\Downloads`) |
| `Esc` | Clear active search filter |
| `?` | Toggle in-app Help & Keymap modal |
| `q` or `Ctrl+C` | Clean exit (restores terminal buffer, cursor, and mouse state) |

> **Pro-Tip:** In Screen A, the top actions allow you to execute `! [Quick Tidy: Auto-Organize All]`, `* [Tidy: Review Groups]`, or `= [Find & Tidy Duplicates]` immediately on the current directory.

---

### Screen B: File Group Organizer (Gather)
*Review proposed file groups, peek filenames, and organize group-by-group.*

| Key | Action |
| :--- | :--- |
| `↑` / `↓` or `k` / `j` | Select file group (e.g., `PNG`, `PDF`, `MP4`) |
| `1` or `Enter` | Stage selected group for moving into its extension folder |
| `4` or `a` | **Quick Tidy ALL**: Stage all groups in the folder in one batch |
| `2` or `→` | Peek inside: preview filenames and exact byte sizes |
| `3` or `s` | Skip current group and proceed to the next |
| `u` | **Undo**: Revert the last move action immediately |
| `b` or `Esc` | Return to Directory Navigator (Screen A) |

---

### Screen C: Confirmation Modal (Confirm)
*Double-check operations before writing changes to disk.*

| Key | Action |
| :--- | :--- |
| `Enter` | **Confirm**: Execute the staged file moves |
| `n` or `Esc` | **Cancel**: Abort operation and return to previous screen |

---

## Architecture & Code Layout

```
solite/
├── build.gradle                 # Gradle build configuration & jpackage task
├── gradlew / gradlew.bat        # Cross-platform Gradle wrapper scripts
├── solite.bat                   # Smart launcher script for Windows
├── solite.exe                   # Standalone self-contained Windows executable
├── DESIGN.md                    # Core visual specification & palette guidelines
├── WORKER.md                    # Contributor architecture rules & specifications
├── handoff.md                   # Engineering handoff & maintenance documentation
└── src/
    ├── main/java/solite/
    │   ├── Main.java            # JLine terminal initialization and bootstrap
    │   ├── core/
    │   │   ├── Action.java                  # Command pattern interface
    │   │   ├── MoveAction.java              # Atomic move, stream copy fallback, audit log
    │   │   ├── FileOrganizer.java           # Recursive traversal, grouping, SHA-256 duplicate engine
    │   │   ├── MoveFailedException.java     # Checked I/O move exception
    │   │   └── UnreadableFolderException.java # Permission / filesystem failure abstraction
    │   └── ui/
    │       ├── TerminalApp.java             # Main TUI event loop, state machine & ANSI renderer
    │       ├── LayoutEngine.java            # Responsive breakpoint calculation (XS, S, M, L)
    │       ├── Theme.java                   # Hermes-yellow palette (Truecolor / 256 / 16 / Mono)
    │       ├── ShadowPainter.java           # Two-pass dithered shadow strips (L1 dust & L2 mist)
    │       └── ClickableRegion.java         # Geometric mouse hit-testing abstractions
    └── test/java/solite/
        ├── core/
        │   └── FileOrganizerTest.java       # Grouping, moving, duplicate detection & undo tests
        └── ui/
            ├── ClickableRegionTest.java     # Hit-testing boundary tests
            ├── LayoutEngineTest.java        # Breakpoint matrix & 200-size resize fuzzing
            ├── TerminalAppLogicTest.java    # Pure logic, formatting, and buffer lifecycle tests
            └── ThemeTest.java               # Palette validation & capability ladders
```

---

## Technical Highlights

### 1. Two-Phase Cryptographic Duplicate Detection
Scanning thousands of files for duplicates with raw cryptographic hashing is bottlenecked by disk I/O. Solite employs a high-efficiency two-phase pipeline:
- **Phase 1 (O(N) Pre-Filter):** Files are partitioned strictly by non-zero byte size using filesystem metadata. Files with unique byte sizes are immediately eliminated without touching disk bytes.
- **Phase 2 (SHA-256 Verification):** Only size collisions undergo cryptographic `SHA-256` hashing via `MessageDigest`.
- Redundant files are sorted descending by total wasted space (`(count - 1) * size`) and tidied safely into `Duplicates/` while preserving the primary original file.

### 2. Transactional Command Pattern & Resilient Moves
All file migrations implement the `Action` interface via `MoveAction`:
- Attempts high-speed atomic rename via `Files.move(source, target)`.
- If atomic rename fails (e.g., across partitions, network mounts, or file system boundaries), it gracefully falls back to an unbuffered byte-stream copy (`FileInputStream` / `FileOutputStream`) followed by secure deletion of the source.
- Symmetric `undo()` support allows instant reversal with automatic parent folder reconstruction.

### 3. Hermes Aesthetic & Terminal Safety
- **Anti-Glitch Geometry:** Windows console hosts (`conhost.exe`) frequently duplicate bottom rows when characters are written to the terminal's very bottom-right corner. Solite pads bottom rows to `width - 1`, completely eliminating terminal ghosting and auto-wrap scroll bugs.
- **Dithered Depth:** Avoids heavy unicode block elements (`█ ▓ ▒ ░`) that fail on basic fonts. Soft shadows are rendered with clean ASCII-compatible glyphs (`· : .`) across L1 dust and L2 mist densities.

---

## Build Commands

| Task | Command | Description |
| :--- | :--- | :--- |
| **Run TUI** | `./gradlew run` | Compiles and launches Solite in your terminal |
| **Run Tests** | `./gradlew test` | Executes the full JUnit 5 test suite |
| **Build Project** | `./gradlew build` | Full compilation, resource processing, and testing |
| **Package Exe** | `./gradlew packageExe` | Builds a standalone Windows app image (`build/dist/solite/`) |
| **Clean Build** | `./gradlew clean` | Wipes build caches and generated artifacts |

---

## Verification & Testing

Solite maintains an exhaustive automated test suite covering both algorithmic backend logic and responsive terminal UI state machines:

```bash
.\gradlew.bat test --rerun
```

```
> Task :test
BUILD SUCCESSFUL in 8s
3 actionable tasks: 2 executed, 1 up-to-date
```

Key test suites:
- **`FileOrganizerTest`**: Tests mixed extension grouping, case-insensitivity, alphabetical determinism, atomic moves, duplicate detection with space metrics, and full undo stack rollbacks.
- **`LayoutEngineTest`**: Validates the breakpoint matrix (60, 80, 110, 150 cols), emergency mode, and dynamic 200-iteration resize fuzzing.
- **`ThemeTest`**: Validates capability ladder transitions from Truecolor to 256/16-color and monochrome, ensuring strict glyph budgets.
- **`TerminalAppLogicTest`**: Verifies path shrinking, middle ellipsis formatting, byte unit conversions (`B`, `KB`, `MB`, `GB`), alternate screen buffer transitions, and clean shutdown signals.

---

## License

This project is licensed under the [MIT License](LICENSE).
