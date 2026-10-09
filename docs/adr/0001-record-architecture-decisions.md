# 1. Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

Telemetrix will grow from a single Spring Boot app into a platform with streaming, caching, search
and analytics (`plan.md`). Many choices along the way (WebFlux or MVC, which deploy target, when to
add Kafka) are trade-offs. Months later the code shows *what* was chosen but not *why*, or what was
rejected.

## Decision

Record every significant architecture decision as an ADR in `docs/adr/`, using the short format
from Michael Nygard: **Context → Decision → Consequences**.

- Files are numbered in order: `NNNN-short-title.md`. Numbers are never reused.
- An ADR is not edited once accepted. If a decision changes, a new ADR supersedes it, and the old
  one's status becomes `Superseded by NNNN`.
- "Significant" means hard to reverse, affects several modules, or picks between real
  alternatives. Routine code choices don't need one.
- A step in `STEPS.md` that says "decide in an ADR" is not done until that ADR is written.

## Consequences

- The reasoning behind the architecture is in the repository, next to the code it explains.
- Revisiting a decision starts from the original trade-offs instead of guesswork.
- A small writing cost per decision — keep each ADR to about a page.
