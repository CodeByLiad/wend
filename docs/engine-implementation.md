# Phase 0 engine implementation

## API and scope

`SchedulingEngine.propose(day, command, now, settings)` is the entry point in
`com.jinatra.wend.engine`. It returns a `Proposal` without applying it or mutating
the input. `previousDay` is the exact input Day. The caller owns application,
persistence and undo history.

This is one pure Kotlin/JVM module. No Android plugin, UI, database, networking,
clock access or AI is included. Times follow spec v0.3's `Minute` representation,
not the superseded LocalTime requirement. `RetryBlock` implements `retry_block`,
which replaces the older `mark_missed` command.

The rebuild functions correspond to the spec's steps: `expireFixedBlocks`,
`windowCheck`, `order`, `dependencies`, and `placeEachBlock`. Placement is split
into `keep`, `later`, `earlier`, `shortenItsDependency`, `shrink` and `giveUp`.
Working collections are local to a single proposal.

## Approved specification clarification

Moving a prerequisite must end at or before the start of every occupying block
that depends on it. Otherwise `move_block` returns `DEPENDENCY_VIOLATION` without
changing the Day. This check runs at the dependency-validation stage, before the
occupying-overlap check. Equality is accepted because intervals are half-open.

This prevents a pinned prerequisite from being moved after its completed dependent,
then moving again when the pin disappears on the next rebuild. The command table
in the spec includes the approved rule. A regression checks DONE, IN_PROGRESS and
PLANNED FIXED dependents, rejection without changes, and the touching boundary.

## Minor implementation choices

- Enum values use the spec's uppercase names. Kotlin command classes use PascalCase
  (`RebuildDay`, `RetryBlock`, etc.); there is no serialization layer in this phase.
- A status-only retry may produce a change with an empty type set and reason
  `RETRIED`: no listed change type describes becoming PLANNED, and no invented
  type is added. Other types still record any resulting interval changes.
- Missing window bounds resolve to 0 and 2880. Relative bounds are resolved against
  the current bedtime and validated in the same way as absolute times.
- Validation returns deterministic lists of typed errors. Input-Day errors are
  reported before command errors. Move preconditions use the specified first-failure
  order. Invalid status for retry, skip or addition uses `INVALID_TARGET`.
- Conflict IDs are unique and sorted by ID. Changes preserve input block order,
  followed by newly added blocks; undo also reports removed IDs.
- New fallback candidates use the requested start or horizon start for sorting and
  placement preference. They are placed by the placement rules, rather than keeping
  an unrelated interval supplied in the new Block. If unplaced, their supplied
  interval remains available for display.
- Large target durations remain valid targets; fit comparisons avoid integer
  overflow, so an impossible full duration can shrink or fail normally.
- Stored-record timestamps and UUID enforcement belong to the later persistence
  layer. Engine IDs remain caller-supplied strings, as required by the spec.

## Acceptance coverage

| Requirement | Verification |
| --- | --- |
| JVM module, version catalog, Gradle wrapper, Kotlin, Kotest | Gradle build and test tasks |
| ktlint through Spotless | `spotlessApply` and `spotlessCheck` on sources and build scripts |
| All 30 worked examples | `ExamplesTest`, one numbered test per example; full Day and change-entry equality |
| Shared base days | `BaseDays.kt`, with immutable A/B/C fixtures |
| Occupying fields preserved | Randomized property over generated valid days and rebuild-triggering commands |
| Horizon, window, dependency, overlap and duration limits | Randomized placement property |
| Locked conflicts, including outside bedtime/windows | Randomized conflict property and examples 26–27 |
| Determinism and no input mutation | Randomized repeated-call and snapshot property; source review for I/O/clock imports |
| Rebuild idempotence | Randomized property for rebuilt OK/INFEASIBLE results; X/Y and move/dependent regressions |
| Exact undo | Randomized property, example 28, addition/removal regression |
| Typed validation and command preconditions | Table-driven invalid inputs and focused command regressions |
| Past midnight, range cap, rounding and touching boundaries | Examples 9/15 and focused boundary regressions |
| GPLv3 and trademark notice | Full LICENSE and README/CONTRIBUTING review |
| No Android, UI, database or AI | Module/dependency and source review |

The six guarantee properties check 8,500 generated cases per normal test run.
Kotest prints a seed and reduced counterexample if a property fails.

Local verification passed all 75 tests: 30 worked examples, 39 focused
command/validation tests, and six guarantee properties. `build`, `test`,
`:core:engine:test` and `spotlessCheck` all passed on JDK 21.

## Spec follow-ups

The approved dependency rule is recorded in the spec. The status-only retry type
set and new-block fallback display interval could be made explicit in a future
revision; the choices above keep this implementation deterministic without adding
new behavior or new enum values.
