# Wend — Scheduling Engine Spec v0.3

Nov 11, 2026 · Liad Bin Aowlad

The scheduling engine is the only part of Wend that changes a schedule. This spec defines exactly what it does, and 30 worked examples that become its tests. Where it differs from the architecture doc, this spec wins.

**v0.3** answers the second Codex review. Main changes: dropped and unplaced blocks are no longer retried automatically (new `retry_block` command replaces `mark_missed`), so rebuilds are idempotent; Keep requires a valid length; the horizon is capped at 2880; validation sub-codes are named; example 26 fixed. The "Answers" sections at the end map every review point to its resolution.

## Time

- A time is a whole number of **minutes since 00:00 on the plan date** (`Int`, wrapped in a value class such as `Minute`). Values from 1440 upward are after midnight (bedtime 00:30 = 1470). Valid range: 0 to 2880, for every time including bedtime. The horizon never extends past 2880. Display as HH:MM.
- **Grid:** block starts and ends, window bounds, bedtime, move targets, overrun ends and requested starts must be multiples of 5, or the command is `REJECTED` (`OFF_GRID`). `now` and the wake time may be off-grid; they're rounded up only when computing the horizon start.
- **Intervals are half-open** `[start, end)`. Touching intervals don't overlap. A block with `end ≤ horizon start` has ended.

## Model

**Block**

| Field | Meaning |
| --- | --- |
| id | Caller-supplied string (UUID in the app; `A1` etc. in tests). Unique within the day |
| title | Display name |
| kind | `FIXED` (never moved by a rebuild), `FLEXIBLE` (can move and shrink), `OPTIONAL` (can move, shrink or be dropped) |
| start, end | Current interval. Actual length = end − start, which may differ from `duration` after shrinking or an overrun |
| duration | Target full length in minutes. The engine never changes it |
| minDuration | Shortest acceptable length in minutes. The engine never changes it |
| priority | `HIGH`, `NORMAL` or `LOW`. Required for FLEXIBLE and OPTIONAL; ignored for FIXED |
| window | Optional. Each bound is either absolute (a minute) or relative to bedtime (bedtime − N minutes) |
| after | Optional id of the block this one must follow |
| status | `PLANNED`, `IN_PROGRESS`, `DONE`, `SKIPPED`, `MISSED`, `DROPPED`, `UNPLACED` |

The engine models carry no created/updated timestamps; the persistence layer (`:core:data`) adds those.

**Occupying blocks** take up time and can't be overlapped: any block that is `DONE` or `IN_PROGRESS`, any `FIXED` block that is `PLANNED`, and a block pinned by `move_block` during that command's rebuild. Blocks that are `SKIPPED`, `MISSED`, `DROPPED` or `UNPLACED` keep their last times for display only and occupy nothing.

**Candidates** are the blocks a rebuild may place: `FLEXIBLE` or `OPTIONAL` blocks whose status is `PLANNED`, other than a pinned block. Blocks that are `MISSED`, `DROPPED` or `UNPLACED` stay out of every later rebuild until `retry_block` makes them `PLANNED` again. This keeps rebuilds stable: the plan doesn't reshuffle each time the app rebuilds.

**Original start** of a candidate = its `start` in the input Day. For a block added through `add_block`'s fallback rebuild, it's the requested start, or the horizon start if none was given.

**Day:** date, wake time, bedtime, blocks. **Settings:** `allowPastBedtime` (default off; when on, the horizon ends 60 minutes after bedtime).

**Proposal (output):** `status`; `day` (the new day); `previousDay` (the input day, used for undo); `changes` (one entry per block that differs from the input); `conflicts` (ids of overlapping occupying blocks, for `CONFLICT`); `errors` (typed validation errors, for `REJECTED`). Validation problems are returned as `REJECTED` proposals, never thrown. Nothing is applied until the caller applies the proposal.

