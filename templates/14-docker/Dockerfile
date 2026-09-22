# syntax=docker/dockerfile:1
# TEMPLATE 14a — Packaging the Spring Boot app as a Docker image
# Where it goes: backend/Dockerfile
# Build & run:   docker build -t telemetrix-backend backend/
#                docker run -p 8080:8080 telemetrix-backend
#
# A MULTI-STAGE build: stage 1 has the full JDK + Maven to COMPILE; stage 2 has only a JRE to RUN.
# The final image contains no compiler, no source code and no build cache — smaller and safer.
#
# Each instruction creates a cached LAYER. Docker reuses a layer if nothing it depends on changed,
# so put what changes RARELY first (dependencies) and what changes OFTEN last (your code).


# ─── Stage 1: build ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 1) Only the build files. As long as pom.xml doesn't change, the next (slow) step is cached.
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Download all dependencies. --mount=type=cache keeps ~/.m2 between builds (BuildKit feature).
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

# 2) Now the source code, then build the jar. Tests are skipped here: they run in CI or on your
#    machine (they need Docker themselves for Testcontainers).
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
 && cp target/*.jar app.jar \
 # Split the jar into layers (dependencies / Spring Boot loader / your classes) for stage 2.
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ─── Stage 2: run ───────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre
WORKDIR /app

# Don't run as root inside the container: create an unprivileged user.
RUN groupadd --system spring && useradd --system --gid spring spring

# Copy the layers, least-changing first. A code-only change now rebuilds just the last small layer.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER spring
EXPOSE 8080
# Let the JVM size its heap from the container's memory limit.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
