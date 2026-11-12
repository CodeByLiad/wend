---
name: Wend
description: A day planner set like a timetable page; warm paper, quiet ink, one crimson for now.
colors:
  crimson: "#A3122C"
  crimson-container: "#F3DEDD"
  on-crimson-container: "#5C0A19"
  paper-ground: "#F7F3EC"
  paper-raised: "#FBF8F3"
  paper-ink: "#1B1714"
  paper-ink-secondary: "#5F5750"
  paper-rule: "#DCD3C7"
  paper-track: "#EAE3D9"
  night-crimson: "#E5485F"
  night-on-crimson: "#1A0408"
  night-crimson-container: "#5C1522"
  night-on-crimson-container: "#FFD9DC"
  night-ground: "#16120F"
  night-raised: "#211C18"
  night-ink: "#F1EBE3"
  night-ink-secondary: "#B5ABA0"
  night-rule: "#3B332D"
  night-track: "#2B2420"
typography:
  display:
    fontFamily: "Source Serif 4 Display, serif"
    fontSize: "112sp"
    fontWeight: 700
    lineHeight: "96sp"
    letterSpacing: "-0.03em"
    fontFeature: "lnum"
  display-stat:
    fontFamily: "Source Serif 4 Display, serif"
    fontSize: "44sp"
    fontWeight: 700
    lineHeight: "48sp"
    letterSpacing: "-0.02em"
    fontFeature: "lnum"
  display-activity:
    fontFamily: "Source Serif 4 Display, serif"
    fontSize: "34sp"
    fontWeight: 700
    lineHeight: "40sp"
    letterSpacing: "-0.015em"
  headline-wordmark:
    fontFamily: "Source Serif 4 Display, serif"
    fontSize: "30sp"
    fontWeight: 700
    lineHeight: "36sp"
    letterSpacing: "-0.01em"
  headline:
    fontFamily: "Source Serif 4 Display, serif"
    fontSize: "26sp"
    fontWeight: 700
    lineHeight: "32sp"
    letterSpacing: "-0.01em"
  title:
    fontFamily: "Source Serif 4, serif"
    fontSize: "20sp"
    fontWeight: 600
    lineHeight: "26sp"
  body-serif:
    fontFamily: "Source Serif 4, serif"
    fontSize: "17sp"
    fontWeight: 400
    lineHeight: "24sp"
  body:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "17sp"
    fontWeight: 400
    lineHeight: "24sp"
  body-medium:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "15sp"
    fontWeight: 400
    lineHeight: "21sp"
  body-small:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "13sp"
    fontWeight: 400
    lineHeight: "18sp"
  label:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "20sp"
  label-medium:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "18sp"
  label-stat:
    fontFamily: "Roboto, system-ui, sans-serif"
    fontSize: "15sp"
    fontWeight: 500
    lineHeight: "20sp"
rounded:
  xs: "2dp"
  sm: "4dp"
  sheet: "16dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
  gutter: "24dp"
  section: "40dp"
components:
  button-primary:
    backgroundColor: "{colors.crimson}"
    textColor: "{colors.paper-raised}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "12dp 24dp"
    height: "52dp"
  button-quiet:
    backgroundColor: "transparent"
    textColor: "{colors.paper-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "12dp 16dp"
    height: "48dp"
  button-outlined-option:
    backgroundColor: "transparent"
    textColor: "{colors.paper-ink}"
    typography: "{typography.headline}"
    rounded: "{rounded.sm}"
    height: "64dp"
  sheet:
    backgroundColor: "{colors.paper-raised}"
    rounded: "{rounded.sheet}"
    padding: "0 24dp 24dp"
  stat-column:
    textColor: "{colors.paper-ink}"
    typography: "{typography.display-stat}"
  timeline-row:
    textColor: "{colors.paper-ink}"
    typography: "{typography.body-serif}"
    padding: "12dp 0"
    height: "56dp"
  change-line:
    backgroundColor: "{colors.paper-raised}"
    textColor: "{colors.paper-ink}"
    typography: "{typography.title}"
    padding: "16dp 0"
  sheet-action:
    textColor: "{colors.paper-ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    height: "56dp"
---

# Design System: Wend

Platform: **android**. Jetpack Compose with Material 3. Material 3 governs structure (Scaffold, ModalBottomSheet, Snackbar, Button, TextButton, OutlinedButton); the brand arrives only through the color scheme, the type scale and the shapes set in `WendTheme`. Light and dark schemes are both first-class; every screen previews in both.

## Overview

**Creative North Star: "The Timetable Page"**

The day is read like a well-set printed timetable: warm paper, near-black warm ink, a serif that does the talking for numbers and headings, and a plain platform sans for everything you merely read. One enormous crimson numeral says how long is left of what you are doing now; everything else stays quiet ink on paper. Density is editorial, not app-like: generous room above headings, tight groups beneath them, and hairline rules doing the work that cards and fills do elsewhere.

The world rejects the category's pastel card stack with an icon on every block. There are no cards, no icons in the timeline, no tinted tiles and no shadows on the page. Hierarchy comes from type size and the serif/sans split, and from crimson's rarity.

