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
./gradlew build     compile + test
./gradlew run       launch the TUI
./gradlew test      run tests only
```

(On Windows: `gradlew.bat`)

## Status

Project scaffolded — implementation not started yet.
