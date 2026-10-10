# Telemetrix API

The REST API served by the backend under `http://localhost:8080`. Keep this file in step with the
controllers: each endpoint is documented here **before** it is implemented (its tests are written
from this contract).

Interactive docs: **Swagger UI** at `/swagger-ui.html`, the OpenAPI document at `/v3/api-docs`.

## Conventions

- **JSON** request and response bodies, `camelCase` field names.
- **Ids** are numbers; timestamps are ISO 8601 in UTC (`2024-03-02T15:03:12.345Z`).
- **Lists** that can grow are paged: `?page=0&size=20`, and the response includes the page metadata.

## Errors

Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem detail with content
type `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Race 42 not found",
  "instance": "/api/races/42"
}
```

| Status | When |
|--------|------|
| 400 | The request is invalid. For a bad body, `errors` maps each failing field to its message: `"errors": {"name": "must not be blank"}` |
| 404 | The requested resource doesn't exist |
| 409 | The request conflicts with current state (e.g. a duplicate) |
| 502 | An external data source (Jolpica, OpenF1) failed even after retries. The `detail` names the source and request |
| 500 | A bug on the server. The `detail` is always `"Unexpected error"`; the cause is only in the server log |

Implemented by `com.telemetrix.common.GlobalExceptionHandler`.

## Endpoints

### Seasons and races

#### `GET /api/seasons`

Every season, newest first, with how many races it has.

```json
[{"year": 2024, "raceCount": 24}, {"year": 2023, "raceCount": 22}]
```

#### `GET /api/seasons/{year}/races`

A season's calendar in round order. `hasOpenF1Data` says whether the race can have full replay and
session data (2023 on, linked to an OpenF1 meeting).

```json
{
  "year": 2024,
  "races": [
    {"id": 1, "round": 1, "name": "Bahrain Grand Prix", "date": "2024-03-02",
     "circuit": {"ref": "bahrain", "name": "Bahrain International Circuit", "locality": "Sakhir", "country": "Bahrain"},
     "hasOpenF1Data": true}
  ]
}
```

| Status | When |
|--------|------|
| 200 | The season exists (`races` may be empty) |
| 400 | `year` is outside 1950–2100 |
| 404 | No such season imported |

#### `GET /api/races/{id}`

One race weekend with its circuit and sessions (in start order). `importedAt` is when its data was
downloaded (null: not yet — see `POST /api/admin/import/races/{id}`).

```json
{
  "id": 1, "season": 2024, "round": 1, "name": "Bahrain Grand Prix", "date": "2024-03-02",
  "circuit": {"ref": "bahrain", "name": "Bahrain International Circuit", "locality": "Sakhir", "country": "Bahrain"},
  "openf1MeetingKey": 1229, "importedAt": "2026-10-09T14:35:47Z",
  "sessions": [
    {"id": 5, "name": "Race", "startsAt": "2024-03-02T15:00:00Z", "endsAt": "2024-03-02T17:00:00Z"}
  ]
}
```

| Status | When |
|--------|------|
| 200 | Found |
| 404 | No race with this id |

#### `POST /api/races/{id}/import` · `GET /api/races/{id}/import-status`

A race page calls `POST …/import` when it opens. If the race's data isn't downloaded yet, the download
starts in the background — once, however many users open it at the same time — and the page polls
`GET …/import-status` while it shows "downloading race data…" (~10 s). After that the race is
instant for everyone.

```json
{"raceId": 1, "state": "IMPORTING", "since": "2026-10-09T15:13:21Z", "message": null}
```

| `state` | Meaning |
|---------|---------|
| `NOT_IMPORTED` | Has OpenF1 data, but nobody has opened it yet |
| `IMPORTING` | Downloading in the background |
| `READY` | Downloaded; a race still to come is refreshed at most every 15 minutes |
| `FAILED` | The last download failed (`message` says why); `POST …/import` again retries |
| `NOT_AVAILABLE` | Before 2023: no OpenF1 data (results and history still work) |

`POST` answers **202** while `IMPORTING`, otherwise **200**; both **404** for an unknown race.

### Track maps

#### `GET /api/circuits/{ref}/layout?year={year}`

A circuit's track map for one year, ready to draw: the outline from the start/finish line round one lap,
**already rotated for display**, plus where sectors 1 and 2 end and the corners (`progress` = share of
the lap distance, 0 at the line). It's fetched when a race at that circuit and year is first opened.
Telemetry `x`/`y` are raw: rotate them by `rotation` degrees (counter-clockwise) to sit on this map.

