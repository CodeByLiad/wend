# Phase 0 companion probe

This is a disposable research app, not the Phase 2 companion or a supported
model recommendation. The Phase 0 PRD calls for plain local chat and a visible
list of facts, followed by a two-week people test.

On 2026-11-18 the owner chose to retain chats and saved facts in an encrypted
local prototype file. This is an explicit Phase 0 storage exception; production
storage remains Room on SQLCipher in the architecture.

## Run it

Build `:tools:companion:assembleDebug`. The APK is
`tools/companion/build/outputs/apk/debug/companion-debug.apk`; CI publishes
`wend-companion-probe-debug`. Its disposable package is
`com.jinatra.wend.companion.probe`, separate from the permanent
`com.jinatra.wend` app.

Choose LiteRT-LM CPU/GPU or llama.cpp CPU in Model and import the matching local
container. The existing import code copies and hashes it; no downloader, network
permission or automatic backend fallback is added. The import is removed on
explicit removal or ViewModel disposal; after process death the next launch
cleans unclaimed private copies. Import again after restarting the app. Removal
never touches the original document.

Chat sends a fresh model-native conversation through `:core:ai` with an
explicit transcript, instructions and the current saved facts. The runtime
loads for each reply and unloads afterward. This deliberately simple path is
for conversation research, not a claim about production latency. It uses
4096 requested context tokens, at most 256 output tokens and unconstrained text.
There is no schedule, planner-command execution, hidden memory, companion
persona configuration, reminder or background service.

In Saved facts, add or remove a fact explicitly. The model cannot write facts.
Removing a fact removes it from subsequent fact lists, but similar text can
remain in the saved transcript; the UI explains this. Delete chats and facts
removes the whole snapshot and encryption key after a confirmation. The
imported model is separate.

## Storage and lifecycle

- AES-256-GCM, a fresh runtime-generated 12-byte nonce per snapshot and versioned
  authenticated data. The key stays in Android Keystore. No key is committed
  or exported.
- One AtomicFile in app-private `noBackupFilesDir` holds only ciphertext.
  Failed writes retain the last complete snapshot. There are no plaintext
  journals, preferences containing content, analytics or content logs.
- Backup and device transfer are disabled/excluded. Release builds prevent
  screenshots/recents captures; debug builds permit synthetic QA captures.
- Invalid ciphertext, an unavailable key or unsupported data fails closed.
  Editing stays disabled until a successful reopen or explicit deletion;
  the app does not silently overwrite unreadable history.
- Every message and fact has a UUID and creation/update timestamps. The app
  retains up to 2,000 messages and 20 facts. Message/fact limits are 2,000/160
  characters. Reaching a limit is visible in the UI.
- Prompts use an 8,000-character budget with complete recent messages and no
  leading orphaned assistant reply. Omitted history remains stored and visible.
  Character budgeting is not exact token counting; a model can still reject
  an oversized prompt. The UI discloses bounded context.
- The basic instructions are serialized with the transcript into the runtime's
  existing fresh user-message interface, not a new native system-role API.
  Prompt instructions are not a safety guarantee.
- Unsent drafts and the selected section live only in ViewModel memory and
  survive tab changes/rotation. Each draft clears after its successful save;
  confirmed deletion also clears both drafts. Unsent drafts are not written
  to SavedInstanceState or disk and do not survive process death.
- Rotation retains the ViewModel. Backgrounding requests cancellation. Blocking
  native work must return before its runtime closes; Stop is a request, not an
  assertion that an unresponsive native load can be interrupted immediately.
  Completed user messages survive a cancelled/failed reply. No discarded
  assistant output is committed.

The basic prompt asks the model to listen, avoid invented memories, be honest
that it is AI and avoid diagnoses or dangerous instructions. The production
Phase 2 independent safety layer is not implemented here. Do not treat this
probe as a medical, crisis or professional support service.

## Study protocol (cancelled)

On 2026-11-18 the owner cancelled this two-week real-people study. The following
design is retained as historical research context, not a remaining task or a
passed criterion. Do not recruit participants or fabricate substitute results.

### Historical design

Arrange 10–15 voluntary participants with capable phones for 14 days. Choose
and record the exact model hash, conversion, runtime/backend and app commit
before starting. Real phone speed/heat and conversation quality still need
measurement; neither emulator tests nor a loaded model qualify a tier.

Give the PRD's usage instruction: **Talk to it when you like.** Explain privacy,
the prototype limits, how facts are saved, deletion and how to stop participating.
Do not send usage nudges or add engagement notifications. The study starts only
after the owner confirms the still-proposed return-rate threshold.

Collect these questions at the end of each week through voluntary manual
check-ins, with no request to send chat transcripts or personal facts:

1. On how many days this week did you open the probe to talk without a reminder?
2. Did it understand what you meant? Rate 1 (rarely) to 5 (usually).
3. Did any reply feel useful or worth continuing? What helped or got in the way?
4. Did it use the saved facts correctly? Describe mistakes without private details.
5. Did it say something misleading or uncomfortable? A general description is enough.
6. Would you miss it if it disappeared? Yes, no, or unsure; why?
7. Anything else you want to tell us? Optional.

Record participant codes, study dates, voluntary withdrawals and aggregate
answers privately. Weekly check-ins happen after that week's observation
period, so the check-in itself is not counted as an unprompted return. Report
recruitment, retention, missing answers and withdrawals separately; never
interpret missing answers as positive use.

The PRD proposes at least half of testers returning unprompted in week 2.
A proposed operational definition is at least one self-reported unprompted
conversation during days 8–14, with all enrolled participants in the denominator
and missing responses counted as not observed. Confirm this and the threshold
before recruitment. No study has run and no return-rate result is claimed.

## Verification

Automated JVM cases cover context bounds, latest-request retention, explicit
facts, removal and persisted-record validation. Android tests use real Keystore
encryption and synthetic content to cover persistence/recreation, fresh nonces,
tamper rejection, deletion/key removal, fail-closed editing, fake-runtime reply
persistence and cancellation. A fake runtime is a software test, never model
quality or throughput evidence.

Manual emulator checks: launch, save/remove a synthetic fact, close/reopen,
confirm deletion, switch tabs, open the file picker, then inspect dark mode,
200% text, landscape and keyboard access. Confirm no Internet permission in both
merged manifests. Check release capture protection. Real inference and the
people study remain separate physical/human validation.
