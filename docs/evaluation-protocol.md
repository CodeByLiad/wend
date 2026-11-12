# Phase 0 planner evaluation

The 150 synthetic English requests in
`tools/eval/src/main/resources/phase0-en-v1.json` were authored before candidate
model testing. The adjacent SHA-256 file freezes their bytes. Fixtures and the
prompt contract live in `:tools:eval`; record the Git revision with every run.
Never tune these requests or expected answers to a candidate's output. Corpus
revisions require a new corpus version and a fresh run for every compared
candidate. Prompt/runtime revisions must be identified by Git revision; compare
candidates only after rerunning them with the same prompt and configuration.

## Confirmed targets

The project owner confirmed 90% correct decisions, proposals under five seconds,
and companion generation at least ten tokens per second on 2026-11-12.
Planner timing includes inference, decoding and engine proposal creation, plus
any correction. It excludes model loading. This headless measurement excludes
rendering the proposal card, which still needs a later UI measurement. Report
median, p95 and maximum. Conservatively, the five-second gate applies to every
case; this is a measurement policy, not evidence of UI performance.

## Corpus and scoring

Coverage: 12 late starts, 10 rebuilds, 12 overruns, 10 retries, 18 moves, 12 skips,
16 additions, 10 fixed-block conversions, 10 bedtime changes, 8 undos,
8 read-only explanations, 16 clarification cases and 8 refusals. The fixtures
are synthetic office, student and Saturday days, including running activities,
previous-day history and duplicate activity names.

The planner returns one strict JSON object. `Prompts.kt` defines the wire
contract. Time fields are integer minutes after midnight. They retain the exact
requested value, including off-grid values: the engine decides whether a
command is legal. A correctly translated command can correctly produce a
rejected or infeasible proposal. No scheduling logic is duplicated in the model.
Retry wording asks for reconsideration without promising that skipped blocks
can return. The engine rejects that target status. Earlier prototype prompt
revisions incorrectly described skipped blocks as restorable; retain their
source revision with those exploratory measurements and do not mix them into
comparisons using the corrected prompt. The frozen gold cases are unchanged.

`clarify`, `refuse` and `explain_day` are harness decisions, not engine commands.
Explanation cases measure intent selection, not prose quality. Future-day
requests require date clarification; routines and unsupported operations are
refused. Missing add duration requires clarification. In this test contract,
additions default to flexible, normal priority, and minimum equal to duration.
Titles use sentence case, retaining explicit capitalization. These are prototype
policies, not decisions about the Phase 1 product.

The caller supplies the new activity UUID. Compare decoded Kotlin commands and
all their fields exactly, then validate their proposal status using the real
engine. Formatting and JSON field order do not affect correctness. Unknown IDs,
extra fields, duplicate JSON keys, prose wrappers and coercions are errors.

One correction is allowed only after a decoding/schema error. It receives an
error code, never the expected answer. A semantically wrong but valid command
gets no gold-assisted second attempt. Report first-pass and corrected accuracy
separately. Missing requests and failures stay in the denominator of 150; a
partial run cannot pass. Correction latency is cumulative. Invalid requested
commands are deliberately preserved rather than silently repaired.

## Host commands

```
./gradlew :tools:eval:test
./gradlew :tools:eval:run --args="validate"
./gradlew :tools:eval:run --args="prompts /absolute/private/path/prompts.jsonl"
./gradlew :tools:eval:run --args="score /absolute/private/path/attempts.jsonl /absolute/private/path/report.json"
```

Input JSONL records contain `id`, `response` (string or null for no output),
`elapsedNanos`, and optionally `correction` with `correctionNanos`. Duplicate or
unknown request IDs fail the run. Imported timings are externally supplied and
must not be presented as verified physical-device measurements. A perfect gold
replay tests the scorer only; it is never a model result.

Reports omit raw responses and requests. Keep physical device profiles, reports
and model files private and outside Git. Record model revision and SHA-256,
runtime revision, backend, constraints mode, context/output limits, Git revision,
corpus hash, device profile, and thermal/charging conditions with actual runs.
Never infer a RAM tier from one phone. Actual candidate runs and the other RAM
tiers remain pending until measurements exist.

## Android execution

`core:ai` has a test-only dependency on the harness. Normal connected tests
validate corpus loading and skip candidate inference. Explicitly selecting
`PlannerEvaluationTest#evaluateImportedCandidate` requires instrumentation
arguments `model` (a filename under the test package's private `files/models`),
`sha256`, `backend`, `gitRevision` and `modelRevision`. Both revisions are full
40-character Git hashes. Hash verification precedes loading. Use the target
package reported by the test manifest, not the production package.

The runner uses a fresh conversation per request, context limit 4096, output
limit 512, greedy sampling, and JSON-object syntax constraints. Command-schema
validation remains in Kotlin. Constraint initialization failure is a failed run;
there is no silent unconstrained fallback. Native chat templates must be
supported by the pinned runtime or loading/generation fails explicitly.

Inference times out after 30 seconds with cancellation and a ten-second grace
period. Optional `controlOnly=true` runs one short JSON control request with a
60-second timeout and 128 output-token limit. Its report is labeled
`RUNTIME_CONTROL_ONLY`; it never produces planner accuracy or qualification.
This distinguishes basic runtime operation from full-context performance.

A normal evaluation uses a ten-second cancellation grace
period. A runtime that does not stop aborts the run as partial. A private JSON
checkpoint is replaced after every case. Reports contain model/runtime pins and
the device profile; raw outputs are discarded after scoring. Export the report
privately, then uninstall the temporary test package and its model files.

`tools/eval/candidates.json` pins two initial compatibility baselines: Qwen2.5
1.5B Instruct GGUF Q4_K_M and its community LiteRT Q8 conversion. The different
quantizations must be reported; this is not an isolated runtime comparison or a
final model catalogue. Repository metadata supplies byte counts and LFS SHA-256
pins. The host-only `Prepare-Candidate.ps1 -CandidateKey KEY` downloads the exact
Hugging Face revision into ignored `.gradle/models`, resumes partial transfers,
and verifies length and SHA-256. It does not install or run anything on a phone.
