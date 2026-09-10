# Solite TUI — Design Proposal v0.1

> Hermes-yellow. Dithered calm. Simple enough for anyone. Fits any terminal.

## 1. Vision

Solite organizes files. The TUI must feel like Hermes looks: dark room, one warm
yellow lamp. Nothing blinks. Nothing shouts. The user always knows:
where am I, what is selected, what happens if I press Enter.

Three anti-overload rules:
- One screen = one question. Never two decisions at once.
- Max 5 visible actions. Everything else hides behind `?` or `:`.
- Plain words. "Put photos together" beats "cluster by MIME + EXIF".

## 2. Palette — Hermes yellow

Dark stage so yellow can glow. All yellows are one hue, only brightness changes.

- `ink` background: #0B0A06 (near-black warm). Fallback 256: 232. Fallback 16: black.
- `honey` primary: #FFC800 — titles, selected row, focused border. 256: 220. 16: yellow bold.
- `amber` dim: #8A6D00 — secondary text, unfocused borders. 256: 136. 16: yellow dim.
- `wax` faint: #4A3D10 — hairlines, gutters, inactive. 256: 58. 16: black bold.
- `paper` text: #F5EDD6 — file names, body. 256: 230. 16: white.
- `ash` muted: #9A917A — meta text (sizes, dates). 256: 246. 16: white dim.
- `leaf` ok: #9DBE00 sparingly (done check only). `ember` warn: #FF5C00 sparingly.

Rule: yellow never fills large areas. It draws edges, one selected row, one
banner line. Everything else is ink/paper/ash. That is what keeps it calm and
readable on cheap terminals.

Truecolor `38;2;255;200;0` first, then 256-color, then 16-color, then mono.
Detect via JLine terminal type + `COLORTERM` + `TERM`. Never assume.

## 3. Dithered shade system (the signature)

No blur in terminals, so depth comes from dot density. Shadows and washes are
made of sparse yellow dots on ink — sparse = lighter shade, dense = darker
shade. From afar it reads as a soft glow/shadow.

Density ladder (all drawn in `wax`/`amber` dim, never bright):
- L0 air: ` ` — 0% — normal background
- L1 dust: `·` on every 4th cell — ~12% — far shadow edge
- L2 mist: `·` checkerboard — ~25% — near shadow / banner fade
- L3 grain: `:` mixed with `·` — ~40% — panel underlay, max density ever

Never use block glyphs `█ ▓ ▒ ░` as the base design. They render wildly
different widths/colors on Windows consoles and break the calm. Use only
`· : .` for dither. Reserve `░` as a progressive enhancement when the terminal
reports full Unicode + truecolor (WezTerm, Windows Terminal, ghostty).

Shadow recipe (every card/panel gets this):
1. Draw panel with single-line border in `amber` (focused: `honey`).
2. Offset +2 cols, +1 row: paint L2 mist strip along right + bottom edge.
3. Offset +3 cols, +2 rows: paint L1 dust strip outside that.
4. On tiny terminals (<70 cols) skip step 3. On <60 cols skip shadows entirely.

Banner wash: app title in `honey` bold on line 1, under it one row of L1 dust
fading out left-to-right (dots thin out: `· ·  ·   ·    `). Reads as Hermes
glow without eating rows.

## 4. Glyph budget (compatibility first)

Base set — guaranteed on cmd.exe, PowerShell 5, mintty, VSCode, SSH:
`─ │ ┌ ┐ └ ┘ ├ ┤ ┬ ┴ ● ○ · : . > < [ ] ( ) ✓ ! ?`

Enhanced set — only when `terminal.getType()` is not dumb and encoding is
UTF-8: `─ │ ╭ ╮ ╰ ╯ ░ → ← ↑ ↓ ✕`.

Mouse support is a first-class feature (see §7.1), not an enhancement-only
extra. Mouse hover and rounded corners remain enhancement-only.

## 5. Layout — responsive by size

One layout engine, three breakpoints. App reflows live on SIGWINCH /
JLine SizeChanged event + a 200ms poll fallback for terminals that swallow
the signal (mintty, VSCode).

- XS narrow: width < 70. One column. List only. Preview collapses to a single
  `ash` line under selection. No shadows. Banner shrinks to `solite ● <path>`.
  Footer becomes one line: `↑↓ move · Enter open · ? keys`.
