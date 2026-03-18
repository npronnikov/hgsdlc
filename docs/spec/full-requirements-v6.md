# HGSDLC Platform — требования, согласованные с текущей реализацией (v6)

## 1. Назначение и границы

HGSDLC Platform в текущей реализации — это **каталог и редактор** версионированных Flow, Rule и Skill с аутентификацией и базовой идемпотентностью сохранения. Платформа **не выполняет** runtime-исполнение flow, не вызывает агент/CLI и не обрабатывает артефакты. 

**Реализовано в v6:**

- CRUD (черновик/публикация) для Flow / Rule / Skill.
- Версионирование и canonical_name для Flow / Rule / Skill.
- Валидация Flow YAML (JSON Schema + дополнительные проверки DAG/переходов).
- Валидация frontmatter в Rule/Skill при публикации.
- Сервис шаблонов Rule/Skill по провайдеру.
- Idempotency-Key на сохранение сущностей.
- Аутентификация на базе токенов (login/logout/me).
- UI: редакторы и списки Flow/Rule/Skill (работают через API).
- UI: набор страниц-«заглушек» для запусков, гейтов, аудита и пр. (только mock-данные).

**Не реализовано в v6 (вне scope):**

- Runtime/Run-исполнение flow, state machine, human gates, external commands.
- Agent integration (Qwen CLI/Claude/Cursor), prompt package, response schema enforcement.
- Project registry, context builder, artifacts storage, audit trail, delta summary.
- Authorization по ролям (role-based access control) на уровне API.

## 2. Роли и аутентификация

### 2.1 Роли
В системе определены роли:

- `FLOW_CONFIGURATOR`
- `PRODUCT_OWNER`
- `TECH_APPROVER`

Роли возвращаются в `/api/auth/me`, но **не используются** для ограничения доступа к API.

### 2.2 Аутентификация

- `/api/auth/login` возвращает Bearer-токен.
- Все запросы к `/api/**` (кроме `/api/auth/login`) требуют `Authorization: Bearer <token>`.
- `/api/auth/logout` инвалидирует токен.
- `/api/auth/me` возвращает текущего пользователя.

### 2.3 Seed-пользователи
При старте приложения создаются пользователи (если отсутствуют):

- `admin` / `admin` (роль берётся из `auth.seed-role`, по умолчанию `FLOW_CONFIGURATOR`)
- `flow_configurator` / `admin`
- `product_owner` / `admin`
- `tech_approver` / `admin`

## 3. Доменные сущности и версии

### 3.1 Общие понятия

- Каждая сущность хранится как **версия** (`version`, `canonical_name`).
- `canonical_name = {id}@{version}` формируется сервером при сохранении.
- Статусы: `draft`, `published`.
- Версия — строка формата `major.minor` или `major.minor.patch`.
  - Валидация допускает `\d+\.\d+(\.\d+)?`.
  - Логика инкремента использует только **major/minor**.

### 3.2 Flow
Хранится как `FlowVersion`:

- `flow_id`, `version`, `canonical_name`, `status`
- `title`, `description`, `start_node_id`
- `rule_refs` (список)
- `flow_yaml` (строка как есть)
- `checksum` (только для published)
- `saved_by`, `saved_at`, `resource_version`

### 3.3 Rule
Хранится как `RuleVersion`:

- `rule_id`, `version`, `canonical_name`, `status`
- `title`, `description`
- `coding_agent` (провайдер)
- `rule_markdown`
- `checksum` (только для published)
- `saved_by`, `saved_at`, `resource_version`

### 3.4 Skill
Хранится как `SkillVersion`:

- `skill_id`, `version`, `canonical_name`, `status`
- `name`, `description`
- `coding_agent` (провайдер)
- `skill_markdown`
- `checksum` (только для published)
- `saved_by`, `saved_at`, `resource_version`

## 4. Версионирование и публикация

### 4.1 Черновик

- Если черновик существует, обновляется **тот же** draft (resource_version обязателен).
- Если черновика нет, создаётся новый с версией:
  - `0.1`, если нет published.
  - `nextMinor(latestPublished)`, если есть published.

### 4.2 Публикация

- `publish=true` сохраняет `published` версию.
- Если есть draft — публикуется версия draft.
- Если draft нет — берётся `nextMinor(latestPublished)` или `0.1`.
- `release=true` публикует **major-бамп** (`major + 1`.0).
- После публикации существующий draft (если был) **бампится** на следующий minor.

### 4.3 Optimistic locking (`resource_version`)

- Для draft: должен совпадать с текущим `resource_version` (или быть `0`, если draft отсутствует).
- Для publish: если базой является published, `resource_version` должен совпасть.
- Несовпадение приводит к `409 Conflict`.

## 5. Форматы и валидация

### 5.1 Идентификаторы

Формат `flow_id`, `rule_id`, `skill_id`:

```
^[a-z0-9][a-z0-9-_]*$
```

### 5.2 Flow YAML

Flow хранится и валидируется по JSON Schema (`flow.schema.json`) и доп. правилам. Обязательные поля:

- `id`
- `version`
- `canonical_name`
- `title`
- `start_node_id`
- `coding_agent`
- `nodes`

Доп. поля:

- `description`
- `status`
- `rule_refs` (массив строк)
- `fail_on_missing_declared_output` (bool)
- `fail_on_missing_expected_mutation` (bool)
- `response_schema` (object)

Сервер **не нормализует** `flow_yaml`, сохраняет строку как есть. Несоответствие `version`/`canonical_name` внутри YAML и версии записи в БД **не проверяется**.

### 5.3 Ноды Flow

Поддерживаемые типы:

