# Интеграционная архитектура

> Тип: multi-part, 4 части
> Дата: 2026-03-26

---

## Карта интеграций

```
┌─────────────────────────────────────────────────────────────────┐
│                         hgsdlc                                  │
│                                                                 │
│   ┌──────────────┐   REST/HTTP    ┌──────────────────────────┐  │
│   │   Frontend   │ ◄────────────► │        Backend           │  │
│   │ React + Vite │  /api/*        │  Spring Boot :8080       │  │
│   │   :5173      │                │                          │  │
│   └──────────────┘                │  ┌──────────────────┐    │  │
│                                   │  │  RuntimeService  │    │  │
│                                   │  │                  │    │  │
│                                   │  │ QwenStrategy     │    │  │
│                                   │  └────────┬─────────┘    │  │
│                                   └───────────┼──────────────┘  │
│                                               │                  │
│              ┌────────────────────────────────┼──────┐          │
│              │                subprocess      │      │          │
│              ▼                                ▼      │          │
│   ┌──────────────────┐          ┌─────────────────┐  │          │
│   │  coding-agent    │          │   PostgreSQL     │  │          │
│   │   (Qwen Code)    │          │   :5432          │  │          │
│   │  workspace FS    │          │                  │  │          │
│   └──────────────────┘          └─────────────────┘  │          │
│                                                       │          │
│   ┌──────────────────────────────────────────────┐    │          │
│   │   catalog-repo (git)                         │ ◄──┘          │
│   │   PublicationService → clone/push/PR         │               │
│   └──────────────────────────────────────────────┘               │
└─────────────────────────────────────────────────────────────────┘
```

---

## Точки интеграции

### 1. Frontend ↔ Backend

| Свойство | Значение |
|----------|---------|
| Протокол | HTTP REST (JSON) |
| Dev URL | `http://localhost:8080/api/*` |
| Proxy | Vite проксирует `/api` → `localhost:8080` |
| Аутентификация | `Authorization: Bearer <token>` |
| Идемпотентность | `Idempotency-Key: <uuid>` для мутирующих операций |
| Формат | JSON, snake_case полей (`@JsonProperty`) |

**Используемые эндпоинты:** все `/api/*` — подробнее в [api-contracts-backend.md](./api-contracts-backend.md)

---

### 2. Backend (Runtime) ↔ Coding Agent

| Свойство | Значение |
|----------|---------|
| Протокол | OS subprocess (Process API) |
| Текущий агент | Qwen Code (`qwen` CLI) |
| Интерфейс расширения | `CodingAgentStrategy` |
| Workspace | Файловая система: `{workspace_root}/{project_id}/{run_id}/{node_execution_id}/` |
| Конфиг агента | `.qwen/QWEN.md` (rules), `.qwen/skills/*.md` (skills) |
| Промпт | `prompt.md` в директории выполнения |
| Логи | `agent.stdout.log`, `agent.stderr.log` |
| Timeout | Настраивается через `ai_timeout_seconds` |

**Материализация workspace перед запуском:**
1. Resolve `rule_refs` → записать в `.qwen/QWEN.md`
2. Resolve `skill_refs` ноды → записать в `.qwen/skills/`
3. Построить промпт (`AgentPromptBuilder`) из шаблона + `feature_request` + `execution_context`
4. Запустить `qwen <prompt_path>` subprocess
5. Собрать `produced_artifacts` из workspace → записать в `artifact_versions`

**Добавление нового агента:** реализовать `CodingAgentStrategy` и пометить `@Component`.

---

### 3. Backend (Publication) ↔ Catalog Repo (Git)

| Свойство | Значение |
|----------|---------|
| Протокол | Git (SSH или HTTPS) |
| Аутентификация | SSH ключ (Ed25519, Bouncy Castle) или username/PAT |
| Настройки | `PUT /api/settings/catalog` → хранятся в `system_settings` |
| Операция | clone → write file → commit → push → create PR |

**Поток публикации:**
```
approve в PublicationController
    → PublicationJob.status = running
    → clone catalog-repo
    → записать YAML/Markdown в catalog-repo/{type}/{id}/{version}/
    → git commit -m "Publish {canonical_name}"
    → git push origin {branch}
    → create PR через git hosting API
    → PublicationJob.status = completed + pr_url
```

**Синхронизация каталога в БД:** `PUT /api/settings/catalog/repair` — сканирует catalog-repo и импортирует артефакты в БД.

---

### 4. Backend ↔ PostgreSQL

| Свойство | Значение |
|----------|---------|
| Протокол | JDBC (PostgreSQL wire protocol) |
| Конфиг | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` env vars |
| ORM | Hibernate 6 (Spring Data JPA) |
| Схема | Управляется Liquibase, `ddl-auto=validate` |
| Dev | H2 in-memory `MODE=PostgreSQL` (без docker) |
| Connection pooling | HikariCP (default Spring Boot) |

---

## Данные, передаваемые между частями

### Frontend → Backend
- YAML-контент flows/rules/skills (text, до ~100KB)
- Команды управления runs (create, resume, cancel)
- Gate-действия (approve/rework с артефактами в base64)
- Настройки системы

### Backend → Frontend
- JSON-ответы всех API
- Содержимое артефактов (base64 → text)
- Streaming-логи агента (offset polling)
- Аудит-события

### Backend → Coding Agent
- Markdown rules (QWEN.md)
- Markdown skills (skills/*.md)
- Markdown промпт (prompt.md)
- Файлы execution_context из workspace

### Coding Agent → Backend (через FS)
- `produced_artifacts` — файлы в workspace
- `expected_mutations` — изменённые файлы
- `agent.stdout.log` / `agent.stderr.log` — выход агента

### Backend → Catalog Repo
- YAML-файл flow/rule/skill
- Метаданные в git-коммите
