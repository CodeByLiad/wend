# Wend by Jinatra

Wend is a free, open-source Android day planner that rebuilds a plan when life
gets in the way. Personal data stays on the phone. This Phase 0 repository
currently contains only the pure Kotlin/JVM scheduling engine; no Android app,
database, UI or AI is included.

Licensed under GPLv3; see LICENSE. The Wend name and logo are Jinatra trademarks
and are not covered by that license.

Requires JDK 21. Run `./gradlew build`, `./gradlew test`,
`./gradlew :core:engine:test`, and `./gradlew spotlessCheck`.
On Windows use `gradlew.bat`. Format with `./gradlew spotlessApply`.
Android lint will be added when an Android module exists.

The authoritative behavior and examples are in [the engine spec](docs/engine-spec.md).
