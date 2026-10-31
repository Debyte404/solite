# AGENTS.md — rules for every agent in this repo

Shared working agreement for AI agents and the human. Read it before you touch a
file. Read `WORKER.md` first for what actually ships today (files, build, tests,
keys, gaps).

Who is who:

- **Shivam** (GitHub: `Debyte404`) — the human. Owns every decision and every push.
- **Hermes** — Discord/CLI agent on this machine. Tags commits `[hermes]`.
- **Antigravity** — Google's IDE agent. Tags commits `[antigravity]`.

## Read order before any edit

1. `WORKER.md` — current state, architecture rules, 112 tests expected green.
2. This file — coordination rules.
3. `git status --short` and `git log -5 --oneline` — know what the other agent
   left uncommitted before you start.

## One writer per file: claim before you edit

Two agents editing the same file silently clobbers work, and neither of you will
notice until the build breaks. So claim first.

Before editing, add a line under `## Active claims` below:

```
- hermes | 21:40 | ui/SimpleUI.java | adding size column to group screen
```

Remove your line when you commit or abandon. Rules:

- **Never edit a file under another agent's active claim.** Add it to
  `## Blocked` with what you need instead.
- **A claim older than 45 minutes with no commit is abandoned** — note it in the
  log, take it, and say so in your next log entry.
- Claim a glob only when you genuinely own the whole area (`core/*.java`).

## Commit discipline

`WORKER.md` fixes the identity: commits are made as `Debyte404`, pushed to
`Shiv-Branch`, never `main`. Keep that. Distinguish authorship in the subject:

```
[hermes] add size column to group screen
[antigravity] fix CRLF handling in KeyInput
```

Commit small and often enough that the other agent can see progress in
`git log`. **Never push** — Shivam pushes.

## Forbidden actions (these break the other agent)

- `git checkout -- <file>`, `git reset --hard`, `git clean -fdx` — destroys
  uncommitted work that may not be yours.
- `git stash` of files you did not write.
- Running the Gradle build twice concurrently (one Gradle lock, second run
  blocks or fails confusingly).
- Editing files under `archive/` — not compiled, kept for reference only.
- Adding a second UI framework or a curses wrapper (`WORKER.md` rule 1).

## Log — newest entry on top

After each work session, add an entry. Keep it to the four lines; detail belongs
in the commit message.

```
### 2026-10-22 21:40 — hermes
Did: added size totals to the group screen, wired into SimpleUI.confirm
Files: ui/SimpleUI.java, core/FileOrganizer.java
Next: none — build green, waiting for Antigravity on the filter bug
Blocked: none
```

## Active claims

_none_

## Blocked

_none_

## Cross-agent messaging

Antigravity has the `hermes` MCP server registered
(`C:\Users\shiva\.gemini\config\mcp_config.json`). Through it, Antigravity can
read and reply to the Hermes Discord thread with `conversations_list`,
`messages_read`, `events_wait`, `messages_send`. Use it when a decision needs a
human answer mid-task instead of guessing — but the repo files above remain the
source of truth, not the chat.
