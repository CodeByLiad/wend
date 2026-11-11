# Wend — Technical Architecture v0.1

Nov 11, 2026 · Liad Bin Aowlad

## Scope, assumptions and constraints

This architecture covers all six phases but is built for Phase 1 first: the planner, habits, manual tracking, reminders and the AI planner. Every later phase plugs into the same foundation without rewrites.

**Product:** Wend by Jinatra. **Android package ID:** `com.jinatra.wend`, set in the first commit. It can never change after release, because a new ID means existing users can't update.

The constraints every component must respect:

- **Personal data never leaves the phone.** No servers, no accounts, no analytics. The only network calls are model downloads, the optional open-in-Maps handoff, and update checks.
- **AI is a layer, not a dependency.** The app is complete without a model. The AI only runs on phones that pass a capability check.
- **Each phase ships polished.** Private beta, then public release. No phase depends on a later one to make sense.
- **Global, Android-only.** Phones first, tablets and Wear OS in Phase 4. Distributed outside the Play Store.

Decided: Phase 1 launches in English only, with the codebase ready for more languages including right-to-left scripts. Minimum Android version is 9 (API 28), the oldest version Health Connect supports. Wend is open source under GPLv3.

## Platform and stack

Native Kotlin with Jetpack Compose. Cross-platform frameworks add a layer between you and the Android APIs this app lives on: alarms, background work, Health Connect, on-device AI and Wear OS.

| Layer | Choice | Why |
| --- | --- | --- |
| Language | Kotlin, coroutines and Flow | First-class Android support, structured concurrency |
| UI | Jetpack Compose, Material 3 | One UI toolkit for phone, tablet and Wear OS (Compose for Wear) |
| Architecture | MVVM with unidirectional data flow | Predictable state, easy to test |
| Dependency injection | Hilt (or Koin if build times matter more) | Standard, scales across many modules |
| Database | Room on top of SQLCipher | Typed queries, migrations, full encryption at rest |
| Key storage | Android Keystore | Database key never stored in plain form |
| Background work | WorkManager plus AlarmManager | Deferrable jobs plus exact-time reminders |
| AI inference | LiteRT-LM (Google AI Edge) primary, llama.cpp fallback | See AI runtime section |
| Build | Gradle with version catalogs and convention plugins | Keeps many modules consistent |

Minimum Android 9 (API 28). Features that need newer versions, such as on-device speech recognition (API 31) or built-in Health Connect (API 34), are detected at runtime and hidden when unavailable. Verify library versions against current releases when the project starts; this table names choices, not versions.

## Module structure

Features sit on a shared core, and only the engine edits schedules. Each box is a Gradle module. Features only talk to core modules, so the companion and commute can be added later without touching the planner, and the engine can be tested on its own.

```
App shells
  :app        phone and tablet
  :wear       Wear OS (Phase 4)
      │ uses
      ▼
Feature modules (never depend on each other)
  Today dashboard · Planner · Habits · Tracking · AI planner · Settings
  Companion (Phase 2) · Commute (Phase 3)
      │ depend on
      ▼
Core modules
  Scheduling engine   pure Kotlin, the only schedule writer
  AI layer            LlmEngine, model manager
  Data                Room + SQLCipher, repositories
  Reminders           alarms, WorkManager jobs
  Export and backup   encrypted archive format
  Design system       Compose theme, components
```

## Local data model and storage

One encrypted SQLite database (Room on SQLCipher) holds everything. Its key is generated on first launch and wrapped by the Android Keystore. Every table gets a stable UUID primary key and created/updated timestamps so exports, imports and future device-to-device transfer can merge records without collisions.

| Entity | Holds | Phase |
| --- | --- | --- |
| ActivityTemplate | Reusable activities: name, category, default duration, fixed/flexible/optional, priority, earliest/latest time | 1 |
| Routine | Named sets of templates per day type: workday, weekend, holiday, travel | 1 |
| PlannedBlock | One block on one day: start, end, min duration, type, priority, status (planned, done, skipped, moved) | 1 |
| Habit | Target, frequency rule, linked category | 1 |
| HabitCheck | One completion or skip, with time and source | 1 |
| LogEntry | Generic measurement: category, value, unit, time, source (manual or automatic) | 1 |
| Reminder | Trigger rule, escalation level, quiet-hours behaviour | 1 |
| Goal, Milestone | Long-term targets linked to habits and blocks | 1 |
| ChangeLog | Every schedule change: who made it (user, engine, AI), before and after, for undo | 1 |
| AiModel | Installed models, tier, file hash, benchmark result | 1 |
| Conversation, Message | Companion chat history | 2 |
| Memory | One fact per row: text, type, source message, created, last used, user-editable, deletable | 2 |
| Persona | Companion name, voice, style settings | 2 |
| Place, Trip | Saved locations and recorded journeys for commute estimates | 3 |

