# syntax=docker/dockerfile:1

# ---- build stage: compile the Spring Boot fat jar ----
# Java 26 toolchain (build.gradle). Gradle wrapper pins the Gradle version.
FROM eclipse-temurin:26-jdk AS build
WORKDIR /workspace

# Warm the dependency/wrapper cache before copying sources so that
# source-only changes don't invalidate the Gradle download layer.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true

COPY src ./src
# bootJar only (skip tests here — CI runs them separately). Produces
# build/libs/yeolo-<version>.jar; the plain jar is not built by this task.
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- runtime stage: minimal JRE, non-root ----
FROM eclipse-temurin:26-jre AS runtime
WORKDIR /app

# Run as an unprivileged user.
RUN groupadd --system app && useradd --system --gid app --home /app app
USER app

COPY --from=build --chown=app:app /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080
# Respect container memory limits; allow per-env tuning via JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