- `ai`
- `command`
- `human_input`
- `human_approval`
- `terminal`

Обязательные поля каждой ноды:

- `id`, `title`, `type`, `execution_context`

Доп. поля:

- `description`, `instruction`, `response_schema`
- `skill_refs` (только для `ai`)
- `produced_artifacts`, `expected_mutations`
- `on_success`, `on_failure`, `on_submit`, `on_approve`, `on_rework`
- `command_engine`, `command_spec`, `success_exit_codes`, `retry_policy`, `idempotent` (для `command`)

### 5.4 Правила переходов

- `ai` требует `on_success` и `on_failure`
- `command` требует `on_success` (и **не допускает** `on_failure`)
- `human_input` требует `on_submit`
- `human_approval` требует `on_approve` и `on_rework.next_node`
- `terminal` **не допускает** переходы

Все целевые ноды должны существовать. Ноды, недостижимые от `start_node_id`, запрещены.

### 5.5 execution_context

`execution_context` — массив объектов:

- `type`: `user_request` | `directory_ref` | `file_ref` | `artifact_ref`
- `required`: boolean (обязателен)
- `path`: обязателен для всех типов, **кроме** `user_request`
- `scope`: `project` | `run` (обязателен для записей с `path`)

Резолв путей:

- `project` → путь относительно корня репозитория
- `run` → путь относительно `.hgsdlc/runs/{run_id}/nodes/{node_id}/artifacts/`

### 5.6 produced_artifacts / expected_mutations

Массив объектов:

- `path` (обязателен)
- `required` (обязателен)
- `scope`: `project` | `run` (обязателен)

### 5.7 Rule Markdown (frontmatter)

Для `publish=true` Rule должен содержать frontmatter:

- Markdown **должен** начинаться и заканчиваться `---` блоком.
- Frontmatter — YAML object.
- Обязательные поля зависят от `coding_agent`:

`qwen`:

- `description`
- `allowed_paths`
- `forbidden_paths`
- `allowed_commands`
- `response_schema` (опционально, полная схема ответа)

`claude`:

- `paths`

`cursor`:

- `description`
- `globs`
- `alwaysApply`

### 5.8 Skill Markdown (frontmatter)

Для `publish=true` Skill должен содержать frontmatter:

- `name`
- `description`

## 6. API

### 6.1 Auth

- `POST /api/auth/login`
  - body: `{ username, password }`
  - response: `{ token, user }`
- `POST /api/auth/logout` → `204`
- `GET /api/auth/me` → `{ id, username, role }`

### 6.2 Flows

- `GET /api/flows` → список последних версий (published либо draft)
- `GET /api/flows/{flowId}` → последняя published или draft
- `GET /api/flows/{flowId}/versions`
- `GET /api/flows/{flowId}/versions/{version}`
- `POST /api/flows/{flowId}/save`
  - header: `Idempotency-Key`
  - body: `{ flow_id, flow_yaml, publish, release, resource_version }`

### 6.3 Rules

- `GET /api/rules`
- `GET /api/rules/{ruleId}`
- `GET /api/rules/{ruleId}/versions`
- `GET /api/rules/{ruleId}/versions/{version}`
- `POST /api/rules/{ruleId}/save`
  - header: `Idempotency-Key`
  - body: `{ title, description, rule_id, coding_agent, rule_markdown, publish, release, resource_version }`

### 6.4 Skills

- `GET /api/skills`
- `GET /api/skills/{skillId}`
- `GET /api/skills/{skillId}/versions`
- `GET /api/skills/{skillId}/versions/{version}`
- `POST /api/skills/{skillId}/save`
  - header: `Idempotency-Key`
  - body: `{ name, description, skill_id, coding_agent, skill_markdown, publish, release, resource_version }`

### 6.5 Шаблоны

- `GET /api/rule-templates/{provider}`
- `GET /api/skill-templates/{provider}`

`provider`: `qwen`, `claude`, `cursor` (регистр и дефисы нормализуются).

### 6.6 Ошибки

- `400 Bad Request` — ошибки валидации
- `404 Not Found` — сущность не найдена
- `409 Conflict` — `resource_version` или idempotency конфликт

### 6.7 Idempotency

`Idempotency-Key` обязателен для всех `save`-операций.

Поведение:

- тот же ключ + тот же request → возвращается сохранённый ответ
- тот же ключ + другой request → `409 Conflict`
- повторный запрос до завершения → `409 Conflict`

## 7. UI

### 7.1 Реализованный функционал (через API)

- Login / Logout / Me
- Списки Flow / Rule / Skill
- Редакторы Flow / Rule / Skill
- Публикация и сохранение draft

### 7.2 Ограничения UI

- Редактор Flow использует **mock-данные** для Rule/Skill refs и не подгружает их из API.
- Страницы Runs, Gates, Audits, Prompt Package, Artifacts, Versions, Projects, Overview — **статические** и используют mock-данные.

## 8. Хранилище и инфраструктура

- Liquibase миграции в `backend/src/main/resources/db/changelog/`.
- Таблицы: `users`, `sessions`, `flows`, `rules`, `skills`, `idempotency_keys`.
- По умолчанию используется H2 in-memory (PostgreSQL-совместимый режим).
- Можно подключить PostgreSQL через `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

## 9. Примечания и ограничения

- Нет runtime-исполнения flow и работы с Git workspace.
- Нет prompt package, artifacts storage, audit trail, gate lifecycle.
- `coding_agent` в Flow — строка из YAML, не валидируется сервером.
- `flow_yaml` хранится как есть; сервер не исправляет `version`/`canonical_name` внутри YAML.