Two rules shape the schema. LogEntry is deliberately generic so new trackers (water, mood, weight) are new categories, not new tables. And ChangeLog is written for every schedule edit from day one, because undo and trust in the AI planner both depend on it.

## Scheduling engine

The engine is a pure Kotlin module with no Android dependencies, so it can be unit-tested exhaustively and reused on Wear OS. It is the only component allowed to change a schedule. The UI, the reminders and the AI planner all ask it; none of them edit blocks directly.

The exact rules, commands and 30 worked examples are in `docs/engine-spec.md`, which wins over this section if the two ever differ.

**Inputs:** the day's blocks with their type (fixed, flexible, optional), duration, minimum duration, priority, allowed time window and dependencies ("shower after gym"), plus a trigger: late wake-up, an activity running over, a missed block, or a user request.

**Rebuild steps, in order:**

1. Lock everything fixed and everything already done.
2. Place flexible blocks by priority into the remaining free time, respecting their windows and dependencies.
3. If time is short, shrink flexible blocks toward their minimum duration, lowest priority first.
4. If still short, drop optional blocks, lowest priority first.
5. Protect the sleep target: never push anything past the planned bedtime unless the user allows it.
6. Return a proposal: the new plan, a diff against the old one, and a machine-readable reason for each change ("Reading moved to 21:30 because Gym ran 25 minutes over").

**Applying changes:** a per-user setting decides whether proposals apply automatically or wait for a tap. Either way the change is written to ChangeLog and can be undone in one step. Because reasons are structured data, the app can explain every change in plain words without any AI; the AI only makes the explanation more natural.

A greedy priority-based placer is enough for Phase 1. A proper constraint solver is only worth it if real schedules show the greedy approach making poor choices.

## AI runtime, model tiers and model manager

All inference goes through one internal interface, `LlmEngine` (load, generate, stream, unload), so the app never depends on a specific runtime. Two backends sit behind it:

- **LiteRT-LM (Google AI Edge):** primary. Optimised for Gemma models with GPU and NPU acceleration on supported chips.
- **llama.cpp via JNI:** fallback. Runs GGUF models (Qwen, Llama, Phi and others) on CPU anywhere, and supports grammar-constrained output, which the AI planner needs.

On-device runtimes change every few months. Re-check both before Phase 0 and pick based on what actually runs well on your test phones.

**Model tiers (approximate, to be calibrated in Phase 0):**

| Tier | Device | Example models | Download size | Use |
| --- | --- | --- | --- | --- |
| None | Under ~6 GB RAM, or failed benchmark | None | 0 | Tracker and planner only, no AI |
| Small | ~6 GB RAM | Gemma-class ~1B, 4-bit | ~0.5–1 GB | AI planner commands, short replies |
| Medium | ~8 GB RAM | Gemma 3n E2B-class | ~2–3 GB | Planner plus conversation |
| Large | 12 GB+ RAM, recent chip | Gemma 3n E4B or ~4B class | ~3–5 GB | Best quality, companion in Phase 2 |

**Model manager flow:**

1. Capability check on first open of the AI screen: total and free RAM, free storage, chip, Android version.
2. Device benchmark before any big download: run a tiny probe model (a few MB) and measure tokens per second. A large model can't be benchmarked before it's downloaded, so the probe result is used to predict speed for each tier.
3. Recommend one tier, show the alternatives with honest warnings: download size, expected speed, battery and heat.
4. Download over Wi-Fi by default, resumable, verified by SHA-256 before first load.
5. Users can switch, delete or re-benchmark models at any time. Uninstalling a model never touches user data.

**Hosting: no Jinatra servers, no cost.** The model catalogue ships inside the app: name, tier, size, license, a download URL pinned to an exact file revision on Hugging Face, and the file's SHA-256. The app downloads directly from Hugging Face and refuses any file whose hash doesn't match. The catalogue only changes with app updates. Users can also import a compatible model file manually.

Prefer openly downloadable, permissively licensed models, such as Qwen (Apache 2.0) or Phi (MIT). Official Gemma repositories are gated, so Gemma is only used through a public re-upload, protected by the hash pin, with Gemma's terms shown before download. Phase 0 decides which models make the catalogue. If a hosted file disappears, that model shows as unavailable until an app update replaces its link. Downloads expose the user's IP address to Hugging Face but never personal data; the privacy policy must say so.

