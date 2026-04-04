# Установка и запуск (PostgreSQL)

Документ описывает локальную установку проекта с PostgreSQL как основной БД.

## 1. Требования

- macOS/Linux (или Windows с совместимым shell)
- Git
- Java 21
- Node.js 18+
- Docker Desktop (рекомендуется для локального PostgreSQL)

Проверка версий:

```bash
java -version
node -v
npm -v
docker --version
docker compose version
```

## 2. Подготовка проекта

```bash
cd /Users/nick/IdeaProjects/human-guided-development
```

## 3. Поднять PostgreSQL (рекомендуемый путь)

Для локальной разработки используйте `deploy/compose.dev.yaml`:

```bash
cd /Users/nick/IdeaProjects/human-guided-development
docker compose -f deploy/compose.dev.yaml up -d
```

Проверить, что контейнер запущен:

```bash
docker compose -f deploy/compose.dev.yaml ps
```

Параметры БД по умолчанию:

- host: `localhost`
- port: `5432`
- database: `hgsdlc`
- username: `hgsdlc`
- password: `hgsdlc`

## 4. Запуск backend с PostgreSQL

```bash
cd /Users/nick/IdeaProjects/human-guided-development/backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun
```

Что важно:

- Liquibase включен и применяет миграции автоматически.
- API поднимается на `http://localhost:8080`.
- Seed-пользователь по умолчанию: `admin / admin`.
- Если `8080` занят, остановите процесс или задайте `SERVER_PORT`.

## 5. Запуск frontend

В отдельном терминале:

```bash
cd /Users/nick/IdeaProjects/human-guided-development/frontend
npm install
npm run dev
```

Frontend будет доступен на `http://localhost:5173`, API-запросы `/api` проксируются на `http://localhost:8080`.

## 6. Быстрая проверка

Проверить backend:

```bash
curl -fsS http://localhost:8080/actuator/health
```

Открыть frontend:

- `http://localhost:5173`

## 7. Остановка

Остановить backend/frontend: `Ctrl+C` в соответствующих терминалах.

Остановить PostgreSQL:

```bash
docker compose -f deploy/compose.dev.yaml down
```

Удалить volume с данными БД (опционально, осторожно):

```bash
docker compose -f deploy/compose.dev.yaml down -v
```

## 8. Альтернатива: запуск без PostgreSQL (H2)

Если переменные `DB_URL/DB_USERNAME/DB_PASSWORD` не заданы, backend стартует с in-memory H2:

```bash
cd /Users/nick/IdeaProjects/human-guided-development/backend
./gradlew bootRun
```

Для рабочей разработки рекомендуется PostgreSQL, так как часть миграций и поведения ориентирована на него.

## 9. Частые проблемы

- `Connection refused` к БД: проверить, что `sdlc-postgres` запущен и порт `5432` свободен.
- Ошибки аутентификации БД: проверить `DB_USERNAME/DB_PASSWORD`.
- Ошибки Java: убедиться, что активна Java 21.
- Frontend не видит backend: проверить, что backend доступен на `http://localhost:8080`.
- Если видите ошибку вида `25.0.2`, убедитесь, что backend запускается на Java 21, а не Java 25.
