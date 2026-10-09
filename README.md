# Telemetrix — F1 Historical Race Visualizer

A full-stack platform that replays Formula 1 races from free/open telemetry and event data
(no video): animated driver dots on a track map, telemetry playback, an event timeline,
championship simulations and historical analytics.

**This repository is an early skeleton.** The Spring Boot backend starts, connects to PostgreSQL
and runs Flyway — there are no tables, endpoints or domain logic yet. Everything to be built is
specified in [`plan.md`](plan.md), and [`STEPS.md`](STEPS.md) breaks it into 55 build steps (1–3 done).
Progress is tracked in [`features.csv`](features.csv); see the top of `plan.md`.

## Layout

| Path                 | What it is                                                                  |
|----------------------|-----------------------------------------------------------------------------|
| `backend/`           | Spring Boot 4 app (Java 25, Maven) — entry point and configuration only      |
| `docker-compose.yml` | Local infrastructure: PostgreSQL 16                                          |
| `templates/`         | Commented reference code for every Spring Boot layer (read, don't run)       |
| `STEPS.md`           | Every build step from empty project to finished platform, with checks       |
| `plan.md`            | The full product & system design, as a checklist with a progress table      |
| `features.csv`       | Source of truth for that checklist: one row per feature, its step and status |
| `scripts/`           | `sync_features.py` — marks features done and syncs `plan.md` / `STEPS.md`    |

Not there yet, but planned: `frontend/` (React + TypeScript + Vite), `docs/` (ADRs, API reference),
Dockerfiles for the backend and frontend, and `analytics/` (Python).

## Prerequisites

- **JDK 25** (with `javac`, not just the runtime). Check with `javac -version`.
- **Docker** with the Compose plugin (`docker compose`), and the daemon running.

Maven does not need to be installed: the **Maven wrapper** (`backend/mvnw`) downloads the right
version on first run. Always use `./mvnw`, never `mvn`.

## Run it

From the repo root, start the database:

```bash
docker compose up -d --wait        # starts postgres:16 and waits until it is healthy
```

Then, from `backend/`, run the app:

```bash
./mvnw spring-boot:run             # http://localhost:8080 — Ctrl+C to stop
```

It connects to Postgres and Flyway applies any migrations in
`src/main/resources/db/migration/` (none yet). There are no endpoints yet, so
`curl localhost:8080` returns a 404 — that is Spring answering.

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

Follow [`STEPS.md`](STEPS.md) from **Step 4**, using [`templates/README.md`](templates/README.md) as the
reference for each Spring layer. Step 4 sets up the test harness (Testcontainers + JaCoCo) so
every later step can start with a failing test. The first schema work is Step 6
(`templates/03-migration/`): write `V1__<name>.sql` in `backend/src/main/resources/db/migration/`
with your first Telemetrix tables, then an entity, repository, service and controller for them,
each driven by its tests. When a feature's tests pass, tick it off: `python3 scripts/sync_features.py done <ID>`.

- **Code** goes under `backend/src/main/java/com/telemetrix/`, one package per feature (`race`,
  `driver`, `telemetry`, …) holding its entity, repository, service, DTOs and controller.
  Shared pieces (exceptions, config) go in `com.telemetrix.common`.
- **Schema changes** are always new Flyway files `V<n>__<name>.sql`; never edit one that has
  already run.
- **Tests** go in `backend/src/test/java/`. See `templates/13-tests/README.md` for the extra
  dependencies (Testcontainers etc.).

## Stack

Java 25 · Spring Boot 4.1 · Spring MVC · Spring Data JPA · Flyway · PostgreSQL 16 · Maven ·
Docker Compose.

Planned (see [`plan.md`](plan.md#final-recommended-stack)): React + TypeScript + Vite frontend,
TimescaleDB, Redis, Kafka, Testcontainers, Prometheus/Grafana, Python analytics.

## Roadmap

Phases are defined in [`plan.md`](plan.md#project-roadmap): (1) ingestion, schema, race list, basic
replay → (2) telemetry visualization, timeline, playback controls → (3) championship engine and
analytics → (4) Kafka/Redis/observability → (5) large-scale analytics and data science.
