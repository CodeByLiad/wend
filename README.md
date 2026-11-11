# Wend by Jinatra

[![Engine checks](https://github.com/CodeByLiad/wend/actions/workflows/engine.yml/badge.svg)](https://github.com/CodeByLiad/wend/actions/workflows/engine.yml)
[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)

Wend is a free, open-source Android day planner that plans and tracks your day
and rebuilds the plan when life gets in the way. The product is designed to keep
personal data on your phone, with optional on-device AI planned for later work.

**Current status: Phase 0 — scheduling-engine prototype.** This repository contains
one pure Kotlin/JVM module, `:core:engine`. There is no Android app, UI, database,
reminder service or AI implementation yet.

## What the engine does

- Places fixed, flexible and optional activities using priorities, windows and dependencies.
- Handles late wake-ups, overruns, moves, skips, additions and bedtime changes.
- Keeps missed, dropped and unplaced activities dormant until explicitly retried.
- Returns proposals with typed changes, explanations, conflicts and validation errors.
- Carries the exact previous day in every proposal so the caller can undo changes.
- Uses only its arguments: no clock access, randomness, I/O or Android dependencies.

The engine uses whole minutes since midnight on the plan date, on a five-minute
grid. Values beyond 1440 represent the next day. `now` is always supplied by the
caller, and proposals are applied by the caller.

## Build and test

Install **JDK 21** and clone the repository. The checked-in Gradle wrapper downloads
the build tools; a separate Gradle installation is not needed.

```sh
git clone https://github.com/CodeByLiad/wend.git
cd wend
./gradlew build
./gradlew test
./gradlew :core:engine:test
./gradlew spotlessCheck
```

On Windows, replace `./gradlew` with `.\gradlew.bat`. To format Kotlin sources
and build scripts, run `./gradlew spotlessApply`. Android lint will be added when
an Android module exists.

Tests include all 30 worked examples, full-state assertions for unchanged blocks,
randomized checks for every engine guarantee, and focused command/validation
regressions. HTML test results are generated at
`core/engine/build/reports/tests/test/index.html`.

## Repository guide

| Path | Contents |
| --- | --- |
| [`core/engine`](core/engine) | Pure Kotlin engine and Kotest tests |
| [`docs/engine-spec.md`](docs/engine-spec.md) | Authoritative behavior and 30 worked examples |
| [`docs/engine-implementation.md`](docs/engine-implementation.md) | API, small implementation choices and verification coverage |
| [`docs/PRD.md`](docs/PRD.md) | Product requirements by phase |
| [`docs/architecture.md`](docs/architecture.md) | Long-term technical context |
| [`AGENTS.md`](AGENTS.md) | Project rules |

Only the engine may change planned block times. Later modules must send it commands
and apply its proposals. The permanent Android package ID is `com.jinatra.wend`.

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md), the project
rules and the engine spec first. Please keep changes within the current phase;
report contradictions in the spec before changing expected example results.

Use [GitHub issues](https://github.com/CodeByLiad/wend/issues) for reproducible bugs
and focused proposals. This prototype is not an Android release or an APK download.

## License and trademarks

Wend's source code is licensed under the **GNU General Public License, version 3**.
See [LICENSE](LICENSE) for the complete text.

The **Wend name and logo are Jinatra trademarks** and are not covered by the
source-code license. The GPLv3 license does not grant trademark rights.
