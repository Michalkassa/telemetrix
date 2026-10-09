# Building Telemetrix step by step

The full path from an empty Spring Boot project to the finished platform described in
[`plan.md`](plan.md), one small step at a time. Each step says **what** to build, **why**, and **how
to check it**. Steps 1–3 are done; the rest is the road ahead, in build order.

**The project is built test-first (TDD).** From Step 4 on, every step opens with a **Tests first**
list: write those tests, watch them fail, and only then write the code under **What**.

Stack: Java 25 · Spring Boot 4.1 · Maven · PostgreSQL 16 · Docker.

> Commands below assume you are in `backend/` unless they say otherwise.

## How we work: test-driven development

Every change follows **red → green → refactor**:

1. **Red** — write one test for the next small piece of behaviour. Run it and watch it fail *for the
   right reason* (an assertion, not a compile error or a missing bean you forgot).
2. **Green** — write the least production code that makes it pass. Hard-coding is fine for a moment;
   the next test will force the real logic.
3. **Refactor** — clean up code *and* tests while everything stays green. Commit.

Features are driven **outside-in** (double-loop TDD): start with one failing *outer* test that
describes the feature as a user or client sees it (an API test, a WebSocket test, a Playwright
journey). It stays red while you drive the pieces underneath with small *inner* unit tests. When the
outer test passes, the feature is done.

| Layer | Test type | Tools |
|-------|-----------|-------|
| Pure logic (calculators, validators, interpolation, parsers) | Unit | JUnit 5, AssertJ, parameterised tests, jqwik for properties |
| Services | Unit with mocked collaborators | Mockito |
| Controllers | Web slice | `@WebMvcTest`, MockMvc, JSONPath |
| Repositories, migrations, SQL | JPA slice on a real database | `@DataJpaTest` + Testcontainers Postgres |
| External APIs (OpenF1, Jolpica) | Client tests on recorded responses | `MockRestServiceServer`, fixtures in `src/test/resources` |
| Whole features | Integration | `@SpringBootTest` + Testcontainers (Postgres, Redis, Kafka, …) |
| Frontend logic and components | Unit / component | Vitest, React Testing Library, MSW for the API |
| User journeys | End-to-end | Playwright |
| Python analytics | Unit | pytest |

House rules:
- **No production code without a failing test that needs it.** Bug fixes start with a test that
  reproduces the bug.
- **Time is injected.** Use a `java.time.Clock` bean (a fixed clock in tests), never
  `Instant.now()` directly, so replay and scheduling are testable without sleeping.
- **Real data in fixtures.** Save real OpenF1/Jolpica responses as JSON under
  `src/test/resources/fixtures/` and test against them; never call the live APIs from tests.
- **Small, known datasets** for SQL and analytics tests, where you can work out the answer by hand.
- **Tests are never weakened to go green.** If a test is wrong, fix it in its own red → green cycle.
- **A feature is ticked off only when its tests pass** (`./mvnw verify`, `npm test`) and the
  coverage report doesn't drop.

Steps 1–3 came before this rule; Step 4's first test covers them retroactively.

## How progress is tracked

Every deliverable in `plan.md` has an id (`PLAY-01`, `DB-03`, …) and a row in
[`features.csv`](features.csv) with the step that builds it. The **Checks off** list under each step
is generated from that CSV — never edit it by hand. When you finish something:

```bash
python3 scripts/sync_features.py done DB-01 DB-02   # repo root: marks them done, updates plan.md + this file
```

A step is finished when its **Tests first** list is written and green, every box in its list is
ticked, and its **Check it** passes. Steps whose
list says *groundwork* have no plan.md item of their own — they make later steps possible.

Phases follow the roadmap in `plan.md`:

| Phase | Steps | Goal |
|-------|-------|------|
| 0 — Foundation | 1–3 | A Spring Boot app talking to PostgreSQL |
| 1 — Data foundation & basic replay | 4–20 | Ingest real F1 data, list races, watch dots move |
| 2 — Telemetry, timeline & playback | 21–29 | Full replay player, telemetry graphs, event timeline |
| 3 — Championship & analytics | 30–36 | Standings engine, driver/team stats, comparisons |
| 4 — Distributed systems & production | 37–48 | Security, Redis, Kafka, observability, CI/CD, deploy |
| 5 — Data science & polish | 49–54 | Python analytics, predictions, portfolio finish |
| Stretch | 55 | Everything in *Stretch Goals* |

**Data sources (all free — see `docs/adr/0003`):** Jolpica owns the calendar and history (1950 →,
API + free CSV dump); OpenF1 adds sessions, laps with sector splits, stints, pit stops, race
control and weather for **2023 →** — downloaded **per race, when a user first opens it**, then
stored; Multiviewer gives track outlines; F1's live-timing archive
optionally gives exact safety-car / VSC / red-flag times. Full 3.7 Hz telemetry is fetched only
on demand.

**Replay model (`docs/adr/0004`):** every car is a coloured dot at its **lap progress** (0–100 %
along the track outline), interpolated between ~4 timing points per lap (lap start + 3 sector
splits). The browser downloads a whole race's timing points at once and plays it like a video:
pause, 0.25–8×, rewind and scrub are instant. Coverage: smooth replay 2023 →, lap-level replay
1996 →, results/standings/analytics 1950 →.

---

# Phase 0 — Foundation

## Step 1 — An empty Spring Boot project

**What:** generated with [Spring Initializr](https://start.spring.io) (Maven, Java 25, Boot 4.1.1,
one dependency: *Spring Web*). The same thing from the command line:

```bash
curl https://start.spring.io/starter.zip -d type=maven-project -d bootVersion=4.1.1 \
  -d javaVersion=25 -d groupId=com.telemetrix -d artifactId=backend \
  -d packageName=com.telemetrix -d dependencies=web -o backend.zip
```

**The files that matter:**

| File | What it is |
|------|------------|
| `pom.xml` | Maven's build file: project name, Java version, dependencies, plugins. |
| `mvnw`, `.mvn/wrapper/` | The **Maven wrapper**: downloads the right Maven version on first run, so nobody needs Maven installed. Always run `./mvnw`, never `mvn`. |
| `src/main/java/com/telemetrix/TelemetrixBackendApplication.java` | The entry point. `@SpringBootApplication` switches on auto-configuration and *component scanning* of `com.telemetrix` and everything under it. |
| `src/main/resources/application.properties` | Configuration (port, database URL, …). Just the app name at this point. |
| `src/test/java/` | Where tests go (empty for now; see `templates/13-tests/`). |

**Key ideas in `pom.xml`:**
- `<parent>spring-boot-starter-parent</parent>` — inherits tested versions for hundreds of
  libraries. That's why our dependencies have no `<version>`.
- **Starters** — `spring-boot-starter-webmvc` is one dependency that pulls in Spring MVC, an
  embedded Tomcat web server and Jackson (JSON). One starter per capability.
- `spring-boot-maven-plugin` — packages everything into one runnable "fat" jar.

**Check it:**

```bash
./mvnw package           # compiles and builds target/backend-0.0.1-SNAPSHOT.jar
./mvnw spring-boot:run   # runs it: "Tomcat started on port 8080" — Ctrl+C to stop
```

There are no endpoints yet, so `curl localhost:8080` gives a 404 — that is Spring answering.

> **Gotcha on this machine:** Fedora had only the Java *runtime* installed (no `javac`), so the
> first build failed with `release version 25 not supported`. Fix:
> `sudo dnf install java-25-openjdk-devel`.

<!-- features:1 -->
**Checks off (2/2):**

- [x] `STACK-01` Java 25
- [x] `STACK-02` Spring Boot 4
<!-- /features -->

---


## Step 2 — A PostgreSQL database in Docker

**What:** `docker-compose.yml` (repo root) describes one container: the official `postgres:16`
image with a database, user and password all called `telemetrix`.

**Why Docker for the database:** nothing to install on your machine, every developer gets the
exact same version, and you can throw it away and recreate it in seconds.

**Reading the file:**
- `image` — which prebuilt image to run (pulled from Docker Hub on first use).
- `environment` — the official image reads these on first start to create the DB and user.
- `ports: "5432:5432"` — *host:container*. Publishes the container's port on your machine so the
  backend (running outside Docker for now) can reach it at `localhost:5432`.
- `volumes` — the database files live in a named volume, not inside the container, so data survives
  `docker compose down`. `docker compose down -v` deletes it (a clean reset).
- `healthcheck` — Docker runs `pg_isready` every 5s; the container only counts as *healthy* once
  Postgres accepts connections. Later a backend container can wait for this (`templates/14-docker/`).

**Check it** (from the repo root):

```bash
docker compose up -d --wait        # start in the background, wait until healthy
docker compose ps                  # STATUS shows (healthy)
docker compose exec postgres psql -U telemetrix -c 'select version();'
docker compose logs -f postgres    # Ctrl+C to stop following
```

<!-- features:2 -->
_No plan.md checklist items — this step is groundwork._
<!-- /features -->

---


## Step 3 — Connect Spring to the database

**What:**
- `pom.xml` gains four dependencies: `spring-boot-starter-data-jpa` (Hibernate + Spring Data),
  `spring-boot-starter-flyway` + `flyway-database-postgresql` (schema migrations), and the
  `postgresql` JDBC driver (`runtime` scope: needed to run, not to compile).
- `application.properties` gets the connection settings.
- An empty `src/main/resources/db/migration/` folder, where your Flyway migrations go.

