# Telemetrix API

The REST API served by the backend under `http://localhost:8080`. Keep this file in step with the
controllers: each endpoint is documented here **before** it is implemented (its tests are written
from this contract).

There are no endpoints yet; the first ones arrive in Step 11 (`STEPS.md`).

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
| 500 | A bug on the server. The `detail` is always `"Unexpected error"`; the cause is only in the server log |

Implemented by `com.telemetrix.common.GlobalExceptionHandler`.

## Endpoints

_None yet._