## AI planner design

The AI planner translates what the user says into engine commands. It never edits the schedule itself. Small models are unreliable at producing correct time blocks directly, but they are good at picking an intent and filling in a few fields.

**How a request flows:**

1. The user types or says something: "I woke up late, fix my day" or "move gym to tomorrow".
2. The app builds a compact prompt: today's plan as short text lines, the user's settings, and a fixed list of available commands.
3. The model outputs one command as JSON, for example `move_block`, `add_block`, `skip_block`, `set_fixed`, `rebuild_day` or `explain_day`. Output is constrained by a grammar where the runtime supports it, so it can't produce invalid JSON.
4. The app validates the command against the schema and the real data. Unknown block, impossible time or missing field means the model is asked once to correct itself, then the app falls back to asking the user.
5. The engine produces its proposal and diff. The AI turns the structured reasons into one or two natural sentences.
6. The proposal is applied automatically or shown for a tap, depending on the setting. Undo is always one step away.

**Without AI**, the same commands are reachable through buttons and quick actions: "I woke up late" is a button that triggers `rebuild_day`. The AI is a faster way in, never the only way.

**Phase 0 test:** write 100–200 real requests in English with the expected command for each. Add Bangla and Banglish sets before those languages launch. Run them against each candidate model on mid-range phones. The pass rate decides which tiers can offer the AI planner at all.

## Reminders and background work

Reminders are the feature users notice most when it fails, so they get their own design rather than being an afterthought.

| Need | Mechanism | Notes |
| --- | --- | --- |
| Exact-time reminders ("leave at 8:10") | AlarmManager exact alarms | Android 12+ needs the user to allow exact alarms; Android 14 denies it by default for new installs. Ask during onboarding with a clear reason. |
| Smart and escalating reminders | Exact alarm, then follow-ups scheduled after each ignored one | Escalation stops at a user-set level and respects quiet hours |
| Daily rollover, plan generation, cleanup | WorkManager periodic jobs | Runs when the system allows; not time-critical |
| Notifications | Notification channels per type | Android 13+ needs the notification permission; users can mute one type without losing others |
| After reboot or time zone change | BOOT_COMPLETED and time-change receivers | Re-register all alarms from the database |

**Battery killers are a Phase 1 problem, not a Phase 3 one.** Xiaomi, Vivo, Oppo, Realme, Tecno and Samsung all stop background apps aggressively. Onboarding should detect the manufacturer and walk the user through the exact settings to exempt the app (battery optimisation, autostart, lock in recents), using the per-brand steps documented by dontkillmyapp.com. A built-in "test my reminders" button that fires one in 60 seconds lets users and testers confirm it works on their phone.

The database is the source of truth. Alarms are always rebuilt from it, so a killed process or cleared alarm never loses a reminder permanently.

## Export, import and backup

One archive format serves export, import, backup and device transfer. Building it once in Phase 1 means Phase 2 encrypted backup is mostly a new destination, not a new system.

**Archive format:** a single file containing a manifest (app version, schema version, created time, record counts) and one JSON file per entity. Companion memories and conversations are included from Phase 2. AI models are never included; they're re-downloaded.

**Encryption:** on by default. The archive is encrypted with AES-256-GCM using a key derived from a user passphrase with Argon2id. Unencrypted export exists only behind an explicit warning, because memories and mood logs are some of the most sensitive data a person has.

**Import:** reads the schema version, runs migrations forward, then lets the user choose replace or merge. Merge uses the UUIDs and updated timestamps, newest wins, with a summary of what changed. A failed import never touches existing data: it writes to a temporary database and swaps only on success.

**Backup destinations (Phase 2):** the user picks a folder through Android's Storage Access Framework. That can be local storage, an SD card, or any cloud drive the user has installed. The app only ever writes an already-encrypted file, and Jinatra never sees it or the passphrase. If the passphrase is lost, the backup can't be recovered; the UI must say that plainly before the first backup.

## Android APIs by phase

Several useful APIs depend on Google Play services. Since you're distributing outside the Play Store, some users will have phones without Play services (Huawei, de-Googled phones). Each of those features needs a fallback or must hide cleanly.

