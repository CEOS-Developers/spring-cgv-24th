# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy@sha256:4b1a74fbffbe6b4e7b6f0171a472478282fb463790a51edf10e465d578f22b69 AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle/wrapper/ ./gradle/wrapper/
COPY src/main/ ./src/main/
RUN chmod +x gradlew \
    && ./gradlew --no-daemon --max-workers=1 bootJar -x test \
    && cp build/libs/*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy@sha256:1eac8b8fee11ca7d6725a48bc5d41992ed1561504ebae25490d2d062847e8269 AS runtime
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/app.jar ./app.jar

ENV SPRING_PROFILES_ACTIVE=deploy \
    JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=6 \
    CMD curl --fail --silent --show-error --max-time 3 http://localhost:8080/actuator/health > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
