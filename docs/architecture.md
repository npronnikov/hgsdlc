# Архитектура системы — Human Guided SDLC

## Назначение

Human Guided SDLC (hgsdlc) — управляемая платформа исполнения AI coding-агентов для корпоративной среды. Предоставляет:
- Декларативные **flows** (графы задач) для управления порядком действий агента
- **Rules** — правила кодирования, инжектируемые в контекст агента
- **Skills** — пакеты инструкций, доступные нодам flow
- **Runtime** — движок исполнения: запускает агента, управляет gate-точками (человеческий контроль), собирает артефакты и аудит
- **Publication pipeline** — процесс ревью и публикации артефактов в git-каталог

---

## Структура проекта

```
hgsdlc/
├── backend/          # Java 21 + Spring Boot 3.3 — REST API + runtime-движок
├── frontend/         # React 18 + Vite — SPA-интерфейс
├── infra/
│   └── docker/compose.yml   # PostgreSQL для dev/prod
└── catalog-repo/     # YAML-артефакты flows/skills для публикации
```

---

## Backend — модульная DDD-архитектура

Пакет: `ru.hgd.sdlc`

Каждый модуль изолирован по слоям:
```
<module>/
├── api/           # REST-контроллеры, DTO (request/response records)
├── application/   # Бизнес-логика (сервисы, стратегии)
├── domain/        # JPA-сущности, enum-ы
└── infrastructure/# Spring Data репозитории
```

### Модули

| Модуль | Ответственность |
|--------|----------------|
| `auth` | Аутентификация (сессионные Bearer-токены), роли: ADMIN, OPERATOR, REVIEWER |
| `flow` | CRUD и версионирование flows. YAML хранится как TEXT, парсится `FlowYamlParser` |
| `rule` | CRUD и версионирование rules (Markdown-контент) |
| `skill` | CRUD и версионирование skills (Markdown-контент) с тегами |
| `runtime` | **Движок исполнения** — жизненный цикл run, выполнение нод, управление gates |
| `publication` | Pipeline публикации: approval workflow + git push в catalog-repo |
| `project` | Проекты — контейнеры для запусков, привязка к git-репозиторию |
| `settings` | Системные настройки: workspace, coding_agent, git credentials, catalog |
| `idempotency` | Идемпотентность мутирующих API-вызовов по ключу |
| `dashboard` | Агрегированные метрики |
| `common` | Исключения, конвертеры, утилиты |
| `platform` | Spring Security конфигурация, TaskExecutor, платформенные бины |

---

## Runtime — движок исполнения

### Жизненный цикл Run

```
[POST /api/runs] → RunEntity (status=pending)
      ↓  (async, after commit)
[startRun()] → status=running, выбирает start_node
      ↓
[tick(runId)] — основной цикл:
  1. Определить текущую ноду
  2. Выполнить ноду по типу
  3. Обновить current_node_id → следующая нода
  4. Повторять до terminal или gate
      ↓
[terminal] → status=completed/failed
[gate] → status=paused, ожидание действия оператора
      ↓ (после approve/rework/submit-input)
[tick()] → продолжение выполнения
```

### Типы нод

| Тип | Описание |
|-----|----------|
| `ai` | Запуск coding-агента. Материализует workspace (rules + skills), создаёт промпт, запускает агент subprocess |
| `human_approval` | Создаёт gate_instance типа `human_approval`. Run ждёт approve или rework |
| `human_input` | Создаёт gate_instance типа `human_input`. Run ждёт данные от оператора |
| `terminal` | Завершение flow (completed или failed) |
| `command` | Выполнение shell-команды |

### CodingAgentStrategy

Интерфейс `CodingAgentStrategy` — точка расширения для новых coding-агентов:
- `materializeWorkspace()` — подготавливает рабочую директорию: пишет rules в `.qwen/QWEN.md`, skills в `.qwen/skills/`, генерирует prompt
- Текущая реализация: `QwenCodingAgentStrategy` (Qwen Code)

**Материализация workspace для AI-ноды:**
1. Резолвит rule_refs из flow → читает `RuleVersion.content` → пишет в `QWEN.md`
2. Резолвит skill_refs из ноды → читает `SkillVersion.content` → пишет файлы в `skills/`
3. Строит промпт (`AgentPromptBuilder`) из шаблона + feature_request + execution_context
4. Запускает `qwen` subprocess, перенаправляет stdout/stderr в лог-файлы
5. После завершения собирает `produced_artifacts` в БД

### Checkpoint (git)

AI-нода может иметь флаг `checkpoint_before_run: true`. В этом случае runtime создаёт git-коммит в workspace перед запуском агента. Это позволяет откатиться при rework.

### Аудит-события

