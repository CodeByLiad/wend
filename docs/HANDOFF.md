# Wend handoff

Updated: 2026-11-18 by Codex, after the production data foundation and approved engine edit extension. This is shared working context for Claude Code and
Codex. Read `AGENTS.md` fully first. The engine spec decides engine behaviour;
the PRD plus the owner implementation directive decide scope. Verify this snapshot against Git before acting.

## Repository and branch

- Public repository: https://github.com/CodeByLiad/wend
- Current working branch: `codex/engine-edit`, stacked on
  `codex/production-data`, for the approved engine edit command.
- PR #7: https://github.com/CodeByLiad/wend/pull/7 - `codex/production-data`
  into `codex/companion-probe`.
- PR #6: https://github.com/CodeByLiad/wend/pull/6 - `codex/companion-probe`
  into `codex/benchmark-import`.
- PR #5: https://github.com/CodeByLiad/wend/pull/5 — `codex/benchmark-import`
  into `codex/prototype-followup`.
- PR #4: https://github.com/CodeByLiad/wend/pull/4 — `codex/prototype-followup`
  into `feat/design-system`.
- PR #3: https://github.com/CodeByLiad/wend/pull/3 — `feat/design-system` into
  `feat/eval` (draft; see the UI prototype section below).
- `main` contains the completed engine work, through `37f1f58`.
- PR #1: https://github.com/CodeByLiad/wend/pull/1 — `feat/benchmark` into `main`.
- PR #2: https://github.com/CodeByLiad/wend/pull/2 — `feat/eval` into `feat/benchmark`.
- These PRs are stacked and open. The active follow-up branch contains the full
  current prototype stack.
  Preserve the stack when updating branches; inspect current PR state first.
- Latest implementation commits: `f1c307e` (bedtime request preservation),
  `dfa44ce` (wake-time-only changes) and `6136f1b` (prototype APK delivery).
  Import follow-up: `3adff91` (cancellation cleanup/diagnostics/regressions),
  `5d12f71` (CI compiles instrumentation tests).
  Claude's weekend branch ended at `4cfa00a`.
  Initial shared handoff commit: `0a7532f`.

At home, clone the working branch before starting Claude Code:

```sh
git clone --branch codex/engine-edit https://github.com/CodeByLiad/wend.git
cd wend
```

For an existing checkout, inspect `git status`, fetch, switch to the correct
branch and pull with `--ff-only` when clean. Do not overwrite uncommitted work
or resolve divergent branches by resetting. `CLAUDE.md` imports the shared rules
and this file; `AGENTS.md` also tells Codex to read this handoff.

## What is built

- `:core:engine`: pure Kotlin/JVM deterministic scheduling engine. All eleven
  commands, typed validation, proposals with exact previous Day, and undo.
  Engine-spec v0.4 is authoritative; the end tables resolve earlier reviews.
  See `docs/engine-implementation.md` for API and implementation choices.
- `:core:ai`: local model import and runtime adapters for LiteRT-LM CPU/GPU and
  pinned llama.cpp CPU. Native chat templates, UTF-8 text completion, configurable
  context/output limits, cancellation, JSON syntax constraints and real token
  counters. No downloader or production AI feature is implemented.
- `:tools:benchmark`: disposable Android foreground ten-minute benchmark,
  private JSON results/export, RAM sampling, battery/temperature measurements,
  and opt-in `DeviceBenchmarkTest` driving the real Activity/ViewModel.
- `:tools:eval`: JVM CLI and reusable evaluation code, strict command decoder,
  scorer, fixtures, prompt and 150 frozen synthetic English requests. The
  `core:ai` Android tests provide opt-in on-device inference.
- `:tools:companion`: plain research chat and explicit saved facts, encrypted
  local snapshots, bounded context, draft/section continuity and cancellation.
  No real-model qualification or people-study result is claimed.
- Gradle wrapper/catalog, Spotless/ktlint, Kotest, GPLv3, trademark notice,
  contribution files and GitHub Actions are set up. CI publishes a prototype
  benchmark, FOSS prototype and companion probe APK artifacts. The app shell, Today screen and
  design system are synthetic in-memory prototypes. Production encrypted
  storage now exists in :core:data but is not yet connected to the app.
  There is no production companion.

