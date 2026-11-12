# Wend by Jinatra

[![Engine checks](https://github.com/CodeByLiad/wend/actions/workflows/engine.yml/badge.svg)](https://github.com/CodeByLiad/wend/actions/workflows/engine.yml)
[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)

Wend is a free, open-source Android day planner that plans and tracks your day
and rebuilds the plan when life gets in the way. The product is designed to keep
personal data on your phone, with optional on-device AI planned for later work.

**Current status: Phase 0 — engine and model benchmark prototypes.** The scheduling
engine is a pure Kotlin/JVM module. A separate Android benchmark app compares local
LiteRT-LM and llama.cpp inference. The production planner app, database, reminders,
AI planner and companion are not implemented yet.

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

Install **JDK 21** and the Android SDK (platform 37.0, NDK 28.2.13676358 and CMake
3.22.1), set `ANDROID_HOME`, and clone the repository. The checked-in Gradle wrapper downloads
the build tools; a separate Gradle installation is not needed.

```sh
git clone https://github.com/CodeByLiad/wend.git
cd wend
./gradlew build
./gradlew test
./gradlew :core:engine:test
./gradlew lint
./gradlew spotlessCheck
```

On Windows, replace `./gradlew` with `.\gradlew.bat`. To format Kotlin sources
and build scripts, run `./gradlew spotlessApply`. Engine-only tests still run without
compiling the native runtimes.

Tests include all 30 worked examples, full-state assertions for unchanged blocks,
randomized checks for every engine guarantee, and focused command/validation
regressions. HTML test results are generated at
`core/engine/build/reports/tests/test/index.html`.

## Repository guide

| Path | Contents |
| --- | --- |
| [`core/engine`](core/engine) | Pure Kotlin engine and Kotest tests |
| [`core/ai`](core/ai) | Local model import, LiteRT-LM and pinned llama.cpp adapters |
| [`tools/benchmark`](tools/benchmark) | Bare Android model benchmark app |
| [`docs/benchmark-protocol.md`](docs/benchmark-protocol.md) | Measurement definitions, device protocol and build instructions |
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
and focused proposals. The benchmark debug APK is available as a GitHub Actions
artifact after checks pass; it is an internal Phase 0 tool, not a production release.

## License and trademarks

Wend's source code is licensed under the **GNU General Public License, version 3**.
See [LICENSE](LICENSE) for the complete text.

The **Wend name and logo are Jinatra trademarks** and are not covered by the
source-code license. The GPLv3 license does not grant trademark rights.