```json
{
  "circuit": "bahrain", "year": 2024, "source": "MULTIVIEWER", "rotation": 92.0, "length": 53395.1,
  "boundingBox": {"minX": -8347.4, "minY": -492.8, "maxX": 3499.3, "maxY": 7260.4},
  "points": [[-1330.1, -421.7], [-1429.1, -422.1]],
  "startFinish": [-1330.1, -421.7],
  "sectorEnds": [0.3279, 0.7362], "sectorEndsSource": "TRACK",
  "corners": [{"number": 1, "x": -8325.6, "y": -248.3, "progress": 0.132}]
}
```

`marshalSectors` say where each marshalling sector starts (`progress` round the lap; yellow flags are
given per sector, and sector 1 usually starts just before the line, so it runs across it). `pitLane` is
one car's path through the pit lane at the race's first ordinary stop, rotated like `points` (empty if
unknown; layouts imported before Step 26 have neither until the race is imported again).

`source` is `MULTIVIEWER`, or `OPENF1_LAP` when the outline was traced from the fastest lap's
positions. `sectorEndsSource` is `TRACK` (from the circuit's mini-sectors) or `ESTIMATED` (from sector
times). [Bahrain 2024 drawn from this endpoint](bahrain-2024-layout.svg).

| Status | When |
|--------|------|
| 200 | Found |
| 400 | `year` missing or outside 1950–2100 |
| 404 | No map yet for that circuit and year (open a race there first) |

### Replay

#### `GET /api/sessions/{id}/replay`

The **whole race at once** for the browser's player (docs/adr/0004): about 80 KB for a full Grand
Prix. Each car has its timing points — `t` in milliseconds after `clockStart`, `laps` = laps done
(9.25 is a quarter of the way round lap 10). Between two points, interpolate linearly; before a car's
first point it's on the grid, after its last it stays put. A dot's place on the map is
`layout.points` at `laps − floor(laps)` of the way round **by distance**. Pause, 8×, rewind and scrub
are just changes to the playback clock. Open the race first (`POST /api/races/{id}/import`).

```json
{
  "sessionId": 5, "raceId": 1, "session": "Race",
  "clockStart": "2024-03-02T15:03:42.341Z", "chequeredAt": "2024-03-02T16:35:27.617Z", "totalLaps": 57,
  "layout": { "...": "the same shape as GET /api/circuits/{ref}/layout" },
  "drivers": [{"number": 1, "code": "VER", "name": "Max VERSTAPPEN", "team": "Red Bull Racing", "teamColour": "3671c6"}],
  "timing": [{"number": 1, "t": [0, 32877, 74143, 97758], "laps": [0.0, 0.3279, 0.7362, 1.0],
              "pits": [{"lap": 17, "from": 1675484, "to": 1700484}], "finished": true, "retiredAt": null}]
}
```

`finished`: the car took the chequered flag (crossed the line after the winner); otherwise
`retiredAt` is when it stopped. `weather` lists the readings (about one a minute): `t` in the same
milliseconds, `airTemperature` / `trackTemperature` °C, `humidity` %, `rainfall`, `windSpeed` m/s.

| Status | When |
|--------|------|
| 200 | Found |
| 404 | Unknown session, or no timing for it (open the race first; before 2023 there is none) |

#### `GET /api/sessions/{id}/replay/frame?at={instant}`

Every car at one moment, worked out on the server: race position, lap, progress round the lap, status
(`ON_GRID`, `RUNNING`, `IN_PIT`, `FINISHED`, `RETIRED`) and its point on the (rotated) map.
[Bahrain 2024, lap 13, drawn from this endpoint](bahrain-2024-lap13-frame.svg).

```json
{"at": "2024-03-02T15:23:42Z", "leaderLap": 13,
 "cars": [{"number": 1, "position": 1, "lap": 13, "progress": 0.3729, "lapsDone": 12.3729, "status": "RUNNING",
           "x": -4088.3, "y": 3696.3}]}
```

#### `GET /api/sessions/{id}/replay/state?at={instant}`

The whole race state at one moment (`state` package, `RaceStateEngine`): the leader's lap, the flag
(`GREEN`, `YELLOW`, `SAFETY_CAR`, `VSC`, `VSC_ENDING`, `RED`), the marshalling sectors under yellow
(`yellowSectors`: `{"7": "DOUBLE_YELLOW"}`, from race control), whether DRS is enabled, the latest
weather reading, and every car in race order, retired cars last. A car has `drs` when DRS is enabled
and it's running within 1 s of the car ahead; `mandatoryStopDone` once it has used two different dry
compounds (or any wet tyres). Gaps are seconds — to the leader and to the car ahead (`interval`) — measured where the car is: how
long ago the other car was there; null for cars on the grid or retired, and frozen at the line once a car
finishes. `tyreAge` counts laps on the current tyres including any they'd done before; `penaltySeconds`
are time penalties so far (added after the race), `pendingPenalties` drive-throughs and stop-gos not yet
served (a pit stop serves one). Answers in a few milliseconds anywhere: positions come straight from the
timing, and the rest starts from the nearest snapshot (every 5 s, every lap, every key event).

