
# F1 Historical Race Visualizer — MVP & System Design

## Progress

Every deliverable in this plan is a checkbox with a hidden id (`<!-- PLAY-01 -->`). The source of
truth is [`features.csv`](features.csv); [`STEPS.md`](STEPS.md) says which step builds each one.
Do not tick boxes here by hand — mark the feature done and sync:

```bash
python3 scripts/sync_features.py done PLAY-01 PLAY-02   # mark done (today's date) + sync
python3 scripts/sync_features.py                        # re-sync after editing the CSV by hand
python3 scripts/sync_features.py --check                # CI: fail if plan.md/STEPS.md are stale
```

<!-- progress:start -->
**30 / 305 features done (9%)**

| Phase | Done | Total | Progress |
|---|---:|---:|---|
| 0 — Foundation | 3 | 3 | `██████████` |
| 1 — Data foundation & basic replay | 27 | 71 | `████░░░░░░` |
| 2 — Telemetry, timeline & playback | 0 | 83 | `░░░░░░░░░░` |
| 3 — Championship & analytics | 0 | 46 | `░░░░░░░░░░` |
| 4 — Distributed systems & production | 0 | 75 | `░░░░░░░░░░` |
| 5 — Data science & polish | 0 | 16 | `░░░░░░░░░░` |
| stretch — Stretch goals | 0 | 11 | `░░░░░░░░░░` |
<!-- progress:end -->

## Project Vision

Build a full-stack Formula 1 historical visualization and analytics platform that replays every available race using telemetry and event data from free/open F1 APIs.

The platform does NOT stream real video footage. Instead, it renders:
- Live-style race playback using animated driver dots on a track map
- Full telemetry playback
- Event timeline system
- Championship simulations
- Historical analytics
- Lap-by-lap progression
- Driver and constructor comparisons
- Multi-season navigation

The system is designed as:
- A serious portfolio project
- A large-scale backend engineering showcase
- A distributed systems learning project
- A data engineering + analytics platform
- A testing-heavy enterprise-style application

---

# Primary Goals

## Technical Goals
- Master Java + Spring Boot backend engineering
- Build production-grade APIs
- Learn advanced PostgreSQL modeling
- Implement clean architecture
- Practice testing at all levels
- Implement event-driven systems
- Build scalable replay engines
- Learn caching and optimization
- Learn observability and monitoring
- Build data pipelines
- Build analytics workloads with Python

## Product Goals
- Replay any race
- Replay any lap
- Replay any timestamp
- Track every driver metric
- Track championships dynamically
- Provide historical comparisons
- Provide telemetry visualizations
- Provide race event timelines
- Provide strategy analysis
- Support fast playback and rewind
- Support multiple synchronized visualizations

---

# Core User Features

## Race Replay Features

### Playback Controls
- [ ] Play <!-- PLAY-01 -->
- [ ] Pause <!-- PLAY-02 -->
- [ ] Rewind <!-- PLAY-03 -->
- [ ] Fast forward <!-- PLAY-04 -->
- [ ] Jump to lap <!-- PLAY-05 -->
- [ ] Jump to timestamp <!-- PLAY-06 -->
- [ ] Variable playback speed: <!-- PLAY-07 -->
  - 0.25x
  - 0.5x
  - 1x
  - 2x
  - 4x
  - 8x
- [ ] Auto-follow selected driver <!-- PLAY-08 -->
- [ ] Multi-driver camera focus mode <!-- PLAY-09 -->
- [ ] Timeline scrubbing <!-- PLAY-10 -->

### Track Visualization
- [ ] 2D track rendering <!-- TRACK-01 -->
- [ ] Driver dots <!-- TRACK-02 -->
- [ ] Real-time sector coloring <!-- TRACK-03 -->
- [ ] Position indicators <!-- TRACK-04 -->
- [ ] Gap visualization <!-- TRACK-05 -->
- [ ] DRS indicators <!-- TRACK-06 -->
- [ ] Pit lane rendering <!-- TRACK-07 -->
- [ ] Safety car visualization <!-- TRACK-08 -->
- [ ] Yellow flag visualization <!-- TRACK-09 -->
- [ ] Weather overlays <!-- TRACK-10 -->
- [ ] Track sectors <!-- TRACK-11 -->
- [ ] Mini-map mode <!-- TRACK-12 -->

