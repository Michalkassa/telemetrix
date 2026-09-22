# Building Telemetrix step by step

A journal of how this project was built, one small step at a time. Each step says **what** was
added, **why**, and **how to check it** yourself. Read it top to bottom alongside the code.

Stack: Java 25 · Spring Boot 4.1 · Maven · PostgreSQL 16 · Docker.

> Commands below assume you are in `backend/` unless they say otherwise.

---

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

---

## From here on: you

The project is an empty but working skeleton. Every part you'll need next (migrations, entities,
repositories, services, controllers, error handling, calling OpenF1, scheduling, tests, the
Dockerfile) is explained with commented example code in [`templates/`](templates/README.md).
Start with `templates/03-migration/` and your first Telemetrix table.