## Verified state

- `build test :core:engine:test lint spotlessCheck` and the companion
  instrumentation APK compilation passed locally for the implementation in
  `e073f41` / `541a376`, on 2026-11-18.
- 135 distinct JVM tests pass: 75 engine, 8 benchmark, 23 evaluation, 24 Today
  and 5 companion. Eight companion Android tests passed on the isolated emulator.
  The engine tests
  include all 30 worked examples, full-block assertions and property tests.
  No engine example expectation was changed in this work.
- Both GitHub push and PR checks passed for `433bb6a` before the handoff changes.
- Both GitHub checks for import follow-up `1e2fe3d` were verified green on
  2026-11-18. The new companion PR's checks can be inspected through PR #6.
- Both GitHub push and PR checks passed for prototype follow-up `02ba716`;
  its benchmark and FOSS app APK artifacts were verified.
- Android smoke checks passed for exact-byte import/hash, recognized model
  suffixes, rejection of invalid native models and loading the frozen corpus.
  Candidate inference is skipped unless instrumentation arguments select it.
- Short constrained-JSON runtime controls passed for all three backends.
- A complete 150-case exploratory LiteRT GPU run failed the confirmed accuracy
  and latency targets. An initial llama.cpp full-prompt run was stopped after
  repeated 30-second timeouts and remains partial, not an accuracy result.
- On 2026-11-12, device testing stopped at the owner's request. The unplugged
  ten-minute llama.cpp CPU pilot completed successfully; its private report
  was retained. The LiteRT CPU attempt failed before inference: the UI test
  found no imported model at its `assertNotNull`, so no timed result exists.
  The LiteRT GPU ten-minute pilot was not started. Do not report either as a
  measured performance failure. The 2026-11-12 phone cleanup was verified: the AI test package,
  benchmark app and benchmark test package were uninstalled; their private
  model copies and explicit temporary staging files were removed. No temporary
  test package remained installed then. Benchmark packages and a staged model
  were reinstalled on 2026-11-15; their latest cleanup remains unverified while
  the phone is unavailable (see the companion follow-up).

Private profiles and raw measurements are in ignored local `.gradle/evaluation`
on the testing workstation. Model binaries are in ignored `.gradle/models`.
They do not arrive with a clone. Public notes intentionally omit identifying
device details and private measurement files. No model/tier has qualified.

## Confirmed decisions and important details

- The owner confirmed 90% correct commands, under five seconds to proposal and
  at least ten generated tokens/second on 2026-11-12. The people-test return
  criterion was cancelled by the owner on 2026-11-18. Phase 0 device evidence is incomplete.
- Engine times are the spec's `Minute` offsets, supporting the next day up to
  2880, rather than `LocalTime`. Do not revert this to the original v0.1 request.
- MISSED, DROPPED and UNPLACED blocks are not rebuild candidates until explicitly
  retried. `retry_block` also accepts PLANNED targets of FLEXIBLE/OPTIONAL kind.
  Skipped-block retries are rejected.
- Owner-approved move rule: reject a move ending after an occupying dependent
  starts with `DEPENDENCY_VIOLATION`. The spec and regression test include it.
- The corpus is `phase0-en-v1`; SHA-256 is
  `f3f0a414992e4a5eeea4cd9094f336ed22e6dd060ebbce4f63666883b8e60c6f`.
  It was authored, validated and committed in `928a6d0` before candidate testing.
  Do not tune its requests or gold answers to a model's output.
- Decoder comparison is strict; only one schema/decoding correction is allowed.
  Gold knowledge must never be used to correct a valid but wrong decision.
  Missing cases stay in the denominator; partial runs cannot qualify. Report
  first-pass and corrected scores separately. See the evaluation protocol.
- Headless latency excludes loading and UI rendering and includes correction,
  decoding and the engine proposal. Its conservative timing policy requires
  every case under five seconds; production card timing remains unmeasured.
- Add defaults and clarification/refusal rules are prototype test-contract
  choices, documented in `docs/evaluation-protocol.md`, not Phase 1 policy.
