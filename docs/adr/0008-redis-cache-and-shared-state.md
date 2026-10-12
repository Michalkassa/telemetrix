# 8. Redis: a shared cache, watch-together state and rate-limit buckets

- **Status:** Accepted
- **Date:** 2026-10-12

## Context

ADR 0002 deferred Redis until there was a measured reason. By Step 37 the backend keeps three kinds
of state in each instance's memory:

- **Worked-out reads**: each session's timeline and race-state engine (with its snapshots) in maps; the
  replay response isn't kept at all.
- **Watch-together sessions** (ADR 0005): the shared clocks, so they end with a restart and live on one
  instance only.
- **Rate-limit buckets** (ADR 0007): per instance, so N instances allow N times the limit.

Measured on the 2023 Singapore GP (tests on Testcontainers; a cold read is the first after the read
models are dropped, as after a restart):

| Read | Cold | Warm (in memory) |
|------|------|------------------|
| Timeline | 5–19 ms | ~0 ms |
| Snapshots (builds the state engine) | 29–53 ms | ~0 ms |
| Replay response | 13–20 ms, every request (never kept) | — |

On the dev stack, warm requests are already 1–16 ms end to end. **So Redis is not about making one warm
instance faster.** It's about doing the cold work once for every instance and every restart, and about
the shared state, which is a matter of correctness: a watch session or a rate limit has to be the same
whichever instance answers.

## Decision

Add **Redis 7.4** (compose service `redis`; Testcontainers in every Spring test context). The backend
requires it, like Postgres. Nothing in Redis is the only copy of anything: losing it costs cold reads,
open watch sessions and rate-limit history, never data.

- **Spring Cache on Redis** (`cache` package). Each cache holds one type as JSON (no Java
  serialization), with a TTL and statistics (hits and misses are the cache metrics):

  | Cache | What (plan.md) | Key | Evicted when |
  |-------|----------------|-----|--------------|
  | `timelines` | Timeline caching | session | its event stream gets a new generation; the race is imported; a rebuild |
  | `snapshots` | Race playback snapshots | session | as above |
  | `replays` | Replay checkpoints (the timing points) | session | the race is imported |
  | `standings` | Leaderboards | season, round | any race's results stream changes; the history is imported; a rebuild |
  | `telemetry-laps` | Frequently accessed telemetry | session, car, lap | a race is imported (a whole session isn't cached: too big) |

  Evictions happen in one place (`CacheEvictions`), **after** the change commits, so a reader can't
  refill a cache with old data. Writes are **immediate**: Spring Data Redis 4 may otherwise finish a put
  after the method returns, and the next request misses (a test caught this).
- **The state engine stays in each instance's memory.** It holds the timing as objects, can't
  sensibly be serialized, and is rebuilt in ~30–50 ms. What the browser gets from it (the snapshots) is
  cached in Redis, together with the replay clock's zero, so a cold instance answers that without
  building one.
- **Watch-together sessions** are JSON values in Redis (`watch:<id>`, kept a day after the last change).
  Each clock change is published on the `watch-clock` channel, and every instance relays what it hears to
  its own WebSocket viewers, including the instance that made it. So a viewer on any instance stays in
  sync, and a restart drops no session.
- **Rate-limit buckets** in Redis (Bucket4j Lettuce, compare-and-swap) on the injected clock. They're
  shared by every instance and dropped a minute after they'd be full again. The limiter has its **own**
  Redis client. The connection factory's client is replaced whenever the factory restarts, and a
  limiter holding the old one failed every request (found when Spring's test contexts were paused and
  resumed).

This replaces the consequences in ADR 0005 (sessions in memory, one instance) and ADR 0007 (buckets per
instance).

## Consequences

- Development runs `docker compose up -d postgres redis`; every Spring test context starts a Redis
  container next to Postgres.
- Several backend instances behind a load balancer now agree on caches, watch sessions and limits.
  The STOMP broker is still the in-memory simple broker on each instance. That's fine, because each
  instance relays the shared channel to its own subscribers.
- Two commands for one watch session handled at the same moment on two instances could interleave
  (read, change, write). Only the host sends commands, so we accepted that.
- Caches are evicted, not updated: the first read after a change is cold again.
