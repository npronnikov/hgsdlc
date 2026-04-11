# Human Guided SDLC — CLAUDE.md

## Проект

Платформа для управляемого выполнения задач кодинг-агентом. Основные сущности:
- **Flow** — граф нод (ai, command, human_approval, human_input, terminal)
- **Rule / Skill** — элементы каталога с lifecycle_status и publication pipeline
- **Runtime** — выполнение flow с аудитом, гейтами, артефактами
- **Benchmark** — A/B сравнение запусков кодинг-агента

## Стек

- **Backend**: Spring Boot 3.3 / Java 21, JPA (Hibernate), Liquibase, PostgreSQL (профиль `postgres`) / H2 (дефолт)
- **Frontend**: React + Vite, JSX, без TypeScript
- **Build**: Gradle (backend), npm (frontend)
- **Auth**: токен-сессии, seed-пользователь `admin / admin`

## Запуск

```bash
# PostgreSQL (основной режим)
docker compose -f deploy/compose.dev.yaml up -d
cd backend && SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun

# Frontend
cd frontend && npm run dev
# → http://localhost:5173, API → http://localhost:8080
```

## Структура пакетов backend

```
ru.hgd.sdlc.
  auth/          — аутентификация, сессии, роли
  flow/          — парсинг YAML, доменная модель flow
  runtime/       — выполнение flow, ноды, гейты, артефакты, аудит
  rule/ skill/   — каталог правил и скиллов
  publication/   — pipeline публикации в git/БД
  benchmark/     — A/B бенчмарк запусков
  project/       — проекты
  common/        — утилиты (исключения, валидация, YAML-парсинг)
  platform/      — security config, auth filter
```

## Liquibase — важное правило

**Каждый новый SQL-файл миграции надо регистрировать в:**
`backend/src/main/resources/db/changelog/db.changelog-master.yaml`

Файлы нумеруются по порядку: `NNN-description.sql`. Текущий максимум — `042`.
Формат файла:
```sql
--liquibase formatted sql
--changeset hgd:NNN-description
...DDL...
```

Hibernate работает в режиме `validate` — если таблица есть в JPA-сущности но не в БД, приложение не стартует.

## Соглашения кода

- Контроллеры возвращают `record`-типы прямо внутри класса контроллера (не отдельные файлы)
- Репозитории — Spring Data JPA интерфейсы
- Исключения из `ru.hgd.sdlc.common.*` (NotFoundException, ValidationException, ConflictException, ForbiddenException)
- `@ExceptionHandler` локально в контроллере, не глобальный `@ControllerAdvice`
- Frontend: компоненты в `frontend/src/components/`, страницы в `frontend/src/pages/`

## Частые проблемы

- `Schema-validation: missing table [X]` → SQL-миграция не добавлена в `db.changelog-master.yaml`
- Приложение не стартует на профиле `postgres` без запущенного Docker-контейнера с PG
