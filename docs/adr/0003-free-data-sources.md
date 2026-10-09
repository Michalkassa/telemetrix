# 3. Free data sources, each owning what it does best

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

Telemetrix must be buildable and runnable for free. Every source below was checked on 2026-10-09.

| Source | What it has | Access | Limits |
|--------|-------------|--------|--------|
| **Jolpica-F1** (Ergast successor) | Calendar with official round numbers, circuits, results, qualifying, standings, retirement status: **1950 → now**. Lap times from 1996, pit stops from 2011 | Free REST API (Ergast-compatible); free **CSV/SQL database dump** (14.5 MB, delayed 14 days, non-commercial use) | API: 4 req/s burst, **500 req/hour**, and Jolpica says this will decrease |
| **OpenF1** | **2023 → now**: meetings, sessions, drivers, laps with sector splits, stints, pit, race control, weather, position, intervals, team radio, overtakes; full car telemetry and location at ~3.7 Hz | Free for historical data, no key. Live data is paid (not used) | **3 req/s, 30 req/min** |
| **Multiviewer circuits API** (linked from OpenF1's `circuit_info_url`) | Track outline (~700 x/y points), corners, marshal sectors, rotation, per circuit and year | Free, unofficial, no key | Unpublished. Be polite: 1 request per circuit-year, cached forever |
| **F1 live-timing static archive** (`livetiming.formula1.com/static/…`, the source behind OpenF1 and FastF1) | `TrackStatus` with exact SC / VSC / red-flag / yellow timestamps; whole-session car and position files | Free, unofficial, undocumented | Unpublished. Throttle and cache. F1 owns the data, so use it "where legal" (`plan.md`) |

Fetching everything from one API is slow: a full Jolpica history backfill through the API alone
would take about 40 hours at 500 requests per hour.

## Decision

Each source owns specific data, and every source is used inside its published limits. We never get
around a limit with extra IPs or accounts.

1. **Jolpica owns the calendar and history.** It is the only source with official round numbers for
   every season. Seasons, circuits and races (with rounds) come from the Jolpica API for the current
   season (1 request per season), and the full history comes from the **free CSV dump** in one
   download.
2. **Race data is downloaded per race, on demand.** Only the calendar is imported up front (so
   users can browse and pick races). When a user opens a race, that race's data is downloaded
   once and stored: for 2023+, OpenF1 sessions, laps with sector times, stints, pit stops, race
   control and weather, each **1 request per session for all drivers**. Nothing is bulk-imported
   for races nobody opens.
3. **Full telemetry is fetched on demand only.** `car_data` and `location` at 3.7 Hz are fetched
   for one driver and session when someone opens the telemetry view, then stored. Replay does
   *not* need them (ADR 0004).
4. **Multiviewer supplies track maps.** It is fetched once per circuit-year and stored.
5. **The F1 archive is optional.** It is used only for exact track status (SC / VSC / red flag),
   behind the same importer interface, so it can be switched off without breaking anything. If it
   is off, OpenF1 `race_control` messages give the same events, accurate to the message time.
6. Every client throttles to its source's limit, retries 429 and 5xx responses with backoff, and
   never calls live APIs from tests (recorded fixtures only).

## Consequences

- **Import cost:** a season's calendar takes 2 requests. Opening a 2023+ race for the first time
  costs about 10–15 requests (~30 s at OpenF1's limit); every later visit is instant from the
  database. The 1950+ results history is one download.
- **Coverage:** smooth replay from 2023, lap-level replay from 1996 (Jolpica lap times),
  results/standings/analytics from 1950. Before 1996 there is no timing data at all.
- **Unofficial sources** (Multiviewer, F1 archive) can change or disappear. Both are optional or
  replaceable: track outlines can be derived from one lap of OpenF1 `location` instead.
- **Non-commercial use only** for the Jolpica dump and OpenF1 free tier. Recheck the terms before
  deploying publicly (Step 47).
- **Not available anywhere for free:** ERS, fuel, tyre wear and temperatures, and the safety car's
  own position. These remain estimates (labelled as such) or overlays.
