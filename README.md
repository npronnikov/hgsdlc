# Human Guided SDLC

Human Guided SDLC — платформа для управляемого выполнения задач кодинг-агентом: через версии `flows`, `rules`, `skills`, публикационный pipeline и runtime с аудитом.

## Что в репозитории

- `backend/` — Spring Boot (Java 21), REST API, Liquibase, JPA.
- `frontend/` — React + Vite UI.
- `deploy/` — production compose/Dockerfiles/scripts.
- `docs/` — проектная документация.

## Быстрый старт (H2 in-memory)

1. Запустить backend:

```bash
cd /Users/nick/IdeaProjects/human-guided-development/backend
./gradlew bootRun
```

2. Запустить frontend (в отдельном терминале):

```bash
cd /Users/nick/IdeaProjects/human-guided-development/frontend
npm install
npm run dev
```

3. Открыть приложение:

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`
- Health check: `http://localhost:8080/actuator/health`

Примечание: при таком запуске используется in-memory H2, данные не сохраняются между перезапусками backend.

## Полная инструкция установки

См. подробный гайд: [docs/installation.md](docs/installation.md)

## Конфигурация

Backend читает параметры БД из переменных окружения:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Если не задавать их, backend стартует на in-memory H2 (подходит для локальных быстрых запусков, но не основной режим разработки).

## Дефолтный доступ

- Seed-пользователь (backend): `admin / admin`

## Полезные команды

Сборка backend:

```bash
cd /Users/nick/IdeaProjects/human-guided-development/backend
./gradlew build
```

Сборка frontend:

```bash
cd /Users/nick/IdeaProjects/human-guided-development/frontend
npm run build
```
