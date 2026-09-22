
# F1 Historical Race Visualizer — MVP & System Design

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
- Play
- Pause
- Rewind
- Fast forward
- Jump to lap
- Jump to timestamp
- Variable playback speed:
  - 0.25x
  - 0.5x
  - 1x
  - 2x
  - 4x
  - 8x
- Auto-follow selected driver
- Multi-driver camera focus mode
- Timeline scrubbing

### Track Visualization
- 2D track rendering
- Driver dots
- Real-time sector coloring
- Position indicators
- Gap visualization
- DRS indicators
- Pit lane rendering
- Safety car visualization
- Yellow flag visualization
- Weather overlays
- Track sectors
- Mini-map mode

### Telemetry Visualization
- Speed graph
- Throttle graph
- Brake graph
- Gear display
- RPM estimate
- Tire compound
- Tire age
- Tire stint
- Fuel estimate
- ERS estimate
- Sector deltas
- Lap delta comparisons

### Timeline System
Timeline events:
- Overtakes
- Pit stops
- Safety car
- VSC
- Yellow flags
- Red flags
- Crashes
- DNFs
- Fastest laps
- Penalties
- Team radio markers
- Tire changes
- Weather changes
- Championship lead changes

---

# Championship Features

## Championship Explorer
- Select any season
- View constructor standings
- View driver standings
- Dynamic standings during race playback
- Historical points systems
- Sprint race support
- Compare seasons

## Driver Analytics
- Wins
- Podiums
- DNFs
- Fastest laps
- Pole positions
- Average finish
- Overtake statistics
- Pit stop averages
- Tire strategy tendencies
- Wet vs dry performance
- Track-specific performance

## Constructor Analytics
- Reliability
- Pit stop efficiency
- Race pace
- Qualifying pace
- Development trend visualization
- Strategy performance

---

# Advanced MVP Features

## Multi-Race Comparison
- Compare same track across years
- Compare driver performance
- Compare tire strategies
- Compare race pace evolution

## Race State Engine
System reconstructs race state at every timestamp:
- Driver positions
- Gap calculations
- Tire states
- Active penalties
- Current flags
- Pit window status
- Championship standings

## Event Sourcing Architecture
Store race state as events:
- Easier replay reconstruction
- Time travel debugging
- Efficient playback
- Historical querying

---

# Recommended Architecture

## Backend
### Primary Stack
- Java 21
- Spring Boot 3
- Spring WebFlux
- Spring Security
- Spring Data JPA
- Spring Batch
- Spring Cache
- Spring Scheduler

### Why WebFlux?
- High-frequency telemetry streaming
- Efficient reactive APIs
- Better scalability for replay systems

---

# Frontend

## Frontend Stack
- React
- TypeScript
- Vite
- React Query
- Zustand
- TailwindCSS
- D3.js
- Canvas API
- WebSockets
- Recharts

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

- **Seasons** — explore a championship year: standings and full season results.
- **Grand Prix** — explore a single event: results, timeline, analytics, telemetry, replay.
- **Drivers & Teams** — explore competitors: per-driver and per-constructor stats/analytics.
- **Replay Theater** — jump straight to watching a race's animated playback.

## Route tree

- `/` landing → `/dashboard` hub.
- **Seasons**: `/seasons` (year grid) → `/seasons/:year` (season hub) → leaves
  `…/standings/drivers`, `…/standings/constructors`, `…/results` (race-by-race grid), `…/races`.
- **Grand Prix**: `/races/:year` (GP list, year filter) → `/races/:year/:sessionKey` (race hub) →
  leaves `…/results`, `…/timeline`, `…/lap-pace`, `…/telemetry`, `…/replay`. Telemetry and replay
  drill further via `?driver=VER` (and a stint / lap-range) for **per-driver / per-stint** views.
- **Drivers & Teams**: `/drivers` directory → `/drivers/:acronym` hub (Stats · Season-by-season ·
  Race log); `/teams` directory → `/teams/:name` hub (Overview · Reliability · Pace).
- **Replay Theater**: `/replay` (year → GP picker) → reuses the race-hub replay leaf.

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
- seasons
- races
- circuits
- drivers
- constructors
- laps
- sectors
- telemetry_points
- race_events
- pit_stops
- weather_data
- championship_snapshots
- tire_stints
- penalties
- sessions

## TimescaleDB Extension
Use for:
- Telemetry time-series storage
- Efficient timestamp querying
- Compression
- Fast analytics

---

# Caching Layer

## Redis
Use Redis for:
- Timeline caching
- Race playback snapshots
- Session state
- Frequently accessed telemetry
- Leaderboards
- Replay checkpoints

---

# Search

## Elasticsearch
Use for:
- Fast event searching
- Driver search
- Telemetry querying
- Historical filtering
- Full-text event search

---

# Messaging / Event Streaming

## Apache Kafka
Topics:
- telemetry-stream
- race-events
- replay-events
- analytics-jobs
- ingestion-status

Use Kafka for:
- Event-driven architecture
- Replay pipelines
- Async analytics
- Scalable ingestion

---

# Data Ingestion System

## Sources
Possible free APIs:
- OpenF1 API
- Ergast API
- FastF1 datasets
- FIA timing archives (where legal)
- Public telemetry datasets

## Ingestion Services
### Scheduled Importers
- Race importer
- Telemetry importer
- Session importer
- Weather importer
- Event importer

