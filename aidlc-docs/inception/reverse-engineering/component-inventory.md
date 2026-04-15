# Component Inventory

## Application Packages

### Backend (Java)

| Пакет | Назначение |
|-------|------------|
| `ru.hgd.sdlc.auth` | Аутентификация, сессии, управление пользователями |
| `ru.hgd.sdlc.flow` | Каталог и версионирование YAML flows |
| `ru.hgd.sdlc.rule` | Каталог и версионирование правил (rules) |
| `ru.hgd.sdlc.skill` | Каталог и версионирование навыков (skills) |
| `ru.hgd.sdlc.runtime` | Исполнение flows, gates, артефакты, аудит |
| `ru.hgd.sdlc.project` | Управление проектами (репозиториями) |
| `ru.hgd.sdlc.publication` | Pipeline публикации flows/rules/skills |
| `ru.hgd.sdlc.benchmark` | A/A и A/B бенчмаркинг |
| `ru.hgd.sdlc.settings` | Системные настройки (runtime, catalog) |
| `ru.hgd.sdlc.dashboard` | Агрегированные метрики для dashboard |
| `ru.hgd.sdlc.idempotency` | Идемпотентность API |
| `ru.hgd.sdlc.common` | Общие утилиты и исключения |
| `ru.hgd.sdlc.platform` | Spring Security конфигурация |

### Frontend (React)

| Пакет | Назначение |
|-------|------------|
| `pages` | Страницы приложения (28 страниц) |
| `components` | Переиспользуемые компоненты UI |
| `components/flow` | Flow компоненты (редактор, ноды, панели) |
| `api` | API клиенты для backend |
| `auth` | AuthContext для аутентификации |
| `hooks` | Кастомные React hooks |
| `utils` | Утилиты (валидация, сериализация, etc.) |
| `data` | Mock данные |
| `theme` | Управление темой (light/dark) |

## Infrastructure Packages

### Backend Infrastructure

| Пакет | Тип | Назначение |
|-------|-----|------------|
| `*.infrastructure` | Spring Data JPA | Репозитории (21 repository) |
| `ru.hgd.sdlc.platform` | Spring Security | Security конфигурация |

### Database Migrations

| Пакет | Тип | Назначение |
|-------|-----|------------|
| `db/changelog` | Liquibase | SQL миграции (47 файлов) |

## Shared Packages

### Backend Shared

| Пакет | Тип | Назначение |
|-------|-----|------------|
| `ru.hgd.sdlc.common` | Утилиты | Исключения, утилиты для JSON/YAML |

### Frontend Shared

| Пакет | Тип | Назначение |
|-------|-----|------------|
| `utils` | Утилиты | Общие функции для валидации, сериализации |
| `theme` | Theme | Контекст темы для всего приложения |
| `api/request.js` | API | Базовый API клиент |

## Test Packages

### Backend Tests

| Пакет | Тип | Назначение |
|-------|-----|------------|
| `test` | JUnit 5 | Юнит и интеграционные тесты |
| `test` | ArchUnit | Архитектурные тесты |
| `test` | Testcontainers | Интеграционные тесты с Docker |

### Frontend Tests

| Пакет | Тип | Назначение |
|-------|-----|------------|
| N/A | N/A | Тесты не реализованы |

## Total Count

### Backend
- **Total Packages:** 13 модулей
- **Application Modules:** 10 (auth, flow, rule, skill, runtime, project, publication, benchmark, settings, dashboard)
- **Cross-cutting Modules:** 3 (idempotency, common, platform)
- **Controllers:** 14 REST контроллеров
- **Services:** 32 сервиса
- **Entities:** 21 JPA сущность
- **Repositories:** 21 Spring Data JPA репозиториев
- **Migrations:** 47 Liquibase миграций

### Frontend
- **Total Pages:** 28 страниц
- **Components:** 11 основных компонентов + 5 flow компонентов
- **API Clients:** 1 базовый + специализированные
- **Hooks:** 1 главный (useFlowEditor)
- **Utils:** 7 утилит

### Overall Project
- **Total Application Packages:** 23 (13 backend modules + 10 frontend categories)
- **Infrastructure Packages:** 2 (backend infrastructure + migrations)
- **Shared Packages:** 5 (2 backend + 3 frontend)
- **Test Packages:** 2 (backend + frontend)
- **Total Lines of Code:** ~25,000 (оценочно)
  - Backend Java: ~15,000 LOC
  - Frontend JSX/JS: ~10,000 LOC
  - SQL migrations: ~2,000 LOC
