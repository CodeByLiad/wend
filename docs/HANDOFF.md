# Wend handoff

Updated: 2026-11-12 by Codex. This is shared working context for Claude Code and
Codex. Read `AGENTS.md` fully first. The engine spec decides engine behaviour;
the Phase 0 PRD decides scope. Verify this snapshot against Git before acting.

## Repository and branch

- Public repository: https://github.com/CodeByLiad/wend
- Current working branch: `feat/eval`.
- `main` contains the completed engine work, through `37f1f58`.
- PR #1: https://github.com/CodeByLiad/wend/pull/1 — `feat/benchmark` into `main`.
- PR #2: https://github.com/CodeByLiad/wend/pull/2 — `feat/eval` into `feat/benchmark`.
- These PRs are stacked and open. `feat/eval` contains all current prototypes.
  Preserve the stack when updating branches; inspect current PR state first.
- Last implementation commit before this handoff: `433bb6a`; initial shared
  handoff commit: `0a7532f`.

At home, clone the working branch before starting Claude Code:

```sh
git clone --branch feat/eval https://github.com/CodeByLiad/wend.git
cd wend
```

For an existing checkout, inspect `git status`, fetch, switch to the correct
branch and pull with `--ff-only` when clean. Do not overwrite uncommitted work
or resolve divergent branches by resetting. `CLAUDE.md` imports the shared rules
and this file; `AGENTS.md` also tells Codex to read this handoff.

## What is built

- `:core:engine`: pure Kotlin/JVM deterministic scheduling engine. All ten
  commands, typed validation, proposals with exact previous Day, and undo.
  Engine-spec v0.3 is authoritative; the end tables resolve earlier reviews.
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
- Gradle wrapper/catalog, Spotless/ktlint, Kotest, GPLv3, trademark notice,
  contribution files and GitHub Actions are set up. CI publishes a prototype
  benchmark APK artifact. There is no production app, database or companion.

## Verified state

- `build test :core:engine:test lint spotlessCheck` passed locally at `433bb6a`.
- 106 JVM tests pass: 75 engine, 8 benchmark, 23 evaluation. The engine tests
  include all 30 worked examples, full-block assertions and property tests.
  No engine example expectation was changed in this work.
- Both GitHub push and PR checks passed for `433bb6a` before the handoff changes.
- Android smoke checks passed for exact-byte import/hash, recognized model
  suffixes, rejection of invalid native models and loading the frozen corpus.
  Candidate inference is skipped unless instrumentation arguments select it.
- Short constrained-JSON runtime controls passed for all three backends.
- A complete 150-case exploratory LiteRT GPU run failed the confirmed accuracy
  and latency targets. An initial llama.cpp full-prompt run was stopped after
  repeated 30-second timeouts and remains partial, not an accuracy result.
- Device testing stopped for the day at the owner's request. The unplugged
  ten-minute llama.cpp CPU pilot completed successfully; its private report
  was retained. The LiteRT CPU attempt failed before inference: the UI test
  found no imported model at its `assertNotNull`, so no timed result exists.
  The LiteRT GPU ten-minute pilot was not started. Do not report either as a
  measured performance failure. Phone cleanup is verified: the AI test package,
  benchmark app and benchmark test package were uninstalled; their private
  model copies and explicit temporary staging files were removed. No temporary
  test package remains installed.

Private profiles and raw measurements are in ignored local `.gradle/evaluation`
on the testing workstation. Model binaries are in ignored `.gradle/models`.
They do not arrive with a clone. Public notes intentionally omit identifying
device details and private measurement files. No model/tier has qualified.

## Confirmed decisions and important details

- The owner confirmed 90% correct commands, under five seconds to proposal and
  at least ten generated tokens/second on 2026-11-12. The people-test return
  threshold remains proposed. Phase 0 is not complete.
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
   cause of the missing imported model is not established; inspect lifecycle
   cancellation/foreground state and add useful test diagnostics if needed.
2. Review the stacked PRs and verify their latest checks before changing bases.
3. Run corrected-prompt comparative evaluations and test further compatible,
   current models. Keep the 150 requests frozen and record source/configuration.
4. Obtain the remaining RAM/device tiers and repeated trials. One device and
   one pilot per runtime cannot establish RAM boundaries or model selection.
5. The Phase 0 companion probe, two-week people study and final five-question
   feasibility report remain outstanding. Do not start Phase 1 features.

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

2026-11-12 — Codex: built the frozen evaluation harness and device automation;
implementation commits `928a6d0`, `6bd6437`, `0b4cff6`, `5f47cfa`, `433bb6a`.
All required software checks pass. One physical pilot completed, a second
attempt stopped at import setup, and further device testing was deferred by
the owner for a Claude Code handoff. Keep the frozen gold data unchanged.
