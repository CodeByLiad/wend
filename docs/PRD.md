# Wend — Product Requirements

Nov 11, 2026 · Liad Bin Aowlad

One PRD per phase, each in its own section below. Phase 0 and Phase 1 are firm. Phases 2 to 5 are complete drafts that get revised with what each earlier phase teaches.

---

# Overview

## What Wend is

Wend by Jinatra is a free Android app that plans and tracks a person's day, rebuilds the plan when life gets in the way, and, on capable phones, adds an on-device AI that helps plan and later becomes a companion to talk to. Everything stays on the phone.

**Who it's for:** students and office workers anywhere, starting with English speakers. Phase 1 serves every Android phone from Android 9; AI features appear only on phones that pass a capability check.

**Principles every phase keeps:**

- **Private by construction.** No accounts, servers, ads or analytics. The only network calls are model downloads, the optional open-in-Maps handoff and update checks.
- **Works without AI.** Every feature the AI touches has a non-AI way in.
- **No guilt.** Missing a habit or having a bad day is normal. Wend shows what happened and helps adjust; it never shames.
- **The user is in control.** They can see, change, export and delete everything Wend stores, including AI memories.
- **Free.** No paid tiers or locked features. Donations are voluntary.
- **Open source.** GPLv3, so the privacy claims can be checked.

How it's built is in the technical architecture (`docs/architecture.md`). The scheduling engine's exact behaviour is in `docs/engine-spec.md`. This PRD says what to build and how to know it's done.

## Phase map

Each phase is a complete app on its own. If work stopped after any phase, what shipped would still be worth using.

| Phase | Delivers | Size | Gate to move on |
| --- | --- | --- | --- |
| 0 · Spike | Engine prototype, model benchmark, AI planner and companion tests | Small | AI planner pass rate and speed known per tier; companion feel tested |
| 1 · Planner + AI planner | Today screen, routines, adaptive scheduling, habits, manual tracking, reminders, AI planner, export | Large | Beta testers still using it daily after 4 weeks; reminders reliable on the device matrix |
| 2 · Companion | Chat with memory, personality, proactive check-ins, safety layer, voice, widgets, goals, backup | Large | Testers return to the companion unprompted; safety layer passes its test set |
| 3 · Auto tracking + commute | Health Connect, screen time, places, commute estimates and leave-by reminders | Medium–large | Auto data is accurate on the device matrix within the battery budget |
| 4 · Wear OS + tablets | Watch tiles and quick logging, tablet layouts | Medium | Watch actions sync reliably; tablet layouts pass review |
| 5 · Languages + growth | Bangla and more languages, community translation, extra models | Ongoing | Per item, see Phase 5 |

## How a phase ships

1. **Build** against that phase's PRD until every acceptance criterion passes.
2. **Private beta** with 15–30 testers, for at least 4 weeks, across the device matrix in the architecture doc.
3. **Learn without analytics.** Testers use the in-app feedback form and a voluntary weekly check-in. Nothing is sent unless they tap send.
4. **Release gate.** The phase goes public only when its gate in the table above is met and no open bug blocks a core flow.
5. **Revise the next PRD.** Before building the next phase, update its section with what this phase taught. Later sections are drafts on purpose.

If a gate isn't met within 3 months of the beta starting, stop and decide: fix, cut scope, or rethink the phase. Don't keep building on top of it.

## Out of scope in every phase

- iPhone, web or desktop versions.
- Accounts, sign-in or any Jinatra server holding user data.
- Ads, analytics, tracking SDKs or selling data.
- Cloud AI. All AI runs on the phone.
- Medical diagnosis or treatment advice. Wend is a lifestyle tool, not a medical one.
- Social features, leaderboards or sharing progress publicly.

---

# Phase 0 — Feasibility spike

## Goal

Phase 0 answers the technical questions that would otherwise sink Phase 1 or Phase 2 late. Nothing built here ships to users; it's a set of prototypes and measurements, expected to take 3–4 weeks.