- The exploratory GPU run used `0b4cff6`. That prompt wrongly described skipped
  blocks as restorable; the gold case already expects rejection. `433bb6a`
  corrected the wording without changing gold data. Comparative accuracy runs
  must use the same corrected prompt and configuration for every candidate.
- Initial pinned baselines are Qwen2.5 1.5B Instruct GGUF Q4_K_M and community
  LiteRT Q8. Quantizations differ: this is not an isolated runtime comparison.
  They are compatibility baselines, not a final or comprehensive model catalogue.
- LiteRT path loading requires the correct container suffix. Import recognizes
  magic bytes and keeps opaque UUID names with `.litertlm`/`.gguf` extensions.
- Closing a LiteRT Engine after failed initialization used to fail; close only
  initialized instances. Keep the invalid-model smoke regressions.
- Both benchmark adapters now use model-native chat templates. Native code is
  optimized even in debug builds. The benchmark is unconstrained; planner
  evaluation uses JSON syntax constraints and then Kotlin schema validation.
- A ten-minute battery test needs charging disconnected and the screen open.
  Use wireless debugging; stage large model files over USB before unplugging.
  Temperature is the battery sensor; unavailable/charging drain stays null.
  Keep failed/partial records separate and remove temporary test apps afterward.

## Build and device instructions

Use JDK 21 and the SDK/NDK/CMake versions in the README and versioned build files.
Set your own `ANDROID_HOME`; local SDK paths, authentication and models are not
shared through Git. On Windows use `.\gradlew.bat` in place of `./gradlew`.

```sh
./gradlew build test :core:engine:test lint spotlessCheck
./gradlew :tools:eval:run --args="validate"
./gradlew :core:ai:assembleDebugAndroidTest
./gradlew :tools:benchmark:assembleDebugAndroidTest
```

Run only one Gradle invocation at a time in a checkout. Overlapping test tasks
caused result-file/EOF failures once; an isolated rerun passed. CI installs its
Android SDK explicitly because runner images did not provide `sdkmanager`.

Read `docs/benchmark-protocol.md` and `docs/evaluation-protocol.md` before device
runs. `tools/eval/candidates.json` pins model revisions, bytes, licenses and
SHA-256; `Prepare-Candidate.ps1 -CandidateKey KEY` downloads and verifies files.
Normal connected tests skip costly model inference without explicit arguments.

## Next work

1. Resume device work only when the owner makes a phone available again.
   Investigate/retry the LiteRT import setup, then finish CPU/GPU pilots.
   The source passed its pinned SHA check and storage was sufficient. The
   cause of the missing imported model is not established. The import follow-up
   below adds diagnostics and fixes a reproduced cancellation cleanup race;
   retry the physical pilot before attributing the original failure to it.
2. Review the stacked PRs and verify their latest checks before changing bases.
3. Run corrected-prompt comparative evaluations and test further compatible,
   current models. Keep the 150 requests frozen and record source/configuration.
4. Obtain the remaining RAM/device tiers and repeated trials. One device and
   one pilot per runtime cannot establish RAM boundaries or model selection.
5. The companion probe is built; its real-model trial, the two-week people
   study is cancelled. The provisional report records outstanding device evidence.
   The owner authorized later software work; see the production data section.

## UI prototype (2026-11-13, `feat/design-system`)

The owner asked (2026-11-13, before going offline) for UI work in an editorial
minimalism style following their sample screen (`Sample/08-stats.png`, kept
outside the repo), using Mobbin for UX references and the Impeccable skill for
UI, unattended and without questions. This is design work ahead of Phase 1 that
Phase 0's PRD otherwise excludes; it was done because the owner asked for it.
It stays a prototype: no database, onboarding, reminders, settings or AI.

What was built:

- `:core:designsystem`: `WendTheme` (light paper and dark night schemes, one
  crimson accent), Source Serif 4 display/text cuts bundled as Latin-subset
  static TTFs (OFL, licence in the module assets), platform sans for body,
  spacing steps, `Hairline`, `SectionHeading`, `StatColumn`, buttons.