```json
{"at": "2023-09-17T13:20:00Z", "lap": 44, "flag": "VSC",
 "cars": [{"number": 55, "code": "SAI", "position": 1, "lap": 44, "lapsDone": 43.8293, "status": "RUNNING",
           "gapToLeader": 0.0, "interval": null, "lapsDown": 0, "compound": "HARD", "tyreAge": 23,
           "pitStops": 1, "penaltySeconds": 0, "pendingPenalties": 0}]}
```

`status`: `ON_GRID`, `RUNNING`, `IN_PIT`, `RETIRED`, `FINISHED`. 404 as for the replay.

#### `GET /api/sessions/{id}/replay/snapshots`

The derived state (flag, yellow sectors, DRS, and per car: tyres and the compounds used, pit stops,
penalties) once per lap, at each key event and at **every change** (`reason`: `LAP`, `SAFETY_CAR`,
`VSC`, `RED_FLAG`, `PIT_STOP`, `CHANGE`), in time order: the latest one at or before a moment is the
derived state then, which is what the replay's overlays draw. About 14 KB gzipped for a Grand Prix. `t` is milliseconds after the
replay's `clockStart`.

```json
[{"t": 0, "at": "2023-09-17T12:03:44.658Z", "lap": 1, "reason": "LAP", "flag": "GREEN",
  "cars": {"14": {"compound": "MEDIUM", "stintLapStart": 1, "ageAtStart": 0, "pitStops": 0, "inPit": false,
                  "penaltySeconds": 0, "pendingPenalties": 0}}}]
```

### Watch together (docs/adr/0005)

#### `POST /api/watch` · `GET /api/watch/{watchId}`

`POST {"sessionId": 5}` creates a watch session: one shared playback clock that everyone with the
`watchId` follows. The creator is the host and gets the `hostKey` (keep it; share only the `watchId`).
**201** `{"watchId": "qJ3x9Kd2", "hostKey": "…", "sessionId": 5}`. `GET` returns the clock (below),
**404** for an unknown watch session. Sessions live in memory: they end when the backend restarts.

#### STOMP over WebSocket at `/ws`

| Frame | Destination | |
|-------|-------------|--|
| `SUBSCRIBE` | `/app/watch/{watchId}` | the current clock, at once (late joiners) |
| `SUBSCRIBE` | `/topic/watch/{watchId}` | every change, in order |
| `SUBSCRIBE` | `/topic/races/{raceId}/timeline` | `{"raceId": 39, "change": "TIMELINE"}` when the race's data was imported again (a race still being run, or a re-import): fetch its timeline, replay and snapshots again. Sent after the import commits |
| `SEND` | `/app/watch/{watchId}/command` | `{"hostKey": "…", "action": "PLAY" \| "PAUSE" \| "SEEK" \| "SPEED", "value": 600000}` — `value` is race time in ms for `SEEK`, 0.25–8 for `SPEED`. Anything without the host key, or invalid, is ignored |

The clock:

```json
{"watchId": "qJ3x9Kd2", "sessionId": 5, "seq": 4, "playing": true, "speed": 4.0, "positionMs": 600000,
 "at": "2026-10-09T20:00:10Z", "serverTime": "2026-10-09T20:00:10.004Z"}
```

Race time now = `positionMs + (server now − at) × speed` while `playing` (ms after the replay's
`clockStart`). `serverTime` lets a client estimate its clock's offset from the server's; `seq` orders
the changes. The browser opens a watch session with "Watch together" on the replay page
(`?watch={watchId}`); the host key stays in that tab.

### Timeline

#### `GET /api/races/{id}/timeline?type={type}`

What happened in the race (its Race session; open the race first), in time order. Events are worked
out from the stored data by one detector per type (`event` package) and kept until the race is imported
again. `t` is milliseconds after `clockStart`, the replay clock's zero, so the player can jump to an
event; `lap` is the leader's lap (a fastest lap: the car's own). Events that last (safety car, VSC, red
and yellow flags, pit stops) have `endsAt` / `endLap`. `participants` act in order: the overtaker
first. Repeat `type` to keep several types.

