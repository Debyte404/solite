# Solite — Contributor Architecture & Engineering Reference

> This file documents the complete architectural specification, coding conventions, and verification procedures for Solite.

---

## 1. Project Overview

**Solite** is an intelligent, high-performance terminal file organizer and duplicate cleaner written in modern Java 17.
- **Repository:** https://github.com/Debyte404/solite
- **Design Spec:** [DESIGN.md](DESIGN.md)
- **Tech Stack:** Java 17 (LTS), Gradle 9.2.0, JLine 3.30.0, Gson 2.13.2, JUnit 5.13.4
- **Target OS:** Windows (primary), Linux / macOS (secondary)

---

## 2. Completed Milestones & Status

All project phases are 100% complete, fully implemented, and validated through automated testing:

1. **Phase 1 — Core TUI Engine:**  
   `TerminalApp`, `LayoutEngine`, `Theme` (Hermes palette: Honey, Amber, Wax, Paper, Ash, Leaf, Ember), and `ShadowPainter` (two-pass dithered shadows using `· : .`).
2. **Phase 2 — Screen State Machine:**  
   `Screen.PICK` (whole-filesystem navigation, live search, path jumping, drive switching) → `Screen.GATHER` (extension group review, peek preview, actions 1-4) → `Screen.CONFIRM` (size impact summary, double-Enter safety modal).
3. **Phase 3 — Polish & High Contrast:**  
   Full-row high-contrast cursor highlights, 3-second fading toast notification system, and `?` in-app help modal.
4. **Phase 4 — Core Algorithms & I/O Safety:**  
   Command design pattern (`MoveAction`), cross-filesystem byte-stream fallback, per-folder `move_log.txt` audit logging, and single-key Undo (`u`).
5. **Phase 5 — Advanced Capabilities:**  
   - **Quick Tidy**: Automatic 1-key folder organization.
   - **Duplicate Detection**: Fast two-phase scan (O(N) size binning + SHA-256 content hashing) and `Duplicates/` folder isolation.
   - **Native Distribution**: Standalone Windows executable (`solite.exe`) packaged via `jpackage`.

---

## 3. Architecture Rules & Coding Standards

1. **No External UI Frameworks:**  
   Keep dependencies minimal. Only JLine 3.30.0 is used for terminal I/O, line editing, and ANSI sequence rendering.
2. **No Colors in Business Logic:**  
   All colors belong strictly to `Theme.java`. Core backend logic (`solite.core`) has zero awareness of terminal colors or UI styling.
3. **No Brittle Block Glyphs:**  
   Only `· : .` are permitted for dithered shading. Never use block characters (`█ ▓ ▒ ░`) as base glyphs, as they distort and break rendering on standard Windows consoles.
4. **Responsive Layout or Nothing:**  
   The application must gracefully reflow on terminal window resize across all 4 breakpoints (`XS`, `S`, `M`, `L`) and operate cleanly in Emergency Mode (<14 rows).
5. **Pure Keyboard Navigation:**  
   Every mouse action must have a first-class keyboard twin. Never require mouse interaction.
6. **Destructive Operations Require Confirmation:**  
   Batch file modifications must always stage first and require confirmation on Screen C.
7. **LIFO Undo Stack:**  
   The undo stack operates in Last-In-First-Out (LIFO) order, ensuring dependent or sequentially moved files are cleanly restored to their exact prior positions.

---

## 4. Key Subsystems

### `solite.core`
- **`FileOrganizer`**:
  - `listEntries(Path dir, String filter)`: Lists directory contents, sorts directories before files, and applies case-insensitive filter.
  - `groupFiles(Path dir)`: Recursively walks the tree, groups files by extension (normalized to uppercase), and returns deterministically sorted `Group` instances.
  - `findDuplicates(Path dir)`: Scans for duplicate files by size and SHA-256 hash, sorted by wasted storage.
  - `moveFiles()`, `moveAllGroups()`, `tidyDuplicates()`: Executes staged file operations using `MoveAction`.
  - `undoMove()`: Reverses the last executed batch of moves.
- **`MoveAction`**:
  - Encapsulates source and destination paths.
  - Tries atomic `Files.move()`, automatically falling back to byte streaming across drives/filesystems.
  - Symmetrically recreates parent directories during `undo()`.
  - Records every action to `baseDir/move_log.txt`.

### `solite.ui`
- **`TerminalApp`**: Main event loop, JLine raw mode lifecycle, key event dispatch, viewport rendering, and toast system.
- **`LayoutEngine`**: Dynamically computes panel dimensions and breakpoints based on terminal width and height.
- **`Theme`**: Detects and adapts palette to Truecolor (24-bit), 256-color, 16-color, and monochrome environments.
- **`ShadowPainter`**: Renders subtle dithered shadow strips along card boundaries.

---

## 5. Verification Commands

```powershell
# Compile and run test suite
.\gradlew.bat test

# Run tests with detailed log output
.\gradlew.bat test --info

# Build standalone distribution executable
.\gradlew.bat packageExe

# Run application in dev mode
.\gradlew.bat run
```