### Telemetry Visualization
- [ ] Speed graph <!-- TELE-01 -->
- [ ] Throttle graph <!-- TELE-02 -->
- [ ] Brake graph <!-- TELE-03 -->
- [ ] Gear display <!-- TELE-04 -->
- [ ] RPM estimate <!-- TELE-05 -->
- [ ] Tire compound <!-- TELE-06 -->
- [ ] Tire age <!-- TELE-07 -->
- [ ] Tire stint <!-- TELE-08 -->
- [ ] Fuel estimate <!-- TELE-09 -->
- [ ] ERS estimate <!-- TELE-10 -->
- [ ] Sector deltas <!-- TELE-11 -->
- [ ] Lap delta comparisons <!-- TELE-12 -->

### Timeline System
Timeline events:
- [ ] Overtakes <!-- TIME-01 -->
- [ ] Pit stops <!-- TIME-02 -->
- [ ] Safety car <!-- TIME-03 -->
- [ ] VSC <!-- TIME-04 -->
- [ ] Yellow flags <!-- TIME-05 -->
- [ ] Red flags <!-- TIME-06 -->
- [ ] Crashes <!-- TIME-07 -->
- [ ] DNFs <!-- TIME-08 -->
- [ ] Fastest laps <!-- TIME-09 -->
- [ ] Penalties <!-- TIME-10 -->
- [ ] Team radio markers <!-- TIME-11 -->
- [ ] Tire changes <!-- TIME-12 -->
- [ ] Weather changes <!-- TIME-13 -->
- [ ] Championship lead changes <!-- TIME-14 -->

---

# Championship Features

## Championship Explorer
- [ ] Select any season <!-- CHAMP-01 -->
- [ ] View constructor standings <!-- CHAMP-02 -->
- [ ] View driver standings <!-- CHAMP-03 -->
- [ ] Dynamic standings during race playback <!-- CHAMP-04 -->
- [ ] Historical points systems <!-- CHAMP-05 -->
- [ ] Sprint race support <!-- CHAMP-06 -->
- [ ] Compare seasons <!-- CHAMP-07 -->

## Driver Analytics
- [ ] Wins <!-- DRV-01 -->
- [ ] Podiums <!-- DRV-02 -->
- [ ] DNFs <!-- DRV-03 -->
- [ ] Fastest laps <!-- DRV-04 -->
- [ ] Pole positions <!-- DRV-05 -->
- [ ] Average finish <!-- DRV-06 -->
- [ ] Overtake statistics <!-- DRV-07 -->
- [ ] Pit stop averages <!-- DRV-08 -->
- [ ] Tire strategy tendencies <!-- DRV-09 -->
- [ ] Wet vs dry performance <!-- DRV-10 -->
- [ ] Track-specific performance <!-- DRV-11 -->

## Constructor Analytics
- [ ] Reliability <!-- CON-01 -->
- [ ] Pit stop efficiency <!-- CON-02 -->
- [ ] Race pace <!-- CON-03 -->
- [ ] Qualifying pace <!-- CON-04 -->
- [ ] Development trend visualization <!-- CON-05 -->
- [ ] Strategy performance <!-- CON-06 -->

---

# Advanced MVP Features

## Multi-Race Comparison
- [ ] Compare same track across years <!-- CMP-01 -->
- [ ] Compare driver performance <!-- CMP-02 -->
- [ ] Compare tire strategies <!-- CMP-03 -->
- [ ] Compare race pace evolution <!-- CMP-04 -->

## Race State Engine
System reconstructs race state at every timestamp:
- [ ] Driver positions <!-- STATE-01 -->
- [ ] Gap calculations <!-- STATE-02 -->
- [ ] Tire states <!-- STATE-03 -->
- [ ] Active penalties <!-- STATE-04 -->
- [ ] Current flags <!-- STATE-05 -->
- [ ] Pit window status <!-- STATE-06 -->
- [ ] Championship standings <!-- STATE-07 -->

## Event Sourcing Architecture
Store race state as events:
- [ ] Easier replay reconstruction <!-- ES-01 -->
- [ ] Time travel debugging <!-- ES-02 -->
- [ ] Efficient playback <!-- ES-03 -->
- [ ] Historical querying <!-- ES-04 -->