- `:feature:today`: Today screen. Hero numeral = minutes left of the current
  activity (or until the next one), Done/Skipped/Left stats, Morning/Afternoon/
  Evening timeline with free gaps and wake/bedtime anchors, off-plan list with
  "Put back" (retry). Tap marks done; long press opens skip, move later, ran
  over, start, keep-at-this-time. "I'm running late" maps to `overrun` when a
  block is in progress, `late_wake` before the day starts, otherwise
  `rebuild_day` at now + delay. Proposals that change more than the target open
  a review sheet with before/after times and a plain-language reason built from
  `Reason`/`causeBlockId` (no AI); INFEASIBLE offers "Allow past bedtime".
  Undo goes through the engine's `undo` command; it stays available in the
  header after the snackbar. All logic lives in pure `TodayController` and
  `buildTodayModel`, with 20 JVM tests.
- `:app`: `com.jinatra.wend` shell with `foss` and `gms` flavours, no
  INTERNET permission, backup disabled, placeholder launcher icon (three rules on
  crimson) until Jinatra supplies the Wend logo.
- Product and design records: `PRODUCT.md` (inferred from the PRD, labelled),
  `.impeccable/surfaces/feature-today.md` (direction contract), `DESIGN.md` and
  `.impeccable/design.json` (derived from the built system), screenshots in
  `docs/design/today/`.

Deliberate prototype limits and deviations to review:

- In-memory only: the sample day is synthetic and the change log is a list in
  `TodayController`. Hard rule 3 (ChangeLog) is honoured in memory; `:core:data`
  must own the persistent ChangeLog in Phase 1.
- The prototype clock starts at 10:25 and then runs in real time so the sample
  day reads mid-morning. Replace with the system clock with real data.
- No Hilt yet (manual ViewModel factory); the AGENTS stack lists Hilt for the app.
- Marking done/started is a status-only edit applied outside the engine (no times
  change), then logged; confirm this matches the intended reading of hard rule 1.
- Bottom navigation is omitted because no other destinations exist yet.
- R8 is not enabled; release APK is about 23.5 MB unminified.
- CI follow-up completed on 2026-11-15 in `6136f1b`: feature/follow-up branches
  run checks, and the workflow uploads `wend-app-foss-debug` alongside
  `wend-benchmark-debug`. The earlier workflow-scope limitation was local to
  Claude's session; this workstation could push the workflow change.

Verification on this machine (Windows, JDK 21 Temurin 21.0.13 in `~/.jdks`,
emulator `cueplay_pixel`, API 35, x86_64, software rendering):

- `./gradlew build test :core:engine:test lint spotlessCheck` passes. 126 JVM
  tests: the existing 106 plus 20 new Today tests. No engine example changed.
- Driven on the emulator: running late → review → apply → snackbar undo; mark
  done; persistent undo after the snackbar; dark theme; system font scale 2.0.
  Emulator evidence only: gestures, performance and the 500 ms render target need
  a real phone.
- Impeccable finish review: two rounds, the unattended budget. Round 1 found 7
  material issues; round 2 scored 6 resolved, and seed recording partial, and
  found two small regressions (undo alignment, stat spacing). The seed was then
  recorded (`seed:22b0b40a26e1757e`, roll not adopted because the owner pinned
  the world) and both regressions fixed and recaptured, but no third reviewer
  verdict was run. Open ceiling ideas from the review: a day-shape track (crimson
  elapsed fill) echoing the sample's bars; captures of the Up-next, Running-over,
  Day-done heroes, the long-press sheet, skipped and off-plan rows.

Next steps for this branch: owner review of the look and the deviations above;
then fold into Phase 1 planning (`:core:data` ChangeLog, Hilt, onboarding).

## Prototype follow-up (2026-11-15)

The owner asked to fetch the latest code and continue. Codex found Claude's
new branch through the remote handoff history, preserved the PR stack and made
the follow-up in PR #4.

- Fixed **Allow past bedtime** discarding the overrun under review. A pending
  review now retains the original command, timestamp, target and settings.
  The engine repeats that command with the allowance enabled, from the original
  unchanged Day. The failed attempt's UNPLACED blocks are not committed before
  retrying. Approval and exact undo remain intact, and stale requests are rejected.
