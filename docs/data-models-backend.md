# Модели данных — Backend

> БД: PostgreSQL (prod) / H2 in-memory MODE=PostgreSQL (dev/test)
> Миграции: Liquibase (`db/changelog/db.changelog-master.yaml`)
> ORM: Hibernate, ddl-auto=validate (схема управляется исключительно Liquibase)

---

## Модуль Auth

### `users`
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | Идентификатор |
| `username` | VARCHAR(128) UNIQUE | Логин |
| `password_hash` | VARCHAR(256) | BCrypt-хэш пароля |
| `role` | VARCHAR(32) | Базовая роль: `ADMIN`, `OPERATOR`, `REVIEWER` |
| `additional_roles` | TEXT (JSON) | Дополнительные роли пользователя |
| `created_at` | TIMESTAMPTZ | Дата создания |

**Начальный пользователь:** создаётся при старте (admin/admin, роль ADMIN), настраивается через `application.yml`.

### `auth_sessions`
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `user_id` | UUID FK→users | |
| `token` | VARCHAR(512) UNIQUE | Bearer-токен |
| `expires_at` | TIMESTAMPTZ | TTL из настройки `auth.session-ttl-seconds` (по умолчанию 86400 сек = 24 ч) |
| `created_at` | TIMESTAMPTZ | |

---

## Модуль Flow

### `flows` (хранит версии — каждая версия отдельная строка)
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `flow_id` | VARCHAR(255) | Логический ID flow |
| `version` | VARCHAR(32) | Версия (напр. `1.0`) |
| `canonical_name` | VARCHAR(255) UNIQUE | `flow_id@version` |
| `status` | VARCHAR(32) | `draft`, `published` |
| `title` | VARCHAR(255) | |
| `description` | VARCHAR(1024) | |
| `start_node_id` | VARCHAR(255) | ID стартовой ноды |
| `rule_refs` | TEXT (JSON array) | Ссылки на rules: `["rule-id@version"]` |
| `coding_agent` | VARCHAR(64) | Coding-агент: `qwen` |
| `flow_yaml` | TEXT | Полный YAML-контент flow |
| `checksum` | VARCHAR(128) | SHA-хэш content для дедупликации |
| `team_code` | VARCHAR(128) | Команда-владелец |
| `platform_code` | VARCHAR(32) | Платформа |
| `tags_json` | TEXT (JSON array) | Теги |
| `flow_kind` | VARCHAR(64) | Вид flow: `analysis`, `code`, `delivery`, `full-cycle` |
| `risk_level` | VARCHAR(32) | Уровень риска |
| `scope` | VARCHAR(32) | `team`, `organization` |
| `approval_status` | VARCHAR(32) | `draft`, `pending_approval`, `approved`, `rejected`, `published` |
| `approved_by` | VARCHAR(128) | |
| `approved_at` | TIMESTAMPTZ | |
| `published_at` | TIMESTAMPTZ | |
| `source_ref` | VARCHAR(128) | |
| `source_path` | VARCHAR(512) | |
| `lifecycle_status` | VARCHAR(32) | `active`, `deprecated`, `retired` |
| `publication_status` | VARCHAR(32) | `draft`, `pending_approval`, `approved`, `publishing`, `published`, `failed`, `rejected` |
| `published_commit_sha` | VARCHAR(64) | SHA коммита в catalog-repo |
| `published_pr_url` | VARCHAR(1024) | URL pull request |
| `last_publish_error` | TEXT | |
| `forked_from` | VARCHAR(255) | Источник форка (`id@version`) для team-сущностей |
| `forked_by` | VARCHAR(128) | Инициатор форка |
| `saved_by` | VARCHAR(128) | |
| `saved_at` | TIMESTAMPTZ | |
| `resource_version` | BIGINT | Оптимистичная блокировка (`@Version`) |

---

## Модуль Rule