```json
{
  "raceId": 39, "sessionId": 15, "clockStart": "2023-09-17T12:03:44.658Z",
  "events": [
    {"type": "SAFETY_CAR", "at": "2023-09-17T12:36:40.792Z", "t": 1976134, "lap": 20,
     "endsAt": "2023-09-17T12:42:49.803Z", "endLap": 22, "severity": "HIGH", "participants": [],
     "summary": "Safety car", "link": null},
    {"type": "INCIDENT", "at": "2023-09-17T13:45:13Z", "t": 6088342, "lap": 59, "endsAt": null, "endLap": null,
     "severity": "MEDIUM", "participants": [{"number": 11, "code": "PER"}, {"number": 23, "code": "ALB"}],
     "summary": "Incident: PER, ALB — causing a collision", "link": null}
  ]
}
```

| `type` | From |
|--------|------|
| `OVERTAKE` | The replay's running order, sampled each second: a pass that holds for 5 s either side. Not counted: swaps on a pit lap or out-lap or around a pit stop, under SC / VSC / red, past a stopped car, or before the leader completes lap 1 |
| `PIT_STOP`, `TYRE_CHANGE` | Pit stops; a new tyre stint (old → new compound) |
| `FASTEST_LAP` | Each new fastest lap of the race when it's completed (not lap 1) |
| `SAFETY_CAR`, `VSC`, `RED_FLAG`, `YELLOW_FLAG` | Track status periods (a VSC includes its ending phase) |
| `INCIDENT` | Each incident the stewards note, once |
| `CRASH` | A collision or "car stopped" message **and** a double yellow / SC / VSC / red from 3 min before to 1 min after it **and** a car retiring (≤ 5 min) or losing 3+ places (≤ 2 min). Replaces the incident |
| `DNF` | A car whose timing stops before the chequered flag |
| `PENALTY` | Penalties from race control |
| `TEAM_RADIO` | Each clip; `link` is the recording |
| `WEATHER` | Rain starting or stopping |

Each detected event is also published inside the backend as a `RaceEventDetected` Spring event.

| Status | When |
|--------|------|
| 200 | Found |
| 400 | An unknown `type` |
| 404 | Unknown race, no Race session, or no timing (open the race first) |

### Telemetry

#### `GET /api/sessions/{id}/telemetry?driver={carNumber}&lap={lap}`

One car's telemetry (~3.7 samples per second) for one lap, or the whole session without `lap`. The
first request for a car in a session downloads it from OpenF1 (~5 s); every later one is served from
the database. Each point has the car data plus the nearest position within 0.5 s (`x`/`y`/`z` null
when there is none). Stretches over 2 s without samples are listed in `gaps`, never filled in.

```json
{
  "sessionId": 5, "driver": 1, "lap": 10,
  "from": "2024-03-02T15:18:15.404Z", "to": "2024-03-02T15:19:52.364Z",
  "points": [{"t": "2024-03-02T15:18:16.271Z", "speed": 286, "throttle": 100, "brake": 0, "gear": 7,
              "rpm": 11234, "drs": 12, "x": -343, "y": 2113, "z": 0}],
  "gaps": []
}
```

| Status | When |
|--------|------|
| 200 | Found (possibly downloaded just now) |
| 400 | `driver` missing or outside 1–99 |
| 404 | Unknown session, a session without OpenF1 data, or a lap the car didn't do (import the race first) |
| 502 | OpenF1 failed |

#### `GET /api/sessions/{id}/telemetry/lap?driver={carNumber}&lap={lap}`

One lap against **distance**, for the telemetry view (no `lap`: the driver's fastest complete lap).
Downloads the car's telemetry first if needed (as above). `trace` points have `distance` (m from the
line, integrated from speed), `time` (ms since the lap started) and the car data; the lap is pinned at
both ends (time 0 and its lap time). `lapTime` and `sectors` are seconds; `tyre.age` counts laps on the
tyres including any done before they were fitted. `estimates` are **models, not data**: fuel burned
evenly from the 110 kg maximum, and MGU-K deployment (full throttle) / harvesting (braking) at 120 kW,
capped at 4 MJ / 2 MJ a lap — `note` says so.

```json
{"sessionId": 5, "driver": 1, "lap": 39, "startsAt": "2024-03-02T16:05:28.544Z", "lapTime": 92.608,
 "sectors": [29.741, 39.916, 22.951], "tyre": {"compound": "SOFT", "stint": 3, "age": 1},
 "estimates": {"fuelKg": 36.7, "ersDeployedMj": 4.0, "ersHarvestedMj": 2.0, "note": "Estimates from simple models …"},
 "distance": 5360.9,
 "trace": [{"time": 0, "distance": 0.0, "speed": 286, "throttle": 100, "brake": 0, "gear": 7, "rpm": 11353, "drs": 0}]}
```

