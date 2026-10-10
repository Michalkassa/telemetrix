# Telemetrix — F1 Historical Race Visualizer

A full-stack platform that replays Formula 1 races from free/open telemetry and event data
(no video): animated driver dots on a track map, telemetry playback, an event timeline,
championship simulations and historical analytics.

**Status: Phase 1 in progress (Steps 1–17 of 55).** The backend imports the full race history from
1950 (Jolpica) and each season's calendar; a race's OpenF1 data (sessions, laps with sector splits,
tyre stints, pit stops, the circuit's track map) downloads in the background the first time someone
opens it, and a car's full telemetry the first time someone asks for it. Seasons, races, import status,
track maps, telemetry, each race's weather, race control, penalties, track status (SC / VSC / red
flag periods) and team radio, **championship standings** for every season since 1950 (each era's points
rules, best-results counting and count-back, matching the official tables) and "if they finished now"
standings during a replay, **driver career stats** (wins, podiums, poles, DNFs, positions gained, pit
stops, tyres, wet vs dry, per track and per season; SQL over a materialized view), **constructor season stats**
(reliability by cause, pit-stop medians ranked, race and qualifying pace gaps, the development trend,
positions gained by strategy), each race's **timeline** (overtakes, pit stops, SC/VSC, incidents,
DNFs, fastest laps, penalties, ...), the race **state** at any moment (order, gaps, tyres, flags,
penalties; instant to seek thanks to snapshots), and the **replay** (a whole race's timing points in one ~80 KB response) are
served over a REST API. The React frontend has the landing page, dashboard, the **Seasons** lens (`/seasons`: a year grid, then each
season's hub with drivers' and constructors' standings, a race-by-race results grid linking every
result to its race, the race list, and points-progression comparisons of two drivers across seasons),
the Grand Prix list, a **race hub** per race (results,
timeline, lap pace, telemetry and replay; before 2023 results and lap pace, with why), and the
replay player (`/races/:year/:raceId/replay`, or pick a race in the **Replay Theater** at `/replay`): a
canvas track map with one dot per car, the running order, and video-style controls — play/pause, ±10 s,
0.25–8×, jump to a lap or a race time, a timeline bar to scrub, keyboard shortcuts (space, ← →, ↑ ↓), and
a camera that follows one driver or keeps several in focus. The race's **events** sit beside it as a
filterable list and as markers on the timeline bar: click one to jump there (the current one is
highlighted as the race plays; when watching together, the host's click moves everyone), and a race
imported again pushes a timeline update over the WebSocket. Overlays: flag colours and the safety
car, yellow marshalling sectors, sector markers, the pit lane, gap labels, a mini-map when zoomed, a
weather badge, and a leaderboard with intervals, tyres, purple/green/yellow sectors, DRS and penalties. The **telemetry**
view (`/races/:year/:raceId/telemetry?driver=VER&vs=LEC`) charts speed, throttle, brake, gear and RPM
against distance (fetched on demand), compares two laps with a delta trace and sector deltas, shows
tyres and fuel/ERS *estimates*, and follows the replay's clock when opened from a watch-together session, with **watch together**: share a link and everyone follows the host's
playback over WebSocket (STOMP). Everything to be built is specified in
[`plan.md`](plan.md), and [`STEPS.md`](STEPS.md) breaks it into 55 build steps.
Progress is tracked in [`features.csv`](features.csv); see the top of `plan.md`.

## Layout

| Path                 | What it is                                                                  |
|----------------------|-----------------------------------------------------------------------------|
| `backend/`           | Spring Boot 4 app (Java 25, Maven): importers, schema, REST API              |
| `docker-compose.yml` | The whole stack: PostgreSQL 16, backend, frontend (nginx), calendar seed     |
| `frontend/`          | React 19 + TypeScript + Vite app: React Router, React Query, Tailwind        |
| `docs/`              | ADRs (`docs/adr/`) and the API reference (`docs/api.md`)                     |
| `templates/`         | Commented reference code for every Spring Boot layer (read, don't run)       |
| `STEPS.md`           | Every build step from empty project to finished platform, with checks       |
| `plan.md`            | The full product & system design, as a checklist with a progress table      |
| `features.csv`       | Source of truth for that checklist: one row per feature, its step and status |
| `scripts/`           | `sync_features.py` (feature checklist), `compose-smoke.sh` (stack smoke test) |
| `contracts/`         | Test cases shared by the backend and frontend (replay progress)              |

Not there yet, but planned: `analytics/` (Python).

## Prerequisites

- **JDK 25** (with `javac`, not just the runtime). Check with `javac -version`.
- **Docker** with the Compose plugin (`docker compose`), and the daemon running.

Maven does not need to be installed: the **Maven wrapper** (`backend/mvnw`) downloads the right
version on first run. Always use `./mvnw`, never `mvn`.

## Run it

### Everything in Docker (one command)

```bash
docker compose up --build -d --wait   # postgres, backend, frontend; imports the 2024 calendar
```

Open http://localhost:3000. The first time a race is opened its data downloads (~10 s). Settings
(ports, credentials, `SEED_SEASONS`) can go in `.env` — see `.env.example`. `scripts/compose-smoke.sh`
checks that the stack is healthy; `docker compose down` stops it (add `-v` to wipe the database).

### For development

From the repo root, start the database:

```bash
docker compose up -d --wait        # starts postgres:16 and waits until it is healthy
```

Then, from `backend/`, run the app:

```bash
./mvnw spring-boot:run             # http://localhost:8080 — Ctrl+C to stop
```

It connects to Postgres and Flyway applies the migrations in `src/main/resources/db/migration/`.
The database starts empty; fill it (all data sources are free — `docs/adr/0003`):

```bash
curl -X POST localhost:8080/api/admin/import/history         # 1950 → today from Jolpica's dump (~20 s, runs in the background)
curl localhost:8080/api/admin/import/history                 # ...until "status": "COMPLETED"
curl -X POST localhost:8080/api/admin/import/seasons/2024     # a season's calendar + OpenF1 links (also nightly for the current season)
curl -X POST localhost:8080/api/admin/import/races/1102       # one race's OpenF1 sessions, laps, stints, pit stops
```

Then the frontend, from `frontend/` (first time: `npm install`):

```bash
npm run dev                        # http://localhost:5173 — calls the backend through Vite's /api proxy
npm test                           # unit and component tests (Vitest)
npm run e2e                        # browser journeys (Playwright; first time: npx playwright install chromium)
```

Or browse the API directly: `curl localhost:8080/api/seasons`, `/api/seasons/2024/races`, `/api/races/{id}`, or
the interactive **Swagger UI** at http://localhost:8080/swagger-ui.html. The API is described in
[`docs/api.md`](docs/api.md). The admin endpoints are unsecured until Step 37 — don't expose the app.

Other useful commands:

```bash
# backend/
./mvnw package                     # compile, test and build target/backend-0.0.1-SNAPSHOT.jar
./mvnw package -DskipTests         # ...without the tests
./mvnw test                        # run the tests only
java -jar target/backend-0.0.1-SNAPSHOT.jar   # run the built jar

# repo root
docker compose ps                  # status and health
docker compose logs -f postgres    # follow the database logs
docker compose exec postgres psql -U telemetrix   # open a SQL shell
docker compose down                # stop, keep the data volume
docker compose down -v             # stop and wipe the database
```

## Configuration

Nothing to configure for local dev: the defaults in `backend/src/main/resources/application.properties`
match `docker-compose.yml` (database, user and password are all `telemetrix`, on `localhost:5432`).

To point the backend somewhere else, set environment variables in the shell that runs it:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/telemetrix ./mvnw spring-boot:run
```

Supported: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

Other settings worth knowing:

- `spring.jpa.hibernate.ddl-auto=validate` — Flyway owns the schema; Hibernate only checks at
  startup that the entities match the tables.
- `spring.mvc.problemdetails.enabled=true` — errors come back as RFC 9457 JSON, not an HTML page.

**Port 5432 already in use?** Another Postgres is running on the host. Stop it, or change the
compose port mapping to `"5433:5432"` and start the backend with the URL above.

## How it's built: test-driven

Every feature is written **test-first**: a failing test, then the least code to pass it, then a
refactor with everything green. Features start from an outside test (API, WebSocket or browser
journey) and are driven inward with unit tests. The rules and the test tools for each layer are in
[`STEPS.md` → How we work](STEPS.md#how-we-work-test-driven-development).

## Where to start implementing

Follow [`STEPS.md`](STEPS.md) from **Step 34** (Drivers & Teams UI; Step 20, CI, is still to do), using [`templates/README.md`](templates/README.md) as the reference for each Spring
layer. Every step starts with its **Tests first** list. When a feature's tests pass, tick it off:
`python3 scripts/sync_features.py done <ID>`.

- **Code** goes under `backend/src/main/java/com/telemetrix/`, one package per feature (`race`,
  `driver`, `telemetry`, …) holding its entity, repository, service, DTOs and controller.
  Shared pieces (exceptions, config) go in `com.telemetrix.common`.
- **Schema changes** are always new Flyway files `V<n>__<name>.sql`; never edit one that has
  already run.
- **Tests** go in `backend/src/test/java/`, written first (see *How it's built*). Database tests run
  against a throwaway PostgreSQL from Testcontainers, so Docker must be running; recorded API
  responses live in `src/test/resources/fixtures/`.

## Stack

Java 25 · Spring Boot 4.1 · Spring MVC · Spring Data JPA · Spring Batch · Flyway · PostgreSQL 16 ·
RestClient · springdoc-openapi · Maven · Docker Compose · JUnit 5 · Testcontainers · JaCoCo.

Frontend: React 19 · TypeScript · Vite · React Router · TanStack Query · TailwindCSS · Vitest ·
Testing Library · MSW · Playwright.

Planned (see [`plan.md`](plan.md#final-recommended-stack)): TimescaleDB, Redis, Kafka, Prometheus/Grafana, Python analytics.

## Roadmap

Phases are defined in [`plan.md`](plan.md#project-roadmap): (1) ingestion, schema, race list, basic
replay → (2) telemetry visualization, timeline, playback controls → (3) championship engine and
analytics → (4) Kafka/Redis/observability → (5) large-scale analytics and data science.