- Three regressions cover an evening overrun and its dependent, stale replay,
  and the ViewModel review → allow → approve → undo flow. No engine rules or
  worked-example expectations changed.
- Fixed a second controller issue: a changed wake time was discarded when no
  block moved. No-op detection now compares the complete Day. Under the existing
  own-edit policy, a wake-only change is applied, logged and undoable. A fourth
  regression verifies the unchanged blocks, changed wake, history and exact undo.
- Enabled the FOSS prototype APK artifact and checks on `feat/**` / `codex/**`.
  The artifact is an internal prototype, not a production release.
- Full local build, tests, engine tests, lint and formatting checks pass.
  The new bedtime edge case is covered by controller/ViewModel tests.
- API 36 isolated emulator smoke at `f02128e`: running late → 30 minutes → review → apply →
  persistent header undo restored the original times. Light review, dark Today
  and 200% text were inspected; both bottom actions remain visible at 200%.
  Both app flavours retain `com.jinatra.wend` and request no Internet permission.
  The test app was removed from the emulator and the emulator shut down. The
  subsequent wake-only correction is covered by its controller regression.
- No physical phone was used. Physical timing, model benchmarks and the other
  Phase 0 feasibility work remain pending as recorded above.

Next: review PR #4 and its APK artifact, then resume the Phase 0 evaluation
backlog when a phone is available. The owner-requested UI exception does not
advance the project to Phase 1.

## Import follow-up (2026-11-15)

The owner asked to continue until outside input was needed. The unfinished
LiteRT pilot had only reported a null model after import. This follow-up fixes
a separately reproduced cancellation race and improves the next diagnosis.

- `ModelImport.copy` keeps ownership across the IO-to-caller return. If prompt
  cancellation discards the already copied result, non-cancellable cleanup
  removes the unclaimed file, including its renamed container suffix.
- Benchmark import cancellation now has a distinct resource message. Failure
  diagnostics report backend, busy/loading/stopping flags, model presence and
  the resource error; no source paths, prompts or generated text are included.
- The ten-minute test verifies the imported copy's SHA-256 against the pinned
  candidate before inference. CI compiles both instrumentation test APKs.
- `build test :core:engine:test lint spotlessCheck` passed (130 distinct JVM
  tests); both instrumentation test APKs compile. API 36 isolated emulator:
  six AI import/runtime smoke tests and one benchmark Activity lifecycle test
  passed. Tests cover completed-copy cancellation, empty imports, retry and
  preserving a good model after failed replacement. The emulator test apps
  were uninstalled and that isolated emulator stopped.
- No engine rules, worked examples, frozen corpus or model pins changed.
- The owner reconnected a phone for the remaining pilots. The benchmark APKs
  are installed and the pinned LiteRT file was copied and verified in private
  storage. Wireless debugging required renewed authorization. No new physical
  ten-minute result exists yet; do not infer the earlier import failure's cause
  from the cancellation regression. Keep test reports and device details private.

Next: finish wireless authorization, unplug charging, retry the LiteRT CPU/GPU
pilots, retain separate reports and remove temporary phone apps/models afterward.

## Companion probe (2026-11-18)

The phone was unavailable, so physical pilots were not resumed. The owner
authorized persistent chats/facts in an encrypted local prototype file.

- Added `:tools:companion`, disposable package
  `com.jinatra.wend.companion.probe`. It does not change the permanent
  `com.jinatra.wend` package or the planner.
- Plain Material Chat / Saved facts / Model sections, explicit local imports
  and backend selection, basic bounded transcript, Stop, fact removal and
  confirmed deletion. No hidden memory, scheduler access, networking or
  production database. The AI cannot write saved facts.
- AES-256-GCM snapshots, fresh nonces, Android Keystore key, AtomicFile and
  no-backup private storage. Unreadable data disables edits rather than being
  overwritten; deletion removes the ciphertext and key.
- Unsent drafts and section selection live in ViewModel memory across tab
  changes/rotation. Failed saves retain drafts; completed saves clear them.
  Unsent drafts do not persist across process death.
