# Solite Handoff

This file used to duplicate `WORKER.md`. It went stale, so it is now only a
pointer.

**Read [WORKER.md](WORKER.md)** — it lists the current files, the build
commands, the keys, the tests, and the known gaps.

Quick version:

```bash
./gradlew installCli     # build cli/solite.jar
solite                   # run the explorer (needs a real terminal)
```

`./gradlew run` does not work for the TUI — Gradle gives the JVM a pipe, not a
terminal, so solite exits with code 2 instead of drawing.
