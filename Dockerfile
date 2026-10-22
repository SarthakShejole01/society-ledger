# syntax=docker/dockerfile:1

# One image: the backend serves the built web app, so there is a single thing to run.

# ---- 1. Angular build -------------------------------------------------------
FROM node:24-alpine AS frontend
WORKDIR /app/frontend
# Dependencies first, so this layer is reused until package*.json change.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --legacy-peer-deps
COPY frontend/ ./
RUN npx ng build --configuration production

# ---- 2. Spring Boot build ---------------------------------------------------
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app/backend
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle gradle
RUN ./gradlew --no-daemon --console=plain dependencies > /dev/null
COPY backend/src src
# The web app is served by Spring Boot from classpath:/static.
COPY --from=frontend /app/frontend/dist/frontend/browser src/main/resources/static
# Tests run in CI (they need Docker for Testcontainers); the image build only packages.
RUN ./gradlew --no-daemon --console=plain bootJar -x test

# ---- 3. Runtime -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=backend /app/backend/build/libs/*-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- "http://localhost:${PORT:-8080}/actuator/health" | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
