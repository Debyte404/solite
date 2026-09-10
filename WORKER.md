# Solite — Contributor Handoff

> This file tells you what's done, what's next, and how to build it.
> Read this before touching any code.

## Project Overview

**Solite** is a smart file organizer with a TUI (terminal user interface) in Java.
The design is Hermes-yellow: dark background, one warm yellow lamp, dithered shadows.

- Repo: https://github.com/Debyte404/solite
- Spec: [DESIGN.md](DESIGN.md) — full TUI design (palette, dither, screens, mouse, compat)
- Stack: Java 17, Gradle 9.2.0, JLine 3.30.0, Gson, JUnit 5
- Platform: Windows (primary), Linux/macOS (secondary)

## What's Done (Phase 1)

Phase 1 scaffolded the core TUI engine. **Do not rewrite these — extend them.**

### Files
- `Main.java` — entry point, wires JLine terminal + app launch
- `ui/Theme.java` — Hermes palette (honey #FFC800, amber, wax, paper, ash) with truecolor/256/16/mono detection
- `ui/LayoutEngine.java` — 4 breakpoints (XS/S/M/L) + responsive geometry
- `ui/ShadowPainter.java` — two-pass dithered shadows (L2 mist + L1 dust) using `· : .` only
- `ui/TerminalApp.java` — main loop, WINCH resize, mouse tracking, banner wash, panel render
- `core/FileOrganizer.java` — file scanner + group-by-extension (incomplete, see Phase 2)
- `DESIGN.md` — full design spec (approved 2026-09-11)

### Build
```bash
./gradlew.bat build        # compile + test
./gradlew.bat run          # launch TUI (currently shows banner + empty panel + footer)
```

### Tests
No tests yet. Phase 4 requires them (see below).

## What's Next (Phases 2–4)

### Phase 2 — Screens (Pick → Gather → Confirm)

**Goal:** Build the 3 screens from DESIGN.md §6.

#### Screen A — Pick (where to tidy)
- List folders in a directory (default: `System.getProperty("user.home")`)
- Type-to-filter (live as user types)
- Enter selects folder, moves to Screen B
- `q` quits
- **Layout:** header 2 rows, footer 1 row (XS) or 2 rows (S/M/L), list fills rest
- **Shadow:** L1 only on S+, no shadow on XS

#### Screen B — Gather (the workhorse)
- Left pane: groups Solite suggests (from `FileOrganizer.groupFiles`)
- Right pane: preview (max 5 files from selected group, then `… N more`)
- Bottom: numbered actions (1/2/3) — see DESIGN.md §6 for exact labels
- Enter on a row = select group, show preview
- Number keys 1/2/3 = fire action (1 = move, 2 = peek, 3 = skip)
- `b` / Esc = back to Screen A
- **Layout:** M/L = two panes (55% left, 45% right); S = one column with inline preview under selection
- **Shadow:** L1+L2 on M+, L1 only on S, none on XS

#### Screen C — Confirm (only for moves/deletes)
- Two buttons: `[Enter] Yes, move` and `[n] No`
- Plain language + size impact ("312 files → Photos/2024, frees ~410 MB")
- Double-Enter for moves (first Enter shows confirm, second Enter applies)
- Toast after apply: `✓ 312 moved · u undo` (leaf green, fades in 3s)
- `u` = undo (one level deep, move back to original location)
- **Layout:** centered modal, max 60% width, L1 shadow
- **Shadow:** L1 only

#### Implementation Notes
- Extend `TerminalApp` to track current screen (enum: PICK, GATHER, CONFIRM)
- Add a `renderScreen()` method that dispatches to screen-specific paint logic
- Keyboard state machine: map keys to actions per screen (see DESIGN.md §7 keymap)
- Mouse: extend `ClickableRegion` for each screen's clickable rows/buttons
- Path state: track current folder (Screen A → B), current group (B → C)
- Undo state: stack of (originalPath, movedPath) pairs, one level deep

#### FileOrganizer Completion
`core/FileOrganizer.java` exists but is incomplete:
- ✅ `listEntries(Path)` — lists children, sorts dirs first
- ✅ `groupFiles(Path)` — groups by extension, returns `Group[]`
- ❌ `moveFiles(List<Path>, Path)` — stub, implement
- ❌ `undoMove(...)` — stub, implement
- ❌ Filter/search for Screen A type-to-filter

### Phase 3 — Mouse + Polish

**Goal:** Wire mouse support (DESIGN.md §7.1) + empty/error states.

#### Mouse
- `terminal.trackMouse(MouseTracking.Normal)` already enabled in Phase 1
- Extend `TerminalApp` to handle `MouseEvent` via JLine's event handling
- Hit-test against `ClickableRegion` list (one per screen)
- Single click = select row, double click = activate (Enter), scroll wheel = move list by 3
- Hover = paint row in `honey` + sync keyboard cursor

#### Empty/Error States
- Empty folder → `○ Nothing to tidy here. Nice.` + `b back`
- Unreadable dir → `! Can't read this folder (permission). Pick another.`
- No groups → `○ No files to organize. Pick another folder.`

#### Toast System
- One-line toast at bottom: `✓ 312 moved · u undo` in `leaf` green
- Auto-fades in 3s (use a `ScheduledExecutorService` or simple counter)
- Only one toast at a time (new toast replaces old)

### Phase 4 — Tests + Commit

**Goal:** Prove it works, commit v0.1.

#### Tests (JUnit 5, in `src/test/java/solite/`)
- **Breakpoint matrix:** test layout at 60/80/110/150 × 14/24/40 (assert correct breakpoint + geometry)
- **Dither snapshot:** test shadow painter output (assert dot counts, not pixels)
- **Dumb terminal:** assert no ANSI leaked beyond `\n` when theme is mono
- **Resize fuzz:** random sizes, assert selection stable + no exception
- **Mouse tests:** click on each screen's clickable regions, double-click activates, scroll wheel moves list, hover syncs keyboard cursor, mouse-off terminals still usable via keyboard
- **FileOrganizer:** test groupFiles on a temp dir with mixed extensions, test moveFiles + undoMove roundtrip

#### Smoke Test
```bash
./gradlew.bat test         # must pass all of the above
./gradlew.bat run          # manual: pick a folder, see groups, move a group, undo
```

#### Commit v0.1
- `git commit -m "feat: v0.1 — 3 screens, mouse, tests"`
- Tag: `git tag v0.1.0`
- Push: `git push origin main --tags`

## Architecture Rules

1. **No external UI frameworks** — JLine 3.30.0 only (no Lanterna, no curses wrappers)
2. **No colors in logic** — Theme object owns palette; never hardcode `#FFC800` outside Theme
3. **No block glyphs** — use `· : .` for dither, never `█ ▓ ▒ ░` (breaks on conhost)
4. **Responsive or nothing** — every screen must reflow on resize; test at all 4 breakpoints
5. **Mouse is optional** — every action must have a keyboard equivalent; never require mouse
6. **Destructive = double-Enter** — moves/deletes always stage first, confirm second
7. **Undo is one level deep** — track last move only, not full history

## Design Constraints (from DESIGN.md)

- **Palette:** ink #0B0A06, honey #FFC800, amber #8A6D00, wax #4A3D10, paper #F5EDD6, ash #9A917A, leaf #9DBE00, ember #FF5C00
- **Dither:** L1 dust (~12%), L2 mist (~25%), L3 grain (40% max). Only `· : .` glyphs.
- **Breakpoints:** XS <70, S 70–109, M 110–139, L 140+. Height <14 = emergency mode (plain list, no borders).
- **Screens:** 3 only (Pick, Gather, Confirm). One question per screen, max 5 visible actions.
- **Mouse:** first-class (DESIGN.md §7.1). `trackMouse(MouseTracking.Normal)` + hit-testing.
- **Compat:** truecolor → 256 → 16 → mono ladder. Round→square→ASCII borders. No Nerd Fonts/emoji required.

## How to Contribute

1. **Read DESIGN.md** — understand the full spec before coding
2. **Pick a phase** — start with Phase 2 (screens) if unsure
3. **Build incrementally** — test one screen at a time, commit often
4. **Write tests** — Phase 4 is not optional; TUI v0.1 ships with tests
5. **Commit as Debyte404** — `git config user.name "Debyte404"` and `git config user.email "ankit.byte.404@gmail.com"` in your local repo (do NOT push as a different account)

## Questions?

If something in this file or DESIGN.md is unclear, open an issue or ask in the Discord thread where this was discussed. Do not guess — ask.
