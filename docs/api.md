# Telemetrix API

The REST API served by the backend under `http://localhost:8080`. Keep this file in step with the
controllers: each endpoint is documented here **before** it is implemented (its tests are written
from this contract).

The public read API arrives in Step 11 (`STEPS.md`); the admin import endpoints below exist now.

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

### Admin: imports

> **Unsecured until Step 37** (Security), when they become ADMIN-only. Don't expose them publicly
> before then.

Both return an **import report**: per kind of row, how many were inserted, updated, unchanged or
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

Downloads one race's data on demand (for now: its OpenF1 sessions). A race that has happened is
marked imported, and later calls make no requests; a race still to come is refreshed each time.
Races before 2023 have no OpenF1 data; the report says so.

| Status | When |
|--------|------|
| 200 | Imported (or already imported); the body is the report |
| 404 | No race with this id |
| 502 | OpenF1 failed |
