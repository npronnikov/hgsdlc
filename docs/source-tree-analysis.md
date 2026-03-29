# Анализ структуры исходного кода

> Тип репозитория: **multi-part** (4 части в одном репозитории)
> Дата: 2026-03-26

---

## Корень проекта

```
hgsdlc/                             # Корень репозитория
├── backend/                        # [ЧАСТЬ 1] Java/Spring Boot REST API + Runtime-движок
├── frontend/                       # [ЧАСТЬ 2] React SPA — веб-интерфейс
├── infra/                          # [ЧАСТЬ 3] Инфраструктура (Docker)
├── catalog-repo/                   # [ЧАСТЬ 4] YAML-каталог артефактов (flows/skills)
├── docs/                           # Документация проекта (project_knowledge)
├── _bmad-output/                   # Артефакты BMad (планирование)
├── README.md                       # Обзор проекта
└── .claude/                        # Claude Code конфигурация
```

---

## Backend (`backend/`)

```
backend/
├── build.gradle.kts                # [ТОЧКА СБОРКИ] Gradle Kotlin DSL — зависимости, плагины
└── src/
    ├── main/
    │   ├── java/ru/hgd/sdlc/       # Корневой пакет приложения
    │   │   ├── api/
    │   │   │   └── HealthController.java   # GET /health
    │   │   ├── auth/               # [МОДУЛЬ] Аутентификация
    │   │   │   ├── api/            # AuthController, DTO (LoginRequest, AuthResponse)
    │   │   │   ├── application/    # AuthService — логин/логаут, токены сессий
    │   │   │   ├── domain/         # User, AuthSession, Role enum
    │   │   │   └── infrastructure/ # UserRepository, AuthSessionRepository
    │   │   ├── flow/               # [МОДУЛЬ] Управление flows
    │   │   │   ├── api/            # FlowController — CRUD + catalog query
    │   │   │   ├── application/    # FlowService, FlowYamlParser
    │   │   │   ├── domain/         # FlowVersion (JPA), FlowModel, NodeModel, enums
    │   │   │   └── infrastructure/ # FlowVersionRepository
    │   │   ├── rule/               # [МОДУЛЬ] Управление rules
    │   │   │   ├── api/            # RuleController, RuleTemplateController
    │   │   │   ├── application/    # RuleService
    │   │   │   ├── domain/         # RuleVersion, enums
    │   │   │   └── infrastructure/ # RuleVersionRepository
    │   │   ├── skill/              # [МОДУЛЬ] Управление skills
    │   │   │   ├── api/            # SkillController, SkillTemplateController
    │   │   │   ├── application/    # SkillService
    │   │   │   ├── domain/         # SkillVersion, TagEntity, enums
    │   │   │   └── infrastructure/ # SkillVersionRepository
    │   │   ├── runtime/            # [МОДУЛЬ] ★ Движок исполнения runs
    │   │   │   ├── api/            # RuntimeController — runs, gates, artifacts, audit
    │   │   │   ├── application/    # ★ RuntimeService (основной движок)
    │   │   │   │                   # CodingAgentStrategy (интерфейс)
    │   │   │   │                   # QwenCodingAgentStrategy (реализация для Qwen)
    │   │   │   │                   # AgentPromptBuilder, AgentInvocationContext
    │   │   │   │                   # CatalogContentResolver, ExecutionTraceBuilder
    │   │   │   │                   # RuntimeStepTxService, RuntimeRecoveryInitializer
    │   │   │   ├── domain/         # RunEntity, NodeExecutionEntity, GateInstanceEntity
    │   │   │   │                   # ArtifactVersionEntity, AuditEventEntity
    │   │   │   │                   # RunStatus, NodeExecutionStatus, GateKind, GateStatus
    │   │   │   │                   # ArtifactKind, ArtifactScope, ActorType
    │   │   │   └── infrastructure/ # Репозитории runs, nodes, gates, artifacts, audit
    │   │   ├── publication/        # [МОДУЛЬ] Pipeline публикации в catalog-repo
    │   │   │   ├── api/            # PublicationController — approve/reject/retry
    │   │   │   ├── application/    # PublicationService — git push, PR creation
    │   │   │   ├── domain/         # PublicationRequest, PublicationJob, enums
    │   │   │   └── infrastructure/ # Репозитории publication
    │   │   ├── project/            # [МОДУЛЬ] Проекты
    │   │   │   ├── api/            # ProjectController — CRUD
    │   │   │   ├── application/    # ProjectService
    │   │   │   ├── domain/         # Project, ProjectStatus
    │   │   │   └── infrastructure/ # ProjectRepository
    │   │   ├── settings/           # [МОДУЛЬ] Системные настройки
    │   │   │   ├── api/            # SettingsController — runtime, catalog, repair
    │   │   │   ├── application/    # SettingsService
    │   │   │   ├── domain/         # SystemSetting
    │   │   │   └── infrastructure/ # SystemSettingRepository
    │   │   ├── idempotency/        # [МОДУЛЬ] Идемпотентность API
    │   │   │   ├── application/    # IdempotencyService
    │   │   │   ├── domain/         # IdempotencyRecord
    │   │   │   └── infrastructure/ # IdempotencyRecordRepository
    │   │   ├── dashboard/          # [МОДУЛЬ] Метрики
    │   │   │   └── api/            # OverviewController
    │   │   ├── platform/           # Spring Security, TaskExecutor, платформенные бины
    │   │   └── common/             # Исключения (Not Found, Validation, Conflict…), утилиты
    │   └── resources/
    │       ├── application.yml             # [КОНФИГ] Основной конфиг (порт, DB, auth seed)
    │       ├── db/changelog/               # [МИГРАЦИИ] 32 Liquibase SQL-файла
    │       │   └── db.changelog-master.yaml
    │       ├── schemas/                    # [СХЕМЫ] JSON Schema валидация YAML артефактов
    │       │   ├── flow.schema.json
    │       │   ├── node-ai.schema.json
    │       │   ├── node-human-approval-gate.schema.json
    │       │   ├── node-human-input-gate.schema.json
    │       │   ├── node-command.schema.json
    │       │   ├── node-terminal.schema.json
    │       │   ├── rule.schema.json
    │       │   └── skill.schema.json
    │       ├── rule-templates/             # Шаблоны rules по агентам
    │       │   ├── claude.md
    │       │   ├── cursor.md
    │       │   └── qwen.md
    │       ├── skill-templates/            # Шаблоны skills по агентам
    │       │   ├── claude.md
    │       │   ├── cursor.md
    │       │   └── qwen.md
    │       └── runtime/
    │           ├── prompt-template.md      # Шаблон промпта для AI-ноды
    │           └── prompt-texts.ru.yaml    # Локализованные тексты промптов (ru)
    └── test/
        └── java/ru/hgd/sdlc/             # Тесты (Testcontainers + PostgreSQL)
```