---

# Recommended Architecture

## Backend
### Primary Stack
- [x] Java 25 <!-- STACK-01 -->
- [x] Spring Boot 4 <!-- STACK-02 -->
- [ ] Spring WebFlux <!-- STACK-03 -->
- [ ] Spring Security <!-- STACK-04 -->
- [x] Spring Data JPA <!-- STACK-05 -->
- [x] Spring Batch <!-- STACK-06 -->
- [ ] Spring Cache <!-- STACK-07 -->
- [x] Spring Scheduler <!-- STACK-08 -->

### Why WebFlux?
- High-frequency telemetry streaming
- Efficient reactive APIs
- Better scalability for replay systems

---

# Frontend

## Frontend Stack
- [ ] React <!-- FE-01 -->
- [ ] TypeScript <!-- FE-02 -->
- [ ] Vite <!-- FE-03 -->
- [ ] React Query <!-- FE-04 -->
- [ ] Zustand <!-- FE-05 -->
- [ ] TailwindCSS <!-- FE-06 -->
- [ ] D3.js <!-- FE-07 -->
- [ ] Canvas API <!-- FE-08 -->
- [ ] WebSockets <!-- FE-09 -->
- [ ] Recharts <!-- FE-10 -->

## Why Canvas?
Telemetry rendering and track animations require:
- High FPS rendering
- Efficient redraws
- Thousands of updates

---

# Frontend Dashboard & Information Architecture

The frontend is a **drill-down dashboard**: a landing page leads into a dashboard hub, and from
there every screen offers a *small* set of choices that lead deeper, progressively narrowing scope
(year → season → race → a single driver/stint). Breadcrumbs let you climb back up any level and
re-select; the selection lives in the URL so every view is deep-linkable and shareable. This is the
concrete realization of the router/Tailwind frontend stack above and turns the *Race Replay
Features*, *Championship Explorer*, and *Driver Analytics* sections into navigable pages.

## Navigation hierarchy

```
L0 Landing      (/)            hero + "Enter Dashboard" CTA
L1 Dashboard hub (/dashboard)  4 choice cards (the "lenses")
L2 Scope picker                narrow the choice (year grid, GP list, directory)
L3 Context hub                 the chosen entity's sub-menu of views
L4 Leaf views                  the niche page (table / chart / replay / telemetry trace)
```

## Top-level lenses (L1)

- [ ] **Seasons** — explore a championship year: standings and full season results. <!-- NAV-01 -->
- [ ] **Grand Prix** — explore a single event: results, timeline, analytics, telemetry, replay. <!-- NAV-02 -->
- [ ] **Drivers & Teams** — explore competitors: per-driver and per-constructor stats/analytics. <!-- NAV-03 -->
- [ ] **Replay Theater** — jump straight to watching a race's animated playback. <!-- NAV-04 -->

## Route tree

- [ ] `/` landing → `/dashboard` hub. <!-- NAV-05 -->
- [ ] **Seasons**: `/seasons` (year grid) → `/seasons/:year` (season hub) → leaves <!-- NAV-06 -->
  `…/standings/drivers`, `…/standings/constructors`, `…/results` (race-by-race grid), `…/races`.
- [ ] **Grand Prix**: `/races/:year` (GP list, year filter) → `/races/:year/:sessionKey` (race hub) → <!-- NAV-07 -->
  leaves `…/results`, `…/timeline`, `…/lap-pace`, `…/telemetry`, `…/replay`. Telemetry and replay
  drill further via `?driver=VER` (and a stint / lap-range) for **per-driver / per-stint** views.
- [ ] **Drivers & Teams**: `/drivers` directory → `/drivers/:acronym` hub (Stats · Season-by-season · <!-- NAV-08 -->
  Race log); `/teams` directory → `/teams/:name` hub (Overview · Reliability · Pace).
- [ ] **Replay Theater**: `/replay` (year → GP picker) → reuses the race-hub replay leaf. <!-- NAV-09 -->

Cross-links stitch the branches together: a standings row links to that driver's hub, a season-
results cell links to that race's hub, and a race hub links into the replay player.

## Page-type taxonomy