### Data Validation
- Duplicate detection
- Timestamp validation
- Lap consistency checks
- Missing telemetry handling

---

# Replay Engine

## Responsibilities
- Reconstruct race state
- Emit synchronized playback frames
- Interpolate telemetry
- Maintain race timing
- Support rewind
- Support pause/resume

## Core Concepts
### Playback Frame
Contains:
- Driver positions
- Track state
- Current lap
- Current flags
- Weather state
- Gap data

### Snapshotting
Create snapshots every:
- 5 seconds
- 1 lap
- Key events

Allows efficient rewind and seeking.

---

# APIs

## REST APIs
Examples:
- GET /seasons
- GET /races/{id}
- GET /races/{id}/timeline
- GET /drivers/{id}/stats
- GET /telemetry/{sessionId}

## WebSocket APIs
Used for:
- Live replay stream
- Telemetry updates
- Timeline synchronization

---

# Authentication

## Security Features
- JWT authentication
- OAuth2 login
- Role-based permissions
- Rate limiting
- API key support

## Roles
- USER
- ADMIN
- ANALYST

---

# Observability

## Logging
- Structured JSON logging
- Correlation IDs

## Monitoring
- Prometheus
- Grafana

## Tracing
- OpenTelemetry
- Jaeger

---

# Testing Strategy

## Backend Testing
### Unit Tests
- Services
- Utilities
- Validators
- Calculators

### Integration Tests
- Repository tests
- Kafka tests
- Redis tests
- API tests

### End-to-End Tests
- Replay pipeline
- Full ingestion flow
- Authentication flows

### Performance Tests
- Telemetry streaming
- Replay scalability
- Database stress tests

### Security Tests
- JWT validation
- Authorization checks
- Injection prevention

---

# Frontend Testing

## Testing Stack
- Vitest
- React Testing Library
- Cypress
- Playwright

## Test Areas
- Timeline controls
- Playback synchronization
- Telemetry rendering
- Race visualization
- State management

---

# DevOps

## Containerization
- Docker
- Docker Compose

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
                    │  JRE 21, non-root        │
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

- **Backend** — build stage `eclipse-temurin:21-jdk-noble` runs the Gradle wrapper (BuildKit cache
  mount on `~/.gradle`; build scripts are copied before `src/` so dependencies are cached as a
  layer), produces the Boot jar and extracts it into Spring Boot layers. Runtime stage
  `eclipse-temurin:21-jre-noble` copies the layers (dependencies first, application last), runs as
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
4. A backend run on the host (`bootRun`) reads `SPRING_DATASOURCE_*` from its shell environment, and
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
`./gradlew bootRun` and `npm run dev` run on the host with hot reload and a debugger; the Vite dev
server calls the backend directly on `:8080`, which is why the backend keeps a CORS allowance for
`:5173`. See `docs/adr/0003-dockerize-backend-and-frontend.md`.

#### Growth path

- **More infrastructure:** add the commented-out services; each gets its own healthcheck, named
  volume and `.env`-driven credentials.
- **CI:** build both images in GitHub Actions, run the backend tests (Testcontainers) against the
  runner's Docker, and push tagged images to a registry (e.g. GHCR) for deployment.

## CI/CD
- GitHub Actions

Pipelines:
- Build
- Test
- Lint
- Security scan
- Integration tests
- Deployment

---

# Deployment

## Recommended Infrastructure
### MVP
- Railway
- Render
- Fly.io

### Advanced
- Kubernetes
- AWS
- Terraform

---

# Python Data Science Integration

## Python Services
Use Python separately for:
- Analytics pipelines
- Data cleaning
- Statistical modeling
- Visualization exports

## Libraries
- pandas
- numpy
- scipy
- matplotlib
- plotly
- scikit-learn
- jupyter

## Analytics Features
- Tire degradation analysis
- Pit strategy analysis
- Driver clustering
- Race pace prediction
- Overtake probability
- Sector dominance analysis

---

# Non-Functional Requirements

## Performance
- Replay latency < 100ms
- API response < 300ms
- Support 10k concurrent users
- Handle millions of telemetry points

## Reliability
- 99.9% uptime
- Graceful degradation
- Retry mechanisms

## Scalability
- Horizontal scaling
- Stateless APIs
- Distributed caching

## Security
- JWT encryption
- HTTPS everywhere
- Input sanitization

## Maintainability
- Clean architecture
- SOLID principles
- Domain-driven design
- Modular services

---

# Suggested Backend Modules

## Modules
- auth-service
- race-service
- telemetry-service
- replay-service
- analytics-service
- ingestion-service
- championship-service
- event-service

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
- Factory Pattern
- Strategy Pattern
- Observer Pattern
- Builder Pattern
- CQRS
- Event Sourcing
- Repository Pattern

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
- 3D visualization
- Live race mode
- Mobile app
- AI commentary
- Voice search
- Fantasy simulator
- Race prediction engine
- Custom telemetry dashboards
- Historical what-if simulator
- Driver racing lines
- ML-generated insights

---

# Final Recommended Stack

## Core
- Java 21
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
- Write ADRs
- Write API docs
- Write architecture diagrams
- Use migrations
- Use test coverage reports
- Use monitoring dashboards
- Write proper READMEs
- Track technical debt
- Benchmark performance

This can absolutely become:
- A flagship CV project
- A strong GitHub portfolio centerpiece
- A junior/mid backend engineering showcase
- A distributed systems learning platform
- A data engineering showcase
