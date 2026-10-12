# 7. Security: our own JWTs after GitHub sign-in, API keys, rate limiting

- **Status:** Accepted
- **Date:** 2026-10-12

## Context

Until Step 36 every endpoint was open, including the imports (which call external APIs) and the
event-store rebuild. Step 37 asks for JWT authentication, OAuth2 login, roles USER / ANALYST / ADMIN,
API keys for programmatic access, rate limiting, and input validation. The frontend has no sign-in yet,
and every page it has reads public data.

## Decision

- **Spring Security, stateless.** The API keeps no session and uses no cookies, so CSRF protection is
  off for it. A caller is a bearer JWT of ours, an API key, or anonymous.
- **Sign in with GitHub, then our own tokens.** `/oauth2/authorization/github` runs the OAuth2 code
  flow (with PKCE). That is the only place with a session, and it only holds the OAuth state. On success
  the subject is `github:<login>`, and the browser goes back to `telemetrix.security.login-redirect`
  with our tokens in the URL **fragment** (never sent to a server, so never in an access log).
- **Tokens:** JWTs **signed RS256** (JWS) with our key from PEM files; without them a key is made at
  startup. Claims: issuer, subject, `roles`, `token_use`, expiry. Access tokens last 15 min and are the
  only ones the API accepts. Refresh tokens last 14 days and only buy a new pair at `POST
  /api/auth/refresh`, with the role worked out again so allowlist changes apply. Expiry is checked
  against the injected `Clock`, with a minute of skew. The claims are readable but can't be forged.
  This is how NFR-11 ("JWT encryption") is met: no secret ever goes in a token, and TLS protects them in
  transit. We chose this over JWE, which Spring's resource server can't decrypt out of the box.
- **Roles from allowlists:** `telemetrix.security.admins` / `analysts` list GitHub logins; anyone else
  who signs in is a USER. ADMIN > ANALYST > USER (a role hierarchy). No users table.
- **Rules:** reads (`GET /api/**`), opening a race (`POST /api/races/{id}/import`, which downloads on
  demand), creating a watch session, `/ws`, health and the API docs are open to everyone, so the
  frontend works signed out. The event log and time travel (`/api/sessions/{id}/events/**`) need ANALYST;
  `/api/admin/**` (imports, projection rebuild) ADMIN; `/api/auth/me` and any other write a signed-in
  caller. Which actuator endpoints exist is decided by `management.endpoints.web.exposure.include`
  (health only). Exposing another one (Step 42: Prometheus) must add its own rule.
- **API keys:** `X-API-Key`. Each configured key has a name, a role and its **SHA-256** (the key itself
  is never stored), compared in constant time. An unknown key gets a 401 even where none is needed.
- **Rate limiting:** Bucket4j token buckets in memory, per caller: anonymous by client address (behind
  nginx, from `X-Forwarded-For` via `server.forward-headers-strategy=native`, trusted only from private
  networks); signed-in callers and keys by name. Over the limit: 429 with `Retry-After`.
- **Input:** references and ids are checked against patterns (`common.Inputs`) before any query; a
  value of the wrong type is a 400 that names the parameter but never echoes the value. Every SQL
  statement was already parameterised. Spring Security's headers add `nosniff` and `X-Frame-Options:
  DENY`. 401, 403 and 429 are problem details like every other error.

## Consequences

- Docker's `seed` service imports with an ADMIN API key. Compose gives the backend only the key's hash,
  with a development default that must be replaced outside development.
- The buckets are per instance. When the backend scales out (Step 38 adds Redis, Step 44 more
  instances), move them to a shared store.
- A configured signing key is required in production, or every restart signs everyone out.
- Revoking one refresh token before it expires isn't possible (no store of issued tokens). Removing a
  login from an allowlist takes effect at its next refresh, within 15 minutes.
- The frontend still needs its sign-in UI: a link to `/oauth2/authorization/github`, a `/auth/callback`
  page that keeps the tokens, and refreshing them. Until then only API clients use the protected
  endpoints.
