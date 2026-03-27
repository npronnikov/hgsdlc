# Руководство по разработке

---

## Требования

| Инструмент | Версия | Использование |
|-----------|--------|--------------|
| Java JDK | 21 | Backend |
| Gradle | встроен (Gradle Wrapper) | Сборка backend |
| Node.js | 18+ (LTS) | Frontend |
| npm | 9+ | Пакеты frontend |
| Docker + Docker Compose | любая актуальная | PostgreSQL (prod-режим) |
| Qwen Code (`qwen`) | последняя | Coding-агент для runtime |

---

## Запуск в dev-режиме

### Вариант 1: без PostgreSQL (H2 in-memory)

**Backend:**
```bash
cd backend
./gradlew bootRun
# API доступен на http://localhost:8080
# H2 Console: http://localhost:8080/h2-console (JDBC URL: jdbc:h2:mem:hgsdlc)
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
# UI доступен на http://localhost:5173
```

### Вариант 2: с PostgreSQL

```bash
# Запустить БД
cd infra/docker
docker compose up -d

# Backend с PostgreSQL
cd backend
export DB_URL=jdbc:postgresql://localhost:5432/sdlc
export DB_USERNAME=sdlc
export DB_PASSWORD=sdlc
./gradlew bootRun

# Frontend
cd frontend
npm install
npm run dev
```

### Начальный пользователь
- **Логин:** `admin`
- **Пароль:** `admin`
- **Роль:** ADMIN

Настраивается в `backend/src/main/resources/application.yml`:
```yaml
auth:
  seed-username: admin
  seed-password: admin
  seed-role: ADMIN
```

---

## Сборка

### Backend
```bash
cd backend
./gradlew build
# JAR: build/libs/hgsdlc-0.0.1-SNAPSHOT.jar
```

### Frontend
```bash
cd frontend
npm run build
# Статика: dist/
```

---

## Тесты

### Backend
```bash
cd backend
./gradlew test
# Интеграционные тесты используют Testcontainers (нужен Docker)
# Тесты поднимают настоящий PostgreSQL в контейнере
```

### Frontend
```bash
cd frontend
npm test
# Запускает vite build (проверка компиляции)
```

---

## Переменные окружения

| Переменная | Дефолт | Описание |
|-----------|--------|---------|
| `DB_URL` | `jdbc:h2:mem:hgsdlc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL` | JDBC URL базы данных |
| `DB_USERNAME` | `sa` | Пользователь БД |
| `DB_PASSWORD` | *(пусто)* | Пароль БД |

Переменные, управляемые через UI Settings (`/api/settings`):
- `workspace_root` — корневая директория workspace агента
- `coding_agent` — имя агента (`qwen`)
- `ai_timeout_seconds` — таймаут выполнения AI-ноды
- `catalog_repo_url` — URL git-репозитория каталога
- `git_ssh_private_key` — SSH-ключ для публикации

---

## Миграции базы данных

Управляются Liquibase. Запускаются автоматически при старте backend.

```
backend/src/main/resources/db/changelog/
├── db.changelog-master.yaml   # Мастер-файл (включает все SQL)
├── 001-auth-tables.sql
├── 003-rules.sql
...
└── 032-rules-flows-publication-pipeline.sql
```

**Добавить новую миграцию:**
1. Создать `0XX-description.sql` в `db/changelog/`
2. Добавить в `db.changelog-master.yaml`
3. Перезапустить backend — Liquibase применит автоматически

**Важно:** `ddl-auto: validate` — Hibernate НЕ модифицирует схему. Все изменения только через Liquibase.

---

## Добавление нового coding-агента

1. Создать класс, реализующий `CodingAgentStrategy`:
```java
@Component
class MyCodingAgentStrategy implements CodingAgentStrategy {
    @Override
    public String codingAgent() { return "my-agent"; }

    @Override
    public AgentInvocationContext materializeWorkspace(MaterializationRequest request)
            throws CodingAgentException {
        // 1. Записать rules в workspace
        // 2. Записать skills в workspace
        // 3. Построить промпт
        // 4. Запустить subprocess агента
        // return контекст с путями к логам
    }
}
```

2. Добавить шаблоны rule и skill в:
   - `resources/rule-templates/my-agent.md`
   - `resources/skill-templates/my-agent.md`

3. Выбрать агент через `/api/settings/runtime`: `{"coding_agent": "my-agent"}`

---

## Работа с каталогом

**Синхронизировать catalog-repo → БД:**
```bash
curl -X PUT http://localhost:8080/api/settings/catalog/repair \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"mode": "full"}'
```

**Настроить catalog-repo:**
```bash
curl -X PUT http://localhost:8080/api/settings/catalog \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "catalog_repo_url": "git@github.com:org/catalog.git",
    "catalog_default_branch": "main",
    "publish_mode": "pr",
    "git_ssh_private_key": "-----BEGIN OPENSSH PRIVATE KEY-----\n..."
  }'
```

---

## Профили Spring

| Профиль | Активация | Особенности |
|---------|-----------|-------------|
| `local` | `./gradlew bootRun` (по умолчанию) | H2 console включён |
| *(default)* | Через env vars | PostgreSQL |

Профиль `local` задаётся в `build.gradle.kts`:
```kotlin
tasks.withType<BootRun> {
    systemProperty("spring.profiles.active", "local")
}
```