**Key Characteristics:**
- Warm paper ground with near-black warm ink; a matching warm night scheme.
- One crimson accent, rationed to the present moment and the one primary action.
- Source Serif 4 (display cut and text cut, bundled) for numerals, wordmark, headings and titles; platform sans for body, labels and metadata.
- Tabular lining figures for every clock time.
- One-pixel hairline rules instead of cards; vertical hairlines split stat columns.
- Small, rare corners (4dp); flat page, the bottom sheet is the only lifted surface.

## Colors

A warm two-ink palette on paper with a single crimson voice; the night scheme is the same family inverted, not a separate world.

### Primary
- **Timetable Crimson** (light `crimson`, dark `night-crimson`): the hero numeral, the "Now" label and the time beside the current block, the single filled action on a screen, and the proposed new time in a change line. On dark, the brighter crimson carries near-black text on its fills (`night-on-crimson`).
- **Blush Container** (`crimson-container` / `night-crimson-container`, with their `on-` inks): mapped to Material's primaryContainer for Material components that ask for it. No Wend surface uses it directly yet.

### Neutral
- **Warm Paper** (`paper-ground` / `night-ground`): the page background, behind the status bar and the pinned action bar alike.
- **Raised Paper** (`paper-raised` / `night-raised`): bottom-sheet surface (surfaceContainerLow) and the text color on crimson fills in light.
- **Warm Ink** (`paper-ink` / `night-ink`): all primary text, the wordmark, headings, quiet-button labels.
- **Secondary Ink** (`paper-ink-secondary` / `night-ink-secondary`): metadata, clock times, "Next:" lines, gap lines, reasons, done and skipped rows and their labels, struck-through old times. Also the Material outline role.
- **Rule** (`paper-rule` / `night-rule`): every hairline, horizontal and vertical (outlineVariant).
- **Track** (`paper-track` / `night-track`): surfaceVariant and surfaceContainerHighest; reserved tonal fill, not used on the Today page.

### Named Rules
**The One Crimson Rule.** Crimson marks only the present (hero numeral, "Now" and its time), the one filled primary action per screen, and a proposed new time. Anything else in crimson is a defect.

**The Neutral Skip Rule.** Skipped, done, missed and dropped are secondary ink with a written label, never crimson. Colour is never the only signal: every state carries a text label ("Now", "Done", "Skipped").

## Typography

**Display Font:** Source Serif 4, display optical cut (SemiBold, Bold), bundled static Latin subset (OFL).
**Title Font:** Source Serif 4, text optical cut (Regular, SemiBold), bundled.
**Body/Label Font:** the platform sans (Material `FontFamily.Default`, Roboto on stock Android).

**Character:** A high-contrast editorial serif sets numbers, names and headings large and heavy; the platform sans stays small, plain and functional underneath, so the serif reads as voice and the sans as information.

### Hierarchy
- **Display** (Bold 700, 112sp, 96sp line, -0.03em, lining figures): the hero numeral only, in crimson. Its descent is trimmed so the activity sits directly under it as one phrase. Grows with font scale to at most 1.25x its 1x size, measured on the resolved pixel size, never below 1x.
- **Display Stat** (Bold 700, 44sp, 48sp line, -0.02em, lining figures): the Done / Skipped / Left numerals.
- **Display Activity** (Bold 700, 34sp, 40sp line): the current activity's name under the hero numeral.
- **Headline Wordmark** (Bold 700, 30sp, 36sp line): the "Wend" wordmark at the head of a screen.
- **Headline** (Bold 700, 26sp, 32sp line): section headings (Morning, Afternoon, Evening, Off plan) and sheet titles; the late-minute option numerals (capped at 1.3x).
- **Title** (text cut SemiBold 600, 20sp, 26sp line): the hero's "min left of" label, wake/bedtime anchors, change-line block names.
- **Body Serif** (text cut Regular 400, 17sp, 24sp line): timeline block titles. Reasons in change lines use the text cut at 15sp.
- **Body** (sans 400, 17sp / 15sp / 13sp): "Next:" lines, sheet bodies and actions, gap lines, metadata, the date.
- **Label** (sans Medium 500, 16sp): button labels. **Label Medium** (14sp): clock-time column and status labels. **Label Stat** (15sp): the stat column labels.

### Named Rules
**The Serif Speaks Numbers Rule.** Numerals, the wordmark and headings use the display cut; titles near body text use the text cut; instructions, metadata and controls use the platform sans. Never set body copy or button labels in the serif.

**The Tabular Clock Rule.** Every clock time uses tabular lining figures (`tnum, lnum`) so times align as a column.

## Layout

A single scrolling column with a 24dp gutter on both sides. Spacing steps are 4 / 8 / 12 / 16 / 24dp, with 40dp of breathing room at the end of the scroll and above the pinned bar. Groups sit tight (4-8dp inside a group); sections get 16dp above their rule and heading.