| Status | Meaning |
| --- | --- |
| `OK` | Done; everything that could be placed was placed |
| `INFEASIBLE` | At least one block is `UNPLACED`. The proposal shows the attempt; the app asks the user what to skip, shorten, or whether to allow running past bedtime |
| `CONFLICT` | Occupying blocks overlap after the command's direct edit. No rebuild ran |
| `NO_HORIZON` | No time left today. No rebuild ran |
| `REJECTED` | Invalid input or command. `day` equals the input day |

**Change entry:** `blockId`, `types` (a set), `reason` (exactly one), optional `causeBlockId`.

Change types: `ADDED`, `MOVED` (start changed), `SHRUNK` (actual length decreased), `EXTENDED` (end moved later by an overrun), `DROPPED`, `UNPLACED`, `MISSED`, `SKIPPED`, `MADE_FIXED`, `RESTORED` (by undo). `ADDED` and `RESTORED` are exclusive: a block with either has a set containing only that type, whatever else differs.

**Reason, picked by the first rule that applies:**

1. The block is the command's target → the command's reason: `USER_MOVE`, `USER_SKIP`, `USER_ADD`, `SET_FIXED`, `OVERRUN`, `RETRIED`. For `undo`, every changed block gets `UNDO`.
2. Became `MISSED` → `WINDOW_PASSED` (flexible or optional) or `FIXED_EXPIRED` (fixed).
3. Became `DROPPED` → `DROPPED_TO_FIT`. Became `UNPLACED` → `NO_ROOM`.
4. Shortened in step 5.4 as another block's dependency → `DEPENDENCY_SHRINK`.
5. Shortened in step 5.5 → `SHRUNK_TO_FIT`.
6. Ends after bedtime → `PAST_BEDTIME`.
7. Its dependency also appears in this proposal's changes → `FOLLOWS_DEPENDENCY`.
8. Otherwise, by trigger: `late_wake` → `LATE_WAKE`; `set_bedtime` → `BEDTIME_CHANGED`; anything else → `DISPLACED`, with `causeBlockId` = the earliest-starting block in the new day (occupying, or placed in this rebuild), other than the block itself, that overlaps the block's original interval; ties broken by id; null if there is none.

**Rejection error codes:** `INVALID_INPUT` (with a sub-code from the Validation section), `OFF_GRID`, `UNKNOWN_BLOCK` (a command's target id isn't in the day), `INVALID_TARGET`, `NOT_LATER`, `OVERLAPS_LOCKED`, `OUTSIDE_WINDOW`, `IN_THE_PAST`, `PAST_BEDTIME_NOT_ALLOWED`, `DEPENDENCY_VIOLATION`.

## How a command runs

The steps run in this order; the first one that returns decides the status. So precedence is `REJECTED` > `CONFLICT` > `NO_HORIZON` > `INFEASIBLE` > `OK`.

