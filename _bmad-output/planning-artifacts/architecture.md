---
stepsCompleted: [step-01-init, step-02-context, step-03-starter, step-04-decisions]
inputDocuments:
  - _bmad-output/planning-artifacts/prd.md
  - _bmad-output/project-context.md
  - docs/architecture.md
  - docs/project-overview.md
  - docs/integration-architecture.md
  - docs/api-contracts-backend.md
  - docs/data-models-backend.md
  - docs/ui-components-frontend.md
  - docs/source-tree-analysis.md
  - docs/development-guide.md
workflowType: 'architecture'
project_name: 'hgsdlc'
user_name: 'HGS Team'
date: '2026-03-27'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Функциональные требования — 39 FR в 8 областях:**

| Область | FR | Архитектурная импликация |
|---|---|---|
| Управление воркфлоу (FR1–FR4) | Запуск, остановка, просмотр, повторный запуск | Расширение существующего `runtime` модуля |
| Движок выполнения (FR5–FR9, FR37) | Version snapshot, пошаговое подтверждение, restart шага | Ядро системы; частично реализовано, нужен version snapshot |
| Артефакты (FR10–FR13) | Просмотр, трассировка, экспорт, Git-сохранение | Git-интеграция в проект (не в catalog-repo); `ArtifactVersion` расширяется |
| Каталог (FR15–FR18, FR39) | Управление, версионирование, иммутабельность, поиск | 2-уровневый каталог (org / workspace); publication pipeline расширяется |
| Workspace + пользователи (FR19–FR23) | Создание workspace, RBAC 6 ролей, изоляция, аутентификация | **Полностью новое** — multi-tenancy отсутствует в текущей системе |
| Dashboard (FR24–FR27) | Статистика, детальный просмотр, экспорт | Модуль существует; нужны workspace-уровень и экспорт |
| Интеграции Git (FR28–FR29) | GitLab CE/EE, Bitbucket Server | Новый `GitProvider` интерфейс |
| Аудит (FR32–FR36) | Иммутабельный log, фильтрация, экспорт, read-only Auditor | `audit_events` существует; нужны фильтрация, экспорт, DB-level защита |

**Нефункциональные требования — архитектурные драйверы:**

- **NFR1/NFR3:** UI < 1с, API без LLM < 500мс → кэширование, оптимизация запросов
- **NFR2:** Асинхронное выполнение воркфлоу без прогресс-индикации → SSE для уведомлений
- **NFR5:** tenant_id обязателен для всех записей → multi-tenancy на уровне БД
- **NFR8:** RBAC на каждый API-запрос → middleware / AOP interceptor
- **NFR10:** До 1000 параллельных запусков → управление очередью задач
- **NFR11:** Горизонтальное масштабирование без изменения архитектуры → stateless runtime
- **NFR12:** 99% uptime → отказоустойчивость и мониторинг
- **NFR13:** Audit log иммутабелен → PostgreSQL GRANT + Liquibase constraints
- **NFR14:** Состояние сохраняется при сбое шага → checkpoint механизм (уже частично есть)

### Scale & Complexity

- **Первичный домен:** Async workflow engine + multi-tenant SaaS
- **Уровень сложности:** Enterprise (высокий)
- **Контекст:** Brownfield — существующая система с 32 Liquibase миграциями и работающими модулями
- **Расчётная нагрузка MVP:** 1–2 команды (пилот), реально 10–20 параллельных запусков; потолок 1000

### Technical Constraints & Dependencies

- **Стек зафиксирован:** Java 21 + Spring Boot 3.3 / React 18 + Ant Design 5 / PostgreSQL — не меняется
- **Liquibase append-only:** 32 существующих миграций неизменяемы; новые — только файлы 33+
- **H2-совместимость:** нативные SQL-запросы должны работать на H2 MODE=PostgreSQL и PostgreSQL (нельзя `ILIKE`, `::cast`)
- **Coding agent:** Qwen Code — первичный агент; интерфейс `CodingAgentStrategy` для расширения
- **Auth:** сессионные токены в `auth_sessions`, не JWT — расширяется, не заменяется
- **Frontend:** нет TypeScript, нет глобального стейт-менеджера; все API через `apiRequest()`