### `rules` (аналогичная версионная структура)
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `rule_id` | VARCHAR(255) | Логический ID |
| `version` | VARCHAR(32) | |
| `canonical_name` | VARCHAR(255) UNIQUE | `rule_id@version` |
| `status` | VARCHAR(32) | `draft`, `published` |
| `title` | VARCHAR(255) | |
| `description` | VARCHAR(1024) | |
| `coding_agent` | VARCHAR(64) | `claude`, `cursor`, `qwen` |
| `rule_markdown` | TEXT | Markdown-контент правила |
| `checksum` | VARCHAR(128) | SHA-хэш content |
| `team_code` | VARCHAR(128) | Команда-владелец |
| `platform_code` | VARCHAR(32) | `FRONT`, `BACK`, `DATA` |
| `tags_json` | TEXT (JSON array) | Теги |
| `rule_kind` | VARCHAR(64) | `architecture`, `coding-style`, `security` |
| `scope` | VARCHAR(32) | `team`, `organization` |
| `approval_status` | VARCHAR(32) | `draft`, `pending_approval`, `approved`, `rejected`, `published` |
| `approved_by` | VARCHAR(128) | |
| `approved_at` | TIMESTAMPTZ | |
| `published_at` | TIMESTAMPTZ | |
| `source_ref` | VARCHAR(128) | |
| `source_path` | VARCHAR(512) | |
| `lifecycle_status` | VARCHAR(32) | `active`, `deprecated`, `retired` |
| `publication_status` | VARCHAR(32) | `draft`, `pending_approval`, `approved`, `publishing`, `published`, `failed`, `rejected` |
| `published_commit_sha` | VARCHAR(64) | |
| `published_pr_url` | VARCHAR(1024) | |
| `last_publish_error` | TEXT | |
| `forked_from` | VARCHAR(255) | Источник форка (`id@version`) |
| `forked_by` | VARCHAR(128) | Инициатор форка |
| `saved_by` | VARCHAR(128) | |
| `saved_at` | TIMESTAMPTZ | |
| `resource_version` | BIGINT | |

---

## Модуль Skill

### `skills` (аналогичная версионная структура)
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `skill_id` | VARCHAR(255) | |
| `version` | VARCHAR(32) | |
| `canonical_name` | VARCHAR(255) UNIQUE | `skill_id@version` |
| `status` | VARCHAR(32) | `draft`, `published` |
| `name` | VARCHAR(255) | |
| `description` | VARCHAR(512) | |
| `coding_agent` | VARCHAR(64) | `claude`, `cursor`, `qwen` |
| `skill_markdown` | TEXT | Markdown-контент скилла |
| `checksum` | VARCHAR(128) | SHA-хэш content |
| `team_code` | VARCHAR(128) | Команда-владелец |
| `platform_code` | VARCHAR(32) | `FRONT`, `BACK`, `DATA` |
| `tags_json` | TEXT (JSON array) | Теги |
| `skill_kind` | VARCHAR(64) | `analysis`, `code`, `review`, `refactor`, `qa`, `ops` |
| `scope` | VARCHAR(32) | `team`, `organization` |
| `approval_status` | VARCHAR(32) | `draft`, `pending_approval`, `approved`, `rejected`, `published` |
| `approved_by` | VARCHAR(128) | |
| `approved_at` | TIMESTAMPTZ | |
| `published_at` | TIMESTAMPTZ | |
| `source_ref` | VARCHAR(128) | |
| `source_path` | VARCHAR(512) | |
| `lifecycle_status` | VARCHAR(32) | `active`, `deprecated`, `retired` |
| `publication_status` | VARCHAR(32) | `draft`, `pending_approval`, `approved`, `publishing`, `published`, `failed`, `rejected` |
| `published_commit_sha` | VARCHAR(64) | |
| `published_pr_url` | VARCHAR(1024) | |
| `last_publish_error` | TEXT | |
| `forked_from` | VARCHAR(255) | Источник форка (`id@version`) |
| `forked_by` | VARCHAR(128) | Инициатор форка |
| `saved_by` | VARCHAR(128) | |
| `saved_at` | TIMESTAMPTZ | |
| `resource_version` | BIGINT | |