- Explicit Phase 0 exception and prototype choices are in
  `docs/companion-probe.md` and `docs/architecture.md`. Production storage
  remains Room/SQLCipher. The guide includes weekly questions, a proposed
  unprompted-return definition and private/manual study handling. The proposed
  people-test threshold/definition still need confirmation before recruitment.
- Commits: e073f41 (probe implementation), 541a376 (context/storage/
  lifecycle tests), 156341f (probe artifact and instrumentation compilation).
- Exact local check:
  `spotlessApply build test :core:engine:test lint spotlessCheck :tools:companion:assembleDebugAndroidTest`
  passed. 135 distinct JVM tests (previous 130 plus five prompt/data cases);
  eight companion Android instrumentation tests passed on the isolated API 36
  emulator using real Keystore and synthetic content/fake inference or injected
  storage failures.
- Android tests cover encrypted recreation/fresh nonces, tamper rejection,
  ciphertext/key deletion, fail-closed editing, reply persistence/runtime close,
  cancellation, draft/section recreation and failed-save draft retention.
- Emulator UI: saved a synthetic fact, reopened the app, typed both drafts,
  switched sections and verified they survived. Light, dark, 200% text and
  landscape captures were inspected. A review found one draft-loss issue; the
  verdict pass scored that issue resolved. Documentation conformance preserved
  the production design files. No visual polish or new brand assets were added.
- Debug/release packages request no Internet permission. Debug screenshots use
  synthetic content; release capture protection is implemented with FLAG_SECURE.
  Manual release/TalkBack and real-model testing remain pending.
- CI now builds the probe and compiles its instrumentation APK, and publishes
  `wend-companion-probe-debug`. Compiling instrumentation is not executing it.
- Temporary companion test apps were removed from the isolated emulator and
  that emulator stopped. No physical phone was used or modified on 2026-11-18.
  The last known phone state from 2026-11-15 still had the benchmark APKs and a
  verified private LiteRT staging copy; removal has not been reverified because
  the phone was unavailable. Check and clean those temporary packages when it
  reconnects.

Next: when a phone is available, finish the LiteRT CPU/GPU pilots, test actual
probe inference with pinned compatible models and remove temporary phone apps/
copies afterward. Then complete corrected-prompt comparisons, remaining device
tiers/repeated trials and the final five-question report. The owner later
cancelled the two-week people study; do not run it.
No model/tier is qualified and Phase 0 is not complete.

## Production data foundation (2026-11-18)

The owner authorized continuing the remaining software phases without the phone
and cancelled the Phase 0 two-week real-people study. Do not recruit participants,
invent substitute results or treat that cancelled criterion as passed.
The owner delegated the Start/Done design choice: validated status-only updates
stay in the repository, never changing planned times; every change is logged.

- Added `:core:data`: Room 2.8.5 / SQLCipher 4.20.0, KSP 2.3.14 and Hilt 2.60.1
  wiring. Versioned Room schema 1 is committed.
- Normalized days/ordered blocks, exact ChangeLog before/after snapshots and
  typed engine change metadata, source, UUIDs and creation/update timestamps.
- Immutable preview capsules, transactional revision checks and engine
  recomputation prevent stale/caller-edited proposals overwriting a plan.
  Rejected/conflicting/no-horizon attempts are not written. Wake/bedtime-only
  edits are retained by comparing the full Day.
- Persistent repeated undo uses the engine and records its own transaction;
  status-only Start/Done and idempotent past-day rollover preserve all scheduling
  fields. Settings/card order persist in the encrypted database.
- Key wrapping uses Android Keystore and AES-GCM in no-backup storage. Key
  loading/native DB opening are lazy. Missing/wrong keys do not regenerate keys
  or delete the database. No destructive migration/recovery fallback; Java
  SQLCipher logging is disabled.
- Exact local command:
  `spotlessApply build test :core:engine:test lint spotlessCheck :core:data:assembleDebugAndroidTest`
  passed (140 distinct JVM tests, previous 135 plus five data/status tests).
  All ten data Android tests passed on the isolated API 36 emulator with real
  Room/SQLCipher/Keystore. They cover exact snapshots, lazy opening, encrypted
  reopen, stale approval, repeated undo, transaction rollback, wrong/missing
  keys, persistent settings and rollover.
