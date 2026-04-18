# Build Instructions

## Prerequisites

### Build Tools
- **Java**: JDK 21 (строго не выше Java 25)
- **Gradle**: 8.x (используется Wrapper через проект)
- **Node.js**: 18+
- **npm**: 9+

### Runtime Services
- **PostgreSQL**: 16-alpine (с расширением pgvector)
  - Docker образ: `pgvector/pgvector:pg16` или стандартный PostgreSQL с установленным расширением
  - Port: 5432
  - Database: hgsdlc
  - Username: hgsdlc
  - Password: hgsdlc
- **Docker Desktop**: для запуска PostgreSQL

### Environment Variables (опционально)
```bash
# PostgreSQL connection (если не задано - используется H2 in-memory)
export DB_URL="jdbc:postgresql://localhost:5432/hgsdlc"
export DB_USERNAME="hgsdlc"
export DB_PASSWORD="hgsdlc"

# OpenAI API (опционально, для OpenAI embeddings)
export OPENAI_API_KEY="your-api-key-here"
```

---

## Build Steps

### 1. Start PostgreSQL (для основной разработки)

```bash
cd /path/to/project/root
docker compose -f deploy/compose.dev.yaml up -d
```

**Проверка работоспособности:**
```bash
docker compose -f deploy/compose.dev.yaml ps
# Expect: postgres is "Up (healthy)"
```

**Для быстрого старта (H2 in-memory):**
Пропустить этот шаг - backend автоматически использует H2 если переменные БД не заданы.

---

### 2. Install Dependencies

#### Backend (Gradle)
```bash
cd backend
./gradlew build --dry-run  # Проверка зависимостей без сборки
```

Gradle автоматически загрузит все зависимости, включая:
- Spring Boot 3.3.0
- PostgreSQL driver
- Liquibase
- Testcontainers

#### Frontend (npm)
```bash
cd frontend
npm install
```

npm автоматически установит зависимости из package.json, включая:
- React 18
- Ant Design 5
- Vite
- ReactFlow
- Monaco Editor

---

### 3. Configure Environment

#### Backend Configuration

Файл конфигурации: `backend/src/main/resources/application.yml`

**Для PostgreSQL (рекомендуется):**
```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)  # macOS
export PATH="$JAVA_HOME/bin:$PATH"
SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun
```

**Для H2 in-memory (быстрый старт):**
```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)  # macOS
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew bootRun
```

**Проверка Java версии:**
```bash
java -version  # Должна быть Java 21, НЕ 25!
```

#### Frontend Configuration

Файл конфигурации: `frontend/vite.config.js`

Проксирование API на backend уже настроено:
```javascript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    }
  }
}
```

---

### 4. Build All Units

#### Build Backend
```bash
cd backend
./gradlew build
```

**Ожидаемый результат:**
- Компиляция Java кода без ошибок
- Liquibase миграции применены (если БД доступна)
- Тесты выполнены
- JAR файл создан: `backend/build/libs/human-guided-sdlc-backend-0.0.1-SNAPSHOT.jar`

**Artifacts location:**
- Build output: `backend/build/libs/`
- Test results: `backend/build/reports/tests/`
- Code coverage: `backend/build/reports/jacoco/` (если настроен)

#### Build Frontend
```bash
cd frontend
npm run build
```

**Ожидаемый результат:**
- Vite собираёт React приложение
- Production bundle создан: `frontend/dist/`
- Без ошибок и предупреждений

**Artifacts location:**
- Build output: `frontend/dist/`

---

### 5. Verify Build Success

#### Backend Verification

**Health check:**
```bash
curl -fsS http://localhost:8080/actuator/health
# Expect: {"status":"UP"}
```

**API availability:**
```bash
curl -fsS http://localhost:8080/api/catalog/skills
# Expect: JSON массив skills (может быть пустым)
```

#### Frontend Verification

**Запуск dev server:**
```bash
cd frontend
npm run dev
# Expect: Vite server запущен на http://localhost:5173
```

**Проверка в браузере:**
- Открыть `http://localhost:5173`
- UI должен загрузиться без ошибок в console

---

## Troubleshooting

### Dependency Errors

**Ошибка: Could not resolve dependencies**
```bash
# Очистить кэш Gradle и перескачать зависимости
cd backend
./gradlew clean --refresh-dependencies
```

**Ошибка: npm install failed**
```bash
cd frontend
rm -rf node_modules package-lock.json
npm install
```

### Compilation Errors

**Ошибка: Unsupported class file major version (Java 25)**
```bash
# Проверить версию Java
java -version
# Если Java 25, переключиться на Java 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

**Ошибка: Cannot connect to PostgreSQL**
```bash
# Проверить, что PostgreSQL запущен
docker compose -f deploy/compose.dev.yaml ps

# Проверить логи
docker compose -f deploy/compose.dev.yaml logs postgres

# Перезапустить БД
docker compose -f deploy/compose.dev.yaml restart postgres
```

### Liquibase Migration Errors

**Ошибка: Migration failed**
```bash
# Проверить статус миграций
cd backend
./gradlew bootRun --args='--spring.liquibase.enabled=true'

# Посмотреть логи Liquibase
tail -f logs/spring.log | grep -i liquibase
```

**Ошибка: pgvector extension not found**
```bash
# Убедиться, что используется правильный Docker образ
# В deploy/compose.dev.yaml должен быть pgvector/pgvector:pg16
docker compose -f deploy/compose.dev.yaml down
docker compose -f deploy/compose.dev.yaml up -d

# Или установить расширение вручную (для стандартного PostgreSQL)
docker exec -it <postgres-container> psql -U hgsdlc -d hgsdlc -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### Frontend Build Errors

**Ошибка: Vite build failed**
```bash
# Проверить версию Node.js
node --version  # Должна быть 18+

# Очистить кэш Vite
cd frontend
rm -rf dist .vite
npm run build
```

---

## Production Build

### Backend
```bash
cd backend
./gradlew clean build -x test  # Build without tests
java -jar build/libs/human-guided-sdlc-backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=postgres \
  --DB_URL=jdbc:postgresql://prod-host:5432/hgsdlc \
  --DB_USERNAME=hgsdlc \
  --DB_PASSWORD=secure-password
```

### Frontend
```bash
cd frontend
npm run build
# Artifact: dist/ directory (может быть развёрнут на nginx или другом static server)
```

---

## Quick Reference

| Задача | Команда |
|--------|---------|
| Start PostgreSQL | `docker compose -f deploy/compose.dev.yaml up -d` |
| Build backend | `cd backend && ./gradlew build` |
| Run backend (postgres) | `cd backend && SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun` |
| Run backend (H2) | `cd backend && ./gradlew bootRun` |
| Install frontend | `cd frontend && npm install` |
| Build frontend | `cd frontend && npm run build` |
| Run frontend dev | `cd frontend && npm run dev` |
| Health check | `curl http://localhost:8080/actuator/health` |
| Stop PostgreSQL | `docker compose -f deploy/compose.dev.yaml down` |
| Clean backend | `cd backend && ./gradlew clean` |
| Clean frontend | `cd frontend && rm -rf dist .vite node_modules` |