### `skill_tags` (теги скиллов)
| Поле | Тип |
|------|-----|
| `skill_id` | UUID FK→skills |
| `tag` | VARCHAR(128) |

---

## Модуль Project

### `projects`
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `name` | VARCHAR(255) | Название проекта |
| `description` | TEXT | |
| `repo_url` | VARCHAR(1024) | Git URL репозитория |
| `status` | VARCHAR(32) | `active`, `archived` |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

---

## Модуль Runtime

### `runs`
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `project_id` | UUID FK→projects | |
| `target_branch` | VARCHAR(255) | Целевая ветка в git |
| `flow_canonical_name` | VARCHAR(255) | `flow_id@version` |
| `flow_snapshot_json` | TEXT | Снимок flow на момент запуска |
| `status` | VARCHAR(32) | `pending`, `running`, `paused`, `completed`, `failed`, `cancelled` |
| `current_node_id` | VARCHAR(255) | Текущая активная нода |
| `feature_request` | TEXT | Описание задачи (передаётся агенту) |
| `pending_rework_instruction` | TEXT | Инструкция для переработки |
| `context_file_manifest_json` | TEXT | Манифест файлов контекста |
| `workspace_root` | VARCHAR(2048) | Путь к рабочей директории |
| `error_code` | VARCHAR(128) | |
| `error_message` | TEXT | |
| `created_by` | VARCHAR(128) | |
| `created_at` | TIMESTAMPTZ | |
| `started_at` | TIMESTAMPTZ | |
| `finished_at` | TIMESTAMPTZ | |
| `resource_version` | BIGINT | |

**Индексы:** `(project_id, target_branch, status)`, `(status)`, `(created_at)`

### `node_executions`
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `run_id` | UUID FK→runs | |
| `node_id` | VARCHAR(255) | ID ноды в flow |
| `node_kind` | VARCHAR(64) | `ai`, `human_approval`, `human_input`, `terminal`, `command` |
| `attempt_no` | INT | Номер попытки (retry) |
| `status` | VARCHAR(32) | `running`, `completed`, `failed`, `skipped` |
| `started_at` | TIMESTAMPTZ | |
| `finished_at` | TIMESTAMPTZ | |
| `error_code` | VARCHAR(128) | |
| `error_message` | TEXT | |
| `checkpoint_enabled` | BOOLEAN | Включён ли чекпоинт (git commit) |
| `checkpoint_commit_sha` | VARCHAR(64) | SHA чекпоинт-коммита |
| `checkpoint_created_at` | TIMESTAMPTZ | |
| `resource_version` | BIGINT | |

### `gate_instances`
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `run_id` | UUID FK→runs | |
| `node_execution_id` | UUID FK→node_executions | |
| `node_id` | VARCHAR(255) | |
| `gate_kind` | VARCHAR(64) | `human_input`, `human_approval` |
| `status` | VARCHAR(64) | `open`, `submitted`, `approved`, `rework_requested`, `closed` |
| `assignee_role` | VARCHAR(64) | Роль, ответственная за gate |
| `payload_json` | TEXT | Данные gate (инструкция, контекст) |
| `opened_at` | TIMESTAMPTZ | |
| `closed_at` | TIMESTAMPTZ | |
| `resource_version` | BIGINT | |

### `artifact_versions`
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `run_id` | UUID FK→runs | |
| `node_id` | VARCHAR(255) | Нода, создавшая артефакт |
| `artifact_key` | VARCHAR(255) | Логический ключ артефакта |
| `path` | VARCHAR(2048) | Путь в workspace |
| `scope` | VARCHAR(16) | `project` (постоянный) / `run` (временный) |
| `kind` | VARCHAR(32) | `file`, `directory` |
| `checksum` | VARCHAR(128) | |
| `size_bytes` | BIGINT | |
| `supersedes_artifact_version_id` | UUID | Ссылка на предыдущую версию |
| `version_no` | INT | Порядковый номер версии артефакта |
| `created_at` | TIMESTAMPTZ | |

