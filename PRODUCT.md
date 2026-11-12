# Product

<!-- impeccable:product-schema 1 -->

> Inferred on 2026-11-13 from `docs/PRD.md`, `docs/architecture.md` and the
> owner's design brief, without an interview (the owner asked for unattended
> work). `docs/PRD.md` stays authoritative for scope; correct this file when it
> disagrees.

## Platform

android

## Users

Students and office workers, English speakers first, on any Android phone from
Android 9. They open Wend in the morning to see the day, glance at it between
things, and come back when the day goes off plan: a late start, a meeting that
ran over, a skipped gym session.

## Product Purpose

Wend plans and tracks a person's day and rebuilds the plan when life gets in the
way. Success is a person who opens it most mornings and trusts it to fix the day
without losing what matters.

## Positioning

A deterministic scheduling engine rebuilds the day by fixed rules (fixed and done
blocks stay, flexible blocks move by priority and shrink toward their minimum,
optional blocks drop, bedtime is protected) and explains every change in plain
words without AI. Everything stays on the phone; there are no accounts, servers,
ads or analytics.

## Operating Context

- Glanced at in short moments, often one-handed, indoors and outdoors.
- Every schedule change arrives as a proposal (show for approval by default),
  is written to a change log and is undoable in one tap.
- Optional on-device AI planner on capable phones; every AI action is also
  reachable through ordinary buttons.

## Capabilities and Constraints

- Only the engine changes planned block times; UI sends commands and applies
  proposals.
- Network use is limited to model downloads, the update-manifest check and the
  Maps hand-off. Fonts and assets must be bundled, never downloaded.
- English only for now, with all strings in resources and RTL-safe layouts.
- Phase 0 (feasibility spike) is the current phase. The owner directed this UI
  work on 2026-11-13 as a design-system prototype ahead of Phase 1.

## Brand Commitments

- Name: Wend by Jinatra. The name and logo are Jinatra trademarks.
- Owner-pinned visual direction (2026-11-13): editorial minimalism, following the
  owner's sample screen (`Sample/08-stats.png` outside the repo): warm paper
  ground, high-contrast serif display with very large numerals, one crimson
  accent, hairline rules, generous whitespace and a plain sans for body text.
- Voice: no guilt. Missing a habit or having a bad day is normal; Wend shows
  what happened and helps adjust, never shames.

## Evidence on Hand

No real user data, testimonials or metrics. Prototype screens use synthetic
sample days that must be labelled as such.

## Product Principles

1. Private by construction.
2. Works fully without AI.
3. No guilt: skipping is neutral, never red.
4. The user is in control: every change visible, explained and undoable.

## Accessibility & Inclusion

TalkBack support, font scaling to 200%, touch targets at least 48 dp, colour is
never the only signal.
