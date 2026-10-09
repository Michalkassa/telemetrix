# 2. Lean infrastructure: PostgreSQL first

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

`plan.md` names a large target stack: PostgreSQL with TimescaleDB, Redis, Kafka, Elasticsearch,
Prometheus/Grafana and Jaeger. Running all of it from day one would mean:

- Every developer machine and every CI run starts six or more containers before a single feature
  exists.
- Each tool brings configuration, failure modes and test setup to learn at the same time as the
  domain itself.
- There is no measured problem yet that any of them would solve, so their design (cache keys,
  topics, indexes) would be guesswork.

PostgreSQL alone can do everything Phases 1–3 need: relational data, time-range queries on
telemetry with indexes, full-text search, and `LISTEN/NOTIFY` for simple events.

## Decision

Start with **PostgreSQL 16 as the only infrastructure**. Add each other component in the
`STEPS.md` step that needs it, when there's a measurable reason:

| Component | Added in | Trigger |
|-----------|----------|---------|
| Redis | Step 38 | Repeated expensive reads (timelines, replay checkpoints) and shared session state across instances |
| TimescaleDB | Step 39 | Telemetry volume makes plain-table time-range queries or storage measurably too slow |
| Kafka | Step 40 | Ingestion and analytics need to be decoupled and scaled independently |
| Elasticsearch | Step 41 | Search needs fuzzy matching and relevance that Postgres full-text can't give |
| Prometheus / Grafana / Jaeger | Step 42 | Enough moving parts that logs alone can't explain behaviour |

Each addition gets its own ADR, recording the measurement that justified it.

## Consequences

- One container to run locally and in CI. Tests stay fast, and Testcontainers needs only
  `postgres:16`.
- Code is written against interfaces in the right places (a cache, an event publisher), so adding
  Redis or Kafka later replaces an implementation rather than rewriting callers.
- Some early code (for example, in-process Spring events instead of Kafka) is deliberately
  temporary and will be revisited in Phase 4.
- The final architecture is the same as in `plan.md`; only the order changes.