**`${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/telemetrix}`** means "use that environment
variable if it's set, otherwise this default". Locally the default points at the Docker Postgres
from Step 2. Inside Docker you set the variable instead. The jar stays the same and only the config
changes; that's how apps are configured in practice.

**`ddl-auto=validate`:** Flyway creates and changes the tables. Hibernate only *checks* at startup
that your entities match them.

**Check it:**

```bash
docker compose up -d --wait     # (repo root) the database must be running
./mvnw spring-boot:run          # starts, connects to Postgres, Flyway reports no migrations yet
```

<!-- features:3 -->
**Checks off (1/1):**

- [x] `STACK-05` Spring Data JPA
<!-- /features -->

---

# Phase 1 — Data foundation & basic replay

Goal: real F1 data in PostgreSQL, a list of races over a REST API, and a browser page where driver
dots move around a track. Everything later builds on this.

---

## Step 4 — Test harness: Testcontainers and coverage

**Tests first:**
- `ApplicationIntegrationTest.contextLoads()` with `@SpringBootTest`. It fails until a Postgres
  Testcontainer is wired in through `@ServiceConnection`, which is the point: it proves Steps 1–3
  end to end.
- A test that Flyway ran: query `flyway_schema_history` through `JdbcTemplate`.
- A deliberately failing assertion, run once, to confirm the test runner actually reports failures.

**What:**
- Test dependencies: `spring-boot-testcontainers`, `testcontainers-postgresql`,
  `testcontainers-junit-jupiter`, `spring-boot-starter-data-jpa-test`.
- `TestcontainersConfiguration` with a `@ServiceConnection` `postgres:16` container, and one
  `@SpringBootTest` that only checks that the context starts.
- The `jacoco-maven-plugin`, so `./mvnw verify` writes `target/site/jacoco/index.html`.

**Why:** the plan calls this a *testing-heavy* application. Tests against a real Postgres catch SQL
and migration mistakes an in-memory database would hide. Set it up before there is anything to
break.

**Learn:** `templates/13-tests/` (read its README first).

**Check it:** `./mvnw verify` is green with Docker running, and the JaCoCo report opens in a browser.

<!-- features:4 -->
**Checks off (1/1):**

- [x] `PRAC-05` Use test coverage reports
<!-- /features -->

---

## Step 5 — Docs, ADRs and shared error handling

**Tests first:**
- `@WebMvcTest` against a small test-only controller:
  - throwing `NotFoundException` → 404, `application/problem+json`, with a `title` and `detail`;
  - `ConflictException` → 409;
  - an invalid `@Valid` body → 400 with the failing field named.

**What:**
- `docs/adr/` with `0001-record-architecture-decisions.md` and `0002-lean-infra-postgres-first.md`
  (why Redis/Kafka/Elasticsearch wait until Phase 4). Keep them short: context, decision,
  consequences.
- `docs/api.md`, an empty API reference you fill in as controllers appear.
- `com.telemetrix.common`: `NotFoundException`, `ConflictException` and a `@RestControllerAdvice`
  that turns them into RFC 9457 problem details (404 / 409).
- `spring-boot-starter-validation` in `pom.xml`, so `@Valid` request bodies return 400s.

**Why:** every later step throws "not found" and records decisions. Building the plumbing once keeps
controllers small and gives the project the paper trail `plan.md` asks for.

**Learn:** `templates/09-errors/`, `templates/06-dto/` (validation).

**Check it:** `./mvnw package` still passes; `docs/adr/` has two ADRs.

<!-- features:5 -->
**Checks off (1/1):**

- [x] `PRAC-01` Write ADRs
<!-- /features -->

---

## Step 6 — Core schema (`V1`)

**Tests first:**
- A `@DataJpaTest` (no entities yet, so use `JdbcTemplate`) that inserts season → circuit → race
  → session and reads them back. It's red because the tables don't exist.
- A duplicate `session_key` is rejected by the unique constraint.
- A session pointing at a missing race is rejected by the foreign key.

