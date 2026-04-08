FROM gradle:8.8-jdk21 AS builder
WORKDIR /workspace

COPY backend/settings.gradle.kts backend/build.gradle.kts backend/gradlew backend/gradlew.bat ./
COPY backend/gradle ./gradle
COPY backend/src ./src

# Windows checkouts may use CRLF; Linux then fails with "./gradlew: not found" on shebang.
RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew && ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre-jammy AS runtime

ARG QWEN_CLI_VERSION=0.14.2
ARG NODE_VERSION=22.22.2
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl git zsh \
    && dpkgArch="$(dpkg --print-architecture)" \
    && case "${dpkgArch}" in \
         amd64) NODE_ARCH=x64 ;; \
         arm64) NODE_ARCH=arm64 ;; \
         *) echo "unsupported architecture: ${dpkgArch}"; exit 1 ;; \
       esac \
    && curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${NODE_ARCH}.tar.gz" -o /tmp/node.tgz \
    && tar -xzf /tmp/node.tgz -C /usr/local --strip-components=1 \
    && rm /tmp/node.tgz \
    && npm install -g "@qwen-code/qwen-code@${QWEN_CLI_VERSION}" \
    && npm cache clean --force \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --create-home --uid 10001 --shell /bin/bash app \
    && mkdir -p /app /tmp/workspace /home/app/.qwen \
    && chown -R app:app /app /tmp/workspace /home/app

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
COPY deploy/backend-entrypoint.sh /app/backend-entrypoint.sh

# Windows CRLF breaks shebang: /usr/bin/env: 'bash\r': No such file or directory
RUN sed -i 's/\r$//' /app/backend-entrypoint.sh && chmod +x /app/backend-entrypoint.sh

ENV HOME=/home/app
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

WORKDIR /app
USER app
EXPOSE 8080

ENTRYPOINT ["/app/backend-entrypoint.sh"]