- S standard: 70–109 cols. One column + inline preview (2 meta lines under
  each selected row). L1 shadows on. Min height 18; below that banner hides
  and list paginates 5-at-a-time.
- M wide: 110–139 cols. Two panes: left file groups (55%), right preview
  (45%). Full L1+L2 shadows. Details rail hidden.
- L ultra: >= 140 cols. Two panes + far-right rail (suggested action, size
  saved, undo). Banner wash full width.

Height rules: header 2 rows, footer 2 rows (1 on XS), everything else is list.
If height < 14: emergency mode — plain scrolling list, no borders, no dither,
still fully usable. Never crash on tiny windows, never require scrolling
outside the list pane.

Reflow checklist: recompute cols/rows → pick breakpoint → rewrap names
(ellipsis middle: `vacation…2024.jpg`) → reposition shadow strips → keep
selection index stable → repaint only dirty rects (JLine Display.update).

## 6. Screens — only three, in order

### Screen A — Pick (where to tidy)
```
 ● solite — smart file organizer              honey bold
 · ·  ·   ·                                  wax L1 wash

 ┌─ Folder ──────────────────┐  · ·          amber border + L1 shadow
 │ > D:/Photos               │               selected path, paper
 │   D:/Downloads            │
 │   D:/Desktop              │
 └───────────────────────────┘  · ·

  3 folders · ↑↓ move · Enter tidy · q quit   ash, one line
```
One question only. Type to filter. Enter moves on. `q` quits here, nowhere else.

### Screen B — Gather (the workhorse, default home)
Left = groups Solite suggests. Right = what is inside. Bottom = the one action.
```
 ● solite   D:/Photos · 1,204 files           honey

 ┌─ Groups ─────────────┐ ┌─ Photos-2024 ───────────────┐
 │ ● Photos-2024   312  │ │ IMG_1012.jpg   2.1 MB       │
 │ ○ Screenshots    88  │ │ IMG_1013.jpg   1.9 MB       │
 │ ○Zips            12  │ │ … 310 more                 │
 └──────────────────────┘ └────────────────────────────┘
   · · mist shadow              · · mist shadow

 > [1] Move 312 into Photos/2024   Enter ✓    honey selected row
   [2] Peek inside                    →
   [3] Skip this group                s

 ↑↓ groups · 1/2/3 act · u undo · ? all keys
```
Why it stays simple: numbered actions (1/2/3) so no memorization. Preview
shows max 5 files then `… N more`. Destructive moves always stage first and
need a second Enter (see Screen C). Nothing applies silently.

### Screen C — Confirm (only for moves/deletes)
```
 ┌─ Move? ───────────────────┐  · ·
 │ 312 files → Photos/2024   │
 │ Frees ~410 MB of clutter  │
 │                           │
 │ [Enter] Yes, move  [n] No │
 └───────────────────────────┘  · ·
```
Two buttons, plain language, size impact stated. Esc/n aborts. After apply:
one-line toast `✓ 312 moved · u undo` in `leaf`, auto-fades in 3s.

Empty/error states: empty folder → `○ Nothing to tidy here. Nice.` + `b back`.
Unreadable dir → `! Can't read this folder (permission). Pick another.`
Undo always available one level deep, stated in footer.

## 7. Keymap (printable on one `?` card)

- `↑ ↓ / j k` move · `Enter` confirm/open · `1 2 3` act on Screen B
- `b / Esc` back · `u` undo · `:` type a path · `?` keycard · `q` quit
- No Ctrl-chords except `Ctrl+C` (always quits cleanly, restores cursor).
- Every action is also a number or Enter — nobody must learn vim keys.

## 7.1 Mouse support (first-class)

Mouse is a primary input alongside the keyboard. Every clickable element is
reachable by tap; keyboard remains the fast path for power users.

**Enable flow** — at startup call `terminal.setMouseTracking(true)` (JLine 3).
If it throws or the terminal reports dumb/pipe, mouse stays off silently —
no error, no crash. Detect via `terminal.getType()` + `WT_SESSION` + `TERM`.
On conhost (legacy Windows console) mouse often works but is flaky; still
enable it, but never rely on it for core flows.

**What's clickable** (every screen):
- Screen A: each folder row, the filter field, the `Enter tidy` button.
- Screen B: each group row, each preview file row, the numbered action
  buttons `[1] [2] [3]`, the footer hints (`u`, `?`, `q`).