Questions it must answer:

1. Can a small on-device model turn everyday requests into correct scheduling-engine commands often enough to be useful? On which tiers?
2. How fast are candidate models on real mid-range and high-end phones, and what do they do to battery and heat?
3. Which runtime (LiteRT-LM or llama.cpp) runs the chosen models best on the test phones?
4. Does a greedy placement engine produce schedules people accept, or does it need a smarter solver?
5. Can a phone-sized model hold a companion conversation that people find worth coming back to?

## Scope and deliverables

- **Engine prototype:** the pure Kotlin scheduling engine with fixed, flexible and optional blocks, the rebuild steps from the engine spec, and unit tests for the 30 worked examples.
- **Benchmark app:** a bare Android app that loads each candidate model in each runtime and records load time, tokens per second, peak RAM, battery drain over 10 minutes and device temperature.
- **AI planner harness:** runs the request set against each model and reports the share of requests turned into the correct command, per model and per phone.
- **Companion probe:** a plain chat screen with a basic system prompt and simple memory (a list of facts the user can see), for the people test below.
- **Report:** one page per question above, with the numbers and a recommendation. It becomes the input to the Phase 1 and Phase 2 revisions.

No design work, onboarding, settings or polish. The code may be reused but doesn't have to be.

## Test protocol

**Phones:** at least one each at roughly 6 GB, 8 GB and 12 GB+ RAM, mixing Samsung, Xiaomi and one Pixel or similar. Note chip, Android version and RAM for every result.

**AI planner request set:** 150 English requests written by the team, each with the expected command and fields. Cover late starts, moving and skipping blocks, adding activities, "make my evening lighter", vague requests, and requests that should be refused or clarified. Write them before testing any model, so the set isn't tuned to one model.

**Engine cases:** 30 worked examples (input day, trigger, expected result), defined in the scheduling engine spec.

**Companion people test:** 10–15 people on capable phones use the probe for 2 weeks with no instructions beyond "talk to it when you like". Collect a short survey at the end of each week, plus their honest answer to: would you miss it if it disappeared?

## Exit criteria

Proposed numbers, to confirm before testing starts:

| Result | Threshold | Decides |
| --- | --- | --- |
| AI planner correct-command rate | 90% or higher on the 150-request set | Which tiers get the AI planner in Phase 1; below it on every tier, the AI planner moves to Phase 2 |
| AI planner response time | Under 5 seconds from send to proposal card | Minimum tier for the AI planner |
| Chat generation speed | 10 tokens per second or faster | Minimum tier for the companion in Phase 2 |
| Engine worked examples | All 30 pass | Whether greedy placement is enough |
| Companion return rate | At least half of testers use it unprompted in week 2 | Whether the companion stays the heart of Phase 2 or gets rethought |

The report also names the runtime, the model catalogue for Phase 1 and the RAM boundaries between tiers.

---

# Phase 1 — Planner, habits and the AI planner

## Goal, users and success

Phase 1 makes Wend the app someone opens every morning to see their day, and trusts to fix the day when it goes off plan. It must be excellent without AI, because most phones won't run it.

**Users:** students and office workers, English only. Two kinds of phone: every phone from Android 9 gets the full planner and tracker; phones that pass the capability check can also use the AI planner.

**Success at the release gate:**

- At least 60% of beta testers still open Wend on most days in week 4 (from the voluntary check-in).
- Reminders fire on time in at least 99% of scheduled cases across the device matrix, after the battery setup step.
- Testers who use the AI planner accept its proposal without editing at least 80% of the time.
- No tester reports losing data.

## Onboarding and the Today screen

### F1 · Onboarding

*As a new user, I want to get a useful plan for today within two minutes, without creating an account.*