The Today page reads top to bottom: wordmark and date on one line, a hairline, the hero (numeral, activity, next), a hairline, three equal stat columns split by vertical hairlines, then the timeline in Morning / Afternoon / Evening sections. Timeline rows are a fixed clock column (72dp at 1x) beside a flexible title column, with a status label at the trailing edge. Free gaps are written as time available in secondary ink, aligned to the title column.

A pinned action bar sits on paper at the bottom, under a hairline: the filled primary action takes the remaining width and the quiet action sits beside it.

**Large text.** The clock column widens with font scale (up to 1.6x) and wraps to two lines rather than clipping AM/PM. At font scale 1.5 and above the action bar stacks both buttons full width. Display type is capped by resolved size under Android 14's non-linear scaling, never shrunk below 1x. The layout must hold at 200%.

**Insets.** Edge to edge: paper fills behind the status bar so content never scrolls under system icons; horizontal and bottom safe-drawing insets are respected by the list and the action bar.

## Elevation & Depth

Flat paper. The page uses no shadows and no tonal cards; separation is made by hairline rules and whitespace. The modal bottom sheet is the only lifted surface: raised paper with Material's default sheet treatment over a scrim (black at 60% light, 80% dark). Snackbars use Material's inverse surface (ink on paper inverted).

### Named Rules
**The Hairline Rule.** A one-pixel rule in the rule color is the only separator. No cards, no filled tiles, no dividers in any other color or weight. Vertical hairlines split stat columns.

## Shapes

Corners are small and rare. Buttons and option buttons use 4dp; snackbars inherit 2dp; the bottom sheet rounds only its top corners at 16dp. Everything on the page itself is square: rules, rows and stat columns have no shape at all. The theme's large shape (8dp) is defined but no surface uses it.

## Components

### Buttons
Few, decisive, and type-led.
- **Shape:** gently squared (4dp).
- **Primary:** filled crimson with raised-paper text (near-black on dark), sans Medium 16sp, 12dp × 24dp padding, at least 52dp tall. One per screen or sheet: "I'm running late", "Apply changes".
- **Quiet:** text-only in warm ink, 12dp × 16dp padding, at least 48dp tall. Secondary choices beside or under the primary ("Rebuild my day", "Keep current plan", "Undo last change", "Put back").
- **Outlined option:** square-ish 4dp outlined buttons, at least 64dp tall, holding a serif headline numeral; used for the late-minute choices (10 / 15 / 30 / 45 / 60) and the single "allow past bedtime" alternative.
- **States:** Material 3 default ripple and disabled treatment; no custom hover or elevation.

### Bottom Sheets
- **Surface:** raised paper, top corners 16dp, skips the half-expanded state, scrolls internally.
- **Padding:** 24dp sides, 24dp bottom.
- **Content:** headline title, sans body in secondary ink, then hairline-separated content. Action lists are full-width 56dp text rows in sans body, ink, left-aligned.

### Stat Row (signature)
Three equal columns divided by vertical hairlines: a sans label (Medium 15sp) over a serif display numeral (44sp), in ink. Labels truncate to one line rather than wrap.

### Hero Numeral (signature)
The display numeral in crimson with the title-size label on its baseline, the activity in display activity type beneath, then "Next:" in secondary sans. The whole block is merged for TalkBack with a spoken sentence.

### Timeline Row
Clock time (label medium, tabular, secondary ink; crimson when it is the current block) beside a serif block title and a sans metadata line (duration, Fixed / Optional). A trailing text status: "Now" in crimson, "Done" and "Skipped" in secondary ink. Done and skipped rows dim their titles to secondary ink. Rows are at least 56dp; tap marks done, long press opens the actions sheet.

### Change Line (signature)
Each proposed change set as an editorial before/after: the block name in title serif with the effect ("Moves", "Runs until 11:30 AM") trailing in secondary label; the old time range struck through in secondary ink, then the new range in crimson, both tabular; the plain-language reason beneath in the serif text cut. Lines are separated by hairlines. Apply is followed by a snackbar offering Undo.

## Do's and Don'ts

### Do:
- **Do** separate content with one-pixel hairlines in the rule color; split stat columns with vertical hairlines.
- **Do** keep crimson to the present moment, the one filled action, and proposed new times.
- **Do** set every clock time with tabular lining figures (`tnum, lnum`).
- **Do** give every state a text label; colour is never the only signal.
- **Do** keep touch targets at least 48dp (primary buttons 52dp, rows 56dp).
- **Do** cap display numerals by resolved size under font scaling (hero 1.25x, option numerals 1.3x) and never below 1x; widen and wrap the clock column; stack the action bar at font scale 1.5 and above.
- **Do** bundle fonts; never download them.
- **Do** design and preview every screen in both light and dark.

### Don't:
- **Don't** put timeline blocks or stats in cards, tinted tiles or shadowed containers.
- **Don't** show skipped, missed or dropped work in crimson or any warning colour.
- **Don't** use more than one filled crimson button on a screen.
- **Don't** set body copy, metadata or button labels in the serif.
- **Don't** add icons to timeline rows.
- **Don't** round page content; corners belong to buttons (4dp) and the sheet's top edge (16dp) only.
