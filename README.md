# Telemetrix — F1 Historical Race Visualizer

A full-stack platform that replays Formula 1 races from free/open telemetry and event data
(no video): animated driver dots on a track map, telemetry playback, an event timeline,
championship simulations and historical analytics.

**This repository is an empty scaffold.** Build tooling, infrastructure, configuration and the
module skeleton are in place and run end to end — there is no domain logic yet. Everything to be
built is specified in [`plan.md`](plan.md).

## Layout

| Path                | What it is                                                                 |
|---------------------|----------------------------------------------------------------------------|
| `backend/`          | Spring Boot 3 modular monolith (Java 21, Gradle) — empty module packages    |
| `frontend/`         | Vite + React + TypeScript app — router, React Query and Tailwind wired up   |
| `docker-compose.yml`| The full stack: PostgreSQL 16, backend and frontend images                 |
| `docs/`             | ADRs, architecture overview, API reference                                  |
| `datasets/`         | Fixtures and raw/exported data (mostly git-ignored)                         |
| `plan.md`           | The full product & system design this scaffold is meant to grow into        |

## Prerequisites

- **Docker** with the Compose plugin (`docker compose`), and the daemon running.

That is all you need to run the app. To develop with hot reload (below) you also need:

- **A JDK** (any recent one, 17+). Gradle downloads the Java 21 toolchain the backend builds with.
- **Node 20+** and npm.

## Run the whole stack in Docker

```bash
docker compose up --build -d      # builds both images, starts postgres → backend → frontend
```

| URL                              | What                                                    |
|----------------------------------|---------------------------------------------------------|
| http://localhost:3000            | The app (nginx serves the React build, proxies `/api`)  |
| http://localhost:8080/api/status | Backend directly                                        |
| `localhost:5432`                 | PostgreSQL, for `psql` or your IDE                      |

Startup is ordered by healthchecks: the backend waits for Postgres to accept connections, and the
frontend waits for the backend's `/actuator/health`. Useful commands:

```bash
docker compose ps                  # status and health
docker compose logs -f backend     # follow a service's logs
docker compose up --build -d backend   # rebuild and restart one service after a change
docker compose down                # stop everything, keep the database volume
docker compose down -v             # ...and wipe the database
```

How the images are built:

- **`backend/Dockerfile`**: multi-stage. A JDK 21 stage runs the Gradle wrapper (with a BuildKit
  cache for `~/.gradle`) and produces the Boot jar, which is then split into Spring Boot layers.
  The runtime stage is a JRE 21 image and runs as a non-root `spring` user. Dependency layers
  come before application classes, so a code-only change rebuilds one small layer.
- **`frontend/Dockerfile`**: multi-stage. `npm ci` + `vite build` in `node:22-alpine`, then the
  static `dist/` is served by `nginx:stable-alpine` using `frontend/nginx.conf`. nginx
  reverse-proxies `/api/` to `backend:8080`, so the browser sees one origin and no CORS is involved.

## Develop with hot reload

Rebuilding images on every edit is slow, so day to day you run only the database in Docker and
the apps on the host. This is the usual split in practice.

First-time setup, from the repo root:

```bash
npm --prefix frontend install                  # frontend dependencies
./backend/gradlew -p backend build -x test     # downloads Gradle, JDK 21 and dependencies
```

No `.env` is needed: every setting has a default that matches `docker-compose.yml`. See
[Configuration](#configuration) if you want to change a port or password.

Every dev run, in three terminals from the repo root (stop the `backend` and `frontend`
containers first if they are running: `docker compose stop backend frontend`):

```bash
# 1. Database (runs in the background; the first run pulls postgres:16)
docker compose up -d postgres

# 2. Backend — http://localhost:8080 (Flyway applies migrations on startup)
./backend/gradlew -p backend bootRun

# 3. Frontend — http://localhost:5173
npm --prefix frontend run dev
```

Check it is up:

```bash
curl localhost:8080/actuator/health     # {"status":"UP"}
curl localhost:8080/api/status          # {"service":"telemetrix-backend","status":"ok"}
```

The landing page calls `/api/status`, so it shows straight away whether the frontend can reach the
backend.

**Stopping:** Ctrl+C the backend and frontend, then `docker compose stop` for the database. Data
is kept in a named volume; `docker compose down -v` removes the container *and* wipes the data.

**Running the backend tests:**

```bash
./backend/gradlew -p backend test    # Testcontainers starts its own throwaway Postgres via Docker
```

## Configuration

Nothing to configure for local dev — the defaults in `docker-compose.yml`,
`application.properties` and `frontend/src/api/client.ts` all line up. To override something:

- **Docker stack** — `cp .env.example .env` and edit the `POSTGRES_*` values or the published
  `*_PORT`s; compose reads the root `.env` automatically and passes the credentials to both
  Postgres and the backend container.
- **Backend on the host** — set the `SPRING_DATASOURCE_*` / `OPENF1_BASE_URL` environment variables in the
  shell that runs `bootRun` (Spring does not read `.env` files), e.g.
  `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/telemetrix ./backend/gradlew -p backend bootRun`.
- **Frontend on the host** — `cp frontend/.env.example frontend/.env` and set `VITE_API_BASE_URL`.
  The Docker image leaves it empty on purpose, so API calls go to nginx's `/api` proxy on the same origin.

**Port 5432 already in use?** Another Postgres is running on the host. Stop it, or move this one:
put `POSTGRES_PORT=5433` in `.env` and start the backend with
`SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/telemetrix`.

## Where to start implementing

**Backend** — `backend/src/main/java/com/telemetrix/`. One package per domain module (`race`,
`driver`, `telemetry`, `event`, `ingestion`, `analytics`, `replay`, `championship`, plus `common`);
each `package-info.java` states the module's responsibility and the `domain` / `repo` / `dto` /
`web` convention. Schema changes go in `src/main/resources/db/migration/` as Flyway
`V<n>__<name>.sql` files — `V1__init.sql` is the placeholder to fill in. Tests already have a
Postgres Testcontainer wired up (`TestcontainersConfiguration`).

**Frontend** — `frontend/src/`. `routes.tsx` holds a single placeholder route; the full drill-down
route tree is specified in `plan.md`. `api/client.ts` has the typed `getJson` helper to build
endpoint functions on.

**Docs** — record decisions as ADRs in `docs/adr/`, and keep `docs/api.md` in step with the
controllers as you add them.

## Stack

Docker · Docker Compose · nginx · Java 21 · Spring Boot 3.5 · Spring Data JPA · Flyway · PostgreSQL 16 · Gradle · JUnit 5 ·
Testcontainers · React 18 · TypeScript · Vite · React Query · React Router · TailwindCSS · Recharts.

## Roadmap

Phases are defined in [`plan.md`](plan.md#project-roadmap): (1) ingestion, schema, race list, basic
replay → (2) telemetry visualization, timeline, playback controls → (3) championship engine and
analytics → (4) Kafka/Redis/observability → (5) large-scale analytics and data science.