---

## Frontend (`frontend/`)

```
frontend/
├── package.json                    # [ТОЧКА СБОРКИ] npm-зависимости, Vite scripts
├── vite.config.js                  # [КОНФИГ] Vite: dev-server, proxy /api → :8080
└── src/
    ├── main.jsx                    # [ТОЧКА ВХОДА] React root, монтирование App
    ├── App.jsx                     # [МАРШРУТИЗАЦИЯ] HashRouter + все Route + RequireAuth
    ├── api/
    │   └── request.js              # HTTP-клиент (fetch + Bearer-токен + Idempotency-Key)
    ├── auth/
    │   └── AuthContext.jsx         # [ГЛОБАЛЬНОЕ СОСТОЯНИЕ] пользователь + токен
    ├── components/
    │   ├── AppShell.jsx            # Layout: боковое меню + Outlet
    │   ├── ActionCenter.jsx        # Панель действий gate (approve/rework)
    │   ├── ArtifactViewer.jsx      # Просмотр артефактов (Markdown/текст/бинарные)
    │   └── StatusTag.jsx           # Цветовые теги статусов
    ├── pages/                      # [СТРАНИЦЫ] 25 страниц SPA
    │   ├── Overview.jsx            # Дашборд
    │   ├── Projects.jsx            # Список проектов
    │   ├── Flows.jsx               # Каталог flows
    │   ├── FlowEditor.jsx          # ★ Monaco YAML-редактор + ReactFlow граф
    │   ├── Rules.jsx / RuleEditor.jsx
    │   ├── Skills.jsx / SkillEditor.jsx
    │   ├── Requests.jsx            # Очередь публикаций
    │   ├── RunLaunch.jsx           # Форма запуска flow
    │   ├── RunConsole.jsx          # ★ Консоль оператора (ноды/лог/артефакты/gate)
    │   ├── GatesInbox.jsx          # Входящие gates
    │   ├── GateInput.jsx           # Форма human_input
    │   ├── GateApproval.jsx        # Форма human_approval
    │   ├── AuditRuntime.jsx        # Аудит-лог runtime
    │   ├── AuditAgent.jsx          # Лог агента (stdout/stderr)
    │   ├── AuditReview.jsx         # Аудит ревью
    │   ├── PromptPackage.jsx       # Просмотр промпта агента
    │   ├── Artifacts.jsx           # Просмотр артефактов
    │   ├── DeltaSummary.jsx        # Сводка изменений
    │   ├── Versions.jsx            # История версий
    │   ├── Settings.jsx            # Настройки системы
    │   └── Login.jsx               # Форма входа
    ├── theme/
    │   └── ThemeContext.jsx        # [ГЛОБАЛЬНОЕ СОСТОЯНИЕ] светлая/тёмная тема
    ├── utils/
    │   ├── errorMessages.js        # Тексты ошибок API
    │   ├── frontmatter.js          # Парсинг YAML frontmatter
    │   └── monacoTheme.js          # Кастомная тема Monaco
    └── data/
        └── mock.js                 # Мок-данные для dev без backend
```

---

## Infrastructure (`infra/`)

```
infra/
└── docker/
    └── compose.yml                 # [ТОЧКА ВХОДА] PostgreSQL 16 + volume + healthcheck
```

---

## Catalog Repo (`catalog-repo/`)

```
catalog-repo/
└── flows/                          # Опубликованные flows
    └── restore-architecture-flow/
        └── 1.0/
            ├── FLOW.yaml           # Полный YAML flow (пример: 2 ноды ai + human_approval)
            └── metadata.yaml       # Мета: canonical_name, status, rule_refs
```

---

## Интеграционные точки между частями

| От | К | Механизм | Детали |
|----|---|----------|--------|
| frontend | backend | REST HTTP | Все вызовы через `src/api/request.js` → `http://localhost:8080/api/*` |
| backend (runtime) | coding agent | subprocess | `QwenCodingAgentStrategy` запускает `qwen` CLI в рабочем каталоге |
| backend (publication) | catalog-repo | git (SSH/HTTPS) | PublicationService клонирует, пушит, создаёт PR |
| backend | PostgreSQL | JDBC (JPA) | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` env vars |