- **Hub / landing pages** — a few large choice cards (L0–L1, context hubs at L3).
- **Scope / list pages** — pickers and grids that select an entity (year grid, GP list, directories).
- **Context-hub pages** — an entity's sub-menu of available views (season hub, race hub, driver hub).
- **Leaf / detail pages** — the actual analytics surface: standings tables, lap-pace charts, the
  season-results grid, the replay player (track canvas + playback controls), and per-driver/per-stint
  telemetry traces.

---

# Database Design

## PostgreSQL
Primary relational database.

### Core Tables
- [x] seasons <!-- DB-01 -->
- [x] races <!-- DB-02 -->
- [x] circuits <!-- DB-03 -->
- [x] drivers <!-- DB-04 -->
- [x] constructors <!-- DB-05 -->
- [ ] laps <!-- DB-06 -->
- [ ] sectors <!-- DB-07 -->
- [ ] telemetry_points <!-- DB-08 -->
- [ ] race_events <!-- DB-09 -->
- [ ] pit_stops <!-- DB-10 -->
- [ ] weather_data <!-- DB-11 -->
- [ ] championship_snapshots <!-- DB-12 -->
- [ ] tire_stints <!-- DB-13 -->
- [ ] penalties <!-- DB-14 -->
- [x] sessions <!-- DB-15 -->

## TimescaleDB Extension
Use for:
- [ ] Telemetry time-series storage <!-- TSDB-01 -->
- [ ] Efficient timestamp querying <!-- TSDB-02 -->
- [ ] Compression <!-- TSDB-03 -->
- [ ] Fast analytics <!-- TSDB-04 -->

---

# Caching Layer

## Redis
Use Redis for:
- [ ] Timeline caching <!-- CACHE-01 -->
- [ ] Race playback snapshots <!-- CACHE-02 -->
- [ ] Session state <!-- CACHE-03 -->
- [ ] Frequently accessed telemetry <!-- CACHE-04 -->
- [ ] Leaderboards <!-- CACHE-05 -->
- [ ] Replay checkpoints <!-- CACHE-06 -->

---

# Search

## Elasticsearch
Use for:
- [ ] Fast event searching <!-- SEARCH-01 -->
- [ ] Driver search <!-- SEARCH-02 -->
- [ ] Telemetry querying <!-- SEARCH-03 -->
- [ ] Historical filtering <!-- SEARCH-04 -->
- [ ] Full-text event search <!-- SEARCH-05 -->

---

# Messaging / Event Streaming

## Apache Kafka
Topics:
- [ ] telemetry-stream <!-- KAFKA-01 -->
- [ ] race-events <!-- KAFKA-02 -->
- [ ] replay-events <!-- KAFKA-03 -->
- [ ] analytics-jobs <!-- KAFKA-04 -->
- [ ] ingestion-status <!-- KAFKA-05 -->

Use Kafka for:
- Event-driven architecture
- Replay pipelines
- Async analytics
- Scalable ingestion

---

# Data Ingestion System

## Sources
Chosen sources, what each owns, and their limits: [`docs/adr/0003-free-data-sources.md`](docs/adr/0003-free-data-sources.md).
Race data is downloaded per race when a user first opens it; only the calendar is imported up front.

Possible free APIs:
- [x] OpenF1 API <!-- SRC-01 -->
- [x] Ergast API <!-- SRC-02 -->
- [ ] FastF1 datasets <!-- SRC-03 -->
- [ ] FIA timing archives (where legal) <!-- SRC-04 -->
- [ ] Public telemetry datasets <!-- SRC-05 -->

## Ingestion Services
### Scheduled Importers
- [x] Race importer <!-- ING-01 -->
- [ ] Telemetry importer <!-- ING-02 -->
- [x] Session importer <!-- ING-03 -->
- [ ] Weather importer <!-- ING-04 -->
- [ ] Event importer <!-- ING-05 -->

### Data Validation
- [x] Duplicate detection <!-- VAL-01 -->
- [ ] Timestamp validation <!-- VAL-02 -->
- [ ] Lap consistency checks <!-- VAL-03 -->
- [ ] Missing telemetry handling <!-- VAL-04 -->

---

# Replay Engine

Cars are replayed as **lap progress** (0–100 % along the track outline) interpolated between ~4
timing points per lap, played client-side like a video — see
[`docs/adr/0004-replay-as-lap-progress.md`](docs/adr/0004-replay-as-lap-progress.md).

