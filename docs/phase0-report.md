# Phase 0 engineering report (provisional)

Updated 2026-11-18. This records available evidence, not a completed device
qualification. Private phone profiles, raw outputs and measurements remain in
ignored local storage. Engine behavior is decided by the engine spec.

The owner cancelled the two-week real-people study on 2026-11-18 and authorized
software implementation through later phases while phone-dependent validation
remains outstanding. No simulated participants, synthetic return rates or
invented hardware results replace that study.

## 1. Can local models translate planner requests correctly?

The frozen English corpus has 150 requests and a committed SHA-256. Strict
decoding, schema validation, engine validation and first-pass/corrected scoring
are implemented. A previous full exploratory LiteRT GPU run failed the confirmed
90% accuracy and five-second latency targets. It used an older prompt; the prompt
correction did not change the gold corpus. The llama.cpp evaluation remains
partial, with repeated timeouts, and cannot qualify.

Recommendation: keep AI optional and offer no qualified model/tier until complete
comparative runs use the same corrected prompt, model pins and configuration.
Do not tune gold answers to a model. Current qualifying tiers: **zero**.

## 2. What are speed, battery and heat costs on real phones?

The benchmark records loading, actual decode tokens, sampled process PSS,
charge-counter/percentage changes and battery-sensor temperature over a requested
600 seconds. It distinguishes failed, cancelled and completed runs. One unplugged
llama.cpp CPU pilot completed; a LiteRT CPU attempt stopped at import setup, and
the LiteRT GPU ten-minute pilot has not run. Import cancellation cleanup and
diagnostics were subsequently fixed and tested in software.

Recommendation: retain the completed pilot privately as a single trial, not a
tier boundary or repeated performance estimate. Finish the CPU/GPU pilots and
repeat across the requested roughly 6 GB, 8 GB and 12 GB+ device tiers. Battery
temperature is not CPU or surface temperature. Emulator performance is not phone
evidence.

## 3. Which runtime should be selected?

Adapters exist for three explicit backends: LiteRT-LM CPU, LiteRT-LM GPU and
llama.cpp CPU. Short constrained-JSON controls previously ran on all three.
The pinned compatibility baselines use Qwen2.5 1.5B with different quantizations
(GGUF Q4_K_M versus LiteRT Q8), so the current evidence is not an isolated runtime
comparison. There is no silent fallback.

Recommendation: retain the interface and both runtimes during implementation.
Do not recommend a winner, production catalogue or RAM boundaries before
comparable real-device measurements. Native binaries/models remain outside the
production no-AI path unless the corresponding feature needs them.

## 4. Is the greedy scheduling engine sufficient?

All **30** worked examples pass, with every block and its change metadata checked.
The engine has **75** tests including guarantees exercised over generated valid
days: deterministic behavior, occupied-block protection, valid placement,
idempotence and exact undo. The Today prototype exercises review/apply/undo.
These establish conformance to v0.3, not human satisfaction with real schedules.

Recommendation: use the specified greedy engine as the production foundation.
Only its proposals change planned times. Retain exact persistent ChangeLog
snapshots and stale-change protection; add solver complexity only for concrete
counterexamples, without rewriting example expectations.

## 5. Is a phone-sized companion worth returning to?

The plain companion probe exists with explicit facts, encrypted local retention,
bounded transcript, deletion and cancellation. Five prompt/data unit tests and
eight Android storage/lifecycle tests pass. Fake-runtime tests verify software,
not model quality or speed. Actual probe inference still needs a phone/model
trial. The real-people study and its return-rate criterion were **cancelled by
the owner**, not passed.

Recommendation: continue software implementation with clear AI limitations.
The confirmed ten-token/second target still requires hardware measurement.
Conversation usefulness and unprompted return remain unmeasured; do not publish
a return-rate or preference claim.

## Implementation versus release

The owner authorized continuing the software beyond Phase 0 without the phone.
That is permission to implement, not evidence that device or release gates pass.
Keep physical reliability, power/thermal measurements, model qualification,
accessibility on hardware, external service configuration and required human
reviews explicitly outstanding. Existing later-phase release criteria remain
unless the owner changes them separately.