Каждое значимое действие записывается в `audit_events` с монотонным `sequence_no`:
- `run_started`, `run_completed`, `run_failed`
- `node_started`, `node_completed`, `node_failed`
- `rules_materialized`, `skills_materialized`
- `gate_opened`, `gate_approved`, `gate_rework_requested`, `gate_submitted`
- `artifact_recorded`

---

## Publication Pipeline

Процесс публикации flows/rules/skills в git catalog-repo:

```
Оператор нажимает "Save" с status=published
      ↓
PublicationRequest создаётся (status=pending_approval)
      ↓
ADMIN делает approve через /api/publications/{type}/{id}/versions/{v}/approve
      ↓ (при достижении required_approvals)
PublicationJob запускается:
  1. clone catalog-repo
  2. записать YAML/Markdown файл
  3. commit + push в новую ветку
  4. создать Pull Request
      ↓
PR мерджится вручную → артефакт доступен в каталоге
```

---

## Flow YAML — формат

```yaml
id: my-flow
version: "1.0"
canonical_name: my-flow@1.0
title: Моё описание
start_node_id: step1
rule_refs:
  - java-backend-rules@1.0
fail_on_missing_declared_output: true
fail_on_missing_expected_mutation: false

nodes:
  - id: step1
    title: AI шаг
    type: ai
    execution_context:
      - type: file
        path: docs/spec.md
        scope: project
        required: false
    instruction: |
      Реализуй функцию согласно спецификации.
    skill_refs:
      - write-unit-tests@1.0
    produced_artifacts:
      - path: src/main/java/Foo.java
        scope: project
        required: true
    expected_mutations: []
    checkpoint_before_run: true
    on_success: review
    on_failure: done

  - id: review
    title: Ревью кода
    type: human_approval
    execution_context: []
    produced_artifacts: []
    expected_mutations: []
    on_approve: done
    on_rework:
      keep_changes: true
      next_node: step1

  - id: done
    title: Завершение
    type: terminal
    execution_context: []
    produced_artifacts: []
    expected_mutations: []
```

---

## Аутентификация и авторизация

- **Механизм:** собственные сессионные токены (не JWT) хранятся в таблице `auth_sessions`
- **TTL:** 24 часа (настраивается `auth.session-ttl-seconds`)
- **Роли:**
  - `ADMIN` — полный доступ, утверждение публикаций
  - `OPERATOR` — создание/запуск flows, работа с gates
  - `REVIEWER` — только просмотр и утверждение gates
- **Начальный пользователь:** `admin/admin` создаётся при старте (настраивается в `application.yml`)

---

## Инфраструктура

| Компонент | Dev | Prod |
|-----------|-----|------|
| База данных | H2 in-memory (MODE=PostgreSQL) | PostgreSQL 16 |
| Docker Compose | `infra/docker/compose.yml` | — |
| Переменные окружения | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | |
| Backend порт | 8080 | 8080 |
| Frontend dev | Vite dev-сервер, proxy `/api` → 8080 | — |

---

## Catalog-repo

Git-репозиторий с YAML-артефактами, публикуемыми через publication pipeline.

Структура:
```
catalog-repo/
└── flows/
    └── restore-architecture-flow/
        └── 1.0/
            ├── FLOW.yaml      # Полный YAML flow
            └── metadata.yaml  # Метаданные
```

---

## Идемпотентность

Все мутирующие операции защищены идемпотентным ключом (`Idempotency-Key` заголовок):
- Клиент генерирует UUID-ключ при отправке запроса
- При повторном запросе с тем же ключом возвращается кэшированный ответ
- `idempotency_records` хранит `request_hash` для обнаружения конфликтов
- Операции: `flows.save`, `rules.save`, `skills.save`, `runs.create`

---

## Технологический стек

### Backend
| Компонент | Версия |
|-----------|--------|
| Java | 21 |
| Spring Boot | 3.3.0 |
| Spring Security | 6.x |
| Spring Data JPA + Hibernate | 6.x |
| Spring Shell | 3.2.0 (CLI) |
| PostgreSQL driver | — |
| H2 | — (dev/test) |
| Liquibase | — |
| Jackson | 2.17.1 |
| json-schema-validator (networknt) | 1.0.87 |
| Lombok | 1.18.42 |
| Bouncy Castle | 1.78.1 |
| Testcontainers | 1.19.8 |
| Build: Gradle Kotlin DSL | — |

### Frontend
| Компонент | Версия |
|-----------|--------|
| React | 18.2.0 |
| Vite | 5.2.0 |
| Ant Design | 5.15.0 |
| React Router DOM | 6.22.0 |
| ReactFlow | 11.11.3 |
| dagre | 0.8.5 |
| Monaco Editor | 4.6.0 |
| react-markdown + remark-gfm | 9.0.1 / 4.0.0 |
| yaml | 2.4.5 |