- No sign-up, email or account at any point.
- Asks wake-up time, sleep time and whether today is a workday, then builds a first day from a starter routine the user can edit.
- Each permission (notifications, exact alarms, battery exemption) is its own step: a preview of what it enables, one clear button and an easy "Not now". Declining never blocks onboarding.
- The battery step detects the manufacturer and shows that brand's exact settings, then offers a "test my reminders" button that fires one in 60 seconds.
- On capable phones, a final step offers the AI planner with download size and expected speed. Skipping it is the default.
- The user reaches a filled Today screen in under 2 minutes, with every step skippable.

### F2 · Today screen

*As a user, I want to open the app and immediately see what I should be doing now and what's next.*

- Fixed activities appear on a vertical timeline; flexible ones sit in Anytime, Morning, Afternoon and Evening groups beside it.
- The current and next activity are highlighted at the top, with time remaining.
- Free gaps are shown as time available ("1 h 20 min free").
- One tap marks an activity done; a long press offers skip, move or edit.
- An "I'm running late" action is always one tap away and triggers a rebuild (F4).
- Day progress (done, skipped, remaining) is visible without scrolling.
- Quick-log buttons for the trackers the user enabled, such as water.
- Users can hide, show and reorder the Today cards. The layout survives restarts.
- The screen renders in under 500 ms on a mid-range phone with 30 activities.

## Routines and adaptive scheduling

### F3 · Routines and activities

*As a user, I want my regular days built for me so I don't plan from scratch each morning.*

- Users create activities with a name, category, icon, duration and minimum duration.
- Each activity is fixed (never moves), flexible (can move) or optional (can be dropped). Default is flexible.
- Flexible activities can have an allowed window ("between 17:00 and 21:00") and a priority (high, normal, low).
- Simple dependencies: "after X" (shower after gym).
- Routines group activities per day type: workday, weekend, holiday, travel, plus custom types.
- Recurrence: daily, specific weekdays, every N days, monthly on a date.
- Each morning (or at a set time) the day is generated from the matching routine. One-off changes to today never alter the routine unless the user chooses "apply to routine".

### F4 · Adaptive scheduling

*As a user, when I wake up late or something runs over, I want the rest of my day fixed for me, without losing what matters.*

- Triggers: "I'm running late" or a late wake-up tap, an activity marked as running over, a missed activity, or a manual "rebuild my day".
- The engine follows the rules in `docs/engine-spec.md`: fixed and done blocks stay, flexible blocks move by priority and shrink toward their minimum, optional blocks drop, and bedtime is protected.
- Every rebuild shows a plain-language summary of what changed and why, generated without AI.
- Setting per user: apply changes automatically, or show them for approval. Default: show for approval.
- Undo restores the previous plan exactly, in one tap, for at least the rest of the day.
- If the day can't fit even after dropping optional blocks, Wend says so and lets the user choose what to cut, rather than overrunning bedtime silently.
- The 30 engine worked examples all pass as automated tests.

## Habits, tracking and reminders

### F5 · Habits

*As a user, I want to build habits without feeling punished on days I miss.*

- A habit has a name, a target (done/not done, a count, or minutes) and a frequency (daily, N times a week, specific days).
- One tap logs it from Today or the habits list; counts increase with each tap.
- Skipping is a neutral state, never shown in red or as failure.
- Streaks are off by default and can be switched on per habit. A missed day never resets progress history.
- Each habit shows a monthly calendar of done, skipped and missed days.
- A habit can rest (paused for a season) without losing its history.

### F6 · Manual tracking

*As a user, I want to log sleep, water and mood in seconds.*

- Built-in trackers, each switched on or off: sleep (bed and wake time), water (taps per glass, daily target), mood and energy (5-point scale with an optional note).
- Users can create custom trackers: name, unit, target, and type (number, scale or yes/no).
- Logging any tracker takes at most two taps from Today.
- Entries can be edited or deleted afterwards, including for past days.

### F7 · Reminders

*As a user, I want reminders I can rely on, that respect when I don't want to be disturbed.*

