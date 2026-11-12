# Contributing to Wend

Wend is in Phase 0. This repository implements the pure Kotlin scheduling engine
and a bare Android model benchmark. The production planner UI, storage, reminders,
AI planner and companion belong to separate tasks.

Before working, read [AGENTS.md](AGENTS.md),
[the engine specification](docs/engine-spec.md), and Phase 0 of [the PRD](docs/PRD.md).
The engine spec is authoritative for scheduling behavior. If requirements conflict,
raise the discrepancy before implementing a different behavior.

## Changes

1. Open a focused issue when a behavior change needs discussion.
2. Use one branch per task and small Conventional Commits, such as `fix:`, `feat:`,
   `test:` or `docs:`.
3. Keep the engine deterministic and independent of Android, I/O and wall clocks.
4. Update the relevant documentation when behavior changes.
5. Add tests for new behavior. Never change an example's expected result just to
   make an implementation pass; resolve the spec discrepancy first.
6. Run the checks below and describe acceptance criteria covered in the pull request.

```sh
./gradlew build test :core:engine:test lint spotlessCheck
```

JDK 21 and the Android SDK/NDK are required; see the benchmark protocol for versions.
On Windows use `.\gradlew.bat`. Format with `./gradlew spotlessApply`.

Property-test failures include a random seed and a reduced input. Include those
details when reporting a scheduling bug, together with the expected rule and output.
Do not include personal schedule content, secrets, signing files or tokens.

## License

Contributions to this repository are made under its GPLv3 license. The Wend name
and logo remain Jinatra trademarks; the source-code license grants no trademark rights.