1. **Validate** the input day and the command (see Validation, and the command's preconditions). Any failure → `REJECTED`, day unchanged.
2. **Apply the command's direct edit:** new wake time, new bedtime, overrun end, pinned move, added block, or status change. `skip_block`, `set_fixed`, `undo` and an `add_block` that succeeds on its fast path stop here with `OK`.
3. **Conflict check:** if any two occupying blocks overlap → `CONFLICT`. The proposal contains the direct edit and nothing else, and lists the overlapping ids.
4. **Horizon.** Start = the later of `now` and wake time, rounded up to the next 5 minutes. End = bedtime, plus 60 minutes if `allowPastBedtime` is on, capped at 2880. If start ≥ end → `NO_HORIZON`, with only the direct edit.
5. **Rebuild** (next section). Any `UNPLACED` → `INFEASIBLE`, otherwise `OK`.
6. **Changes:** compare every block with the input day; record types and a reason for each one that differs.

## How a rebuild works

1. **Expire fixed blocks.** A `FIXED` block that is `PLANNED` and has ended (end ≤ horizon start) becomes `MISSED` (`FIXED_EXPIRED`).
2. **Window check.** A candidate becomes `MISSED` (`WINDOW_PASSED`) if, even at its minimum duration, it can't fit between the later of horizon start and its window start, and the earlier of horizon end and its window end. Other blocks are ignored for this check.
3. **Order.** Sort the remaining candidates by priority (HIGH, NORMAL, LOW), then original start, then id. Then walk that list once, dependency-first: before visiting a block, visit its dependency if that dependency is an unvisited candidate (recursively, for chains). Each block is visited exactly once; unrelated blocks keep their sorted order.
4. **Dependencies.** For a block whose dependency is occupying or already placed in this rebuild, the dependency's end is a lower bound on its start. A dependency that is `SKIPPED`, `MISSED`, `DROPPED` or `UNPLACED` is ignored.
5. **Place each block in the visit order.** Its allowed range starts at the latest of horizon start, window start and dependency end. It ends at the earliest of horizon end, window end, and the start of any occupying block that depends on it. Occupied time = occupying blocks plus blocks already placed in this rebuild. Full length = `duration`.
   1. **Keep** its current interval if that's inside the allowed range, free, and its length is between minDuration and duration (a shrunk block keeps its shrunk interval; nothing tries to restore it).
   2. **Later:** otherwise, the earliest start at or after its original start where the full length fits, free and inside the range.
   3. **Earlier:** otherwise, the latest start before its original start where the full length fits, free and inside the range.
   4. **Shorten its dependency:** otherwise, if its direct dependency is a candidate placed earlier in this rebuild with lower priority, shorten that dependency from its end in 5-minute steps (not below the dependency's minimum) until this block fits at full length by steps 2–3. If it can't fit at full length even with the dependency at its minimum, leave the dependency at its minimum and go to step 5. If step 5 also fails, restore the dependency to its length before this step and go to step 6. Only the direct dependency is ever shortened.
   5. **Shrink:** otherwise, consider each maximal free gap inside the allowed range that's at least the minimum duration. Pick the gap with the most usable time. Ties go first to gaps that end after the original start (at or after it, or spanning it), earliest first; then to gaps entirely before it, latest first. Length = full length or the usable time rounded down to 5 minutes, whichever is less. Place it at the start of a gap that ends after the original start, or ending at the end of a gap entirely before it.
   6. **Give up:** otherwise an `OPTIONAL` block becomes `DROPPED` and a `FLEXIBLE` block becomes `UNPLACED`. Both keep their last times for display and stay out of later rebuilds until `retry_block`.

Because higher-priority blocks are placed first, lower-priority ones get whatever time is left. That's how "shrink and drop the least important first" works without a separate pass.

## Commands

Every command that names a block is `REJECTED` with `UNKNOWN_BLOCK` if the id isn't in the day.

| Command | Preconditions (otherwise `REJECTED`) | Direct edit, then |
| --- | --- | --- |
| `rebuild_day` | — | None; rebuild |
| `late_wake(time)` | — | Sets the day's wake time. The caller passes `now` = time. Rebuild |
| `overrun(id, newEnd)` | Target is `IN_PROGRESS` (`INVALID_TARGET`); newEnd on grid and later than its current end (`NOT_LATER`) | Sets the target's end; rebuild |
| `retry_block(id)` | Target is `FLEXIBLE` or `OPTIONAL`, status `PLANNED`, `MISSED`, `DROPPED` or `UNPLACED` | Sets it `PLANNED` and makes it a candidate; rebuild. Used for "I didn't do this yet, fit it back in" and for putting a dropped or unplaced block back after the user frees time |
| `move_block(id, start)` | Target is `FLEXIBLE` or `OPTIONAL`, status `PLANNED`, `MISSED`, `DROPPED` or `UNPLACED` (`INVALID_TARGET`). The new interval is start to start + `duration` and must: begin at or after the horizon start (`IN_THE_PAST`), end by the horizon end (`PAST_BEDTIME_NOT_ALLOWED`), sit inside its window (`OUTSIDE_WINDOW`), begin at or after its dependency's end when the dependency is occupying or `PLANNED` (`DEPENDENCY_VIOLATION`), and not overlap any occupying block (`OVERLAPS_LOCKED`). Checks run in that order; the first failure is reported | Sets the interval and status `PLANNED`, and pins it for this rebuild only; rebuild |
| `skip_block(id)` | Status `PLANNED`, `MISSED`, `DROPPED` or `UNPLACED`; any kind | Sets `SKIPPED`. No rebuild; nothing else moves |
| `add_block(block, requestedStart?)` | New unique id; the block passes validation; status `PLANNED` | `FIXED`: added, then conflict check and rebuild. `FLEXIBLE` or `OPTIONAL`: fast path first — the earliest start at or after the latest of requested start, horizon start, window start and dependency end, where the full length fits inside its window and the horizon without overlapping any block that is `PLANNED`, `IN_PROGRESS` or `DONE`. If found, it's placed there with `OK` and nothing else changes. If not, a full rebuild runs with the new block as a candidate |
| `set_fixed(id)` | Target is `FLEXIBLE` or `OPTIONAL` and `PLANNED` (`INVALID_TARGET`); its interval doesn't overlap an occupying block (`OVERLAPS_LOCKED`) | Sets kind `FIXED` at its current interval. No rebuild |
| `set_bedtime(time)` | On grid; later than the wake time; at most 2880 | Sets bedtime; relative windows move with it; rebuild |
| `undo(previousDay)` | `previousDay` passes validation | Returns `previousDay` with `OK`; every block that differs (including blocks that appear or disappear) gets `RESTORED`, `UNDO`. The caller keeps the history; a repeated undo passes an older day. Undo is offered only after a proposal was applied |

## Validation and guarantees

**Rejected as `INVALID_INPUT`, with these sub-codes:**

| Sub-code | Case |
| --- | --- |
| `DUPLICATE_ID` | Two blocks share an id |
| `UNKNOWN_DEPENDENCY` | `after` names an id not in this day |
| `SELF_DEPENDENCY` | `after` names the block itself |
| `DEPENDENCY_CYCLE` | Dependencies form a cycle |
| `INVALID_DURATION` | duration ≤ 0 or not a multiple of 5 |
| `INVALID_MIN_DURATION` | minDuration ≤ 0, not a multiple of 5, or above duration |
| `EMPTY_INTERVAL` | end ≤ start |
| `INVALID_WINDOW` | After resolving relative bounds, window start isn't before window end, or the window is shorter than minDuration |
| `MISSING_PRIORITY` | A FLEXIBLE or OPTIONAL block has no priority |
| `MULTIPLE_IN_PROGRESS` | More than one block is `IN_PROGRESS` |
| `TIME_OUT_OF_RANGE` | Any time, including bedtime, outside 0–2880 |
| `WAKE_NOT_BEFORE_BEDTIME` | Wake time isn't before bedtime |

An interval whose length differs from `duration` is valid (it may have been shrunk or overrun); the Keep step simply won't keep a candidate whose length is outside its bounds.

**Guarantees, each covered by property-based tests on random valid days:**

- A rebuild never changes the start, end, kind, priority or window of an occupying block. Only two exceptions: the command's own target, and `FIXED` blocks that expire, whose status becomes `MISSED`.
- Every block placed by a rebuild is inside the horizon and its window, starts at or after its dependency's end (when the dependency is occupying or placed), overlaps no occupying or placed block, and has a length between its minDuration and its duration.
- Occupying blocks may sit outside their window or past bedtime; that's not an error. Overlapping occupying blocks give `CONFLICT`.
- Same input, same output. No clock reads, randomness or I/O.
- Idempotence: for a proposal whose command ran a rebuild (step 5) and returned `OK` or `INFEASIBLE`, applying it and running `rebuild_day` on the result with the same `now` returns no changes. Commands that stop at step 2 (`skip_block`, `set_fixed`, `undo`, fast-path `add_block`) don't rebuild, so this doesn't apply to them.
- `undo` with the proposal's `previousDay` restores the day exactly, field for field.

## Base days

Every example starts from one of these days. Unless an example says otherwise, all blocks are `PLANNED`, `allowPastBedtime` is off, and the expected status is `OK`.

### Day A · Office workday

Date 2026-11-16 (Monday). Wake 07:00, bedtime 23:00.

| Id | Block | Kind | Planned | Full / min | Priority | Window | After |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A1 | Morning routine | FLEXIBLE | 07:00–07:30 | 30 / 15 | NORMAL | 07:00–08:15 | — |
| A2 | Breakfast | FLEXIBLE | 07:30–07:50 | 20 / 10 | HIGH | 07:00–08:15 | A1 |
| A3 | Commute to work | FIXED | 08:15–09:00 | 45 / 45 | — | — | — |
| A4 | Work | FIXED | 09:00–18:00 | 540 / 540 | — | — | — |
| A5 | Commute home | FIXED | 18:00–18:45 | 45 / 45 | — | — | — |
| A6 | Gym | FLEXIBLE | 19:00–20:00 | 60 / 40 | NORMAL | 18:45–21:30 | — |
| A7 | Dinner | FLEXIBLE | 20:15–20:45 | 30 / 20 | HIGH | 19:00–22:00 | — |
| A8 | Reading | OPTIONAL | 21:00–21:30 | 30 / 15 | LOW | — | — |
| A9 | Plan tomorrow | FLEXIBLE | 22:30–22:40 | 10 / 5 | LOW | — | — |
| A10 | Wind down | FLEXIBLE | 22:45–23:00 | 15 / 10 | NORMAL | bedtime − 60 to bedtime | — |

### Day B · Student evening

Date 2026-11-17. Wake 07:00, bedtime 23:00. The evening is exactly full: no free time after 19:30.

| Id | Block | Kind | Planned | Full / min | Priority | Window | After |
| --- | --- | --- | --- | --- | --- | --- | --- |
| B1 | Evening class | FIXED | 18:00–19:30 | 90 / 90 | — | — | — |
| B2 | Dinner | FLEXIBLE | 19:30–20:00 | 30 / 20 | HIGH | 19:00–21:30 | — |
| B3 | Study | FLEXIBLE | 20:00–21:30 | 90 / 60 | HIGH | — | — |
| B4 | Series | OPTIONAL | 21:30–22:15 | 45 / 30 | LOW | — | — |
| B5 | Call family | FLEXIBLE | 22:15–22:35 | 20 / 15 | NORMAL | — | — |
| B6 | Wind down | FLEXIBLE | 22:35–23:00 | 25 / 15 | NORMAL | bedtime − 60 to bedtime | — |

### Day C · Saturday

Date 2026-11-21 (Saturday). Wake 08:00, bedtime 23:00.

| Id | Block | Kind | Planned | Full / min | Priority | Window | After |
| --- | --- | --- | --- | --- | --- | --- | --- |
| C1 | Laundry | FLEXIBLE | 09:00–09:45 | 45 / 30 | NORMAL | — | — |
| C2 | Gym | FLEXIBLE | 10:00–11:00 | 60 / 45 | HIGH | 08:00–13:00 | — |
| C3 | Shower | FLEXIBLE | 11:00–11:20 | 20 / 15 | HIGH | — | C2 |
| C4 | Lunch with friends | FIXED | 13:00–14:30 | 90 / 90 | — | — | — |
| C5 | Groceries | OPTIONAL | 11:30–12:15 | 45 / 30 | LOW | 09:00–20:00 | — |

## Examples

How to read them:

- "Given" is the starting state. "As example N" means example N's Given state, not its result.
- For `late_wake(t)`, `now` = t.
- "Then" lists every changed block as `id → new interval {types} REASON (cause)`. Blocks not listed must be unchanged in every field. Status is `OK` unless stated.

### Examples 1–9 · Late starts (Day A)

| # | Given | When | Then |
| --- | --- | --- | --- |
| 1 | Day A, now 06:30 | `rebuild_day` | No changes |
| 2 | Day A with example 3's proposal applied, now 07:20 | `rebuild_day` | No changes (idempotent) |
| 3 | Day A | `late_wake(07:20)` | A1 → 07:20–07:50 {MOVED} LATE_WAKE. A2 → 07:50–08:10 {MOVED} FOLLOWS_DEPENDENCY |
| 4 | Day A | `late_wake(07:35)` | A1 → 07:35–07:55 {MOVED, SHRUNK} DEPENDENCY_SHRINK. A2 → 07:55–08:15 {MOVED} FOLLOWS_DEPENDENCY. Breakfast is HIGH, so the NORMAL routine gives up time first |
| 5 | Day A | `late_wake(07:50)` | A1 → 07:50–08:05 {MOVED, SHRUNK} DEPENDENCY_SHRINK. A2 → 08:05–08:15 {MOVED, SHRUNK} SHRUNK_TO_FIT. Both at their minimum |
| 6 | Day A | `late_wake(08:00)` | `INFEASIBLE`. A1 → 08:00–08:15 {MOVED, SHRUNK} SHRUNK_TO_FIT. A2 {UNPLACED} NO_ROOM (keeps 07:30–07:50 for display). The app asks: skip Morning routine, or skip Breakfast. Choosing the first sends `skip_block(A1)` then `retry_block(A2)`, which places Breakfast at 08:00–08:15 |
| 7 | Day A | `late_wake(08:05)` | A1 {MISSED} WINDOW_PASSED. A2 → 08:05–08:15 {MOVED, SHRUNK} SHRUNK_TO_FIT (its dependency is ignored because A1 is missed) |
| 8 | Day A | `late_wake(09:30)` | A1 {MISSED} WINDOW_PASSED. A2 {MISSED} WINDOW_PASSED. A3 {MISSED} FIXED_EXPIRED. A4 unchanged (still running) |
| 9 | Day A | `late_wake(07:23)` | Horizon starts 07:25. A1 → 07:25–07:55 {MOVED} LATE_WAKE. A2 → 07:55–08:15 {MOVED} FOLLOWS_DEPENDENCY |

### Examples 10–17 · Overruns, bedtime and missed blocks

| # | Given | When | Then |
| --- | --- | --- | --- |
| 10 | Day A, A1–A5 DONE, A6 IN_PROGRESS (19:00–20:00), now 20:00 | `overrun(A6, 20:25)` | A6 → 19:00–20:25 {EXTENDED} OVERRUN. A7 → 20:25–20:55 {MOVED} DISPLACED (A6) |
| 11 | As example 10 | `overrun(A6, 21:20)` | A6 → 19:00–21:20 {EXTENDED} OVERRUN. A7 → 21:20–21:50 {MOVED} DISPLACED (A6). A8 → 21:50–22:20 {MOVED} DISPLACED (A6) |
| 12 | Day B, B1 IN_PROGRESS, now 19:30 | `overrun(B1, 20:00)` | B1 → 18:00–20:00 {EXTENDED} OVERRUN. B2 → 20:00–20:30 {MOVED} DISPLACED (B1). B3 → 20:30–22:00 {MOVED} DISPLACED (B2). B4 {DROPPED} DROPPED_TO_FIT. 22:00–22:15 stays free |
| 13 | Day B, B1 IN_PROGRESS, now 20:30 | `overrun(B1, 21:00)` | `INFEASIBLE`. B1 → 18:00–21:00 {EXTENDED} OVERRUN. B2 → 21:00–21:30 {MOVED} DISPLACED (B1). B3 → 21:30–23:00 {MOVED} DISPLACED (B1). B4 {DROPPED} DROPPED_TO_FIT. B5 {UNPLACED} NO_ROOM. B6 {UNPLACED} NO_ROOM |
| 14 | Day A, A1–A7 DONE, A9 DONE at 21:40–21:50 (done early), A8 and A10 PLANNED, now 22:30 | `rebuild_day` | A8 → 22:30–22:45 {MOVED, SHRUNK} SHRUNK_TO_FIT. Nothing goes past 23:00 |
| 15 | As example 14, `allowPastBedtime` on | `rebuild_day` | A8 → 23:00–23:30 {MOVED} PAST_BEDTIME. A full-length slot beats shrinking |
| 16 | Day A, A1–A5 DONE, now 18:50 | `set_bedtime(22:00)` | A10's window becomes 21:00–22:00. A10 → 21:45–22:00 {MOVED} BEDTIME_CHANGED. A9 → 21:35–21:45 {MOVED} BEDTIME_CHANGED |
| 17 | Day A, A1–A7 DONE, A8 PLANNED (21:00–21:30, not done), now 21:40 | `retry_block(A8)` | A8 → 21:40–22:10 {MOVED} RETRIED |

### Examples 18–25 · Dependencies and commands

| # | Given | When | Then |
| --- | --- | --- | --- |
| 18 | Day C, now 08:30 | `move_block(C2, 11:30)` | C2 → 11:30–12:30 {MOVED} USER_MOVE. C3 → 12:30–12:50 {MOVED} FOLLOWS_DEPENDENCY. C5 → 14:30–15:15 {MOVED} DISPLACED (C2) |
| 19 | Day C, now 09:50 | `skip_block(C2)` | C2 {SKIPPED} USER_SKIP. Nothing else changes |
| 20 | Day C, C1 DONE, C2 IN_PROGRESS (10:00–11:00), now 11:00 | `overrun(C2, 11:30)` | C2 → 10:00–11:30 {EXTENDED} OVERRUN. C3 → 11:30–11:50 {MOVED} FOLLOWS_DEPENDENCY. C5 → 11:50–12:35 {MOVED} DISPLACED (C3) |
| 21 | Day C, C1 DONE, C2 IN_PROGRESS (10:00–11:00), now 12:00 | `overrun(C2, 12:50)` | C2 → 10:00–12:50 {EXTENDED} OVERRUN. C3 → 14:30–14:50 {MOVED} FOLLOWS_DEPENDENCY. C5 → 14:50–15:35 {MOVED} DISPLACED (C2) |
| 22 | Day C, now 08:30 | `add_block` FIXED D1 "Doctor" 10:00–12:30 | D1 {ADDED} USER_ADD. C2 → 09:00–10:00 {MOVED} DISPLACED (D1). C3 → 12:30–12:50 {MOVED} FOLLOWS_DEPENDENCY. C1 → 14:30–15:15 {MOVED} DISPLACED (C2). C5 → 15:15–16:00 {MOVED} DISPLACED (D1) |
| 23 | Day C, C1–C4 DONE, C5 PLANNED, now 19:50 | `rebuild_day` | C5 {MISSED} WINDOW_PASSED |
| 24 | Day A, A1–A3 DONE, now 10:00 | `add_block` FLEXIBLE N1 "Call mom" 20 / 15, NORMAL, requested 20:00 | Fast path. N1 → 21:30–21:50 {ADDED} USER_ADD. Nothing else changes |
| 25 | Day B, B1 IN_PROGRESS, now 18:30 | `add_block` FLEXIBLE N2 "Assignment" 45 / 30, NORMAL, no requested start | No fast-path slot, so a full rebuild runs (N2's original start = 18:30). N2 → 21:30–22:15 {ADDED} USER_ADD. B4 {DROPPED} DROPPED_TO_FIT |

### Examples 26–30 · Rejections, conflicts, undo and edge cases

| # | Given | When | Then |
| --- | --- | --- | --- |
| 26 | Day A, now 10:00 | `move_block(A8, 12:00)` | `REJECTED`, error `OVERLAPS_LOCKED` (A4). Day unchanged. (Reading has no window, so the overlap check is the first to fail) |
| 27 | Day A, A1–A4 DONE, A5 IN_PROGRESS, A6 kind FIXED (19:00–20:00), now 18:50 | `overrun(A5, 19:20)` | `CONFLICT`, conflicts [A5, A6]. A5 → 18:00–19:20 {EXTENDED} OVERRUN. Nothing else changes. The app offers: start the gym at 19:20, make it flexible again, or skip it |
| 28 | Example 11's proposal applied | `undo(previousDay of that proposal)` | The day equals example 11's Given state exactly. A6 → 19:00–20:00, A7 → 20:15–20:45, A8 → 21:00–21:30, each {RESTORED} UNDO |
| 29 | Day A, A1–A5 DONE, A6 IN_PROGRESS (19:00–20:00), now 19:30 | `rebuild_day` | No changes. The in-progress block is locked |
| 30 | Day A, A1–A7 DONE, A8–A10 PLANNED, now 23:10 | `rebuild_day` | `NO_HORIZON`, no changes. (At the daily rollover, outside the engine, unfinished blocks become MISSED.) |

**Turning these into tests:** each example is one test that builds the base day, applies the Given state, runs the command and asserts the status plus, for every block, its interval, duration, minDuration, kind, status, and change entry (types, reason, cause). Blocks not listed must be asserted unchanged. If an example and the rules ever disagree, stop and report it; don't change a test to match the code.

## Answers to the v0.1 review

| Point | Resolution |
| --- | --- |
| 1 · Example 14 | Fixed: A9 was done early (21:40–21:50), so 22:30–22:45 is free |
| 2 · Example 29 | Already said "A1–A5 DONE" in v0.1; unchanged. Example 27 and 30 now state their past blocks explicitly too |
| 3 · Locked blocks never change | Rewritten guarantee: occupying blocks keep start, end, kind, priority and window, except the command's target and expiring FIXED blocks |
| 4 · SKIPPED occupied or not | SKIPPED, MISSED, DROPPED and UNPLACED occupy nothing (Model, Occupying blocks) |
| 5 · Guarantees vs locked blocks | Placement guarantees apply to blocks placed by a rebuild; locked blocks outside window or bedtime are fine; move checks its dependency |
| 6 · CONFLICT and precedence | How a command runs: steps 1–6; CONFLICT and NO_HORIZON return the direct edit only; precedence stated |
| 7 · DROPPED and UNPLACED | Added as statuses; keep last times; ignored as dependencies; retried only via `retry_block` (changed in v0.3) |
| 8 · Duration meaning | duration and minDuration are targets the engine never changes; actual length = end − start; shrunk blocks keep their slot |
| 9 · Original start | Start in the input day; pins last one command |
| 10 · Change entries and reasons | Types are a set, one reason by precedence, new codes added; omitted status means OK |
| 11 · Undo | `undo(previousDay)`; the caller keeps history |
| 12 · Dependency order | Stable dependency-first walk, each block once; MISSED never retried unless `retry_block` |
| 13 · Dependency shrinking | Step 5.4: direct dependency only, restore on failure, minimum when needed |
| 14 · Shrink ranking | Step 5.5: most usable time first, then the stated tie-breaks |
| 15 · Command preconditions | Commands table, with specific error codes |
| 16 · add_block contract | Commands table; caller supplies id; fallback original start defined |
| 17 · Time and validation | Time section (minutes since 00:00, past midnight up to 2880, half-open intervals); Validation list; REJECTED, not exceptions |
| 18 · Fixtures | Dates and Day B wake added; now = wake time for late_wake; "as example N" = its Given state |
| 19 · Conventions | No timestamps in engine models; Android lint deferred until an Android module exists (AGENTS.md updated) |

## Answers to the v0.2 review

| Point | Resolution |
| --- | --- |
| 1 · Example 26 | Correct: moving Gym to 12:00 fails the window check first. The example now moves Reading (no window), which fails `OVERLAPS_LOCKED` as intended |
| 2 · INFEASIBLE idempotence | Dropped and unplaced blocks are no longer candidates; they come back only through `retry_block`. After a rebuild, every candidate sits in a valid slot and Keep holds it, so a second rebuild changes nothing. Your X/Y case: Y stays UNPLACED and X keeps 10:00–11:00 |
| 3 · Commands that don't rebuild | Idempotence now applies only to proposals whose command ran a rebuild |
| 4 · Keep and lengths | Keep requires length between minDuration and duration; validation stays permissive. Occupying blocks are unaffected |
| 5 · Range past 2880 | Horizon end is capped at 2880; bedtime must be ≤ 2880 |
| 6 · ADDED and RESTORED | Exclusive types, as you proposed |
| 7 · Sub-codes and causes | Sub-codes named in the Validation table; `UNKNOWN_BLOCK` added for missing command targets; DISPLACED cause excludes the block itself, ties by id |
