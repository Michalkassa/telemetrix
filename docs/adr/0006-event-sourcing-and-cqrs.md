# 6. Event sourcing and CQRS for replay, standings and analytics

- **Status:** Accepted
- **Date:** 2026-10-12

## Context

Until Step 35 every read model was worked out straight from the imported tables, each in its own way:
the timeline ran its detectors over the raw data and kept the result in memory, `RaceStateEngine` built
its fold inputs from the same tables, `championship_snapshots` and the `driver_race_results` view read
`session_results`. A re-import overwrote the tables, so what the app had shown before was gone, and there
was no single record of *what happened* in a race to rebuild from, query, or step through.

`plan.md` asks for event sourcing (easier replay reconstruction, time-travel debugging, efficient
playback, historical querying) and CQRS. Step 40 (Kafka) also wants events to already be the interface.

## Decision

**An append-only event store is the source of truth; every read model is a projection of it.**

- `event_store` (V14): one row per event — stream (`SESSION` *id*, `RACE_RESULTS` *race id*), a
  `generation`, a `seq` within it, a `type`, `occurred_at` (null: not at a moment) and a JSONB payload.
  A trigger rejects `UPDATE`, `DELETE` and `TRUNCATE`.
- **Generations instead of rewrites.** When a race is imported again, its stream is worked out again
  and, if it differs, appended as the next generation. Readers use the latest (the `current_events`
  view, V15); earlier ones stay queryable. An unchanged stream appends nothing.
- **Write side** (`StreamRecorder`s): `state.SessionRecorder` derives a session's stream
  (`SessionEvents.derive`: the stream's opening, the starting tyres, then flag / pit / tyre / penalty /
  sector-flag / DRS / weather events and the detectors' `RACE_EVENT_DETECTED` events, in time order)
  after each race import. `result.ResultsRecorder` derives each race's results stream (its wet/dry
  conditions, then a line per car per Grand Prix and sprint) after the history import and each race
  import. A stream never recorded (a race opened before the store existed) is recorded on first read.
  Detected events are published (`RaceEventDetected`) when first recorded.
- **Read side** (`Projection`s), each rebuilt from events alone: the timeline (in memory), the race
  state engine and its snapshots (a fold over the session's events — snapshots are only a cache),
  `championship_snapshots`, and the `driver_race_results` materialized view (now SQL over the results
  events). `POST /api/admin/projections/rebuild` throws them all away and rebuilds them.
- **Time travel:** `GET /api/sessions/{id}/events/{seq}/state` is the state right after event *n*;
  `GET /api/sessions/{id}/events` queries the log by type, time window and generation.

**What stays outside the events:** the cars' *positions* come from the timing points (ADR 0004); they
are a continuous signal sampled ~4 times a lap, not state changes, and are still read from the timing
tables. Pages that show the imported data as such (a race's results table, lap times, telemetry,
qualifying, constructor stats, comparisons) still query those tables, as do the "if they finished now"
standings for the round's sprint points.

## Consequences

- Deleting every read model and the imported data the events came from, then rebuilding, gives the
  same answers (`EventSourcingFlowTest`, the Step 36 check).
- The store only grows: a re-import with changes adds a generation. Small — a Grand Prix's session
  stream is a few thousand rows, a race's results a few dozen. Archiving old generations can come later.
- Events are stored under stable type names (`SessionEventCodec`), not class names, so classes can be
  renamed; changing a payload's shape needs an upcaster or a new type.
- Tests can't truncate the store, so stream ids (session and race ids) get reused across test classes
  that restart identities. That's safe because recording compares with the latest generation and
  appends whenever the data differs; tests must not assume a stream's generation is 1.
- Step 40 can publish `event_store` appends to Kafka as they are; the projections become consumers.
