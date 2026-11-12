---
version: 1
slug: "feature-today"
primary_target: "feature/today"
related_targets: []
---

# Today screen

Mode: Operate. Code-led (native app screen). Owner-pinned direction: editorial
minimalism after the owner's sample stats screen. Inferred without an interview;
the owner asked for unattended work on 2026-11-13.

Audience and job: a student or office worker glancing at the day: what now, how
long is left, what's next, and fixing the day in one tap when it slips (PRD F2, F4).

Constraints: only the engine changes block times; every change is a proposal with
a plain-language, non-AI summary, applied on approval and undoable in one tap.
Skipping is neutral, never red. 48 dp targets, 200% font scale, colour never the
only signal. Bundled fonts only.

## Direction contract

THESIS: The day read like a well-set timetable page: one enormous crimson numeral
says how long is left of what you are doing now; everything else is quiet ink on
paper. Refuses the category's pastel card stack with an icon on every block.

OWN-WORLD: Warm paper ground, near-black warm ink, one crimson for "now" and the
primary action only. Source Serif 4 (display cut for numerals and headings, text
cut for titles), the platform sans for body and labels, tabular figures for clock
times. Hairline rules instead of cards; vertical hairlines split stat columns.
Corners are small (4 dp) and rare.

STORY: The person sees the minutes left and what's next, sees progress without
scrolling, scans the rest of the day in Morning/Afternoon/Evening with free gaps
written as time available, and, when late, taps one button, reads what changes
and why, applies, and can undo.

FIRST VIEWPORT: Top: serif "Wend" wordmark, date at right. Hairline. Hero: the
crimson display numeral (112 sp) with "min left" beside its baseline, the
current activity in serif below, then "Next: Lunch with Sam at 12:30". Hairline.
Three stat columns, Done / Skipped / Left, serif numerals, vertical hairlines.
Hairline. "Schedule" heading and the first timeline rows. Pinned bottom bar: a
filled crimson "I'm running late" button and a text "Rebuild my day" button.

FORM: Owner-pinned editorial stats page (sample screen), translated to a planner;
position 1. concept-seed ran after the build (operate mode); its assignment was not
adopted because the owner pinned the world, which overrides the roll. seed:22b0b40a26e1757e

Signature interaction: the rebuild proposal sheet sets each change as an
editorial before/after line, old time struck through, new time in crimson, with
the reason beneath; Apply, then a snackbar with Undo.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