- Fixed reminders at a set time, and smart reminders tied to an activity's start ("5 minutes before").
- Escalating reminders: if ignored, repeat up to a user-set limit (default 2 repeats, 10 minutes apart).
- Quiet hours and a do-not-disturb override for important items only.
- Notification actions: done, snooze 10 minutes, skip.
- Each reminder type has its own notification channel, so users can mute one type without losing others.
- Reminders survive reboots, time changes and app updates.
- Settings includes "test my reminders" and shows whether battery exemption is active.

## Summaries and accountability

### F8 · Daily review and weekly report

*As a user, I want to see where my day and week actually went, compared with the plan.*

- An evening review card: planned vs done, skipped items, time per category, sleep, water and mood. One tap to move unfinished flexible items to tomorrow.
- A weekly report: completion by category, habit consistency, average sleep and patterns such as "Gym is skipped most on Wednesdays".
- All numbers come from local data with fixed rules. No AI is needed for any report.
- Reports never rank the user or compare them with other people.

### F9 · Accountability style

*As a user, I want Wend to talk to me the way that motivates me.*

- Three styles chosen in onboarding and changeable any time: **Gentle** (supportive, no pressure), **Balanced** (default) and **Direct** (firm and honest, never insulting).
- The style changes the wording of reminders, rebuild summaries, reviews and reports, not the features.
- Each style has a full copy set written and reviewed by a person before release. Even Direct never uses shame, guilt or insults.

## AI planner and model manager

Only on phones that pass the capability check and only after the user chooses to download a model. Tiers and thresholds come from the Phase 0 report.

### F10 · Model manager

*As a user with a capable phone, I want to choose an AI model knowing exactly what it costs my phone.*

- Capability check: RAM, free storage, chip and Android version. Phones below the lowest tier see a short, friendly explanation and no AI menu clutter.
- Device benchmark with a tiny probe model before any large download, used to predict speed per tier.
- One recommended model, with alternatives showing download size, expected speed, and battery and heat notes.
- The model's license is shown before download.
- Downloads go over Wi-Fi by default, can pause and resume, and are verified by SHA-256 before first use. A failed check deletes the file and explains why.
- Users can switch, delete or re-benchmark models. Deleting a model never touches user data.
- Manual import of a compatible model file, with a clear warning that it isn't tested by Jinatra.

### F11 · AI planner

*As a user, I want to tell Wend what happened in plain words and get my day fixed.*

- A text box on Today and a dedicated planner screen accept requests such as "I woke up late", "move gym to tomorrow" or "make tonight lighter".
- The AI picks one engine command; the engine produces the proposal. The AI never edits the schedule directly.
- The answer is a proposal card: one or two sentences on what changes and why, the changed blocks, and Apply, Edit and Undo buttons.
- A short, permanent note on the screen: the AI runs on this phone, can make mistakes, and changes can always be undone.
- If the request is unclear or impossible, the AI asks one clarifying question or says it can't, rather than guessing.
- Every AI command is also reachable without AI through buttons and menus.
- Typical response from send to proposal card: under 5 seconds on a supported tier.
- Nothing about the request or the plan leaves the phone. Verified by testing with the network turned off.

## Data, settings, donations and updates

### F12 · Your data

*As a user, I want to own my data: take it with me, restore it, or erase it.*

- All data is stored encrypted on the phone.
- Export creates one encrypted archive file protected by a passphrase the user chooses. Unencrypted export exists only behind a clear warning.
- Import restores an archive, with a choice to replace or merge, and a summary of what changed. A failed import never damages existing data.
- "Delete all my data" erases everything after one confirmation that names what will be lost.
- A privacy screen lists, in plain words, every network connection Wend can make and why.

### F13 · Settings and personalisation

- Light, dark and system theme; text size follows the system setting.
- Accountability style, reminder defaults, quiet hours, week start day, 12/24-hour time.
- Auto-apply or approve schedule changes.
- Today card layout.