### Cross-Cutting Concerns Identified

1. **Multi-tenancy** — `workspace_id` / `tenant_id` затрагивает каждый модуль и каждый API-запрос; блокирует разработку всех новых модулей
2. **RBAC** — 3 текущие роли → 6 ролей PRD; проверка на каждый запрос без исключений
3. **Async execution scaling** — tick-based движок должен корректно работать при горизонтальном масштабировании
4. **Audit logging** — иммутабельность, 100% покрытие, фильтрация + экспорт
5. **Version pinning** — snapshot всех компонентов (воркфлоу, скиллы, rules, LLM-модель) на старте запуска
6. **Git integration** — абстракция двух self-hosted провайдеров (GitLab API v4, Bitbucket REST 2.0)
7. **Security** — TLS 1.2+, зашифрованные секреты, блокировка аккаунта после 5 попыток

### Key Architectural Decisions (Pre-decided via Party Mode)

| Решение | MVP | Growth |
|---|---|---|
| Очередь задач (1000 параллельных) | DB polling + `SELECT ... FOR UPDATE SKIP LOCKED` | Redis Streams / RabbitMQ |
| In-app уведомления (FR38) | SSE — Spring `SseEmitter`, без зависимостей | — |
| Git-абстракция (FR28–29) | `GitProvider` интерфейс, Strategy pattern (аналог `CodingAgentStrategy`) | +GitHub, GitLab.com |
| Multi-tenancy migration | Ликвибейз миграции 33+: `workspaces` таблица → `default_workspace` → `workspace_id` на все сущности → RBAC middleware | — |
| Роли — миграция | ADMIN→Platform Admin, OPERATOR→Team Lead+Developer, REVIEWER→Auditor; Architect+Analyst — новые enum-значения | LDAP/SAML |
| Audit иммутабельность | PostgreSQL GRANT (без DELETE/UPDATE на `audit_events`) + Liquibase trigger | — |

## Starter Template Evaluation

### Primary Technology Domain

Brownfield full-stack: Java/Spring Boot backend + React SPA frontend. Стек зафиксирован — стартовый шаблон не применяется.

### Existing Technical Foundation

**Backend:** Java 21 + Spring Boot 3.3, модульная DDD (api/application/domain/infrastructure), PostgreSQL 16, Liquibase append-only (миграции 1–32), Gradle Kotlin DSL

**Frontend:** React 18.2 JSX (без TypeScript), Vite 5.2, Ant Design 5.15, HashRouter, локальный state (useState/useEffect), все API через `apiRequest()`

**Ключевые ограничения стека:**

- Новые Liquibase-файлы — только нумерация 33+; существующие не редактировать
- SQL должен работать на H2 MODE=PostgreSQL и реальном PG (нельзя `ILIKE`, `::cast`)
- UUID генерируется в сервисном слое (`UUID.randomUUID()`), не `@GeneratedValue`
- Frontend без TypeScript; UI только из Ant Design; нет Redux/Zustand

**Новые модули для реализации:**

| Модуль | Тип | Назначение |
|---|---|---|
| `workspace` | Новый Spring модуль | Multi-tenancy, RBAC, управление пользователями |
| `notification` | Новый Spring модуль | SSE-уведомления (`SseEmitter`) |
| `git-provider` | Новый Spring модуль | Абстракция GitLab CE/EE + Bitbucket Server |

**Note:** Первая история реализации — добавление `workspace` модуля и Liquibase-миграция 33 (таблица `workspaces` + `workspace_id` на существующих сущностях).

## Core Architectural Decisions

### Decision Priority Analysis

**Critical (блокируют реализацию):**
- Multi-tenancy: shared schema + workspace_id (затрагивает все модули)
- RBAC: 6 ролей, middleware на каждый запрос