- Screen C: the two confirm buttons `[Enter] Yes` and `[n] No`.

**Hover feedback** — on mouse move over a clickable, paint that row in
`honey` (same as keyboard selection) and move the keyboard cursor to it so
mouse and keyboard stay in sync. On terminals that can't do hover (no
`MouseMotionListener` support), hover is skipped — click still works.

**Click semantics**:
- Single click on a row = select it (same as arrowing there).
- Double click on a row = activate it (same as Enter): open folder, open
  group preview, fire the default action.
- Single click on a button = fire that action immediately.
- Scroll wheel = move the list by 3 rows (natural scroll on).

**Hit-testing** — keep a `List<ClickableRegion>` rebuilt on every layout
reflow. Each region stores (x, y, w, h, target action). On mouse event,
find the region by coordinates, highlight it, fire its action. Regions are
cheap to rebuild; do it on every repaint.

**Visual affordance** — clickable rows already use `●`/`○` markers and
`honey` selection, so they read as tappable without extra chrome. Buttons
render as `[1] Label` in `honey` when focused, `amber` when not. No hand-
pointer cursor (terminals can't reliably change it), but the glow-on-hover
makes intent obvious.

**Accessibility** — mouse never gates any action. Every mouse action has a
keyboard equivalent (numbers, arrows, Enter, Esc). Footer always shows both.
Users on screen readers / braille terminals (dumb/pipe) get the line-mode
fallback and never see mouse code paths fire.

## 8. Compatibility ladder

- Colors: truecolor → 256 → 16 → mono. Mono keeps layout + `>`/`●` markers,
  drops all yellow, dither becomes spaces. Fully usable uncolored.
- Borders: round `╭╮` on capable terms, square `┌┐` on legacy, `+-+` on dumb.
- Width: use `wcwidth` for CJK/emoji filenames; truncate with ellipsis middle.
- No alt-screen lock-in: prefer JLine `Display` full-screen; if terminal is
  dumb/pipe, fall back to line mode (`? path`, numbered stdout list).
- Windows notes: query `WT_SESSION` (Windows Terminal) vs legacy conhost;
  on conhost force 16-color + square borders + no `░`. mintty/git-bash: poll
  size, do not trust SIGWINCH alone.
- Perf: repaint dirty regions only; dither strips precomputed per width and
  cached; no per-frame allocation in the render hot path (GC pauses kill TUI
  feel on 1k-file lists). Virtualize list (render visible rows + 2).

## 9. Build it in JLine (concrete)

- `TerminalBuilder.builder().system(true).jansi(true).build()`; `terminal.enterRawMode()`.
- `Display` + `AttributedString`/`AttributedStyle` for all painting.
- `SizeChangeListener` + scheduled re-layout; `Status` bar reserved bottom row.
- **Mouse**: `terminal.setMouseTracking(true)` at startup (wrap in try/catch —
  dumb terminals throw, fall back silently). Register a `MouseListener` via
  JLine's event handling; on `MouseEvent` translate to row/column, run
  hit-test against `ClickableRegion` list, fire the matching action. Rebuild
  the region list on every layout reflow. Hover = `MouseMotionListener` if
  supported; if not, clicks still work.
- Theme object: `Theme.truecolor() / v256() / v16() / mono()` returning
  (honey, amber, wax, paper, ash) + (border set) + (dither chars). Pick once
  at startup, re-pick on `COLORTERM` change.
- Shadow painter: `paintShadow(display, x, y, w, h, density)` — two passes
  (L2 then L1), writes only `AttributedChar` dots, skips when width < 70.
- Tests: breakpoint matrix (60/80/110/150 × 14/24/40), dither snapshot tests
  (assert dot counts, not pixels), dumb-terminal run (assert no ANSI leaked
  beyond `\n`), resize fuzz (random sizes, assert selection stable + no
  exception), **mouse tests** (click on each screen's clickable regions,
  double-click activates, scroll wheel moves list, hover syncs keyboard
  cursor, mouse-off terminals still fully usable via keyboard).
  `gradlew test` must cover these before TUI v0.1 ships.

## 10. Decisions (approved 2026-09-11)

1. ✅ Palette + dither direction — approved as-is.
2. ✅ 3 screens / numbered actions — approved as-is.
3. ✅ Min terminal floor — 70×18 XS is fine.