#### `GET /api/sessions/{id}/telemetry/compare?driver=1&lap=39&vs=16&vsLap=36`

Two laps compared (no laps: each driver's fastest): both analyses, `lapDelta` and `sectorDeltas`
(other − reference, seconds), and `delta` — at each point of the reference lap (`distance` m), how far
behind (s; negative: ahead) the other lap was at the same share of its lap. It starts at 0 and ends at
exactly `lapDelta`. Bahrain 2024, Verstappen's 1:32.608 against Leclerc's 1:34.090: ends at +1.482.

| Status | When |
|--------|------|
| 200 | Found (telemetry downloaded now if it wasn't) |
| 400 | `driver` / `vs` missing or outside 1–99 |
| 404 | Unknown session, or a lap the car didn't do (or no timed lap at all) |
| 502 | OpenF1 failed |

### Admin: imports

> **Unsecured until Step 37** (Security), when they become ADMIN-only. Don't expose them publicly
> before then.

The season and race imports return an **import report**: per kind of row, how many were inserted, updated, unchanged or
skipped, plus notes (why rows were skipped, "already imported", ...).

```json
{
  "counts": {
    "races":    {"inserted": 24, "updated": 0, "unchanged": 0, "skipped": 0},
    "meetings": {"inserted": 0, "updated": 24, "unchanged": 0, "skipped": 1}
  },
  "notes": ["meeting 1228 Pre-Season Testing: no race on its dates 2024-02-21 to 2024-02-23"]
}
```

#### `POST /api/admin/import/seasons/{year}`

Imports a season's calendar: the season, its circuits and races with official rounds (Jolpica),
and for 2023+ links each race to its OpenF1 meeting. 2 requests to the sources; safe to repeat. Also
run nightly for the current season (`telemetrix.import.calendar-cron`, default 04:00 UTC).

| Status | When |
|--------|------|
| 200 | Imported; the body is the report |
| 400 | `year` is outside 1950–2100 |
| 502 | Jolpica or OpenF1 failed |

#### `POST /api/admin/import/races/{id}`

Downloads one race's data on demand: its OpenF1 sessions; for the race and sprint sessions every
car's laps (with sector splits — the replay's timing points), tyre stints and pit stops, plus the
weather, every race-control message (penalties are picked out of the stewards' messages), team radio
links and the **track status** (yellow / SC / VSC / VSC ending / red periods, with the leader's laps);
and the circuit's track map, once per circuit and year. About 8–12 requests (~25 s at OpenF1's rate
limit). Track status comes from the F1 live-timing archive, timed to the millisecond
(`f1archive.enabled`, on by default); when it's off or doesn't have the session, the same periods come
from race-control messages, to the second. The report's notes say which was used. Lap problems (a gap in lap numbers, sectors that don't add up,
a pit stop without a tyre change) are stored anyway and listed in `notes` as `ERROR …` / `WARNING …`.
A race that has happened is marked imported, and later calls make no requests; a race still to come
is refreshed (its timing replaced) each time.
Each import records the importer's version (`races.import_version`; 2 since Step 26). A race imported
by an older version is imported again when it's next opened: `POST …/import` answers `READY` at once
(its pages work with what's there) and downloads it in the background, fetching its track map again
too; when that ends, `/topic/races/{id}/timeline` tells open pages to reload.
Races before 2023 have no OpenF1 data; the report says so.

| Status | When |
|--------|------|
| 200 | Imported (or already imported); the body is the report |
| 404 | No race with this id |
| 502 | OpenF1 failed |

#### `POST /api/admin/import/history` · `GET /api/admin/import/history`

Imports the whole history (1950 on: seasons, circuits, drivers, constructors, races, entries, every
session's results, lap times from 1996, pit stops from 2011) from Jolpica's free CSV dump, as a
restartable Spring Batch job. `POST` answers **202 Accepted** at once and runs in the background (about
20 s: download + import); `GET` shows the last run.

```json
{"executionId": 1, "status": "COMPLETED", "startedAt": "2026-10-09T14:50:56Z",
 "endedAt": "2026-10-09T14:51:11Z", "exitCode": "COMPLETED"}
```

| Status | When |
|--------|------|
| 202 | `POST`: started (`status` `STARTING`, `executionId` still null while the dump downloads) |
| 200 | `GET`: the last run |
| 404 | `GET`: no run yet |
| 409 | `POST`: a run is already in progress |