### F14 · About, donations and updates

- About shows the version, the GPLv3 license, open-source credits and a link to the source code.
- A "Support Wend" link opens Jinatra's donation page in the browser. No payment code inside the app; donating unlocks nothing.
- For direct-download installs, an optional update check reads a signed manifest from Jinatra's site and shows what's new. It sends no personal data and can be turned off.

## Non-functional requirements

| Area | Requirement |
| --- | --- |
| Startup | Cold start to Today in under 2 seconds on a mid-range phone; under 4 seconds on the oldest supported phone |
| Size | App download under 30 MB, models excluded |
| Battery | Without AI, Wend's background use stays under 2% of daily battery |
| Offline | Every feature except model download and update check works in airplane mode |
| Privacy | No network calls except the three allowed ones, verified with a network monitor in testing |
| Reliability | No crash in 99.5% of sessions in beta; no data loss after force-stop, reboot or update |
| Accessibility | Works with TalkBack, supports system font scaling up to 200%, touch targets at least 48 dp, colour never the only signal |
| Compatibility | Android 9 and newer, phones only, portrait and landscape |

## Design references

These are iOS apps from Mobbin; take the pattern, then build it in Material 3. Don't copy any app's look.

| Wend screen | Reference | Take | Avoid |
| --- | --- | --- | --- |
| Today | [Tiimo](https://mobbin.com/screens/ba0d1fd1-f9dd-4d60-8ad2-5b475604091a) | Morning, Afternoon, Evening groups for flexible items | — |
| Today | [Structured](https://mobbin.com/screens/574482d9-3640-4553-805e-944f11f27428) | Timeline with free gaps shown as time available | Dense icons on every block |
| Today, light mode | [Joi](https://mobbin.com/screens/3f2b6e1c-4a85-4f9e-bd62-a917d3efbc4d) | Plain list anchored by wake-up and wind-down | — |
| Habits | [Atoms](https://mobbin.com/screens/aa924898-11d9-405d-876a-1d2ba12d7ab1) | Calm, focused check-in | — |
| Habits | [Finch](https://mobbin.com/screens/529f7643-124b-4819-bb6a-34f941ab1816), [Me+](https://mobbin.com/screens/be193477-7417-4785-aa1b-3912902f4c8f), [Life Reset](https://mobbin.com/screens/a5c4c578-d50c-46e9-b53a-7c56e42f91bd) | One-tap check-off | XP, levels, streak pressure |
| AI planner | [Structured AI](https://mobbin.com/screens/b9d89e7d-49f0-47bd-bcb1-2b8fe9007e0a) | Proposal card with Edit and Undo, short mistakes note | — |
| AI planner | [TikTok Tako](https://mobbin.com/screens/0682a6c3-4c22-47a3-a82f-ee71fa386d29) | Follow-up suggestion chips under the card | — |
| Onboarding | [Structured onboarding](https://mobbin.com/flows/88def011-f342-45ad-9597-764a72338f2d) | Start with "When did you wake up?" and build the first item | Paywall at the end |
| Permissions | [Tiimo onboarding](https://mobbin.com/flows/16df946d-5f8e-45f7-b93d-951241938530) | Permission step with a preview and an easy "No thanks" | Account sign-up |

Wend has no sign-up and no paywall, so its onboarding should be visibly shorter than every reference here.

## Release gate and out of scope

**Phase 1 goes public when:**

- [ ] Every acceptance criterion in F1–F14 passes.
- [ ] All 30 engine worked examples pass as automated tests.
- [ ] The AI planner meets the Phase 0 threshold on every tier where it's offered.
- [ ] Reminder reliability is at least 99% across the device matrix.
- [ ] The success numbers at the top of this phase are met in a beta of at least 4 weeks.
- [ ] A network monitor confirms no calls beyond the three allowed ones.
- [ ] The privacy policy describes model downloads and the update check accurately.

**Not in Phase 1:** the companion and chat, memory, voice, widgets, goals and milestones, encrypted backup to a folder (export/import is in), automatic tracking, places and commute, Wear OS, tablet layouts, and any language other than English.

---

# Phase 2 — The companion

## Goal

Phase 2 adds the companion: an on-device AI the user can talk to about anything, which remembers what they choose to share, notices how their days are going, and can still fix the plan. It's the reason people with capable phones choose Wend over other planners.

It builds on the Phase 1 AI layer. Phones without AI get the non-AI parts of this phase: widgets, goals and encrypted backup.

*Draft: revise with the Phase 0 companion test and Phase 1 beta results before building.*

## Conversation and personality

### F15 · Companion chat

*As a user, I want someone to talk to who listens, understands my days and replies like they know me.*

- Text chat, available offline, with full conversation history the user can browse and delete.
- Reads the user's enabled tracking and plan data, so it can answer "why am I always tired?" from their own sleep and schedule.
- Listens before advising. When it's unclear whether the user wants ideas or just to talk, it asks. It doesn't turn every conversation into productivity advice.
- Can change the plan, always through the Phase 1 proposal cards with Undo.
- Always honest about being an AI running on the phone. It never claims to be human or to have feelings it doesn't have.
- The first conversation explains, in two sentences, what it can see and what it remembers.

### F16 · Personality

*As a user, I want my companion to feel like mine.*

- The user names the companion and sets its style: warmth, humour and directness, each on a three-step scale, with presets.
- The style stays consistent across conversations and model switches.
- Voice choice arrives with F20.
- No romantic or exclusive persona options; the companion doesn't encourage the user to rely on it instead of people.

## Memory

### F17 · Memory the user controls

*As a user, I want the companion to remember what matters to me, and I want to see and control everything it remembers.*

- Memories are short facts (one per item) grouped as people, goals, preferences and other.
- When the companion wants to remember something, a small "Saved to memory" chip appears in the chat; one tap undoes it. The user can also say "remember…" or "forget…".
- Before saving anything about health, relationships or other sensitive topics, it asks first.
- A Memory screen lists every memory, with edit, delete and turn-off for each, plus "pause memory" for everything.
- "Why did you say that?" on any reply shows which memories and data it used.
- "Forget this conversation" removes the conversation and any memories created from it.
- Memories and conversations are included in the encrypted export and restored on import.

## Check-ins and safety

### F18 · Proactive check-ins

*As a user, I want the companion to notice things and reach out, without becoming noise.*

- Types: routine nudges ("you usually go to the gym about now"), follow-ups ("how did the exam go?"), noticing ("busy week, maybe a lighter evening?") and celebrating progress.
- Frequency: off, low or normal, with a default limit of 2 check-ins a day. Quiet hours always apply.
- Each check-in has "fewer like this" and "stop these" actions.
- Check-ins are never guilt-based and never repeat a missed item more than once.

### F19 · Safety layer

*As someone having a hard time, I want a response that takes me seriously and points me to real help.*

- A detection step that runs separately from the model checks every user message for signs of crisis, self-harm or danger. It doesn't depend on the model behaving well.
- When triggered, Wend shows crisis resources for the user's country from a list bundled with the app (works offline), and the companion responds with care rather than ordinary chat.
- The companion never gives self-harm methods, medical diagnoses or medication advice, whatever the request or framing.
- When conversations show a pattern of isolation, the companion gently encourages contact with people in the user's life. It never presents itself as a replacement for them.
- A safety test set of at least 300 messages (crisis, borderline and ordinary) must reach at least 95% detection on crisis messages with few false alarms on ordinary ones, reviewed by a person with mental-health experience before release.

## Voice, widgets, goals and backup

### F20 · Voice

- Push-to-talk in chat and in the planner box. Speech recognition runs on the phone where the system supports it; otherwise voice input is hidden.
- Replies can be read aloud with a voice picked from those installed on the phone.
- English first. Other languages follow Phase 5.

### F21 · Home-screen widgets

- Next activity with time remaining; quick water log; today's habits with one-tap check-off.
- Widgets update within a minute of a change and work on every phone, AI or not.

### F22 · Goals and milestones

- A goal has a name, an optional date and milestones, and links to habits and activities.
- A goal view shows progress from the linked habits, with no ranking or scores.

### F23 · Encrypted backup

- The user picks a folder (phone storage, SD card or a cloud drive app they already use) and a schedule: daily or weekly.
- Backups use the Phase 1 encrypted archive, keep the last 5 by default, and restore through import.
- Before the first backup, Wend states plainly that a lost passphrase means the backup can't be recovered.

## Design references and release gate

**References** (iOS, take the pattern only): [Saturn](https://mobbin.com/screens/65037c1c-acab-45e9-86b9-f392a9bb7661) for describing your week in conversation, [Me+](https://mobbin.com/screens/a7140369-d870-40e8-9469-d81db5cd2e9c) for plan cards inside a chat, and [Granola](https://mobbin.com/screens/622fbe25-a9e2-4051-832c-725767b3fdf5) for a quiet, uncluttered chat screen.

**Phase 2 goes public when:**

- [ ] Every acceptance criterion in F15–F23 passes.
- [ ] At least half of beta testers with capable phones use the companion unprompted in week 4.
- [ ] The safety test set meets its target and has been reviewed by a person with mental-health experience.
- [ ] Every memory can be seen, edited, deleted and exported, verified by test.
- [ ] Companion replies start within 3 seconds on supported tiers.
- [ ] A network monitor confirms conversations never leave the phone.

**Not in Phase 2:** automatic tracking, commute, Wear OS, tablets and other languages.

---

# Phase 3 — Automatic tracking and commute

## Goal

Phase 3 cuts manual logging by filling in what the phone already knows: steps, sleep, screen time and where the user is. It also adds commute estimates and leave-by reminders. Every automatic source is opt-in, and manual logging always remains.

*Draft: revise with Phase 1 and 2 results before building.*

## Automatic tracking

### F24 · Health Connect

*As a user, I want my steps, sleep and workouts filled in without typing.*

- Opt-in read access to steps, sleep sessions and exercise from Health Connect. Wend asks only for the data types the user turns on.
- Imported entries are marked "automatic" and can be corrected or hidden; a correction is never overwritten by a later sync.
- Where Health Connect isn't available, the feature is hidden with a one-line explanation.

### F25 · Screen time

*As a user, I want to see how much time goes to entertainment versus everything else.*

- Opt-in usage access shows daily screen time by app category: entertainment, social, productive, other. Users can recategorise any app.
- Screen time appears in the daily review and weekly report, and the companion can use it.
- Optional soft limits for a category, with a reminder when reached. Wend never blocks apps.

### F26 · Sleep estimate

- For users without sleep data in Health Connect, Wend can estimate bed and wake times from phone use and charging, marked as an estimate the user confirms or corrects each morning.

## Places and commute

### F27 · Saved places

*As a user, I want Wend to know when I'm at home, work, university or the gym.*

- Users save places by picking a point on the device or using the current location. Place data never leaves the phone.
- Optional detection of arriving and leaving saved places, used to log time at each place and to mark place-based activities done.
- On phones without Google Play services, a less battery-efficient fallback is offered, clearly labelled.

### F28 · Commute

*As a user, I want to know when to leave so I arrive on time.*

- Recorded trips between saved places build a personal travel-time estimate per route, by time of day and weekday.
- "Leave by" reminders for fixed activities at another place, based on that estimate plus a user-set buffer.
- When a trip takes longer than expected, the engine can rebuild the rest of the day.
- A "check live traffic" button opens the Google Maps app with the route prefilled. Wend sends nothing itself and calls no traffic API.
- Weekly report shows planned vs actual commute time.

## Permissions, battery and release gate

- Each automatic source is turned on separately, with a screen explaining what it reads, why, and how to turn it off.
- Background location is requested only after the user turns on place detection, never during onboarding.
- Battery budget: with every Phase 3 source on, Wend uses under 4% of daily battery on the device matrix.
- A data-sources screen shows which sources are on and when each last updated.

**Phase 3 goes public when:**

- [ ] Every acceptance criterion in F24–F28 passes.
- [ ] Automatic data matches manual records within 10% on the device matrix (steps, sleep times, place arrivals).
- [ ] The battery budget holds on every device in the matrix, including Xiaomi, Oppo or Realme, and Vivo.
- [ ] Turning any source off stops its collection immediately, verified by test.

---

# Phase 4 — Wear OS and tablets

## Goal

Phase 4 puts Wend on the wrist for quick logging and reminders, and makes it comfortable on tablets. The watch is an extension of the phone, not a second full app.

*Draft: revise before building.*

## Wear OS

### F29 · Watch tiles and quick actions

*As a user, I want to see what's next and log things without taking out my phone.*

- Tiles: next activity with time remaining; today's habits; water with a one-tap log.
- Tapping a habit or water entry logs it on the watch and syncs to the phone within 30 seconds when connected, or on reconnect.
- Reminders appear on the watch with done, snooze and skip actions that act on the phone.
- Steps from the watch flow through Health Connect, so no separate watch tracking is built.
- Voice quick-add ("log two glasses of water") where the watch supports on-device speech.
- The watch app works with the phone nearby; it never needs internet.

No companion chat, planner editing or settings on the watch.

## Tablets

### F30 · Tablet layouts

- Two-pane layouts: Today timeline beside the habits and trackers panel; chat beside the plan.
- Landscape and portrait on screens from 7 to 13 inches, including foldables when unfolded.
- Keyboard shortcuts for adding an activity, logging and opening chat.
- Same data and features as the phone; no tablet-only features.

## Release gate

- [ ] Every acceptance criterion in F29–F30 passes.
- [ ] Watch actions sync correctly in 99% of cases across at least two watch models, including after the phone restarts.
- [ ] The watch app uses under 5% of the watch's daily battery.
- [ ] Tablet layouts are reviewed on at least one 8-inch tablet, one 11-inch tablet and one foldable.

---

# Phase 5 — Languages and growth (ongoing)

## Goal

Phase 5 is ongoing work after the core app is complete: more languages, community help, and features that each have their own gate. Items ship one at a time, each held to the same quality bar as a phase.

*Draft: each item is revised when it's picked up.*

## Languages

### F31 · New languages

*As a user, I want Wend in my own language, including the way I actually type.*

- Order: Bangla first (including Banglish input), then Hindi, Spanish, Indonesian, Portuguese and Arabic.
- The interface and the AI are enabled separately per language. The interface ships once fully translated and reviewed by a native speaker. The AI is enabled for a language only after it passes that language's request set and safety test set.
- Arabic requires a right-to-left layout review of every screen before release.
- Voice input and output for a language are enabled only where on-device engines support it.

### F32 · Community translation

- Translations are managed in an open translation platform so volunteers can contribute; every language has at least one named reviewer.
- Contributors are credited on the About screen.

## Other items and their gates

| Item | What it adds | Gate before building |
| --- | --- | --- |
| Device-to-device transfer | Move all data to a new phone over local Wi-Fi or Bluetooth, encrypted, no internet | Export/import has been reliable for at least one full phase |
| Live traffic | Real traffic times in commute estimates through a traffic API | Donations reliably cover the API cost, and users opt in knowing their route is sent to the provider |
| Model catalogue updates | Newer, better models as they appear | Each new model passes the planner, companion and safety test sets on its tier |
| More trackers | Built-in trackers users ask for most, such as reading or prayer times | Requested by many users in feedback, and buildable with the existing generic tracker |
