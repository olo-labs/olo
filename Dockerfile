# Multi-stage build: compile with Gradle, run with JRE
FROM gradle:8-jdk21 AS builder
WORKDIR /app

# Copy project (gradle wrapper, build files, source, subprojects)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
COPY olo-sdk olo-sdk
COPY olo-worker-input olo-worker-input

# Build executable JAR (skip tests for faster image build; run tests in CI separately if desired)
RUN gradle bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN adduser -D -h /app appuser
USER appuser

COPY --from=builder /app/build/libs/olo-backend-*.jar app.jar

EXPOSE 7080
ENTRYPOINT ["java", "-jar", "app.jar"]
