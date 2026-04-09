FROM gradle:8.8-jdk21 AS builder
WORKDIR /workspace

COPY backend/settings.gradle.kts backend/build.gradle.kts backend/gradlew backend/gradlew.bat ./
COPY backend/gradle ./gradle
COPY backend/src ./src

RUN chmod +x ./gradlew && ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre-jammy AS runtime

ARG QWEN_CLI_VERSION=0.14.0
ARG NODE_VERSION=23.5.0
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl bash git xz-utils \
    && arch="$(dpkg --print-architecture)" \
    && case "${arch}" in \
        amd64) node_arch='x64' ;; \
        arm64) node_arch='arm64' ;; \
        *) echo "Unsupported architecture: ${arch}" >&2; exit 1 ;; \
    esac \
    && curl -fsSLO "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${node_arch}.tar.xz" \
    && tar -xJf "node-v${NODE_VERSION}-linux-${node_arch}.tar.xz" -C /usr/local --strip-components=1 --no-same-owner \
    && rm -f "node-v${NODE_VERSION}-linux-${node_arch}.tar.xz" \
    && npm install -g "@qwen-code/qwen-code@${QWEN_CLI_VERSION}" \
    && npm cache clean --force \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --create-home --uid 10001 --shell /bin/bash app \
    && mkdir -p /app /tmp/workspace /home/app/.qwen \
    && chown -R app:app /app /tmp/workspace /home/app

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
COPY deploy/backend-entrypoint.sh /app/backend-entrypoint.sh

RUN chmod +x /app/backend-entrypoint.sh

ENV HOME=/home/app
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

WORKDIR /app
USER app
EXPOSE 8080

ENTRYPOINT ["/app/backend-entrypoint.sh"]
