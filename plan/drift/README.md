# plan/drift — framework drift tracker

**Drift** = the gap between what Tina4Pascal is *said* to do (README, docs, an app
that assumes a capability) and what the source *actually ships*. This folder is
where that gap is written down the moment it is found, so the next developer can
pick up the pieces instead of re-auditing from scratch.

It complements the rest of `plan/`:
- **ADRs** (`plan/adr/`) record a *decision* that has been made.
- **Plan docs** (`plan/<task>.md`) track *one task* through to done.
- **Drift** (here) is the *standing backlog of truth*: every known gap between
  claim and code, with the evidence, kept honest over time.

## How to use it

1. **`framework-gaps.md`** is the live table — the single source of truth for
   "what's missing". Read it before promising a capability to an app built on the
   engine.
2. When you **find a gap** (a doc/README/app assumes something the source doesn't
   do), add a row: what, status, the file:line evidence that proves it, and which
   milestone it blocks. Cite real source — a gap without evidence is a rumour.
3. When you **close a gap**, don't delete the row: flip it to ✅, link the commit
   or ADR, and move it to the "Recently closed" section. The history is the point.
4. A gap big enough to need a design decision graduates to an **ADR**; a gap big
   enough to need staged work graduates to a **plan doc**. Link both ways.

## Status legend

- 🔴 **Blocking** — a shipped target or a committed milestone can't work without it.
- 🟡 **Optional** — a nicety; a milestone ships without it (note the fallback).
- ✅ **Closed** — shipped; row kept with its commit/ADR for the record.

## The one rule

Evidence or it didn't happen. Every "shipped" and every "missing" claim here
carries a `file:line` (or a failing/absent test). This folder is only useful while
it is *true*; an out-of-date drift tracker is worse than none.