## Responsibilities
- [ ] Reconstruct race state <!-- REPLAY-01 -->
- [ ] Emit synchronized playback frames <!-- REPLAY-02 -->
- [ ] Interpolate telemetry <!-- REPLAY-03 -->
- [ ] Maintain race timing <!-- REPLAY-04 -->
- [ ] Support rewind <!-- REPLAY-05 -->
- [ ] Support pause/resume <!-- REPLAY-06 -->

## Core Concepts
### Playback Frame
Contains:
- [ ] Driver positions <!-- FRAME-01 -->
- [ ] Track state <!-- FRAME-02 -->
- [ ] Current lap <!-- FRAME-03 -->
- [ ] Current flags <!-- FRAME-04 -->
- [ ] Weather state <!-- FRAME-05 -->
- [ ] Gap data <!-- FRAME-06 -->

### Snapshotting
Create snapshots every:
- [ ] 5 seconds <!-- SNAP-01 -->
- [ ] 1 lap <!-- SNAP-02 -->
- [ ] Key events <!-- SNAP-03 -->

Allows efficient rewind and seeking.

---

# APIs

## REST APIs
Examples:
- [x] GET /seasons <!-- API-01 -->
- [x] GET /races/{id} <!-- API-02 -->
- [ ] GET /races/{id}/timeline <!-- API-03 -->
- [ ] GET /drivers/{id}/stats <!-- API-04 -->
- [ ] GET /telemetry/{sessionId} <!-- API-05 -->

## WebSocket APIs
Used for:
- [ ] Live replay stream <!-- WS-01 -->
- [ ] Telemetry updates <!-- WS-02 -->
- [ ] Timeline synchronization <!-- WS-03 -->

---

# Authentication

## Security Features
- [ ] JWT authentication <!-- AUTH-01 -->
- [ ] OAuth2 login <!-- AUTH-02 -->
- [ ] Role-based permissions <!-- AUTH-03 -->
- [ ] Rate limiting <!-- AUTH-04 -->
- [ ] API key support <!-- AUTH-05 -->

## Roles
- [ ] USER <!-- ROLE-01 -->
- [ ] ADMIN <!-- ROLE-02 -->
- [ ] ANALYST <!-- ROLE-03 -->

---

# Observability

## Logging
- [ ] Structured JSON logging <!-- OBS-01 -->
- [ ] Correlation IDs <!-- OBS-02 -->

## Monitoring
- [ ] Prometheus <!-- OBS-03 -->
- [ ] Grafana <!-- OBS-04 -->

## Tracing
- [ ] OpenTelemetry <!-- OBS-05 -->
- [ ] Jaeger <!-- OBS-06 -->

---

# Testing Strategy