**Important (формируют архитектуру):**
- Version snapshot: JSONB blob в runs
- 2-уровневый каталог: единая таблица + scope enum
- Workspace-контекст: URL-based (`#/ws/:workspaceId/...`)
- Git-клиент: Spring RestClient

**Deferred (Post-MVP):**
- K8s: Helm charts, Ingress, namespace стратегия

### Data Architecture

- **Version snapshot (FR8):** `components_snapshot JSONB` в таблице `runs` — иммутабельный blob, содержит версии flow, skills, rules, LLM-модели на момент старта. Читается целиком, JOIN не нужен.
- **2-уровневый каталог (FR15–16):** таблица `workflow_catalog` с `scope ENUM('SYSTEM','WORKSPACE')` + `workspace_id UUID NULLABLE`. SYSTEM-воркфлоу доступны всем workspace'ам, WORKSPACE — только своему.
- **Multi-tenancy:** `workspace_id` обязателен на всех бизнес-сущностях. Liquibase-миграции 33+: таблица `workspaces` → `default_workspace` → ALTER TABLE для существующих сущностей.
- **Audit иммутабельность:** PostgreSQL GRANT без DELETE/UPDATE на `audit_events`; Liquibase-триггер, блокирующий UPDATE.

### Authentication & Security

- **Auth:** сессионные токены в `auth_sessions` (существующий механизм, не меняется). Расширение: `account_locked`, `failed_attempts` поля для блокировки после 5 неудачных попыток (NFR6).
- **RBAC:** 6 ролей вместо 3. Миграция: ADMIN→Platform Admin, OPERATOR→Team Lead+Developer, REVIEWER→Auditor; Architect и Analyst — новые enum-значения. Проверка на каждый API-запрос через Spring Security (без исключений, NFR8).
- **Secrets:** через переменные окружения K8s Secrets → `${ENV_VAR}` в `application.yml` (уже так).

### API & Communication Patterns

- **REST API:** существующий паттерн `/api/{resource}` сохраняется. Новые эндпоинты: `/api/workspaces`, `/api/notifications` (SSE), `/api/git-providers`.
- **SSE уведомления (FR38):** Spring `SseEmitter` в `notification` модуле. Backend эмитирует событие при смене статуса шага воркфлоу; frontend подписывается на `/api/notifications/stream`.
- **Git-провайдеры (FR28–29):** `GitProvider` интерфейс + `GitLabProvider` / `BitbucketServerProvider` реализации через Spring `RestClient`. Конфигурация хранится в модуле `settings`.

### Frontend Architecture

- **Workspace-контекст:** URL-based — `#/ws/:workspaceId/` prefix для всех workspace-aware маршрутов. Platform Admin маршруты: `#/admin/...` (вне workspace-контекста).
- **Состояние:** локальный `useState`/`useEffect` — без изменений.
- **Новые страницы:** WorkspaceSelector, WorkspaceAdmin, NotificationBell (Ant Design Badge).

### Infrastructure & Deployment

- **Dev/test:** одна инстанция, Docker Compose (существующий `infra/docker/compose.yml`).
- **Production:** Kubernetes. Stateless backend (сессии в БД). Spring Actuator `/actuator/health` → K8s readiness/liveness probe. K8s Secrets → переменные окружения (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).
- **Kubernetes детали** (Helm, Ingress, namespace): отложены до стадии инфраструктуры.

### Decision Impact Analysis

**Порядок реализации:**
1. Workspace-модуль + Liquibase 33+ (multi-tenancy — всё блокирует)
2. RBAC-миграция (6 ролей + middleware)
3. Runtime: version snapshot при старте run
4. Workflow catalog: scope + workspace_id
5. Notification модуль (SSE)
6. Git-provider модуль (GitLab + Bitbucket)

**Cross-component зависимости:**
- Workspace-модуль блокирует все остальные модули
- RBAC middleware зависит от workspace-модуля
- Git-provider использует settings-модуль для хранения credentials
- SSE-уведомления зависят от runtime (события смены статуса)
