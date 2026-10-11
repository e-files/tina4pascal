# plan/ — how Tina4Pascal maintenance is planned and recorded

Two kinds of document live here. Both exist so the *why* of a change survives long
after the diff, and so the next maintainer (or future-you) can trust what's done.

## 1. Plan docs — `plan/<task>.md`

One per substantive maintenance task (a feature port, a toolchain fix, a cross-
platform verification pass). Write it **before** the work when you can; keep it
honest as you go. Follow the shape the existing plans use:

```markdown
# Task: <name>

**Outcome:** the one concrete result this task commits to.

## Scope
- [ ] checklist of the steps, ticked as done

## Parity
| Surface | macOS/iOS | Android | Windows/Linux |
|---|---|---|---|
| <feature> | ✅/—/… | … | … |

## Tests (real)
- [ ] the ACTUAL commands run and their result (suite 20/20, compliance 222/222…).
      "Tests (real)" means real — never tick a box you didn't run.

## Bugs
- [ ] anything found + whether fixed

## Commits
- <sha>  <subject>

## Status: In progress | Complete
```

The non-negotiable is **Tests (real)**: the project's whole credibility is that a
claimed pass actually passed. See the verification harnesses in MASTER.md.

## Repository scope: keep framework documentation app-agnostic

Tina4Pascal is a reusable framework repository, not an application repository.
Plans, ADRs, README files, and framework documentation must not contain
consumer-app-specific names, bundle identifiers, screenshots, file paths, UI
labels, or acceptance criteria. Describe the framework behavior, shell contract,
portable regression test, or generic consumer integration instead. App-specific
details belong in the consuming application's repository and may be referenced
only as external context when necessary; they must not become part of the
Tina4Pascal plan or verification record.

## 2. ADRs — `plan/adr/NNNN-title.md`

An Architecture Decision Record captures a **decision**, not a task: a design
boundary, a platform constraint, a pitfall-driven rule. Write one when a choice
will outlive the commit that made it — when someone later will ask "why on earth
is it done this way?".

- Number sequentially (`0001`, `0002`, …); never renumber.
- Copy [`adr/0000-template.md`](adr/0000-template.md): Status, Context, Decision,
  Consequences.
- A reversal is a **new** ADR that marks the old one `Superseded by ADR-NNNN` — the
  history stays readable.

The current ADRs are indexed in [MASTER.md](../MASTER.md).