The whole project is built with **test-driven development**: red → green → refactor, outside-in
(a failing API / end-to-end test per feature, driven to green by unit tests). No production code is
written without a failing test that needs it. The working rules and the test type for each layer are
in [`STEPS.md`](STEPS.md#how-we-work-test-driven-development); every step there starts with the tests
to write first.

## Backend Testing
### Unit Tests
- [x] Services <!-- TEST-01 -->
- [x] Utilities <!-- TEST-02 -->
- [x] Validators <!-- TEST-03 -->
- [ ] Calculators <!-- TEST-04 -->

### Integration Tests
- [x] Repository tests <!-- TEST-05 -->
- [ ] Kafka tests <!-- TEST-06 -->
- [ ] Redis tests <!-- TEST-07 -->
- [x] API tests <!-- TEST-08 -->

### End-to-End Tests
- [ ] Replay pipeline <!-- TEST-09 -->
- [ ] Full ingestion flow <!-- TEST-10 -->
- [ ] Authentication flows <!-- TEST-11 -->

### Performance Tests
- [ ] Telemetry streaming <!-- TEST-12 -->
- [ ] Replay scalability <!-- TEST-13 -->
- [ ] Database stress tests <!-- TEST-14 -->

### Security Tests
- [ ] JWT validation <!-- TEST-15 -->
- [ ] Authorization checks <!-- TEST-16 -->
- [ ] Injection prevention <!-- TEST-17 -->

---

# Frontend Testing

## Testing Stack
- [ ] Vitest <!-- FETEST-01 -->
- [ ] React Testing Library <!-- FETEST-02 -->
- [ ] Cypress <!-- FETEST-03 -->
- [ ] Playwright <!-- FETEST-04 -->

## Test Areas
- [ ] Timeline controls <!-- FETEST-05 -->
- [ ] Playback synchronization <!-- FETEST-06 -->
- [ ] Telemetry rendering <!-- FETEST-07 -->
- [ ] Race visualization <!-- FETEST-08 -->
- [ ] State management <!-- FETEST-09 -->

---

# DevOps

## Containerization
- [ ] Docker <!-- OPS-01 -->
- [ ] Docker Compose <!-- OPS-02 -->

### How Docker Compose composes the stack

`docker-compose.yml` in the repository root runs the whole application. Each app is built from its
own multi-stage Dockerfile; services reach each other by service name on the compose network, and
only the ports below are published to the host.

```
  browser ──▶ :3000  ┌──────────────────────────┐
                     │ frontend (nginx)         │  static React build
                     │  /api/**  ── proxy ──┐   │  (same origin, no CORS)
                     └──────────────────────┼───┘
                                            ▼
             :8080  ┌──────────────────────────┐
                    │ backend (Spring Boot)    │  Flyway migrates on startup
                    │  JRE 25, non-root        │
                    └────────────┬─────────────┘
                                 │ jdbc:postgresql://postgres:5432
             :5432  ┌────────────▼─────────────┐
                    │ postgres:16              │  volume: telemetrix_pgdata
                    └──────────────────────────┘
```

#### Services

| Service    | Built from / image                | Host port | Purpose                                                   |
|------------|-----------------------------------|-----------|-----------------------------------------------------------|
| `postgres` | `postgres:16`                     | `5432`    | The single source of truth; schema owned by Flyway.       |
| `backend`  | `backend/Dockerfile`              | `8080`    | Spring Boot API; waits for Postgres to be healthy.        |
| `frontend` | `frontend/Dockerfile` + nginx     | `3000`    | Serves the SPA, proxies `/api/` to `backend:8080`.        |

Later phases add `timescaledb`, `redis`, `kafka` and `elasticsearch` as extra services — they are
already listed as commented stubs in the file so the growth path stays visible. See
`docs/adr/0002-lean-infra-postgres-first.md` for why they are deferred.

#### Images

- **Backend** — build stage `eclipse-temurin:25-jdk-noble` runs the Maven wrapper (BuildKit cache
  mount on `~/.m2`; `pom.xml` and `.mvn/` are copied before `src/` so dependencies are cached as a
  layer), produces the Boot jar and extracts it into Spring Boot layers. Runtime stage
  `eclipse-temurin:25-jre-noble` copies the layers (dependencies first, application last), runs as
  the non-root `spring` user, and sizes the heap with `-XX:MaxRAMPercentage=75`. Tests are not run
  in the image build; they run on the host or in CI, where Testcontainers has Docker available.
- **Frontend** — build stage `node:22-alpine` runs `npm ci` and `vite build`; runtime stage
  `nginx:stable-alpine` serves `dist/` using `frontend/nginx.conf` (SPA fallback to `index.html`,
  long-lived caching for hashed `/assets/`, reverse proxy for `/api/`). `VITE_API_BASE_URL` is a
  build arg that defaults to empty, so the bundle calls the API on its own origin.
- Each app has a `.dockerignore` so `node_modules/`, `build/` and local `.env` files never enter
  the build context.

#### Configuration flow

One `.env` file at the repository root drives the stack, so the database and the backend can never
drift apart:

1. `cp .env.example .env` — `.env` is git-ignored, `.env.example` is committed.
2. Compose auto-loads `.env` and interpolates `${POSTGRES_DB}`, `${POSTGRES_USER}`,
   `${POSTGRES_PASSWORD}` and the published `${*_PORT}`s. Every variable has an inline default
   (`${POSTGRES_DB:-telemetrix}`), so the stack also starts without a `.env`.
3. Compose passes the same credentials to the backend container as `SPRING_DATASOURCE_*`, with the
   URL pointing at `postgres:5432` on the compose network.
4. A backend run on the host (`./mvnw spring-boot:run`) reads `SPRING_DATASOURCE_*` from its shell environment, and
   `application.properties` falls back to the compose defaults on `localhost:5432`.

#### State and lifecycle

Postgres data lives in the named volume `telemetrix_pgdata`, not in the container, so it survives
restarts and recreation. Healthchecks gate startup order: `pg_isready` for Postgres, then
`/actuator/health` for the backend; `depends_on: condition: service_healthy` makes each service wait
for the one below it. All services use `restart: unless-stopped`.

```bash
docker compose up --build -d     # build images and start the stack
docker compose ps                # health status
docker compose logs -f backend
docker compose down              # stop containers, keep the data volume
docker compose down -v           # stop and DELETE the data volume (full schema reset)
```

#### Development workflow

For fast iteration only `postgres` runs in Docker (`docker compose up -d postgres`) while
`./mvnw spring-boot:run` and `npm run dev` run on the host with hot reload and a debugger; the Vite dev
server calls the backend directly on `:8080`, which is why the backend keeps a CORS allowance for
`:5173`. See `docs/adr/0003-dockerize-backend-and-frontend.md`.

#### Growth path

- [ ] **More infrastructure:** add the commented-out services; each gets its own healthcheck, named <!-- OPS-03 -->
  volume and `.env`-driven credentials.
- [ ] **CI:** build both images in GitHub Actions, run the backend tests (Testcontainers) against the <!-- OPS-04 -->
  runner's Docker, and push tagged images to a registry (e.g. GHCR) for deployment.

## CI/CD
- [ ] GitHub Actions <!-- CI-01 -->

Pipelines:
- [ ] Build <!-- CI-02 -->
- [ ] Test <!-- CI-03 -->
- [ ] Lint <!-- CI-04 -->
- [ ] Security scan <!-- CI-05 -->
- [ ] Integration tests <!-- CI-06 -->
- [ ] Deployment <!-- CI-07 -->

---

# Deployment

## Recommended Infrastructure
### MVP
- [ ] Railway <!-- DEPLOY-01 -->
- [ ] Render <!-- DEPLOY-02 -->
- [ ] Fly.io <!-- DEPLOY-03 -->

### Advanced
- [ ] Kubernetes <!-- DEPLOY-04 -->
- [ ] AWS <!-- DEPLOY-05 -->
- [ ] Terraform <!-- DEPLOY-06 -->

---

# Python Data Science Integration

## Python Services
Use Python separately for:
- [ ] Analytics pipelines <!-- PY-01 -->
- [ ] Data cleaning <!-- PY-02 -->
- [ ] Statistical modeling <!-- PY-03 -->
- [ ] Visualization exports <!-- PY-04 -->

## Libraries
- pandas
- numpy
- scipy
- matplotlib
- plotly
- scikit-learn
- jupyter

## Analytics Features
- [ ] Tire degradation analysis <!-- PYA-01 -->
- [ ] Pit strategy analysis <!-- PYA-02 -->
- [ ] Driver clustering <!-- PYA-03 -->
- [ ] Race pace prediction <!-- PYA-04 -->
- [ ] Overtake probability <!-- PYA-05 -->
- [ ] Sector dominance analysis <!-- PYA-06 -->

---

# Non-Functional Requirements

## Performance
- [ ] Replay latency < 100ms <!-- NFR-01 -->
- [ ] API response < 300ms <!-- NFR-02 -->
- [ ] Support 10k concurrent users <!-- NFR-03 -->
- [ ] Handle millions of telemetry points <!-- NFR-04 -->

## Reliability
- [ ] 99.9% uptime <!-- NFR-05 -->
- [ ] Graceful degradation <!-- NFR-06 -->
- [ ] Retry mechanisms <!-- NFR-07 -->

## Scalability
- [ ] Horizontal scaling <!-- NFR-08 -->
- [ ] Stateless APIs <!-- NFR-09 -->
- [ ] Distributed caching <!-- NFR-10 -->

## Security
- [ ] JWT encryption <!-- NFR-11 -->
- [ ] HTTPS everywhere <!-- NFR-12 -->
- [ ] Input sanitization <!-- NFR-13 -->

## Maintainability
- [ ] Clean architecture <!-- NFR-14 -->
- [ ] SOLID principles <!-- NFR-15 -->
- [ ] Domain-driven design <!-- NFR-16 -->
- [ ] Modular services <!-- NFR-17 -->

---

# Suggested Backend Modules

## Modules
- [ ] auth-service <!-- MOD-01 -->
- [x] race-service <!-- MOD-02 -->
- [ ] telemetry-service <!-- MOD-03 -->
- [ ] replay-service <!-- MOD-04 -->
- [ ] analytics-service <!-- MOD-05 -->
- [x] ingestion-service <!-- MOD-06 -->
- [ ] championship-service <!-- MOD-07 -->
- [ ] event-service <!-- MOD-08 -->

---

# Domain Modeling Concepts

## Important Entities
### Driver
- metadata
- stats
- telemetry references

### Race
- sessions
- weather
- events
- standings

### TelemetryPoint
- timestamp
- speed
- throttle
- brake
- coordinates

### RaceEvent
- type
- timestamp
- severity
- participants

---

# Recommended Design Patterns

## Patterns To Use
- [ ] Factory Pattern <!-- PAT-01 -->
- [ ] Strategy Pattern <!-- PAT-02 -->
- [ ] Observer Pattern <!-- PAT-03 -->
- [ ] Builder Pattern <!-- PAT-04 -->
- [ ] CQRS <!-- PAT-05 -->
- [ ] Event Sourcing <!-- PAT-06 -->
- [x] Repository Pattern <!-- PAT-07 -->

---

# Project Roadmap

## Phase 1
- Basic ingestion
- Database schema
- Race list
- Basic replay

## Phase 2
- Telemetry visualization
- Timeline engine
- Playback controls

## Phase 3
- Championship engine
- Analytics
- Historical comparisons

## Phase 4
- Distributed systems
- Kafka
- Redis
- Observability

## Phase 5
- Massive analytics
- Data science integration
- Prediction systems

---

# CV Value Highlights

This project demonstrates:
- Enterprise backend engineering
- Distributed systems
- Reactive programming
- Advanced frontend rendering
- Data engineering
- Event-driven architecture
- Testing discipline
- DevOps
- Database optimization
- Real-time systems
- Analytics engineering
- Domain-driven design

---

# Stretch Goals

## Possible Future Features
- [ ] 3D visualization <!-- STRETCH-01 -->
- [ ] Live race mode <!-- STRETCH-02 -->
- [ ] Mobile app <!-- STRETCH-03 -->
- [ ] AI commentary <!-- STRETCH-04 -->
- [ ] Voice search <!-- STRETCH-05 -->
- [ ] Fantasy simulator <!-- STRETCH-06 -->
- [ ] Race prediction engine <!-- STRETCH-07 -->
- [ ] Custom telemetry dashboards <!-- STRETCH-08 -->
- [ ] Historical what-if simulator <!-- STRETCH-09 -->
- [ ] Driver racing lines <!-- STRETCH-10 -->
- [ ] ML-generated insights <!-- STRETCH-11 -->

---

# Final Recommended Stack

## Core
- Java 25
- Spring Boot
- PostgreSQL
- TimescaleDB
- React
- TypeScript

## Infrastructure
- Redis
- Kafka
- Docker
- Grafana
- Prometheus

## Data Science
- Python
- pandas
- scikit-learn

## Testing
- JUnit
- Testcontainers
- Mockito
- Cypress
- Playwright

---

# Recommended Repository Structure

backend/
frontend/
analytics/
infrastructure/
docs/
scripts/
datasets/

---

# Final Advice

Treat this project like a real enterprise platform:
- [x] Write ADRs <!-- PRAC-01 -->
- [x] Write API docs <!-- PRAC-02 -->
- [ ] Write architecture diagrams <!-- PRAC-03 -->
- [x] Use migrations <!-- PRAC-04 -->
- [x] Use test coverage reports <!-- PRAC-05 -->
- [ ] Use monitoring dashboards <!-- PRAC-06 -->
- [ ] Write proper READMEs <!-- PRAC-07 -->
- [ ] Track technical debt <!-- PRAC-08 -->
- [ ] Benchmark performance <!-- PRAC-09 -->

This can absolutely become:
- A flagship CV project
- A strong GitHub portfolio centerpiece
- A junior/mid backend engineering showcase
- A distributed systems learning platform
- A data engineering showcase
