# Multi-stage build: compile with Gradle, run with JRE
# Build context: olo repo root. CI sparse-checkouts olo-mono shared libs.
FROM gradle:8-jdk21 AS builder
WORKDIR /workspace

COPY olo-mono/gradle olo-mono/gradle
COPY olo-mono/olo-spi olo-mono/olo-spi
COPY olo-mono/olo-annotation olo-mono/olo-annotation
COPY olo-mono/olo-definition olo-mono/olo-definition
COPY olo-mono/olo-workflow-input olo-mono/olo-workflow-input
COPY olo-temporal-sdk olo-temporal-sdk
COPY gradle gradle
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
COPY docker/olo-mono-ci.init.gradle /tmp/olo-mono-ci.init.gradle
COPY src src

RUN cd olo-mono/olo-spi \
    && gradle -I /tmp/olo-mono-ci.init.gradle -PoloPublishBuildDir=../build/publish-work/olo-spi publishMavenPublicationToOloMonoRepository -x test --no-daemon \
    && test -f ../build/repo/org/olo/olo-spi/0.1.0-SNAPSHOT/maven-metadata.xml \
    && cd ../olo-annotation \
    && gradle -I /tmp/olo-mono-ci.init.gradle -PoloPublishBuildDir=../build/publish-work/olo-annotation publishMavenPublicationToOloMonoRepository -x test --no-daemon \
    && test -f ../build/repo/org/olo/olo-annotation/0.1.0-SNAPSHOT/maven-metadata.xml \
    && cd ../olo-definition \
    && gradle -I /tmp/olo-mono-ci.init.gradle -PoloPublishBuildDir=../build/publish-work/olo-definition :olo-definition:publishMavenPublicationToOloMonoRepository -x test --no-daemon \
    && test -f ../build/repo/org/olo/olo-definition/0.1.0-SNAPSHOT/maven-metadata.xml \
    && cd ../olo-workflow-input \
    && gradle -I /tmp/olo-mono-ci.init.gradle -PoloPublishBuildDir=../build/publish-work/olo-workflow-input publishMavenPublicationToOloMonoRepository -x test --no-daemon \
    && test -f ../build/repo/org/olo/olo-workflow-input/0.1.0-SNAPSHOT/maven-metadata.xml \
    && cd /workspace \
    && chmod +x gradlew \
    && ./gradlew -I /tmp/olo-mono-ci.init.gradle bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN adduser -D -h /app appuser
USER appuser

COPY --from=builder /workspace/build/libs/olo-backend-*.jar app.jar
COPY olo-mono/olo-definition/olo-configuration /app/olo-configuration

ENV OLO_CONFIGURATION_DIR=/app/olo-configuration

EXPOSE 7080
ENTRYPOINT ["java", "-jar", "app.jar"]
