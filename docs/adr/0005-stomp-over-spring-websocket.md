# 5. Watch-together over STOMP on Spring WebSocket (not WebFlux)

- **Status:** Accepted
- **Date:** 2026-10-09

## Context

Replay runs in the browser (ADR 0004): it downloads a race's timing once and plays it like a video.
Step 24 adds **watch-together**: several viewers — or the player plus separate timeline and telemetry
windows — follow one playback. That needs a single source of time on the server that broadcasts
play / pause / seek / speed to everyone watching, and gives a late joiner the current position at
once. The same channel is what a future live mode would push timing through.

The traffic is small: a handful of commands per minute per watch session, fanned out to a few
viewers. Nothing streams per frame — each client turns "playing at 4× from 10:00 since T" into its
own frames.

`plan.md` lists Spring WebFlux, and STEPS.md asks this ADR to choose between STOMP over Spring
WebSocket and WebFlux. The backend today is Spring MVC with blocking JPA everywhere.

## Decision

Use **STOMP over Spring WebSocket** (`spring-boot-starter-websocket`) on the existing servlet
stack, with Spring's in-memory simple broker:

- endpoint `/ws`; clients send commands to `/app/watch/{id}/command` and subscribe to
  `/topic/watch/{id}`; subscribing to `/app/watch/{id}` answers at once with the current state
  (late joiners);
- a server-held clock per watch session (`SharedClock`, on the injected `java.time.Clock`) is the
  only source of time; every change is broadcast with a sequence number and the server's time, so
  clients can correct for their own clock's offset;
- only the host (who holds the key returned when the session is created) can change the clock.

**WebFlux is not adopted** (`STACK-03` stays open). It would mean a second web stack, or rewriting
the blocking JPA code, for traffic that a few servlet threads handle easily.

## Consequences

- No new infrastructure: the simple broker lives in the backend. Watch sessions are in memory, so
  they don't survive a restart and don't span several backend instances. When the backend is scaled
  out (Step 44), switch to a broker relay (RabbitMQ/ActiveMQ via STOMP) — the clients don't change.
- STOMP gives destinations, subscribe replies and ordered delivery per session for free; the browser
  uses `@stomp/stompjs`.
- nginx (Docker) and the Vite dev server must proxy `/ws` with the WebSocket upgrade headers.
- If streaming needs ever become heavy (live telemetry for many viewers), revisit WebFlux or a
  dedicated broker then, with measurements.