| Phase | Feature | API | Needs Play services |
| --- | --- | --- | --- |
| 1 | Exact reminders | AlarmManager, SCHEDULE_EXACT_ALARM | No |
| 1 | Background jobs | WorkManager | No |
| 1 | Notifications | NotificationManager, POST_NOTIFICATIONS (13+) | No |
| 1 | Encrypted storage | Room, SQLCipher, Android Keystore | No |
| 1 | On-device AI | LiteRT-LM or llama.cpp | No |
| 2 | Voice input | SpeechRecognizer on-device (API 31+); whisper.cpp as fallback | No |
| 2 | Voice output | TextToSpeech | No, but voice quality depends on installed engines |
| 2 | Widgets | Jetpack Glance | No |
| 2 | Backup destination | Storage Access Framework | No |
| 3 | Steps, sleep, exercise | Health Connect | Built in on Android 14+; on 9–13 installed from Play |
| 3 | Screen time | UsageStatsManager, PACKAGE_USAGE_STATS special access | No |
| 3 | Location and places | Fused Location and Geofencing; platform LocationManager as fallback | Yes for fused; fallback is less battery-efficient |
| 3 | Activity detection | Activity Recognition API | Yes |
| 3 | Live traffic | Intent into Google Maps with route prefilled | Needs the Maps app, not an API key |
| 4 | Watch sync | Wearable Data Layer API | Yes |
| 4 | Watch UI | Compose for Wear OS, Tiles | No |

Bangla speech recognition on-device is limited on many phones today. Treat Phase 2 voice as English-first, with other languages enabled as engine support allows.

## Localisation

The UI and the AI are localised separately, because they fail in different ways. UI translation is predictable work. AI language quality depends on the model and has to be measured.

**UI, from day one:** every string in Android resources, ICU plural rules, locale-aware dates, times and numbers (including Bangla digits when the user wants them), and right-to-left layout support so Arabic or Urdu can be added later without redesign. Bangla text needs a font check on older phones, where some conjuncts render badly.

**AI language:** the model gets the user's language and a note that mixed input like Banglish is normal. Reply language follows the user's last message unless they set it. Each language gets its own test set in the Phase 0 harness, and a language is only advertised as supported for AI once it passes.

Suggested order after English: Bangla first, then Hindi, Spanish, Indonesian, Portuguese and Arabic for global Android reach.

## Distribution outside the Play Store

A few architecture choices depend on which stores you pick, so they're worth fixing early.

- **F-Droid requires the app to be open source** and free of proprietary libraries such as Play services. Wend is open source under GPLv3, with the Wend name and logo kept as Jinatra trademarks. Keep every Play services feature in a separate build flavour from the start so the F-Droid build stays clean.
- **Updates for direct downloads:** the app checks a small signed manifest on Jinatra's site, shows what's new, and hands the APK to the system installer. No personal data is sent; the check is opt-out.
- **Signing key:** one release key, backed up offline in at least two places. Losing it means existing users can never update.
- **Verification:** publish SHA-256 checksums and the signing certificate fingerprint for each release, as already planned for Jinatra downloads.
- **Donations:** outside the Play Store, a plain link to your donation page is fine. No payment code lives in the app.

## Testing strategy

Three kinds of testing matter more than coverage numbers.

**Scheduling engine:** hundreds of unit tests plus property-based tests that generate random days and check invariants: fixed blocks never move, nothing overlaps, sleep is protected, undo restores the exact previous state. Because the engine is pure Kotlin, these run in seconds.

**AI evaluation harness:** the Phase 0 request set becomes a permanent regression suite. Every model or prompt change runs the full set on real phones and reports pass rate per language and tier. No model ships to a tier unless it passes the agreed threshold.

**Real device matrix:** at least one phone each from Xiaomi, Samsung, Realme or Oppo, Vivo, Tecno or Infinix, and a Pixel, spread across Android 11 to the latest, including one low-end phone with no AI. Reminder reliability is tested on every device before each release: schedule reminders, lock the phone overnight, check what fired.

Beta testers provide what analytics would elsewhere: a short in-app feedback form and a voluntary weekly check-in, both sent only when the user taps send.

## Open decisions

Decided: English only for Phase 1, minimum Android 9 (API 28), open source under GPLv3 with the Wend name and logo kept as trademarks, and models downloaded directly from Hugging Face with no paid hosting.

- [ ] **AI planner threshold:** the Phase 0 pass rate a tier must reach before the AI planner is offered on it.
- [ ] **Which stores:** the list of free stores you'll launch on, since each has its own rules.
- [ ] **Team roles:** who owns the engine, the AI layer, the device testing and support.