- CI compiles the new data instrumentation APK; compilation is not execution.
- App wiring is still the synthetic prototype at this checkpoint. Next work is
  real onboarding/routines/Today persistence, not a claim that those screens are
  already production-ready. Emulator data-test package remains installed for
  continued work; clean up temporary packages/emulator before the session ends.
- No engine behavior, example expectations or frozen AI corpus changed. No
  physical phone was used. Model tiers, phone performance and other release
  evidence remain unverified.

See `docs/phase0-report.md`, `docs/IMPLEMENTATION.md` and
`docs/data-implementation.md` for evidence, current scope and API choices.


Implementation commits: e5d502f and d8f6ca8.

## Approved engine activity editing (2026-11-18)

The owner approved adding edit_block after the PRD editing requirement exposed a
missing engine command. Spec v0.4 and Command.EditBlock now replace a PLANNED
block in place while preserving its id/status/list position. Full/minimum
duration, title, kind, interval, priority, window and dependency are editable.
All validation and rebuilding remain in the pure engine. Flexible edit intervals
are preferences, not pins; fixed edits check horizon/window and occupying
dependencies, and overlapping locked intervals expose a conflict without apply.
Dormant/completed/in-progress records cannot be rewritten by this command.

EDITED / USER_EDIT explain metadata-only edits; existing interval/status types
combine normally. Undo restores all fields. Repository operation EDIT_BLOCK and
exact typed metadata persist across restart. Today maps metadata-only edits to
Edited rather than Retried, using resource copy; no editor control is exposed yet.

Verification: spotlessApply build test :core:engine:test lint spotlessCheck
passed after the final encoding correction. 156 distinct JVM tests pass (90
engine including all 30 unchanged worked examples, 25 Today, and previous 41
other tests). Six generated-day guarantee properties now also exercise edits.
Eleven actual SQLCipher/Keystore tests passed on the isolated API 36 emulator,
including persisted edit metadata and exact undo after reopen.
The source/copy-only Today review and documentation audit passed; no new screen
or visual validation is claimed. No layout/design files changed. Preexisting
PRODUCT.md phase wording is stale and was not silently repaired.

Next concrete step: persist routine/activity templates and recurrence, generate
each date once through the engine, then connect onboarding and Today to real
saved data and a real clock. The app remains the synthetic prototype until that
integration is implemented and tested. No physical phone was used.

## Session handback

Before the Saturday handback or any agent switch, update this file with what
changed, relevant implementation commits, exact checks and remaining work.
Commit and push the code and updated notes. On Sunday, Codex should fetch/pull
the clean tracking branch, read the updated rules and this file, and continue
from the recorded next step. Do not copy private agent directories or chat dumps.
For current evaluation follow-up, use `feat/eval`. If work moves to another task
branch, record its exact name and PR in the handback so the returning agent can
check out the right context. If the expected tracking branch has not changed,
inspect recent remote handoff commits and PRs before assuming no work occurred.
For the latest complete stack, use `codex/engine-edit` until
the stacked PRs are merged or the owner names a different branch.

2026-11-12 — Codex: built the frozen evaluation harness and device automation;
implementation commits `928a6d0`, `6bd6437`, `0b4cff6`, `5f47cfa`, `433bb6a`.
All required software checks pass. One physical pilot completed, a second
attempt stopped at import setup, and further device testing was deferred by
the owner for a Claude Code handoff. Keep the frozen gold data unchanged.

2026-11-13 — Claude Code: built the owner-requested editorial UI prototype on
`feat/design-system` (see "UI prototype" above). Device work from "Next work"
was not possible overnight (no phone); the evaluation items there are unchanged.

2026-11-15 — Codex: synchronized `feat/design-system` at `4cfa00a`; fixed the
review's bedtime recovery in `f1c307e`, fixed wake-only changes in `dfa44ce`,
completed CI APK delivery in `6136f1b`, and opened PR #4. 130 JVM tests and all required local checks pass; emulator
smoke checks passed. Physical device work was not resumed.
