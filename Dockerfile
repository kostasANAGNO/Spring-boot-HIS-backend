# syntax=docker/dockerfile:1

# Base images can be pinned to a digest or taken from an internal mirror: --build-arg BUILD_IMAGE=... RUNTIME_IMAGE=...
ARG BUILD_IMAGE=eclipse-temurin:21-jdk
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

# ---- build: compiles and packages the application (tests run in CI, not here) ----------------------------------
FROM ${BUILD_IMAGE} AS build
WORKDIR /workspace

# dependencies first, so they stay cached until the pom changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY lombok.config ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/his-*.jar extract --layers --launcher --destination /workspace/layers

# ---- runtime: only a JRE, a non-root user and the application's layers ---------------------------------------
FROM ${RUNTIME_IMAGE} AS runtime
RUN addgroup -S his && adduser -S -G his his
WORKDIR /app

# layers from least to most frequently changing, for fast rebuilds and small pushes
COPY --from=build /workspace/layers/dependencies/ ./
COPY --from=build /workspace/layers/spring-boot-loader/ ./
COPY --from=build /workspace/layers/snapshot-dependencies/ ./
COPY --from=build /workspace/layers/application/ ./

USER his
EXPOSE 8080

# Configuration comes from the environment: DB_URL, DB_USERNAME, DB_PASSWORD and JWT_SECRET are required
# (see README). No profile is active by default, so Swagger UI stays off.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
