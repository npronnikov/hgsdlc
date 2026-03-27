---
project: hgsdlc
generated: "2026-03-26"
scan_level: deep
repository_type: multi-part
parts_count: 4
---

# Индекс документации — Human Guided SDLC

## Обзор проекта

- **Тип:** multi-part, 4 части
- **Основной язык:** Java (backend), JavaScript/JSX (frontend)
- **Архитектура:** Modular DDD (backend) + SPA (frontend)

## Быстрый старт

```bash
# Dev без Docker (H2):
cd backend && ./gradlew bootRun     # http://localhost:8080
cd frontend && npm install && npm run dev  # http://localhost:5173
# Логин: admin / admin

# Dev с PostgreSQL:
cd infra/docker && docker compose up -d
export DB_URL=jdbc:postgresql://localhost:5432/sdlc DB_USERNAME=sdlc DB_PASSWORD=sdlc
cd backend && ./gradlew bootRun
```

---

## Части проекта

### Backend (`backend/`)
- **Тип:** Java 21 + Spring Boot 3.3
- **Архитектура:** Modular DDD (api → application → domain → infrastructure)
- **Точка входа:** `backend/src/main/java/ru/hgd/sdlc`

### Frontend (`frontend/`)
- **Тип:** React 18 + Vite 5 + Ant Design 5
- **Архитектура:** SPA, HashRouter, local state per page
- **Точка входа:** `frontend/src/main.jsx`

### Infrastructure (`infra/`)
- **Тип:** Docker Compose
- **Точка входа:** `infra/docker/compose.yml`

### Catalog Repo (`catalog-repo/`)
- **Тип:** YAML-артефакты (flows, skills, rules)
- **Назначение:** Git-based versioned artifact store

---

## Сгенерированная документация

### Общая
- [Обзор проекта](./project-overview.md) — назначение, стек, домен, ссылки
- [Архитектура системы](./architecture.md) — runtime-движок, flow-формат, жизненный цикл run, publication pipeline
- [Структура исходного кода](./source-tree-analysis.md) — аннотированное дерево всех частей
- [Интеграционная архитектура](./integration-architecture.md) — как части взаимодействуют
- [Руководство по разработке](./development-guide.md) — setup, команды, миграции, добавление агентов
- [Метаданные частей](./project-parts.json) — JSON-манифест частей и интеграций

### Backend
- [API Контракты](./api-contracts-backend.md) — 40+ REST эндпоинтов
- [Модели данных](./data-models-backend.md) — 15 таблиц БД с полями и индексами

### Frontend
- [UI-компоненты](./ui-components-frontend.md) — 25 страниц/компонентов, маршруты, state management

---

## Существующая документация

- [README.md](../README.md) — высокоуровневый обзор и инструкции запуска
