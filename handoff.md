# Solite — Engineering Handoff & Project Summary

Welcome to the official Solite handoff document. This document certifies the complete implementation, architecture, and production readiness of the Solite project.

---

## 1. Project Overview & Final Status

Solite is a smart, high-performance file organizer and duplicate cleaner with a terminal user interface (TUI) written in Java 17, styled with the Hermes-yellow aesthetic.

### Implementation Checklist
- [x] **Phase 1 (Core TUI Scaffold):** Application loop, JLine 3 terminal binding, responsive `LayoutEngine`, `Theme` capability ladder (Truecolor/256/16/Mono), and `ShadowPainter`.
- [x] **Phase 2 (Screens & State Machine):** Screen A (Directory Navigator / Pick), Screen B (File Group Organizer / Gather), and Screen C (Confirmation Modal / Confirm).
- [x] **Phase 3 (UI Polish & UX):** High-contrast focus row, type-to-filter live searching, whole-filesystem browsing, drive switching (`C:\`, `D:\`), instant path jump, asynchronous fading toast notification system, and in-app modal help (`?`).
- [x] **Phase 4 (Safety & Audit):** Transactional Command pattern (`MoveAction`), cross-device byte-stream copy fallback, symmetric parent folder creation, LIFO undo stack (`undoMove()`), and per-directory `move_log.txt` audit logging.
- [x] **Phase 5 (Advanced Features):**
  - **Quick Tidy**: 1-key automatic organization of all groups in a folder.
  - **Duplicate Detection & Tidy**: Two-phase O(N) size grouping + SHA-256 cryptographic content verification with space waste ranking.
  - **Packaging**: Self-contained Windows executable (`solite.exe`) via `jpackage` and resilient launcher script (`solite.bat`).
- [x] **Automated Test Suite:** Exhaustive JUnit 5 test suite covering core algorithms, I/O resilience, layout breakpoints, theme ladders, and terminal lifecycle. **All tests passing 100% green.**

---

## 2. Core Architecture

Solite follows strict separation between domain data processing (`solite.core`) and terminal presentation (`solite.ui`).

```
┌────────────────────────────────────────────────────────┐
│                      solite.Main                       │
│       (JLine 3 Terminal Setup & Raw Mode Launch)       │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                   solite.ui.TerminalApp                │
│    - Screen State Machine (PICK -> GATHER -> CONFIRM)  │
│    - Event Loop (Keyboard input & ANSI rendering)      │
│    - Viewport scrolling & conhost anti-wrap padding    │
└─────────────┬────────────────────────────┬─────────────┘
              │                            │
              ▼                            ▼
┌───────────────────────────┐ ┌──────────────────────────┐
│   solite.ui.LayoutEngine  │ │    solite.ui.Theme       │
│  - 4 Breakpoints (XS/S/M/L)│ │ - Hermes Palette         │
│  - Emergency Mode (<14h)  │ │ - Capability detection   │
└───────────────────────────┘ └──────────────────────────┘
              │
              ▼
┌────────────────────────────────────────────────────────┐
│                solite.core.FileOrganizer               │
│    - listEntries(dir, filter)                          │
│    - groupFiles(dir) [Case-insensitive & sorted]       │
│    - findDuplicates(dir) [Size binning + SHA-256]      │
│    - moveFiles() / moveAllGroups() / tidyDuplicates()  │
│    - undoMove() [LIFO reverse undo]                    │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                  solite.core.MoveAction                │
│    - execute(): Atomic Files.move + Stream Fallback    │
│    - undo(): Symmetric Reversal + Parent Dir Rebuild   │
│    - logAction(): Base directory move_log.txt logging  │
└────────────────────────────────────────────────────────┘
```

---

## 3. Key Design Decisions & Invariants

1. **Transactional Moves & Fallback:**  
   Moving files across different drives or mount points causes `Files.move()` to fail with an `IOException`. `MoveAction` catches this and automatically falls back to an unbuffered byte-stream copy (`copyFallback()`) followed by deleting the source.
2. **Symmetric Rollback (LIFO):**  
   `FileOrganizer.undoMove()` iterates backwards through `undoStack`. `MoveAction.undo()` automatically verifies and recreates missing parent directories before moving the file back, ensuring reliable undo even if intermediary folders were cleaned up.
3. **Deterministic Grouping:**  
   Extensions are normalized case-insensitively (`p -> getExtension(p).toUpperCase()`) and groups are sorted alphabetically by name. Mixed files like `img.JPG` and `img.jpg` are unified into one `JPG` group.
4. **Local Audit Trail:**  
   Logs are written to `baseDir.resolve("move_log.txt")`—the actual folder being organized—rather than the process working directory or subfolders. Recursive file scans explicitly skip `move_log.txt` so it is never displaced.
5. **Zero Conhost Auto-Wrap Duplication:**  
   Windows terminal / `conhost.exe` duplicates the footer row if a character is drawn in the terminal's bottom-right cell (`x = width - 1, y = height - 1`). `TerminalApp` renders the bottom row with length `width - 1`, keeping terminal output crisp and glitch-free.

---

## 4. Verification & Testing

Run all automated unit and integration tests with:
```powershell
.\gradlew.bat test --rerun
```

### Test Coverage Summary:
- `FileOrganizerTest`:
  - `testGroupFiles`: Verifies grouping of mixed files and subdirectories.
  - `testGroupFilesCaseInsensitiveAndDeterministic`: Confirms case normalization (`.PNG`, `.png`, `.Png`) and alphabetical sort ordering.
  - `testMoveFilesAndUndo`: Validates atomic moves and reverse undo.
  - `testMoveAllGroupsAndUndo`: Tests batch auto-organization and complete reversal.
  - `testFindAndTidyDuplicates`: Tests two-phase duplicate identification, space calculation, moving duplicates to `Duplicates/`, and full restoration.
- `LayoutEngineTest`: Breakpoint computation across dimensions and resize fuzzing.
- `ThemeTest`: Validates palette styles across color capabilities.
- `TerminalAppLogicTest`: Validates byte formatting, string truncation, middle ellipsis path shortening, and alternate screen buffer cleanup.
- `ClickableRegionTest`: Validates screen action coordinate matching.

---

## 5. Maintenance & Building

- **To run in development:**
  ```powershell
  .\gradlew.bat run
  ```
- **To build the standalone Windows executable:**
  ```powershell
  .\gradlew.bat packageExe
  ```
  The packaged application image will be produced in `build/dist/solite/`.
- **To launch via batch:**
  ```powershell
  .\solite.bat
  ```