### `audit_events`
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `run_id` | UUID | |
| `node_execution_id` | UUID | |
| `gate_id` | UUID | |
| `sequence_no` | BIGINT | Монотонный порядковый номер в рамках run |
| `event_type` | VARCHAR(128) | Тип события: `run_started`, `node_started`, `rules_materialized`, `gate_opened`, `gate_approved` и др. |
| `event_time` | TIMESTAMPTZ | |
| `actor_type` | VARCHAR(32) | `SYSTEM`, `USER`, `AGENT` |
| `actor_id` | VARCHAR(128) | username или `runtime` |
| `payload_json` | TEXT | Детали события |

**Уникальный индекс:** `(run_id, sequence_no)` — гарантирует порядок событий.

---

## Модуль Idempotency

### `idempotency_records`
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `idempotency_key` | VARCHAR(256) UNIQUE | Ключ клиента |
| `operation` | VARCHAR(128) | Имя операции (`flows.save`, `runs.create`) |
| `request_hash` | VARCHAR(128) | Хэш payload запроса |
| `response_json` | TEXT | Кэшированный ответ |
| `status` | VARCHAR(32) | `processing`, `completed` |
| `created_at` | TIMESTAMPTZ | |
| `completed_at` | TIMESTAMPTZ | |

---

## Модуль Publication

### `publication_requests`
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `entity_type` | VARCHAR(32) | `FLOW`, `RULE`, `SKILL` |
| `entity_id` | VARCHAR(255) | Логический ID артефакта |
| `version` | VARCHAR(32) | |
| `canonical_name` | VARCHAR(255) | `id@version` |
| `author` | VARCHAR(128) | |
| `requested_mode` | VARCHAR(32) | Вычисляется по `scope`: `team -> local`, `organization -> pr` |
| `status` | VARCHAR(32) | `pending_approval`, `approved`, `rejected`, `in_progress`, `published`, `failed` |
| `approval_count` | INT | Число полученных утверждений |
| `required_approvals` | INT | Требуемое число утверждений |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |
| `last_error` | TEXT | |

### `publication_jobs`
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | UUID PK | |
| `request_id` | UUID FK→publication_requests | |
| `entity_type` | VARCHAR(32) | |
| `entity_id` | VARCHAR(255) | |
| `version` | VARCHAR(32) | |
| `status` | VARCHAR(32) | `pending`, `running`, `completed`, `failed` |
| `step` | VARCHAR(128) | Текущий шаг (clone, commit, push, pr_create) |
| `attempt_no` | INT | |
| `branch_name` | VARCHAR(255) | Ветка в catalog-repo |
| `pr_url` | VARCHAR(1024) | URL pull request |
| `pr_number` | INT | |
| `commit_sha` | VARCHAR(64) | |
| `error` | TEXT | |
| `started_at` | TIMESTAMPTZ | |
| `finished_at` | TIMESTAMPTZ | |
| `created_at` | TIMESTAMPTZ | |

---

## Модуль Settings

### `system_settings`
| Поле | Тип | Описание |
|------|-----|----------|
| `id` | VARCHAR(64) PK | Ключ настройки |
| `value` | TEXT | Значение |
| `updated_at` | TIMESTAMPTZ | |
| `updated_by` | VARCHAR(128) | |

**Ключи:** `workspace_root`, `coding_agent`, `ai_timeout_seconds`, `catalog_repo_url`, `catalog_default_branch`, `publish_mode`, `git_ssh_private_key`, `git_ssh_public_key`, `git_ssh_passphrase`, `git_certificate`, `git_certificate_key`, `git_username`, `git_password_or_pat`
