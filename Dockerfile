# Multi-stage build: compile with Gradle, run with JRE
# Build context should be the olo-labs workspace root (parent of olo/ and oolo-mono/).
FROM gradle:8-jdk21 AS builder
WORKDIR /workspace

COPY olo-mono/olo-definition olo-mono/olo-definition
COPY olo-mono/olo-workflow-input olo-mono/olo-workflow-input
COPY olo-mono/olo-configuration olo-mono/olo-configuration
COPY olo olo

WORKDIR /workspace/olo
RUN gradle bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN adduser -D -h /app appuser
USER appuser

COPY --from=builder /workspace/olo/build/libs/olo-backend-*.jar app.jar
COPY --from=builder /workspace/olo-mono/olo-configuration /olo-mono/olo-configuration

ENV OLO_CONFIGURATION_DIR=/olo-mono/olo-configuration

EXPOSE 7080
ENTRYPOINT ["java", "-jar", "app.jar"]
