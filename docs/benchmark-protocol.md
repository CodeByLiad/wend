# Phase 0 model benchmark

## What is implemented

`:tools:benchmark` is an isolated Android prototype using `:core:ai` runtime
adapters. It does not contain the production day-planner UI, AI planner, database,
downloader or companion. It never edits schedules.

The production package ID remains `com.jinatra.wend`. This disposable tool uses
`com.jinatra.wend.benchmark` so it cannot replace a future production installation.
Minimum Android is API 28; target/compile SDK is the stable Android 17/API 37.
ARM64 and x86-64 native libraries are packaged. There are no Google Play services
or Internet permissions, so no gms-specific behavior is needed in this tool.

Choose LiteRT-LM CPU, LiteRT-LM GPU, or llama.cpp CPU; import a compatible model
through Android's file picker; then run the ten-minute test. Models are copied
to private app storage and SHA-256 hashed. No model URLs or catalogue choices are
implied by the file picker. Model format, conversion and license must be checked
before a real experiment.

The app stores JSON results privately and exports them only when the tester picks
a destination. It does not store prompts or generated text. The fixed workload is
the `organize-day-v1` prompt in `strings.xml`, greedy sampling, a requested 2048-token context,
up to 128 generated tokens per fresh conversation and four CPU threads.

## Runtime revisions

- LiteRT-LM Android `0.19.1`, using the official Maven artifact and its native
  benchmark counters. CPU and GPU are separate selections; there is no automatic
  fallback that could mislabel a result.
- llama.cpp commit `93cf736a8d1d46aa922e27eb64aa712bcd5ebc30`, fetched at build time
  from a pinned archive verified by SHA-256. The adapter uses JNI and CPU inference.
  Its native code is compiled with release optimization even in a debug APK.
- Runtime license texts are included as APK assets. Wend's own source remains GPLv3.

References: [LiteRT-LM Kotlin API](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md),
[llama.cpp Android build](https://github.com/ggml-org/llama.cpp/blob/93cf736a8d1d46aa922e27eb64aa712bcd5ebc30/docs/android.md).

## Measurement definitions

| Value | Definition and limitation |
| --- | --- |
| Load time | Monotonic elapsed time for model and inference-engine initialization; excludes file import |
| Decode tokens/second | Sum of completed generations' actual output tokens divided by their summed decode time; never chunk or word counts |
| Peak RAM | Maximum process PSS sampled with `Debug.getMemoryInfo` once per second, starting before model load; includes app overhead and can miss brief peaks |
| Battery drain | Start/end charge-counter difference in microamp-hours and battery-level difference in percentage points; null if charging was observed, unavailable, or readings increased |
| Temperature | Maximum reported battery-sensor Celsius; explicitly not CPU, ambient or surface temperature; unsupported values stay null |
| Thermal state | Android's qualitative thermal status at the start/end when supported; not a Celsius estimate |
| Duration | Ten minutes requested after loading; actual monotonic duration is exported, because a native operation may finish shortly after the deadline |

The model remains loaded throughout the run. Every generation starts a fresh
conversation/KV state. The output-token cap bounds work between cancellation checks.
The current native operation may need to return before cleanup finishes. Leaving
the screen cancels the run; rotation retains it. The display is kept awake during
foreground use. There is no background service.

Completed, cancelled and failed results are distinct. Partial runs do not count as
ten-minute measurements. Models that fail to load or generate do not get invented
throughput values. A result with no successful generation is failed.

GPU caches use a new app cache directory per run, removed afterward. OS file caches
cannot be controlled by an ordinary Android app and are explicitly marked
uncontrolled in the export. Record cold/warm conditions externally; do not claim
that a fresh app cache also clears the OS page cache.

LiteRT-LM applies the model's conversation template, whereas the llama.cpp speed
probe tokenizes the fixed text directly. Runtime tokenizers and compiled model
formats can differ. Record the original model/version, quantization and conversion
settings with each comparison. This tool measures performance, not command accuracy.

## Real-phone test protocol (pending devices)

The PRD calls for roughly 6 GB, 8 GB and 12 GB+ phones, mixing Samsung, Xiaomi and
a Pixel or similar. No physical phone was connected when implementation began.
Emulator smoke tests validate software operation; they are not device-performance
or battery evidence.

Before measuring candidate models, agree the candidate files, conversions and
licenses, and confirm the PRD's proposed thresholds: 90% planner accuracy, under
five seconds to proposal, and at least ten chat tokens/second. Planner accuracy and
proposal latency require the separate 150-request evaluation harness.
Freeze that 150-request English set before testing candidates, as required by the
PRD. Some compiled LiteRT models fix their context size; record that conversion
setting rather than assuming the requested context overrides it.

For each real device/runtime/model combination:

1. Record phone model, RAM, chipset, OS, runtime/backend, model SHA-256,
   quantization and model-conversion provenance. Exported JSON captures device
   and runtime identity, RAM, model hash and test configuration automatically.
2. Install the APK, import the matching `.litertlm` or `.gguf` file and select its
   runtime. Keep sufficient free storage for the private model copy.
3. Unplug charging, close other workloads, use consistent screen brightness and
   power mode, and let the phone return to a comparable baseline temperature.
4. Run all ten minutes in the foreground, export JSON, and retain failed/partial
   records separately. Check charging and unavailable sensor fields before using
   a battery result. Repeat trials and report variability rather than one number.
5. Compare compatible versions of the same source model across runtimes. Do not
   infer CPU or surface temperature from the battery sensor.

## Automated and manual verification

Automated JVM tests cover the ten-minute constants, weighted token throughput,
explicit load/generation failure, cancellation cleanup, missing sensors, charging
and exact battery units. Existing engine tests remain unchanged.

Android instrumentation smoke tests cover exact-byte model import/SHA-256 and
loading each native library with an invalid model that must be rejected without
crashing. Both ABI builds are compiled; a local x86-64 emulator can exercise native
loading. No valid candidate-model performance numbers are claimed from these tests.

Verified locally on 2026-11-12: `build`, `test`, `lint` and `spotlessCheck` passed;
all 83 JVM tests (including the unchanged 75 engine tests) and all three Android
smoke tests passed. The isolated API 36 emulator also launched the APK and exposed
all controls through scrolling in dark mode with 200% font scaling. The merged
debug/release manifests request no Internet permission. Runtime license assets
and ARM64/x86-64 native libraries were verified inside the APK.

Manual acceptance on a physical phone:

- Import and replace compatible model files; verify an inaccessible/empty file
  produces a clear error and leaves no partial model copy.
- Complete a ten-minute run for each available backend, export JSON and check the
  phone, hash, timing units and charging/sensor flags.
- Stop during loading and during generation, rotate, background and return; verify
  work stops or survives rotation as described and another run can begin.
- Test invalid model/backend pairs; require failure rather than fallback.
- Check text at 200% font scale, dark mode and RTL layout; controls must remain
  readable, scrollable and at least 48 dp.
- Confirm no prompts, generated output or model paths appear in release logs or
  result JSON, and the merged manifest requests no Internet permission.

## Build

JDK 21, Android SDK 37.0, NDK 28.2.13676358 and CMake 3.22.1 are required. Point
`ANDROID_HOME` at the SDK; do not commit `local.properties`.

```sh
./gradlew build test lint spotlessCheck
./gradlew :tools:benchmark:assembleDebug
./gradlew :core:ai:connectedDebugAndroidTest
```

The APK is at `tools/benchmark/build/outputs/apk/debug/benchmark-debug.apk`.
GitHub Actions publishes that prototype APK as a build artifact after checks pass.
It is not a production release.
