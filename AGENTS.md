# AGENTS.md — Wend by Jinatra

Read this file fully before starting any task. It overrides your defaults.

## What this project is

Wend is a free, open-source Android app that plans and tracks a person's day,
rebuilds the plan when life gets in the way, and on capable phones adds an
on-device AI planner (and later a companion). All user data stays on the phone.

- Package ID: `com.jinatra.wend` (permanent — never change it)
- License: GPLv3. The Wend name and logo are Jinatra trademarks and are not covered by the license.
- Stack: Kotlin, coroutines/Flow, Jetpack Compose + Material 3, Hilt, Room on SQLCipher, WorkManager + AlarmManager
- minSdk 28 (Android 9), target the latest stable SDK
- UI language: English only (all strings in resources, RTL-safe layouts)

**Current phase: Phase 0 (feasibility spike).** Only build what the current
phase's PRD asks for. Do not start features from later phases.

## Where the truth lives

| Document | Path | Decides |
| --- | --- | --- |
| Scheduling engine spec | `docs/engine-spec.md` | Exactly how schedules change. Wins over everything for engine behaviour |
| PRD, one section per phase | `docs/PRD.md` | What to build and when it's done (acceptance criteria) |
| Technical architecture | `docs/architecture.md` | How it's built: modules, data model, runtimes, APIs |

If two documents disagree, or a document is unclear, **stop and ask**. Do not
guess and do not quietly pick one.

## Hard rules — never break these

1. **Only the engine changes a schedule.** UI, reminders and the AI planner
   send commands to `:core:engine` and apply the proposal it returns. Nothing
   else writes planned block times.
2. **The engine is pure Kotlin.** `:core:engine` is a JVM module with no Android
   imports, no I/O, no clock access (`now` is always a parameter) and no
   randomness. Same input, same output.
3. **Every schedule change is written to ChangeLog** with before/after state so
   `undo` can restore it exactly.
4. **Only three network uses exist:** model downloads (pinned Hugging Face
   URLs, verified by SHA-256), the update-manifest check, and the
   open-in-Google-Maps intent (no network call by Wend). Networking code lives
   only in `:core:ai` (downloads) and `:core:update`. No other module may
   depend on a networking library.
5. **No analytics, crash reporting, ads or tracking SDKs.** No Firebase. Never
   log user content (activity names, notes, messages) in release builds.
6. **Features never depend on each other.** `:feature:*` modules depend only on
   `:core:*` modules.
7. **Google Play services only in the `gms` build flavour.** The `foss` flavour
   must build and run without them.
8. **AI is optional.** Every action the AI planner can trigger must also be
   reachable through normal UI. The app must work fully with no model installed.
9. **No secrets in the repo.** Never commit signing keys, keystores, tokens or
   `local.properties`.

## Module layout

```
:app                      phone app shell (flavours: foss, gms)
:core:engine              scheduling engine (pure Kotlin/JVM)
:core:data                Room + SQLCipher, repositories, ChangeLog
:core:ai                  LlmEngine interface, runtimes, model manager, downloads
:core:reminders           alarms, WorkManager jobs, notification channels
:core:export              encrypted archive export/import
:core:update              signed update-manifest check (direct-download builds)
:core:designsystem        Compose theme and shared components
:feature:today            Today screen
:feature:planner          routines, activities, adaptive scheduling UI
:feature:habits           habits
:feature:tracking         manual trackers
:feature:aiplanner        AI planner screen and proposal cards
:feature:settings         settings, data, about
:tools:benchmark          Phase 0 only: model benchmark app
:tools:eval               Phase 0 only: AI planner evaluation harness
```

Create modules only when the current phase needs them.

## Testing rules

- The 30 worked examples in `docs/engine-spec.md` are tests in
  `:core:engine`, one test per example, named `example01_…` to `example30_…`.
- Each test asserts the proposal status and, for every block, times, duration,
  status, change type and reason. Blocks not listed in an example must be
  asserted unchanged.
- **Never edit an example test to make code pass.** If code and an example
  disagree, fix the code. If you believe the example is wrong, stop and ask.
- The engine guarantees in the spec are covered by property-based tests
  (Kotest property testing) over randomly generated days.
- Every acceptance criterion you implement gets at least one test, or a written
  manual test step in the PR description when it can't be automated.

## Commands

Set these up in Phase 0 and keep them working:

```
./gradlew build                 # compile everything
./gradlew test                  # all unit tests
./gradlew :core:engine:test     # engine tests only (fast, run often)
./gradlew lint                  # Android lint (once an Android module exists)
./gradlew spotlessCheck         # formatting (ktlint)
./gradlew spotlessApply         # fix formatting
```

## Code conventions

- Kotlin official style, enforced by ktlint through Spotless.
- Immutable data classes for models; `sealed` types for statuses, change types and reason codes, matching the names in the engine spec exactly.
- All IDs are UUIDs; every stored record has `createdAt` and `updatedAt` (persistence layer only; pure engine models carry no timestamps).
- All user-facing text in `strings.xml`, never hard-coded.
- Compose: stateless composables with state hoisted to ViewModels; previews for every screen in light and dark.
- Accessibility: content descriptions on icons, touch targets ≥ 48 dp, support font scaling to 200 %, never use colour as the only signal.

## Working style

- One task per branch. Small commits using Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `chore:`).
- Before finishing a task: `build`, `test` and `spotlessCheck` pass, plus `lint` once an Android module exists.
- In the PR description, list which acceptance criteria or engine examples the change covers.
- If behaviour changes, update the relevant doc in `docs/` in the same PR.
- When a requirement is ambiguous, ask instead of inventing behaviour.

## Continuity between agents and machines

- At session start, inspect the working tree and fetch the remote. When the current tracking branch and working tree are clean, update with `git pull --ff-only`. Preserve local changes and divergent commits; never reset or force-push to synchronize notes.
- Read `docs/HANDOFF.md` after synchronizing. If the pull changed this file or project rules, re-read the updated instructions before implementation. The handoff records context; the engine spec and PRD still decide behaviour and scope.
- Before handing work back, update `docs/HANDOFF.md` with the branch, relevant commits, completed work, exact checks, unfinished work and the next concrete steps. Commit and push those notes with the work when authorized. Keep credentials, personal device profiles and raw private results out of the public repository.