**What:** `src/main/resources/db/migration/V1__core_schema.sql` with the reference tables:
`seasons`, `circuits`, `drivers`, `constructors`, `races` (one Grand Prix weekend = OpenF1
*meeting*) and `sessions` (FP1…Race, with OpenF1's `session_key`). Use natural unique keys from
the sources (`session_key`, `meeting_key`, driver number + season) so imports can upsert.
Add foreign keys and indexes on every column you will filter by.

**Why:** everything hangs off these tables. Keying by the source ids makes re-running an import
idempotent instead of creating duplicates.

**Learn:** `templates/03-migration/`.

**Check it:** start the app; `docker compose exec postgres psql -U telemetrix -c '\dt'` lists the
tables plus `flyway_schema_history`.

<!-- features:6 -->
**Checks off (7/7):**

- [x] `DB-01` seasons
- [x] `DB-02` races
- [x] `DB-03` circuits
- [x] `DB-04` drivers
- [x] `DB-05` constructors
- [x] `DB-15` sessions
- [x] `PRAC-04` Use migrations
<!-- /features -->

---

## Step 7 — Entities and repositories

**Tests first:**
- One repository test per query before writing it: save, `findBy…`, and ordering by date.
- Saving and loading a `Race` keeps its `Session`s.
- Loading a season's races with sessions runs a bounded number of SQL statements. Count them with
  Hibernate statistics; this catches N+1 queries.

**What:** a JPA entity and a Spring Data repository per table from Step 6, in feature packages
(`com.telemetrix.race`, `com.telemetrix.driver`, …). Use `@ManyToOne(fetch = LAZY)` everywhere.
Add derived queries you know you'll need (`findBySeasonYearOrderByDateStart`) and `@DataJpaTest`
tests against the Testcontainer.

**Why:** `ddl-auto=validate` now proves that the entities and the migration agree every time the
app starts. Repository tests pin the queries down.

**Learn:** `templates/04-entity/`, `templates/05-repository/`, `templates/13-tests/BookRepositoryTest.java`.

**Check it:** `./mvnw test` is green; the app starts without schema validation errors.

<!-- features:7 -->
**Checks off (2/2):**

- [x] `TEST-05` Repository tests
- [x] `PAT-07` Repository Pattern
<!-- /features -->

---

## Step 8 — OpenF1 HTTP client

**Tests first:**
- With `MockRestServiceServer`, assert the client requests the right URL and query parameters
  (`/meetings?year=2024`).
- A recorded fixture (`fixtures/openf1/meetings_2024.json`) maps to the expected records, including
  nulls and odd date formats.
- A 429 followed by a 200 makes the client retry and succeed. Repeated 5xx errors make it give up
  after N attempts with a clear exception.

**What:** `spring-boot-starter-restclient`, an `OpenF1Properties` record (`openf1.base-url`,
default `https://api.openf1.org/v1`, timeouts), and an `OpenF1Client` with one method per endpoint
you need (`meetings`, `sessions`, `drivers`, then more later). Map responses into records. Add
retry with backoff on 429/5xx and stay under OpenF1's published rate limits.

**Why:** one class owns *how* to talk to OpenF1. Importers only say *what* they want, and tests can
mock the client.

**Learn:** `templates/10-config-properties/`, `templates/11-http-client/`.

**Check it:** a test against a `MockRestServiceServer`, plus one manual run that logs the 2024
meetings.

<!-- features:8 -->
**Checks off (1/1):**

- [x] `SRC-01` OpenF1 API
<!-- /features -->

---

## Step 9 — Season calendar & on-demand race import

**Tests first:**
- `JolpicaClient` tests against a recorded `races.json` (2024): requests `/ergast/f1/2024/races.json`,
  maps rounds, dates and circuits with coordinates; throttled and retried like the OpenF1 client.
- `CalendarImporter` tests on the Testcontainers database with the clients mocked (fed from
  fixtures):
  - a season's calendar creates the season, its circuits and its races with official rounds;
  - OpenF1 meetings are linked to races by date (`openf1_meeting_key`, `openf1_circuit_key`);
  - pre-season testing and **cancelled** meetings (Imola 2023) are skipped and counted;
  - **outer test:** importing the same season twice gives identical row counts, and the second
    report shows nothing inserted;
  - a changed field in the source (a race renamed or moved) updates the row;
  - seasons before 2023 never call OpenF1.
- `RaceImporter.importRace(raceId)` tests: the first call downloads that race's OpenF1 sessions and
  stores them; a second call makes **no** API calls (already imported); a race without an OpenF1
  meeting (pre-2023) imports nothing and says so.
- Validator unit tests: a race with no date or round, a session ending before it starts, a session
  outside its meeting's dates are rejected and counted as skipped.
- The nightly job imports the *current* season's calendar according to an injected `Clock`; the
  admin endpoints return the import report (`@WebMvcTest`).

**What:** the `ingestion` package.
- Extract the throttling + retry logic from `OpenF1Client` into a shared `RateLimitedClient`
  (refactor under the existing tests), and add `JolpicaClient` on top of it (`jolpica.*` settings;
  500 req/hour → one request every 8 s).
- `CalendarImporter.importSeason(year)` — **2 requests per season**: the Jolpica calendar → upsert
  season, circuits, races by natural key; for 2023+, OpenF1 meetings matched to races by date.
- `RaceImporter.importRace(raceId)` — **on demand**, for the race a user picks: that meeting's
  OpenF1 sessions (1 request), upserted by `openf1_session_key`; a `races.imported_at` timestamp
  marks it done. Steps 12 and 21 add laps, stints, pit stops, race control and weather to this same
  per-race import.
- A `@Scheduled` nightly job for the current season's calendar, and admin endpoints
  `POST /api/admin/import/seasons/{year}` and `POST /api/admin/import/races/{id}` (unsecured until
  Step 37 — note it in `docs/api.md`).

**Why:** Jolpica is the only source with official round numbers for every season, so it owns the
calendar; race data is only downloaded for races someone actually opens (ADR 0003). Upserts make
every import safe to re-run.

**Learn:** `templates/07-service/`, `templates/12-scheduling/`.

**Check it:** import the 2024 calendar twice — the second report inserts nothing and
`select count(*) from races where season_year = 2024` is 24; import one race — its 5 sessions
appear; import it again — the log shows no OpenF1 call.

<!-- features:9 -->
**Checks off (7/7):**

- [x] `STACK-08` Spring Scheduler
- [x] `SRC-02` Ergast API
- [x] `ING-01` Race importer
- [x] `ING-03` Session importer
- [x] `VAL-01` Duplicate detection
- [x] `TEST-03` Validators
- [x] `MOD-06` ingestion-service
<!-- /features -->

---

## Step 10 — Full history from the Jolpica database dump

**Tests first:**
- Unit tests: Jolpica times (`hh:mm:ss` with 0–3 decimals) → milliseconds; status codes → a
  readable classification (0 finished, 1 lapped, 10 retired after an incident, 11 mechanical,
  20 disqualified, 30 did not start, 40 did not qualify); CSV rows read **by column name** (the
  fixture deliberately reverses the columns of two files — Jolpica doesn't guarantee column order).
- Downloader test: metadata → zip download → SHA-256 verified → unzipped; a hash mismatch fails.
- **Outer tests** (Spring Batch job on the Testcontainers database, against a trimmed copy of the
  real dump — British GP 1950, Australian GP 2008, Bahrain 2024 and a cancelled round):
  - every table gets the expected rows; the cancelled round is skipped;
  - 2008: retirements keep their reason ("Accident", "Collision") and classification;
  - 2024: Verstappen won from pole with 26 points; qualifying has his Q1/Q2/Q3 times; lap times and
    pit stops are stored;
  - importing twice gives identical row counts; a race created by Step 9 keeps its OpenF1 link;
  - a job that fails halfway (a corrupt lap file) **restarts from where it stopped** once fixed.

**What:** a restartable **Spring Batch** job (Batch metadata tables in a Flyway migration):
- `JolpicaDumpDownloader`: `GET https://api.jolpi.ca/data/dumps/download/` →
  `delayed_dumps.csv.download_url`, download, verify the SHA-256, unzip.
- Steps: index the dump's small tables → upsert seasons, circuits, drivers, constructors, races
  (official round; cancelled rounds skipped) → `race_entries` (race + driver + constructor + car
  number) → `session_results` (every session: position, grid, points, laps, time, status +
  reason, best lap) → `lap_times` (races and sprints, 1996 →) → `pit_stops` (2011 →). Chunked
  CSV reader → processor → JDBC batch upsert (`INSERT … ON CONFLICT`).
- A `qualifying` view: final position = the last knockout segment reached (Q3, else Q2, else Q1),
  with each segment's best lap.
- `POST /api/admin/import/history` starts it in the background; `GET` shows the last run.

`race_entries` also solve source matching exactly: an OpenF1 `driver_number` in a race is that
race's entry `car_number`, so Step 12 needs no fuzzy name matching.

**Why:** the whole 1950 → history in one 15 MB download instead of ~40 hours of API calls at 500
requests/hour (ADR 0003). Non-commercial use only — fine for this project, recheck before Step 47.

**Check it:** after a full import, `select count(*) from races` ≈ 1,170; Hamilton's and
Schumacher's win counts match public records.

<!-- features:10 -->
**Checks off (1/1):**

- [x] `STACK-06` Spring Batch
<!-- /features -->

---

## Step 11 — Race list REST API

**Tests first:**
- Write the contract in `docs/api.md` first, then turn it into `@WebMvcTest` tests: status codes,
  JSON shape (JSONPath), paging parameters, and the 404 problem detail for an unknown id.
- Service unit tests with Mockito: entity → DTO mapping, and filtering by year.
- **Outer test:** `@SpringBootTest` + Testcontainers calling the real endpoint over HTTP.

**What:** the `race` package's web layer: `GET /api/seasons`, `GET /api/seasons/{year}/races`,
`GET /api/races/{id}` (with its sessions). Use DTO records, never entities, and paging where lists
grow. Add `springdoc-openapi` for a Swagger UI and keep `docs/api.md` current. Write service unit
tests (Mockito) and `@WebMvcTest` controller tests.

**Why:** the first thing the frontend needs. It's also the template every later endpoint copies.

**Learn:** `templates/06-dto/`, `templates/08-controller/`, `templates/13-tests/BookControllerTest.java`.

**Check it:** `curl localhost:8080/api/seasons` returns JSON, `/swagger-ui.html` lists the
endpoints, and an unknown id gives a 404 problem detail.

<!-- features:11 -->
**Checks off (7/7):**

- [x] `API-01` GET /seasons
- [x] `API-02` GET /races/{id}
- [x] `TEST-01` Services
- [x] `TEST-02` Utilities
- [x] `TEST-08` API tests
- [x] `MOD-02` race-service
- [x] `PRAC-02` Write API docs
<!-- /features -->

---

## Step 12 — Laps, sector splits, stints and pit stops (the replay timing points)

**Tests first:**
- Lap-consistency validator unit tests on hand-built laps: a gap in lap numbers → error; sectors not
  adding up to the lap time (within 0.05 s) → error; a pit lap without a stint change → warning; a
  missing sector (e.g. lap 1 or a pit lap) → kept, marked partial.
- Importer tests from recorded OpenF1 `laps` / `stints` / `pit` fixtures (Bahrain 2024).
- **Outer test:** import one recorded race; laps per driver equal the classified laps, and every
  complete lap yields 4 timing points (start, S1, S2, finish).

**What:** New migration tables `laps` (session, driver, lap number, `starts_at`, sector 1–3 durations, lap
duration, speed traps, pit-out flag), `tire_stints` and `pit_stops`. Importers for OpenF1 `laps`,
`stints` and `pit` — **one request per session for all drivers** each — added to Step 9's
**per-race, on-demand** `RaceImporter`. For 1996–2022, Jolpica lap
times from the dump (Step 10) give lap-level points.

**Why:** these are the timing points the replay interpolates between (ADR 0004), and the backbone of
lap charts, strategy views and pace analysis.

**Check it:** for Bahrain 2024, 1,129 laps across 20 drivers; the validator report has no errors.

<!-- features:12 -->
**Checks off (0/5):**

- [ ] `DB-06` laps
- [ ] `DB-07` sectors
- [ ] `DB-10` pit_stops
- [ ] `DB-13` tire_stints
- [ ] `VAL-03` Lap consistency checks
<!-- /features -->

---

## Step 13 — Race import on open, and on-demand telemetry

**Tests first:**
- Import status: opening a race that isn't imported starts its import in the background and reports
  `IMPORTING`, then `READY` (or `FAILED` with the reason); two users opening it at the same time
  trigger **one** import; a failed import can be retried.
- On-demand telemetry service: the first request for (session, driver) fetches OpenF1 `car_data` +
  `location` (2 requests), merges them by nearest timestamp, validates timestamps (monotonic, inside
  the session) and stores them; the second request is served from the database with **no** API call;
  gaps are recorded, not filled.
- `@WebMvcTest` for `GET /api/races/{id}/import-status` and
  `GET /api/sessions/{id}/telemetry?driver=1&lap=10`.
- **Outer test (end to end):** with recorded fixtures, open a never-imported race → it becomes
  `READY` with its sessions, laps, stints and pit stops stored.

**What:** wire Step 9's `RaceImporter` to the user: `GET /api/races/{id}/import-status` and an
async import (a single-flight lock per race) that runs when a race page is opened and isn't imported
yet — the page shows "downloading race data…" for the ~30 s it takes, then is instant forever after.
A `telemetry_points` table (a new migration) filled **only on demand**, per driver and session, when the
telemetry view asks for it (ADR 0003).

**Why:** the user picks the races that get downloaded; nothing is fetched for races nobody opens, and
replay never needs bulk 3.7 Hz telemetry (ADR 0004).

**Check it:** open an un-imported race — "downloading…", then the replay data is there; open it
again — instant, and the log shows no OpenF1 call. Ask for one driver's telemetry twice — the second
answer is instant.

<!-- features:13 -->
**Checks off (0/7):**

- [ ] `DB-08` telemetry_points
- [ ] `ING-02` Telemetry importer
- [ ] `VAL-02` Timestamp validation
- [ ] `VAL-04` Missing telemetry handling
- [ ] `API-05` GET /telemetry/{sessionId}
- [ ] `TEST-10` Full ingestion flow
- [ ] `MOD-03` telemetry-service
<!-- /features -->

---

## Step 14 — Track maps

**Tests first:**
- Multiviewer client test on a recorded circuit fixture (Bahrain 2024): outline points, rotation,
  corners, marshal sectors.
- `TrackOutline` unit tests on synthetic tracks (a square, a circle) where the answer is known:
  - arc-length parametrisation: `pointAt(0) == pointAt(1)` (closed loop), `pointAt(0.5)` is
    halfway round by distance, not by point index;
  - rotation is applied; the bounding box is correct;
  - sector-boundary fractions come from track data, or are estimated from median sector/lap time
    ratios when missing.
- Fallback: an outline derived from one clean lap of OpenF1 `location` when Multiviewer has none.
- `@WebMvcTest` for `GET /api/circuits/{id}/layout?year=2024`.

**What:** a `circuit_layouts` table (per circuit and year — layouts change): outline polyline,
rotation, corners, sector-boundary fractions, start/finish point. Fetched from the Multiviewer
circuits API (OpenF1's `circuit_info_url`) once per circuit-year and stored (ADR 0003).

**Why:** the map the dots slide along. Lap progress 0–100 % only becomes a position on screen through
`pointAt(progress)`.

**Check it:** plot the polyline (a quick HTML page or notebook) — it looks like the circuit, and
`pointAt(0.25)` / `(0.5)` / `(0.75)` land where you'd expect.

<!-- features:14 -->
_No plan.md checklist items — this step is groundwork._
<!-- /features -->

---

## Step 15 — Replay engine v1 (lap progress)

**Tests first:**
- `ProgressTimeline` unit tests (ADR 0004):
  - at a timing point → exactly that lap/progress; halfway in time between two points → halfway in
    progress;
  - before the start → on the grid (lap 0, progress 0); after the chequered flag → finished;
  - a retired driver stops at their last point; a driver with a missing sector is interpolated
    across it;
  - progress never runs backwards.
- Running order at time *t* is sorted by `lap + progress` and matches the official classification at
  the end of each lap.
- Frame builder: drivers ordered by position; current lap correct at lap boundaries.
- `@WebMvcTest` for `GET /api/sessions/{id}/replay`.

**What:** the `replay` package. Build each driver's timing points from Step 12 (4 per lap) and serve
the **whole race at once** — drivers (number, code, team colour), timing points, pit windows,
retirements, and the circuit layout — in one compact JSON response (a few hundred KB). Also a
server-side `PlaybackFrame` builder (position, lap, progress per driver at *t*) for tests, the
leaderboard API and later features.

**Why:** with every timing point on the client, the player can compute any moment instantly — the
basis for video-style controls.

**Check it:** the response for Bahrain 2024 is under ~300 KB; frames at lap boundaries match the
lap-by-lap positions.

<!-- features:15 -->
**Checks off (0/9):**

- [ ] `STATE-01` Driver positions
- [ ] `REPLAY-01` Reconstruct race state
- [ ] `REPLAY-02` Emit synchronized playback frames
- [ ] `REPLAY-03` Interpolate telemetry
- [ ] `REPLAY-04` Maintain race timing
- [ ] `FRAME-01` Driver positions
- [ ] `FRAME-03` Current lap
- [ ] `MOD-04` replay-service
- [ ] `PAT-04` Builder Pattern
<!-- /features -->

---

## Step 16 — Frontend scaffold

**Tests first:**
- Set up Vitest and React Testing Library *first*, and run a failing test to prove they work.
- `getJson<T>`: parses JSON; throws a typed error with the status on a non-2xx response (mock
  `fetch`).
- `<App />` renders without crashing.

**What:** `frontend/` from `npm create vite@latest` (React + TypeScript), then TailwindCSS, React
Router, TanStack React Query, a typed `api/client.ts` (`getJson<T>`), ESLint and Prettier, and Vitest
+ React Testing Library with one smoke test. Point the Vite dev server's proxy for `/api` at
`localhost:8080`, so no CORS setup is needed in development.

**Why:** a clean, typed base before any pages. React Query handles caching and loading states, so
components stay simple.

**Check it:** `npm run dev` serves on :5173, `npm test` is green, and a page shows data from
`/api/seasons`.

<!-- features:16 -->
**Checks off (0/7):**

- [ ] `FE-01` React
- [ ] `FE-02` TypeScript
- [ ] `FE-03` Vite
- [ ] `FE-04` React Query
- [ ] `FE-06` TailwindCSS
- [ ] `FETEST-01` Vitest
- [ ] `FETEST-02` React Testing Library
<!-- /features -->

---

## Step 17 — Landing, dashboard hub and Grand Prix list

**Tests first:**
- RTL tests with MSW mocking the API:
  - the hub renders four lens cards;
  - `/races/2024` lists that year's races;
  - changing the year updates the URL;
  - breadcrumbs link back up;
  - loading and error states render.

**What:** the `/` landing page with an "Enter Dashboard" call to action, the `/dashboard` hub with the
four lens cards (only Grand Prix wired up for now), `/races/:year` (a GP list with a year filter), and
breadcrumbs. All selection state lives in the URL.

**Why:** this sets up the drill-down navigation from `plan.md` early, so every later page slots in.

**Check it:** go from landing → dashboard → 2024 → a race, then reload; the deep link still
works.

<!-- features:17 -->
**Checks off (0/1):**

- [ ] `NAV-05` / landing → /dashboard hub.
<!-- /features -->

---

## Step 18 — Basic replay player

**Tests first:**
- Pure functions first, no canvas needed:
  - `progressAt(points, t)` — the same rules as the server's `ProgressTimeline` (share the test
    cases);
  - `pointAt(outline, progress)` and the world → canvas transform (fits the bounding box, keeps the
    aspect ratio, applies rotation);
  - the playback clock: `t` advances by elapsed × speed while playing and not while paused.
- A component test: play/pause toggles the clock, and drawing is called with the expected dot
  positions (a mocked 2D context).
- **Outer test:** the first Playwright journey — open a race, press play, the lap counter advances;
  press pause, it stops.

**What:** `/races/:year/:sessionKey/replay`. Fetch the race once (Step 15), then a
`requestAnimationFrame` loop advances the clock and draws, on a `<canvas>`, the track outline and
one coloured dot per driver (team colour, number) at `pointAt(progressAt(driver, t))`. Only play
and pause for now.

**Why:** the **Phase 1 milestone** — you can watch a real race. Canvas, because 20 dots at 60 fps
would otherwise mean constant DOM layout work.

**Check it:** a 2024 race plays smoothly; overtakes on screen match reality; pause freezes it.

<!-- features:18 -->
**Checks off (0/7):**

- [ ] `PLAY-01` Play
- [ ] `PLAY-02` Pause
- [ ] `TRACK-01` 2D track rendering
- [ ] `TRACK-02` Driver dots
- [ ] `TRACK-04` Position indicators
- [ ] `FE-08` Canvas API
- [ ] `FETEST-08` Race visualization
<!-- /features -->

---

## Step 19 — Dockerize the stack

**Tests first:**
- A failing compose smoke test (a script, or a Testcontainers `ComposeContainer` test):
  - `docker compose up` reaches healthy;
  - `/actuator/health` returns UP;
  - `http://localhost:3000/api/seasons` (through nginx) returns 200.
- Actuator health test in the backend suite.

**What:** `backend/Dockerfile` (multi-stage: a `eclipse-temurin:25-jdk` stage builds with
`./mvnw`, caching `~/.m2`, then a `25-jre` runtime with Spring Boot layers, run as non-root) and
`frontend/Dockerfile` (build with `node:22-alpine`, serve with `nginx:stable-alpine` and proxy
`/api/` to the backend). Add `.dockerignore` files, `spring-boot-starter-actuator` for
`/actuator/health`, a `docker-compose.yml` with `backend` and `frontend` services gated on
healthchecks, and a `.env.example`. `plan.md` → *DevOps* describes the target in detail.

**Why:** one command runs everything on any machine. It's also the unit you deploy later.

**Learn:** `templates/14-docker/`.

**Check it:** `docker compose up --build -d`, then http://localhost:3000 plays a replay.

<!-- features:19 -->
**Checks off (0/2):**

- [ ] `OPS-01` Docker
- [ ] `OPS-02` Docker Compose
<!-- /features -->

---

## Step 20 — CI pipeline

**Tests first:**
- Push a branch with a deliberately failing test and confirm CI goes red. Then fix it and confirm
  green.
- Add the JaCoCo `check` goal with a minimum line coverage (start at ~80% for `domain`/`service`
  packages). The build must fail below it.

**What:** `.github/workflows/ci.yml` with these jobs:
- Backend: `./mvnw verify`. Testcontainers works on GitHub's Ubuntu runners, and the JaCoCo report
  is uploaded as an artifact.
- Frontend: `npm ci && npm run lint && npm test && npm run build`.
- `python3 scripts/sync_features.py --check`, so the checklist never drifts.

Add a Checkstyle or Spotless check for Java formatting.

**Why:** every push is proven to build and pass tests. That's the minimum for the "enterprise-style"
claim.

**Check it:** open a PR and every job is green. Break a test on purpose and the job goes red.

<!-- features:20 -->
**Checks off (0/5):**

- [ ] `CI-01` GitHub Actions
- [ ] `CI-02` Build
- [ ] `CI-03` Test
- [ ] `CI-04` Lint
- [ ] `CI-06` Integration tests
<!-- /features -->

---

# Phase 2 — Telemetry, timeline & playback

Goal: the full replay experience, with every control, overlays, telemetry graphs and a clickable
event timeline.

---

## Step 21 — Weather, race control, penalties and track status

**Tests first:**
- Parameterised parser tests: a table of real race-control message strings → the expected flag,
  SC/VSC state, or penalty (driver, seconds, reason), e.g. `SAFETY CAR DEPLOYED`,
  `VIRTUAL SAFETY CAR ENDING`, `TURN 13 INCIDENT INVOLVING CARS 11 (PER) AND 23 (ALB) NOTED -
  CAUSING A COLLISION`.
- F1-archive `TrackStatus` parser tests on a recorded stream (Singapore 2023): status codes 1 all
  clear, 2 yellow, 4 SC, 5 red, 6 VSC, 7 VSC ending → timestamped track-status periods.
- `TrackStatusSourceFactory` picks the F1 archive when enabled, and falls back to OpenF1
  race-control messages when it's disabled or the file is missing (both produce the same periods).
- Importer tests for weather, race control and team radio from fixtures.

**What:** New migration tables `weather_data`, `race_events` (raw race-control messages), `penalties` and
`track_status` (green / yellow / SC / VSC / red periods). Importers for OpenF1 `weather`,
`race_control` and `team_radio` (1 request per session each), and the optional F1-archive
`TrackStatus` source (ADR 0003, `f1archive.enabled`), all part of the per-race on-demand import.

**Why:** the facts the timeline, the SC/flag overlays and the crash detector are built from.

**Check it:** for Singapore 2023, `track_status` shows the SC from lap 20 to 22 and the VSC on lap
44–45.

<!-- features:21 -->
**Checks off (0/6):**

- [ ] `DB-09` race_events
- [ ] `DB-11` weather_data
- [ ] `DB-14` penalties
- [ ] `ING-04` Weather importer
- [ ] `ING-05` Event importer
- [ ] `PAT-01` Factory Pattern
<!-- /features -->

---

## Step 22 — Race events & timeline API

**Tests first:**
- One detector test class per event type, each on a tiny hand-built fixture:
  - an overtake is detected from the running order; a pit-cycle position swap is *not*;
  - fastest lap, DNF, SC/VSC start and end, red flag, yellow flags;
  - **crash / incident**: raised when signals agree — a collision or "car stopped" race-control
    message, a double yellow or SC in that sector or right after, and the car retiring or losing
    many places; *not* raised for a track-limits note alone.
- An Observer test: publishing a detected event notifies listeners (`@RecordApplicationEvents`).
- **Outer test:** `GET /api/races/{id}/timeline` returns events ordered by time and filterable by
  type.

**What:** the `event` package. A `RaceEvent` model (type, timestamp, lap, severity, participants)
and one detector per type, all implementing an interface:
- overtakes, from the progress-based running order (Step 15), cross-checked with OpenF1 `overtakes`;
- pit stops and tyre changes (Step 12); fastest laps;
- SC / VSC / red / yellow from `track_status` (Step 21);
- crashes and incidents, from the combined signals above (no source has a "crash" event —
  ADR 0003); DNFs from results; team radio markers; weather changes.

Detectors publish Spring application events that other modules listen to (the Observer pattern).

**Why:** the timeline is the event-driven heart of the app. Separate detectors keep each rule
testable on its own.

**Check it:** Singapore 2023's timeline shows the SC, the VSC and the late Pérez collisions.

<!-- features:22 -->
**Checks off (0/16):**

- [ ] `TIME-01` Overtakes
- [ ] `TIME-02` Pit stops
- [ ] `TIME-03` Safety car
- [ ] `TIME-04` VSC
- [ ] `TIME-05` Yellow flags
- [ ] `TIME-06` Red flags
- [ ] `TIME-07` Crashes
- [ ] `TIME-08` DNFs
- [ ] `TIME-09` Fastest laps
- [ ] `TIME-10` Penalties
- [ ] `TIME-11` Team radio markers
- [ ] `TIME-12` Tire changes
- [ ] `TIME-13` Weather changes
- [ ] `API-03` GET /races/{id}/timeline
- [ ] `MOD-08` event-service
- [ ] `PAT-03` Observer Pattern
<!-- /features -->

---

## Step 23 — Race state engine & snapshots

**Tests first:**
- Fold unit tests: given events → expected `RaceState` (order, gaps, tyres, flags, penalties,
  pit-window status).
- A jqwik **property test**: for any time *t*, the state from scratch equals the state from the
  nearest snapshot plus replay.
- Snapshot-policy tests: one per lap and one per key event (SC, red flag, pit stop).
- Seek tests: the nearest-snapshot lookup picks the latest snapshot ≤ *t*.

**What:** a `RaceStateEngine` that folds events and timing into a complete `RaceState` at any
timestamp. Car positions are already instant (ADR 0004); snapshots make the **derived** state —
gaps, tyres, penalties, flags — instant too, so the leaderboard and overlays keep up while scrubbing.
Persist snapshots per lap and per key event and ship them with the race (Step 15).

**Why:** without snapshots, the leaderboard at lap 50 means recomputing 49 laps of events on every
scrub.

**Check it:** state at *t* from scratch equals state from snapshot + replay; seeking anywhere stays
under 100 ms.

<!-- features:23 -->
**Checks off (0/4):**

- [ ] `REPLAY-05` Support rewind
- [ ] `SNAP-01` 5 seconds
- [ ] `SNAP-02` 1 lap
- [ ] `SNAP-03` Key events
<!-- /features -->

---

## Step 24 — Shared viewing over WebSocket

**Tests first:**
- **Outer test:** a WebSocket integration test with `WebSocketStompClient` on a random port: a host
  and a viewer join the same watch session; host play / pause / seek / speed commands reach the
  viewer in order.
- The shared clock is a controllable `Clock`, so the test runs in milliseconds; late joiners get the
  current position immediately.

**What:** replay itself stays client-side (ADR 0004). This step adds **watch-together**: a server
-held clock per shared session that broadcasts play / pause / seek / speed, so several viewers (or
the player plus separate telemetry and timeline windows) stay in sync. STOMP over Spring WebSocket,
or WebFlux — decide in **ADR 0005** and tick `STACK-03` once it's implemented. The same channel is
what a future live mode would use.

**Why:** synchronised views are a plan requirement, and they need a single source of time.

**Check it:** two browser tabs on the same watch session stay in sync within 100 ms when the host
pauses or scrubs.

<!-- features:24 -->
**Checks off (0/7):**

- [ ] `STACK-03` Spring WebFlux
- [ ] `FE-09` WebSockets
- [ ] `REPLAY-06` Support pause/resume
- [ ] `WS-01` Live replay stream
- [ ] `WS-02` Telemetry updates
- [ ] `TEST-09` Replay pipeline
- [ ] `NFR-01` Replay latency < 100ms
<!-- /features -->

---

## Step 25 — Full playback controls & Replay Theater

**Tests first:**
- Zustand store unit tests: each action → the expected state (play, pause, seek, speed, follow,
  focus set).
- RTL tests: each control calls the right store action; keyboard shortcuts work.
- **Outer test:** the Playwright journey from the Check-it below, written before the controls.

**What:** rewind and fast forward, jump to lap or timestamp, speed (0.25×–8×), a scrubbable timeline
bar, auto-follow a selected driver, and multi-driver focus (zoom the canvas to fit selected cars).
Keep player state in a Zustand store. Build the `/replay` Replay Theater lens (year → GP picker →
player). Add Playwright tests for the controls.

**Why:** this turns the player into the product.

**Check it:** Playwright: play → 4× → seek to lap 30 → pause. Assert the lap counter and that the
followed driver stays centred.

<!-- features:25 -->
**Checks off (0/14):**

- [ ] `PLAY-03` Rewind
- [ ] `PLAY-04` Fast forward
- [ ] `PLAY-05` Jump to lap
- [ ] `PLAY-06` Jump to timestamp
- [ ] `PLAY-07` Variable playback speed
- [ ] `PLAY-08` Auto-follow selected driver
- [ ] `PLAY-09` Multi-driver camera focus mode
- [ ] `PLAY-10` Timeline scrubbing
- [ ] `FE-05` Zustand
- [ ] `NAV-04` Replay Theater — jump straight to watching a race's animated playback.
- [ ] `NAV-09` Replay Theater: /replay (year → GP picker) → reuses the race-hub replay leaf.
- [ ] `FETEST-04` Playwright
- [ ] `FETEST-05` Timeline controls
- [ ] `FETEST-09` State management
<!-- /features -->

---

## Step 26 — Track overlays & full frame state

**Tests first:**
- Backend unit tests: gap calculation (leader, lapped cars), tyre age, pit-window status, and
  active penalties expiring.
- Frontend pure-function tests: state → overlay draw commands (sector colour rules for
  purple/green/yellow, the SC banner, yellow-sector highlighting).

**What:** extend `PlaybackFrame` and `RaceState` with gaps, tyre state, active penalties, flags,
pit-window status and weather. Draw them: sector colouring (purple, green, yellow), gap labels, DRS
indicator, the pit lane (derived like Step 14), SC car and flag colouring, yellow-flag sectors, a
weather badge, sector markers and a mini-map. Use D3 scales and colours for consistency.

**Why:** dots alone don't tell the race story. Overlays do.

**Check it:** during a known SC period the overlay shows it, and yellow sectors match race
control messages.

<!-- features:26 -->
**Checks off (0/19):**

- [ ] `TRACK-03` Real-time sector coloring
- [ ] `TRACK-05` Gap visualization
- [ ] `TRACK-06` DRS indicators
- [ ] `TRACK-07` Pit lane rendering
- [ ] `TRACK-08` Safety car visualization
- [ ] `TRACK-09` Yellow flag visualization
- [ ] `TRACK-10` Weather overlays
- [ ] `TRACK-11` Track sectors
- [ ] `TRACK-12` Mini-map mode
- [ ] `STATE-02` Gap calculations
- [ ] `STATE-03` Tire states
- [ ] `STATE-04` Active penalties
- [ ] `STATE-05` Current flags
- [ ] `STATE-06` Pit window status
- [ ] `FE-07` D3.js
- [ ] `FRAME-02` Track state
- [ ] `FRAME-04` Current flags
- [ ] `FRAME-05` Weather state
- [ ] `FRAME-06` Gap data
<!-- /features -->

---

## Step 27 — Telemetry visualization (on demand)

**Tests first:**
- Backend: distance-based alignment of two laps; the lap-delta trace ends at the lap-time
  difference; sector delta maths.
- Fuel/ERS estimate models tested against hand-calculated values, and labelled as estimates.
- Frontend: transformation functions (API → chart series) tested; RTL renders a chart for given
  data, and shows a loading state while the on-demand fetch (Step 13) runs.

**What:** `/races/:year/:sessionKey/telemetry?driver=VER&lap=…` — Recharts traces for speed,
throttle, brake, gear and RPM against distance, fetched **on demand** (Step 13) and cached. Tyre
compound, age and stint; fuel and ERS *estimates*; sector deltas and lap-vs-lap delta between two
drivers. Charts follow the replay clock.

**Why:** the "full telemetry playback" half of the vision — paid for only when someone looks.

**Check it:** compare two drivers' fastest laps; the delta trace ends at their lap-time difference.

<!-- features:27 -->
**Checks off (0/14):**

- [ ] `TELE-01` Speed graph
- [ ] `TELE-02` Throttle graph
- [ ] `TELE-03` Brake graph
- [ ] `TELE-04` Gear display
- [ ] `TELE-05` RPM estimate
- [ ] `TELE-06` Tire compound
- [ ] `TELE-07` Tire age
- [ ] `TELE-08` Tire stint
- [ ] `TELE-09` Fuel estimate
- [ ] `TELE-10` ERS estimate
- [ ] `TELE-11` Sector deltas
- [ ] `TELE-12` Lap delta comparisons
- [ ] `FE-10` Recharts
- [ ] `FETEST-07` Telemetry rendering
<!-- /features -->

---

## Step 28 — Timeline UI & synchronisation

**Tests first:**
- RTL tests:
  - clicking an event calls `seek(event.timestamp)`;
  - the current event is highlighted for a given clock;
  - filters hide event types.
- **Outer test:** the Playwright journey from the Check-it below.

**What:** event markers on the scrub bar and a filterable event list, both from Step 22. Clicking an
event seeks the player, and the list highlights the current event during playback. Push timeline
updates over the WebSocket.

**Why:** the timeline is how users find the interesting moments.

**Check it:** Playwright: click "Safety car" and the player seeks there, with all views in sync.

<!-- features:28 -->
**Checks off (0/2):**

- [ ] `WS-03` Timeline synchronization
- [ ] `FETEST-06` Playback synchronization
<!-- /features -->

---

## Step 29 — Race hub pages

**Tests first:**
- RTL with MSW: the hub lists the available leaves; a pre-2023 race shows replay disabled with
  the explanation.
- **Outer test:** a Playwright journey through every leaf of one race.

**What:** `/races/:year/:sessionKey` as a hub linking to the leaves: results, timeline, lap-pace
chart, telemetry and replay. Mark the Grand Prix lens as done on the dashboard. Pre-2023 races show
results and analytics, with replay greyed out and an explanation.

**Why:** this completes the Grand Prix branch of the information architecture, and **Phase 2 is
done**.

**Check it:** every leaf works for a 2024 race. A 1998 race shows results and explains why there's
no replay.

<!-- features:29 -->
**Checks off (0/2):**

- [ ] `NAV-02` Grand Prix — explore a single event: results, timeline, analytics, telemetry, replay.
- [ ] `NAV-07` Grand Prix: /races/:year (GP list, year filter) → /races/:year/:sessionKey (race hub) →
<!-- /features -->

---

# Phase 3 — Championship & analytics

---

## Step 30 — Championship engine

**Tests first:**
- Parameterised tests per `PointsSystem`: finishing position → points for each era, plus the
  fastest-lap bonus rules, sprint points and half points.
- Calculator tests: tie-breaks (count of wins, then seconds, …).
- **Outer test:** final standings for 1950, 2008, 2021 and 2024 equal the official tables (stored
  as fixtures).
- Dynamic in-race standings: the "if they finished now" points for a given race order.

**What:** the `championship` package. A `PointsSystem` strategy per era (pre-1991, 1991–2002,
2003–2009, 2010+, fastest-lap bonus 2019–2024, sprint formats 2021+, and half points for shortened
races), and a calculator that rebuilds standings race by race into `championship_snapshots`. Add
in-race dynamic standings ("if they finished now") for the replay, and a "championship lead
change" timeline event.

**Why:** "standings as they stand during playback" is a signature feature, and points rules are a
textbook use of the Strategy pattern.

**Check it:** final standings for 1950, 2008, 2021 and 2024 match the official ones (a
parameterised test).

<!-- features:30 -->
**Checks off (0/9):**

- [ ] `TIME-14` Championship lead changes
- [ ] `CHAMP-04` Dynamic standings during race playback
- [ ] `CHAMP-05` Historical points systems
- [ ] `CHAMP-06` Sprint race support
- [ ] `STATE-07` Championship standings
- [ ] `DB-12` championship_snapshots
- [ ] `TEST-04` Calculators
- [ ] `MOD-07` championship-service
- [ ] `PAT-02` Strategy Pattern
<!-- /features -->

---

## Step 31 — Championship explorer UI

**Tests first:**
- RTL with MSW: the standings table renders and sorts; every results-grid cell links to the right
  race hub; the season comparison draws two series.

**What:** the Seasons lens: `/seasons` (year grid) → `/seasons/:year` hub → driver and constructor
standings, a race-by-race results grid, and a race list. Add season comparison: points-progression
lines for two seasons or two drivers.

**Check it:** 2021's grid shows the Abu Dhabi finale, and every cell links to that race's hub.

<!-- features:31 -->
**Checks off (0/6):**

- [ ] `CHAMP-01` Select any season
- [ ] `CHAMP-02` View constructor standings
- [ ] `CHAMP-03` View driver standings
- [ ] `CHAMP-07` Compare seasons
- [ ] `NAV-01` Seasons — explore a championship year: standings and full season results.
- [ ] `NAV-06` Seasons: /seasons (year grid) → /seasons/:year (season hub) → leaves
<!-- /features -->

---

## Step 32 — Driver analytics

**Tests first:**
- SQL tests against Testcontainers with a **seeded mini-dataset** (3 races, 4 drivers), where
  every stat is worked out by hand first: wins, podiums, DNFs, poles, average finish, wet vs dry.
- **Outer test:** `GET /api/drivers/{id}/stats` returns those numbers.

**What:** the `analytics` package. Compute wins, podiums, DNFs, fastest laps, poles, average finish,
overtakes, pit-stop averages, tyre tendencies, wet vs dry (from weather) and per-track performance.
Use SQL (window functions, materialized views refreshed after imports) rather than Java loops.
Serve it with `GET /api/drivers/{id}/stats`.

**Why:** this is where the "advanced PostgreSQL" goal is learnt.

Count poles as **race grid position 1**, not qualifying P1: the `qualifying` view is the order before
penalties (Step 10 found Hamilton 107 "fastest in qualifying" vs 104 official poles).

**Check it:** career wins and poles for a few drivers match public records, and `EXPLAIN ANALYZE`
shows index use.

<!-- features:32 -->
**Checks off (0/13):**

- [ ] `DRV-01` Wins
- [ ] `DRV-02` Podiums
- [ ] `DRV-03` DNFs
- [ ] `DRV-04` Fastest laps
- [ ] `DRV-05` Pole positions
- [ ] `DRV-06` Average finish
- [ ] `DRV-07` Overtake statistics
- [ ] `DRV-08` Pit stop averages
- [ ] `DRV-09` Tire strategy tendencies
- [ ] `DRV-10` Wet vs dry performance
- [ ] `DRV-11` Track-specific performance
- [ ] `API-04` GET /drivers/{id}/stats
- [ ] `MOD-05` analytics-service
<!-- /features -->

---

## Step 33 — Constructor analytics

**Tests first:**
- The same seeded-dataset approach for each constructor metric: reliability, pit-stop medians,
  race and qualifying pace gaps, and development trend.

**What:** reliability (DNFs by cause), pit-stop efficiency, race pace and qualifying pace (median
gap to the fastest), development trend across a season, and strategy performance (positions
gained per strategy).

**Check it:** pit-stop medians per team for one season look plausible against published data.

<!-- features:33 -->
**Checks off (0/6):**

- [ ] `CON-01` Reliability
- [ ] `CON-02` Pit stop efficiency
- [ ] `CON-03` Race pace
- [ ] `CON-04` Qualifying pace
- [ ] `CON-05` Development trend visualization
- [ ] `CON-06` Strategy performance
<!-- /features -->

---

## Step 34 — Drivers & Teams UI

**Tests first:**
- RTL with MSW: directories, hubs and tabs render; cross-links point at the right routes;
  breadcrumbs work.

**What:** `/drivers` → `/drivers/:acronym` (Stats · Season-by-season · Race log) and `/teams` →
`/teams/:name` (Overview · Reliability · Pace), plus cross-links: standings rows → driver hub, and
results cells → race hub.

**Check it:** you can go from any table row to the entity's page and back with breadcrumbs.

<!-- features:34 -->
**Checks off (0/2):**

- [ ] `NAV-03` Drivers & Teams — explore competitors: per-driver and per-constructor stats/analytics.
- [ ] `NAV-08` Drivers & Teams: /drivers directory → /drivers/:acronym hub (Stats · Season-by-season ·
<!-- /features -->

---

## Step 35 — Multi-race comparison

**Tests first:**
- Service tests over seeded data: the same circuit across two years and driver vs driver.
- API tests for `/api/compare/...`, including validation (the circuit must exist in both years).

**What:** the same circuit across years (lap times, pace evolution, strategies) and driver vs driver
across races. Add `GET /api/compare/...` endpoints and a comparison page with overlaid charts.

**Check it:** Monza 2023 vs 2024 shows both strategies side by side.

<!-- features:35 -->
**Checks off (0/4):**

- [ ] `CMP-01` Compare same track across years
- [ ] `CMP-02` Compare driver performance
- [ ] `CMP-03` Compare tire strategies
- [ ] `CMP-04` Compare race pace evolution
<!-- /features -->

---

## Step 36 — Event sourcing & CQRS

**Tests first:**
- The existing replay, timeline and standings tests are the safety net. They must stay green
  throughout this refactor.
- New tests:
  - the event store is append-only (update and delete are rejected);
  - rebuilding read models from events equals the current read models (a golden test);
  - time travel: state at event #n is deterministic.

**What:** make the race event stream the source of truth for replay. Every state change is an
append-only event, `RaceState` is a fold over events, and snapshots are a cache. Split read models
(timeline, standings, stats tables) from the write side that ingests and derives events. Add
"time travel" debugging: dump state at any event number.

**Why:** the plan's architecture section asks for both. This is also the step that makes Phase 4's
Kafka natural, because events are already the interface.

**Check it:** delete all snapshots and read models, rebuild from events, and every replay and
standings test still passes.

<!-- features:36 -->
**Checks off (0/6):**

- [ ] `ES-01` Easier replay reconstruction
- [ ] `ES-02` Time travel debugging
- [ ] `ES-03` Efficient playback
- [ ] `ES-04` Historical querying
- [ ] `PAT-05` CQRS
- [ ] `PAT-06` Event Sourcing
<!-- /features -->

---

# Phase 4 — Distributed systems & production

---

## Step 37 — Security

**Tests first:**
- Write the **authorization matrix** as one parameterised test first: (anonymous / USER / ANALYST /
  ADMIN) × endpoint → expected status.
- JWT tests: valid, expired, wrong signature, wrong issuer, refresh flow.
- API-key and rate-limit (429) tests.
- Injection attempts (SQL in query parameters, script in text fields) are rejected or escaped.
- **Outer test:** the OAuth2 login → JWT → protected call flow.

**What:** Spring Security with stateless JWT (access and refresh tokens), OAuth2 login (GitHub or
Google) that issues your JWT, and roles USER / ANALYST / ADMIN (import endpoints are ADMIN only,
heavy analytics ANALYST). Add API keys for programmatic access, rate limiting (Bucket4j), and input
validation and sanitisation everywhere. Write tests for token validation, every role × endpoint
rule, and SQL-injection attempts.

**Why:** the plan's authentication section, and a must-have for anything deployed publicly.

**Check it:** an anonymous user can read races but gets 401 on admin endpoints, a USER gets 403, an
ADMIN gets 200, and a burst of requests hits 429.

<!-- features:37 -->
**Checks off (0/16):**

- [ ] `STACK-04` Spring Security
- [ ] `AUTH-01` JWT authentication
- [ ] `AUTH-02` OAuth2 login
- [ ] `AUTH-03` Role-based permissions
- [ ] `AUTH-04` Rate limiting
- [ ] `AUTH-05` API key support
- [ ] `ROLE-01` USER
- [ ] `ROLE-02` ADMIN
- [ ] `ROLE-03` ANALYST
- [ ] `TEST-11` Authentication flows
- [ ] `TEST-15` JWT validation
- [ ] `TEST-16` Authorization checks
- [ ] `TEST-17` Injection prevention
- [ ] `NFR-11` JWT encryption
- [ ] `NFR-13` Input sanitization
- [ ] `MOD-01` auth-service
<!-- /features -->

---

## Step 38 — Redis caching

**Tests first:**
- Testcontainers Redis tests:
  - the second call doesn't reach the repository (verify the mock is called once);
  - an import evicts the affected caches;
  - WebSocket session state survives a context restart.

**What:** add a `redis` service to compose and `spring-boot-starter-data-redis` with Spring Cache.
Cache timelines, replay snapshots and checkpoints, hot telemetry chunks and leaderboards, and store
WebSocket session state in Redis so any instance can serve any client. Write Testcontainers Redis
tests.

**Check it:** the second request for a timeline is served from cache (check logs or metrics), and a
backend restart doesn't drop replay sessions.

<!-- features:38 -->
**Checks off (0/9):**

- [ ] `STACK-07` Spring Cache
- [ ] `CACHE-01` Timeline caching
- [ ] `CACHE-02` Race playback snapshots
- [ ] `CACHE-03` Session state
- [ ] `CACHE-04` Frequently accessed telemetry
- [ ] `CACHE-05` Leaderboards
- [ ] `CACHE-06` Replay checkpoints
- [ ] `TEST-07` Redis tests
- [ ] `NFR-10` Distributed caching
<!-- /features -->

---

## Step 39 — TimescaleDB for telemetry

**Tests first:**
- A migration test on the `timescale/timescaledb` image: `telemetry_points` is a hypertable
  (`timescaledb_information.hypertables`), and the compression policy exists.
- The whole existing telemetry and replay suite passes unchanged on the new image.

**What:** switch the Postgres image to `timescale/timescaledb` (PG16). In a Flyway migration, turn
`telemetry_points` into a hypertable partitioned by time, add compression for old chunks, and add
continuous aggregates for downsampled views. Benchmark before and after.

**Why:** millions of rows per season. This is the "handle millions of telemetry points"
requirement.

**Check it:** storage drops after compression, and a time-range query is faster than it was in the
Step 13 benchmark.

<!-- features:39 -->
**Checks off (0/5):**

- [ ] `TSDB-01` Telemetry time-series storage
- [ ] `TSDB-02` Efficient timestamp querying
- [ ] `TSDB-03` Compression
- [ ] `TSDB-04` Fast analytics
- [ ] `NFR-04` Handle millions of telemetry points
<!-- /features -->

---

## Step 40 — Kafka event streaming

**Tests first:**
- Testcontainers Kafka tests:
  - publish → the consumer handles it;
  - an idempotent consumer (the same message twice → one effect);
  - a transaction rolled back → nothing published (the outbox);
  - a consumer stopped and restarted catches up.

**What:** add Kafka to compose (KRaft mode, no ZooKeeper) and the topics `telemetry-stream`,
`race-events`, `replay-events`, `analytics-jobs` and `ingestion-status`. Ingestion publishes, while
detectors, the read models from Step 36 and analytics consume. Use an outbox table for reliable
publishing. Write Testcontainers Kafka tests.

**Why:** decoupled, scalable ingestion and async analytics. Phase 4's headline.

**Check it:** stop the analytics consumer, run an import, start it again, and it catches up with
nothing lost.

<!-- features:40 -->
**Checks off (0/7):**

- [ ] `KAFKA-01` telemetry-stream
- [ ] `KAFKA-02` race-events
- [ ] `KAFKA-03` replay-events
- [ ] `KAFKA-04` analytics-jobs
- [ ] `KAFKA-05` ingestion-status
- [ ] `TEST-06` Kafka tests
- [ ] `OPS-03` More infrastructure: add the commented-out services; each gets its own healthcheck, named
<!-- /features -->

---

## Step 41 — Elasticsearch search

**Tests first:**
- Testcontainers Elasticsearch: index then search; typo tolerance; filters by season, type and
  driver; a deleted entity disappears from results.

**What:** add an Elasticsearch service and index drivers, races and events (fed from Kafka). Add
`GET /api/search?q=` with full-text and filters (season, type, driver), and a search box in the
frontend.

**Check it:** "safety car monza" finds the right events, and typos still match.

<!-- features:41 -->
**Checks off (0/5):**

- [ ] `SEARCH-01` Fast event searching
- [ ] `SEARCH-02` Driver search
- [ ] `SEARCH-03` Telemetry querying
- [ ] `SEARCH-04` Historical filtering
- [ ] `SEARCH-05` Full-text event search
<!-- /features -->

---

## Step 42 — Observability

**Tests first:**
- The correlation id from the request header (or a generated one) appears in the response header
  and in every log line's MDC.
- A log line parses as JSON with `traceId`.
- `/actuator/prometheus` exposes your custom metrics.

**What:**
- Structured JSON logs (Spring Boot's built-in structured logging) with a correlation id per request
  and per job.
- Micrometer → Prometheus, and Grafana dashboards for API latency, replay sessions, import
  throughput and Kafka lag.
- OpenTelemetry tracing exported to Jaeger.
- All of the above as compose services.

**Check it:** one request can be followed from browser → API → DB in Jaeger, and the dashboards show
live data.

<!-- features:42 -->
**Checks off (0/7):**

- [ ] `OBS-01` Structured JSON logging
- [ ] `OBS-02` Correlation IDs
- [ ] `OBS-03` Prometheus
- [ ] `OBS-04` Grafana
- [ ] `OBS-05` OpenTelemetry
- [ ] `OBS-06` Jaeger
- [ ] `PRAC-06` Use monitoring dashboards
<!-- /features -->

---

## Step 43 — Module boundaries

**Tests first:**
- `ApplicationModules.of(TelemetrixBackendApplication.class).verify()`. It's red until the
  module violations are fixed.

**What:** add Spring Modulith and a test that verifies module dependencies (for example, `race` must
not reach into `replay` internals). Document the module map and clean up violations. Record the
"modular monolith now, split services later" decision in an ADR.

**Why:** this is the plan's maintainability goals (clean architecture, DDD, modular services),
enforced by a test rather than by hope.

**Check it:** the Modulith verification test passes and generates module diagrams.

<!-- features:43 -->
**Checks off (0/4):**

- [ ] `NFR-14` Clean architecture
- [ ] `NFR-15` SOLID principles
- [ ] `NFR-16` Domain-driven design
- [ ] `NFR-17` Modular services
<!-- /features -->

---

## Step 44 — Performance & scalability

**Tests first:**
- Load tests are tests too: write the Gatling/k6 scenarios **with assertions** (p95 < 300 ms, error
  rate < 1%) first, run them red, then optimise until green.

**What:** load tests with Gatling or k6 for the REST API, WebSocket replay at scale (aim for the 10k
concurrent users target), and database stress. Profile and fix the hot spots: indexes, N+1 queries,
caching. Run two backend instances behind a load balancer to prove the API is stateless.

**Check it:** p95 API latency is under 300 ms at the target load. Publish the results in
`docs/benchmarks.md`.

<!-- features:44 -->
**Checks off (0/8):**

- [ ] `TEST-12` Telemetry streaming
- [ ] `TEST-13` Replay scalability
- [ ] `TEST-14` Database stress tests
- [ ] `NFR-02` API response < 300ms
- [ ] `NFR-03` Support 10k concurrent users
- [ ] `NFR-08` Horizontal scaling
- [ ] `NFR-09` Stateless APIs
- [ ] `PRAC-09` Benchmark performance
<!-- /features -->

---

## Step 45 — Resilience

**Tests first:**
- WireMock (or Toxiproxy via Testcontainers) tests:
  - OpenF1 returning 500s → retries, then the circuit opens;
  - a slow response → timeout;
  - Redis down → results served from the DB;
  - Elasticsearch down → SQL search.

**What:** Resilience4j retries, circuit breakers and timeouts around OpenF1, Jolpica, Redis and
Elasticsearch. Add graceful degradation: no Redis means serving from DB, no Elasticsearch means
basic SQL search.

**Check it:** kill Redis mid-replay and playback continues, only slower.

<!-- features:45 -->
**Checks off (0/2):**

- [ ] `NFR-06` Graceful degradation
- [ ] `NFR-07` Retry mechanisms
<!-- /features -->

---

## Step 46 — Full CI/CD

**Tests first:**
- The Playwright suite runs against the compose stack in CI.
- Prove the security scan works by adding a known-vulnerable dependency on a branch: the scan goes
  red.

**What:** extend CI with dependency and image security scans (OWASP Dependency-Check or Trivy, plus
CodeQL), Playwright end-to-end tests (optionally Cypress, if you want to compare) against the
compose stack, and image builds pushed to GHCR on `main`.

**Check it:** a PR runs the full matrix, and a merge to `main` publishes tagged images.

<!-- features:46 -->
**Checks off (0/3):**

- [ ] `FETEST-03` Cypress
- [ ] `OPS-04` CI: build both images in GitHub Actions, run the backend tests (Testcontainers) against...
- [ ] `CI-05` Security scan
<!-- /features -->

---

## Step 47 — Deploy the MVP

**Tests first:**
- A post-deploy smoke test job hits the public `/actuator/health` and one replay endpoint. If it
  fails, the deploy fails.

**What:** deploy to Railway, Render or Fly.io (pick one and record the choice in an ADR) with managed
Postgres, HTTPS, secrets in the platform, and a CI job that deploys on tag. Add uptime monitoring
and an SLO page for the 99.9% target.

**Check it:** a public URL plays a replay, and the uptime monitor reports it.

<!-- features:47 -->
**Checks off (0/6):**

- [ ] `CI-07` Deployment
- [ ] `DEPLOY-01` Railway
- [ ] `DEPLOY-02` Render
- [ ] `DEPLOY-03` Fly.io
- [ ] `NFR-05` 99.9% uptime
- [ ] `NFR-12` HTTPS everywhere
<!-- /features -->

---

## Step 48 — Advanced infrastructure

**Tests first:**
- `terraform validate` + `tflint` + `terraform plan` in CI. Optionally, Terratest to apply to a
  sandbox account and assert outputs.

**What:** Terraform for AWS (or another cloud): VPC, RDS or EKS, MSK or self-hosted Kafka,
ElastiCache. Add Kubernetes manifests or a Helm chart with HPA autoscaling, and move compose's
optional services into the cluster.

**Check it:** `terraform apply` from scratch brings up a working environment, and
`terraform destroy` removes it.

<!-- features:48 -->
**Checks off (0/3):**

- [ ] `DEPLOY-04` Kubernetes
- [ ] `DEPLOY-05` AWS
- [ ] `DEPLOY-06` Terraform
<!-- /features -->

---

# Phase 5 — Data science & polish

---

## Step 49 — Python analytics workspace

**Tests first:**
- pytest set up first. Cleaning functions are tested on small hand-made DataFrames (duplicates
  dropped, units normalised, missing laps flagged) before any pipeline uses them.

**What:** `analytics/` as a Python project (`uv` or `pip` + `pyproject.toml`) with pandas, numpy,
scipy, matplotlib, plotly, scikit-learn and Jupyter. Read from the Telemetrix Postgres (read-only
user), and also pull FastF1 for 2018–2022 telemetry and any public datasets. Write cleaning
pipelines as scripts, not only notebooks, and check the FIA document licences before using them.

**Check it:** `python -m analytics.pipelines.clean --season 2024` writes a clean parquet dataset.

<!-- features:49 -->
**Checks off (0/5):**

- [ ] `SRC-03` FastF1 datasets
- [ ] `SRC-04` FIA timing archives (where legal)
- [ ] `SRC-05` Public telemetry datasets
- [ ] `PY-01` Analytics pipelines
- [ ] `PY-02` Data cleaning
<!-- /features -->

---

## Step 50 — Tyre degradation & pit strategy

**Tests first:**
- pytest on synthetic data with a known degradation slope: the fit recovers it within tolerance.
- Undercut maths tested on a hand-made two-car scenario.

**What:** fuel-corrected lap-time regression per compound and stint for degradation curves, and
undercut/overcut analysis on stop timing. Use statistical modelling with scipy and statsmodels.

**Check it:** degradation curves look sensible for a known high-deg race.

<!-- features:50 -->
**Checks off (0/3):**

- [ ] `PY-03` Statistical modeling
- [ ] `PYA-01` Tire degradation analysis
- [ ] `PYA-02` Pit strategy analysis
<!-- /features -->

---

## Step 51 — Driver clustering & sector dominance

**Tests first:**
- pytest: the clustering pipeline is deterministic with a fixed seed. Sector dominance on
  synthetic data where the winner per sector is known.

**What:** cluster drivers by style features (braking points, throttle application, sector
strengths) with k-means and PCA, and build sector dominance maps per circuit.

<!-- features:51 -->
**Checks off (0/2):**

- [ ] `PYA-03` Driver clustering
- [ ] `PYA-06` Sector dominance analysis
<!-- /features -->

---

## Step 52 — Predictions

**Tests first:**
- pytest with evaluation thresholds: each model must beat a naive baseline on held-out races.
  The test asserts it, so a regression fails the build.

**What:** race pace prediction from practice long runs, and overtake probability from gap, tyre
delta and DRS. Use scikit-learn with proper train/test splits by race, and write up the
evaluation.

<!-- features:52 -->
**Checks off (0/2):**

- [ ] `PYA-04` Race pace prediction
- [ ] `PYA-05` Overtake probability
<!-- /features -->

---

## Step 53 — Analytics in the app

**Tests first:**
- **Outer test:** an integration test (Testcontainers Kafka + Postgres) where an
  `analytics-jobs` message → a results row appears and the API returns it.

**What:** analytics jobs triggered through Kafka `analytics-jobs`, with results written back to
Postgres tables or exported files the frontend shows (degradation charts on the race hub, cluster
plots on driver pages).

**Check it:** a new import automatically produces updated analytics in the UI.

<!-- features:53 -->
**Checks off (0/1):**

- [ ] `PY-04` Visualization exports
<!-- /features -->

---

## Step 54 — Portfolio polish

**Tests first:**
- No new behaviour, so no new tests. Check that the coverage report is published and the README
  explains how to run every test suite.

**What:** architecture diagrams (C4: context, containers, components) in `docs/`, proper READMEs for
each top-level folder, a `docs/tech-debt.md` register, and screenshots and a demo GIF in the root
README.

**Check it:** someone new can clone the repo, run it and understand the architecture in 15 minutes.

<!-- features:54 -->
**Checks off (0/3):**

- [ ] `PRAC-03` Write architecture diagrams
- [ ] `PRAC-07` Write proper READMEs
- [ ] `PRAC-08` Track technical debt
<!-- /features -->

---

# Stretch

## Step 55 — Stretch goals

Pick any, in any order, once the core is done. Each one is its own mini-project: write an ADR first.

**Tests first:**
- The same rule as everywhere: each stretch feature starts with a failing outer test.

<!-- features:55 -->
**Checks off (0/11):**

- [ ] `STRETCH-01` 3D visualization
- [ ] `STRETCH-02` Live race mode
- [ ] `STRETCH-03` Mobile app
- [ ] `STRETCH-04` AI commentary
- [ ] `STRETCH-05` Voice search
- [ ] `STRETCH-06` Fantasy simulator
- [ ] `STRETCH-07` Race prediction engine
- [ ] `STRETCH-08` Custom telemetry dashboards
- [ ] `STRETCH-09` Historical what-if simulator
- [ ] `STRETCH-10` Driver racing lines
- [ ] `STRETCH-11` ML-generated insights
<!-- /features -->
