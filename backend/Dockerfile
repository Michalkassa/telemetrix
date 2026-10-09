# syntax=docker/dockerfile:1
# The backend image (plan.md → DevOps; templates/14-docker). Built by docker-compose.yml.
# Tests aren't run here: they run on the host or in CI, where Testcontainers has Docker.

# ─── Build: JDK + Maven wrapper ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

# build files first: dependencies stay cached as long as pom.xml doesn't change
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Djacoco.skip=true \
 && cp target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ─── Run: JRE only, non-root, layers least-changing first ───────────────────────────────────────
FROM eclipse-temurin:25-jre-noble
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER spring
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
