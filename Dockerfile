# Multi-stage build: compile with Gradle, run with JRE
# Build context: olo repo root. Requires olo-mono/olo-spi + olo-annotation (CI checks out olo-mono).
FROM gradle:8-jdk21 AS builder
WORKDIR /workspace

COPY olo-mono/gradle olo-mono/gradle
COPY olo-mono/olo-spi olo-mono/olo-spi
COPY olo-mono/olo-annotation olo-mono/olo-annotation
COPY olo-mono/olo-annotation-processor olo-mono/olo-annotation-processor
COPY olo-definition olo-definition
COPY olo-workflow-input olo-workflow-input
COPY olo-temporal-sdk olo-temporal-sdk
COPY gradle gradle
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
COPY src src

RUN chmod +x gradlew olo-mono/publish-libs.sh \
    && olo-mono/publish-libs.sh \
    && ./gradlew bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN adduser -D -h /app appuser
USER appuser

COPY --from=builder /workspace/build/libs/olo-backend-*.jar app.jar
COPY olo-definition/olo-configuration /app/olo-configuration

ENV OLO_CONFIGURATION_DIR=/app/olo-configuration

EXPOSE 7080
ENTRYPOINT ["java", "-jar", "app.jar"]
