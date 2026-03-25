# Catalog Requirements: Rules / Skills / Flows

## 1. Цель

Документ фиксирует требования к каталогу `rules`, `skills`, `flows` для enterprise-инсталляций Human Guided Development.

Ключевые принципы:
- `rules`, `skills`, `flows` остаются раздельными сущностями.
- Источник опубликованного контента версий: единый Git-репозиторий каталога.
- Для каждой опубликованной версии публикуются два файла: контент + `metadata.yaml`.
- Локальные сущности всегда разрешены и могут использоваться без публикации в git.
- В БД хранится индекс, статусы и локальный контент; git — источник опубликованного контента.

---

## 2. Runtime settings

В `Runtime Settings` задаются:
- `catalog_repo_url` — URL каталожного репозитория.
- `catalog_default_branch` — базовая ветка (обычно `main`).
- `publish_mode_default` — `local` или `pr`.

Настройки авторизации к git (две вкладки в UI):
1. `SSH Key`
- `git_ssh_private_key`
- `git_ssh_public_key` (опционально)
- `git_ssh_passphrase` (опционально)

2. `Login / Password`
- `git_username`
- `git_password_or_pat`

Требования:
- хранение секретов только в secret storage;
- маскирование секретов в UI;
- аудит изменения секрета без логирования значения.

---

## 3. Структура репозитория каталога

Структура фиксированная:

```text
catalog-repo/
  rules/
    <rule-id>/
      <version>/
        RULE.md
        metadata.yaml
  skills/
    <skill-id>/
      <version>/
        SKILL.md
        metadata.yaml
  flows/
    <flow-id>/
      <version>/
        FLOW.yaml
        metadata.yaml
```

Правила:
- Новая версия = новая папка `<version>/`.
- Опубликованные версии immutable.
- Изменение опубликованной версии на месте запрещено.

---

## 4. Модель данных (поля сущностей)

Обозначения:
- `Уже есть` — поле уже есть в текущей модели.
- `Добавляется` — новое поле.

## 4.1 Rules

| Поле | Статус поля | Зачем нужно |
|---|---|---|
| `id` (UUID) | Уже есть | Технический PK версии |
| `rule_id` | Уже есть | Стабильный идентификатор правила |
| `version` | Уже есть | Версия правила |
| `canonical_name` | Уже есть | Уникальная ссылка `rule_id@version` |
| `title` | Уже есть | Короткое имя для каталога |
| `description` | Уже есть | Описание назначения |
| `coding_agent` | Уже есть | Для какого агента правило |
| `rule_markdown` | Уже есть | Контент (для локальных и draft версий) |
| `status` | Уже есть | Текущее состояние версии |
| `checksum` | Уже есть | Контроль целостности |
| `saved_by`, `saved_at` | Уже есть | Аудит сохранения |
| `resource_version` | Уже есть | Optimistic locking |
| `team_code` | Добавляется | Владелец правила |
| `platform_code` | Добавляется | Одна платформа: `UFS`, `PPRB`, `DATA` |
| `tags` (array<string>) | Добавляется | Теги для поиска |
| `rule_kind` | Добавляется | Классификация правила |
| `scope` | Добавляется | `global` или `project` |
| `environment` (`dev|prod`) | Добавляется | Среда версии |
| `approval_status` | Добавляется | `draft/pending_approval/approved/rejected/published` |
| `approved_by` | Добавляется | Кто апрувил публикацию |
| `approved_at` | Добавляется | Когда апрувили |
| `published_at` | Добавляется | Когда опубликовано |
| `source_ref` | Добавляется | Commit/tag в git (nullable для local-only) |
| `source_path` | Добавляется | Путь в git (nullable для local-only) |
| `content_source` (`db|git`) | Добавляется | Где хранится актуальный контент |
| `visibility` | Добавляется | `internal/restricted/public` |
| `lifecycle_status` | Добавляется | `active/deprecated/retired` |

## 4.2 Skills

| Поле | Статус поля | Зачем нужно |
|---|---|---|
| `id` (UUID) | Уже есть | Технический PK версии |
| `skill_id` | Уже есть | Стабильный идентификатор скила |
| `version` | Уже есть | Версия скила |
| `canonical_name` | Уже есть | Уникальная ссылка `skill_id@version` |
| `name` | Уже есть | Имя скила |
| `description` | Уже есть | Описание |
| `coding_agent` | Уже есть | Для какого агента скил |
| `skill_markdown` | Уже есть | Контент (для локальных и draft версий) |
| `status` | Уже есть | Текущее состояние версии |
| `checksum` | Уже есть | Контроль целостности |
| `saved_by`, `saved_at` | Уже есть | Аудит сохранения |
| `resource_version` | Уже есть | Optimistic locking |
| `team_code` | Добавляется | Владелец скила |
| `platform_code` | Добавляется | Одна платформа: `UFS`, `PPRB`, `DATA` |
| `tags` (array<string>) | Добавляется | Теги для поиска |
| `skill_kind` | Добавляется | Тип скила |
| `environment` (`dev|prod`) | Добавляется | Среда версии |
| `approval_status` | Добавляется | Состояние публикации |
| `approved_by` | Добавляется | Кто апрувил |
| `approved_at` | Добавляется | Когда апрувили |
| `published_at` | Добавляется | Когда опубликовано |
| `source_ref` | Добавляется | Commit/tag в git (nullable для local-only) |
| `source_path` | Добавляется | Путь в git (nullable для local-only) |
| `content_source` (`db|git`) | Добавляется | Где хранится актуальный контент |
| `visibility` | Добавляется | Видимость |
| `lifecycle_status` | Добавляется | Жизненный цикл |

## 4.3 Flows

| Поле | Статус поля | Зачем нужно |
|---|---|---|
| `id` (UUID) | Уже есть | Технический PK версии |
| `flow_id` | Уже есть | Стабильный идентификатор флоу |
| `version` | Уже есть | Версия флоу |
| `canonical_name` | Уже есть | Уникальная ссылка `flow_id@version` |
| `title` | Уже есть | Название |
| `description` | Уже есть | Описание |
| `start_node_id` | Уже есть | Старт исполнения |
| `rule_refs` | Уже есть | Ссылки на rules |
| `coding_agent` | Уже есть | Агент исполнения |
| `flow_yaml` | Уже есть | Контент (для локальных и draft версий) |
| `status` | Уже есть | Текущее состояние версии |
| `checksum` | Уже есть | Контроль целостности |
| `saved_by`, `saved_at` | Уже есть | Аудит сохранения |
| `resource_version` | Уже есть | Optimistic locking |
| `team_code` | Добавляется | Владелец флоу |
| `platform_code` | Добавляется | Одна платформа: `UFS`, `PPRB`, `DATA` |
| `tags` (array<string>) | Добавляется | Теги для поиска |
| `flow_kind` | Добавляется | Тип флоу |
| `risk_level` | Добавляется | `low/medium/high/critical` |
| `environment` (`dev|prod`) | Добавляется | Среда версии |
| `approval_status` | Добавляется | Состояние публикации |
| `approved_by` | Добавляется | Кто апрувил |
| `approved_at` | Добавляется | Когда апрувили |
| `published_at` | Добавляется | Когда опубликовано |
| `source_ref` | Добавляется | Commit/tag в git (nullable для local-only) |
| `source_path` | Добавляется | Путь в git (nullable для local-only) |
| `content_source` (`db|git`) | Добавляется | Где хранится актуальный контент |
| `visibility` | Добавляется | Видимость |
| `lifecycle_status` | Добавляется | Жизненный цикл |

---

## 5. Новые таблицы БД (полный перечень)

## 5.1 Справочники

### `teams`
Назначение: справочник команд.

| Колонка | Тип | Описание |
|---|---|---|
| `code` | varchar PK | Ключ команды |
| `name` | varchar | Отображаемое имя |
| `status` | varchar | `active/inactive` |
| `created_at` | timestamp | Когда создана |
| `updated_at` | timestamp | Когда обновлена |

### `tags`
Назначение: справочник тегов (ключ = имя/код тега).

| Колонка | Тип | Описание |
|---|---|---|
| `code` | varchar PK | Ключ тега |
| `name` | varchar | Отображаемое имя |
| `created_at` | timestamp | Когда создан |
| `updated_at` | timestamp | Когда обновлен |

## 5.2 Таблицы связей сущностей с тегами

### `rule_tags`
| Колонка | Тип | Описание |
|---|---|---|
| `rule_canonical_name` | varchar | Ссылка на rule версию |
| `tag_code` | varchar | Ссылка на tag |

PK: (`rule_canonical_name`, `tag_code`)

### `skill_tags`
| Колонка | Тип | Описание |
|---|---|---|
| `skill_canonical_name` | varchar | Ссылка на skill версию |
| `tag_code` | varchar | Ссылка на tag |

PK: (`skill_canonical_name`, `tag_code`)

### `flow_tags`
| Колонка | Тип | Описание |
|---|---|---|
| `flow_canonical_name` | varchar | Ссылка на flow версию |
| `tag_code` | varchar | Ссылка на tag |

PK: (`flow_canonical_name`, `tag_code`)

## 5.3 Таблица заявок на публикацию

### `catalog_publication_requests`
Назначение: хранит workflow запроса публикации.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | uuid PK | Идентификатор заявки |
| `entity_type` | varchar | `rule/skill/flow` |
| `entity_canonical_name` | varchar | Какая версия публикуется |
| `requested_by` | varchar | Кто отправил |
| `requested_at` | timestamp | Когда отправил |
| `status` | varchar | `pending/approved/rejected/published/failed` |
| `approver` | varchar nullable | Кто апрувил/отклонил |
| `decision_at` | timestamp nullable | Время решения |
| `comment` | text nullable | Комментарий к решению |
| `publish_mode` | varchar | `local/pr` |
| `git_branch` | varchar nullable | Ветка публикации |
| `pr_url` | varchar nullable | Ссылка на PR |
| `error_message` | text nullable | Ошибка публикации |

## 5.4 Таблицы рейтингов и комментариев

### `catalog_feedback`
Назначение: рейтинги и комментарии пользователей.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | uuid PK | Идентификатор записи |
| `entity_type` | varchar | `rule/skill/flow` |
| `entity_canonical_name` | varchar | Версия сущности |
| `user_id` | varchar | Кто поставил оценку |
| `score` | int | 1..5 |
| `comment` | text nullable | Комментарий |
| `created_at` | timestamp | Создано |
| `updated_at` | timestamp | Обновлено |
| `is_deleted` | boolean | Soft delete |

Unique: (`entity_type`, `entity_canonical_name`, `user_id`)

### `catalog_rating_agg`
Назначение: агрегаты для сортировки и UI.

| Колонка | Тип | Описание |
|---|---|---|
| `entity_type` | varchar | `rule/skill/flow` |
| `entity_canonical_name` | varchar PK-part | Версия сущности |
| `avg_score` | numeric | Средний рейтинг |
| `votes_count` | int | Количество голосов |
| `wilson_score` | numeric | Ранжирование |
| `usage_count` | int | Использования |
| `success_rate` | numeric | Доля успешных запусков |
| `last_used_at` | timestamp nullable | Последнее использование |

PK: (`entity_type`, `entity_canonical_name`)

---

## 6. Metadata: структура и примеры

## 6.1 Общая структура `metadata.yaml`

```yaml
entity_type: rule|skill|flow
id: <string>
version: <semver>
canonical_name: <id@version>
display_name: <string>
description: <string>
coding_agent: <string>

team_code: <string>
platform_code: UFS|PPRB|DATA
tags: [tag1, tag2]

environment: dev|prod

lifecycle_status: active|deprecated|retired

source_ref: <git commit/tag>
source_path: <path in repo>
checksum: <sha256>

approved_by: <string|null>
approved_at: <ISO-8601|null>
published_at: <ISO-8601|null>

created_at: <ISO-8601>
updated_at: <ISO-8601>
```

Важно:
- рабочие поля (`draft/pending_approval/...`) живут в БД платформы;
- публикационные поля (`approved_by/approved_at/published_at`) пишутся в metadata при publish.
- `visibility` хранится только в БД платформы и не публикуется в git metadata.

## 6.2 Пример Rule metadata

```yaml
entity_type: rule
id: project-architecture-rule
version: 1.2.0
canonical_name: project-architecture-rule@1.2.0
display_name: Project Architecture Guidance
description: Defines architecture documentation boundaries.
coding_agent: qwen

team_code: platform-architecture
platform_code: UFS
tags: [architecture, docs, java]

rule_kind: architecture
scope: project

environment: prod

lifecycle_status: active

approved_by: ivan.petrov
approved_at: 2026-03-21T10:15:00Z
published_at: 2026-03-21T10:20:00Z

source_ref: 9f2a6e1b7c4d2f6a2e5a1498f44d7cbcb2201d70
source_path: rules/project-architecture-rule/1.2.0
checksum: sha256:cf2658e8d6b1de47f6f81db6e27dbad3d288230f1f0f4369f58e16c6365e6bc5

created_at: 2026-03-21T10:10:00Z
updated_at: 2026-03-21T10:20:00Z
```

## 6.3 Пример Skill metadata

```yaml
entity_type: skill
id: restore-c4-architecture
version: 1.1.0
canonical_name: restore-c4-architecture@1.1.0
display_name: Restore C4 Architecture
description: Reconstruct C4 model from codebase.
coding_agent: qwen

team_code: platform-architecture
platform_code: PPRB
tags: [java, spring-boot, architecture]

skill_kind: analysis

environment: dev

lifecycle_status: active

approved_by: maria.sidorova
approved_at: 2026-03-21T11:00:00Z
published_at: 2026-03-21T11:03:00Z

source_ref: 6f43f20b09f6e55727fcce3013fa8f84f13f92a9
source_path: skills/restore-c4-architecture/1.1.0
checksum: sha256:b0f57f4ad66bbd13722f42ca92a539f49fef3e4fcf2512c4711d8fa39e1f52e2

created_at: 2026-03-21T10:50:00Z
updated_at: 2026-03-21T11:03:00Z
```

## 6.4 Пример Flow metadata

```yaml
entity_type: flow
id: restore-architecture-flow
version: 2.0.0
canonical_name: restore-architecture-flow@2.0.0
display_name: Restore Architecture Flow
description: Builds architecture artifacts with approval gates.
coding_agent: qwen

team_code: platform-runtime
platform_code: DATA
tags: [governance, architecture, approval]

flow_kind: governance
risk_level: high

environment: prod

lifecycle_status: active

approved_by: pavel.kuznetsov
approved_at: 2026-03-21T12:12:00Z
published_at: 2026-03-21T12:20:00Z

source_ref: 0ac22456dfe338e7ba40dd1ee0899a5f46a5fd9e
source_path: flows/restore-architecture-flow/2.0.0
checksum: sha256:8b85ff983d911313d95e0f76058bd42bc6d788f6994fd352cc8f98edb0ea7063

created_at: 2026-03-21T11:45:00Z
updated_at: 2026-03-21T12:20:00Z
```

---

## 7. Local-only использование (без push в git)

Да, ты прав: если пользователь создает новую локальную версию и использует ее только локально, в git пушить не нужно.

Требования:
- локальная версия хранит контент в БД (`rule_markdown` / `skill_markdown` / `flow_yaml`);
- `content_source='db'`;
- `source_ref` и `source_path` могут быть `null`;
- такая версия доступна runtime внутри инсталляции;
- при публикации в каталог контент переносится в git и версия переключается на `content_source='git'`.

---

## 8. Логика sync и checkout

## 8.1 Старт системы
1. Подготовить локальную mirror-копию `catalog_repo_url`.
2. Выполнить `fetch --prune --tags`.
3. Просканировать все `metadata.yaml`.
4. Валидировать metadata и контент.
5. Сделать upsert индекса в БД.

## 8.2 Repair (ручной sync)
Инкрементальный авто-sync и webhook пока не используются.

В `Settings` есть кнопка `Repair catalog`:
- полный перескан репозитория;
- переиндексация БД;
- восстановление ссылок `source_ref/source_path`.

## 8.3 Runtime checkout
- Для версий с `content_source='git'`: контент читается из локальной git-копии по `source_ref + source_path`.
- Для версий с `content_source='db'`: контент читается из БД.

---

## 9. Publish и approve

Состояния:
1. `draft`
2. `pending_approval`
3. `approved` или `rejected`
4. `published`

Поток:
1. Пользователь редактирует версию.
2. Нажимает `Request publication` -> `pending_approval`.
3. `CATALOG_APPROVER` принимает решение.
4. При `approved` система публикует в git контент + metadata.
5. После успешной git-операции фиксируются `published_at`, `source_ref`, `approved_by`.

Режимы:
- `local`: commit в локальную копию.
- `pr`: feature branch + commit + PR; статус `published` после merge.

## 9.1 Варианты работы новой версии до публикации

### Вариант A: Локальный черновик из новой сущности
- Пользователь создает новую сущность/версию.
- `approval_status=draft`, `content_source=db`.
- Контент хранится в БД, в git ничего не пишется.
- Версия доступна только локально для запуска в этой инсталляции.

### Вариант B: Локальный черновик на основе опубликованной версии
- Пользователь берет published версию из каталога и делает `Create local draft`.
- Создается новая локальная версия (обычно с новым `version` в `dev`).
- `content_source=db`, в git изменений нет.
- Можно тестировать/использовать локально без публикации.

### Вариант C: Запрос публикации локального черновика
- Локальная версия переводится в `pending_approval`.
- После approve система переносит контент в git (контент + metadata).
- После успешного publish версия становится `content_source=git`, фиксируются `source_ref/source_path`.

### Вариант D: Отклонение публикации
- При `rejected` версия остается локальной (`content_source=db`).
- Можно продолжить редактирование и повторно отправить запрос.

### Вариант E: Публикация не выполнена из-за ошибки
- При git-ошибке статус заявки `failed`, версия не становится published.
- Доступен retry после исправления причины (доступ, конфликт ветки, policy).

---

## 10. Роли и права

- `CATALOG_APPROVER` — единственная роль, которая может approve/reject и запускать финальную публикацию в git.
- Остальные пользователи:
  - видят каталог,
  - создают локальные версии,
  - отправляют запрос на публикацию,
  - не могут approve.

---

## 11. Рейтинги и комментарии

- Рейтинги/комментарии в БД, не в git.
- Таблицы: `catalog_feedback`, `catalog_rating_agg`.
- Для мульти-инсталляций: синк через Feedback Hub API (event/outbox).

---

## 12. Поиск

Фильтры:
- `entity_type` (`rule|skill|flow`)
- `coding_agent`
- `team_code`
- `platform_code` (`UFS|PPRB|DATA`)
- `tags`
- `kind` (`rule_kind|skill_kind|flow_kind`)
- `environment` (`dev|prod`)
- `approval_status`
- `lifecycle_status`
- `content_source` (`db|git`)
- для flow: `risk_level`

---

## 13. Валидация перед publish

1. Валидация схемы контента.
2. Валидация схемы metadata.
3. Проверка `canonical_name == id@version`.
4. Проверка `team_code` и `platform_code`.
5. Автосоздание отсутствующих `tags`.
6. Проверка checksum.
7. Проверка ролевой политики (`CATALOG_APPROVER`).
8. Для `pr`: проверка успешности создания PR.

---

## 14. Immutability published версий

Правило:
- версия со статусом `published` неизменяема;
- правки делаются только через новую версию.

Контроль на 3 уровнях:
1. API: запрет update/delete published версии (`409/422`).
2. БД: trigger/guard на запрет изменения published записей.
3. Git: запрет перезаписи уже опубликованной папки версии.

---

## 15. Клиентские пути работы (однотипно для rules/skills/flows)

Ниже описаны одинаковые пользовательские потоки для всех трех типов сущностей:
- Rule
- Skill
- Flow

Различается только контентный файл (`RULE.md` / `SKILL.md` / `FLOW.yaml`) и типовые поля (`scope`, `skill_kind`, `risk_level`).

## 15.1 Синк каталога при нажатии `Repair`

Клиентский поток:
1. Пользователь открывает `Settings -> Runtime -> Catalog`.
2. Нажимает кнопку `Repair catalog`.
3. UI показывает статус операции: `queued -> running -> completed|failed`.
4. По завершении UI обновляет:
   - число найденных версий по типам,
   - число созданных/обновленных записей индекса,
   - список ошибок валидации (если были).

Серверный результат `Repair`:
1. Полный перескан репозитория.
2. Чтение `metadata.yaml` и проверка наличия контента рядом.
3. Upsert индекса в БД.
4. Перестроение ссылок `source_ref/source_path`.

Ограничение:
- `Repair` не меняет локальный draft-контент в БД (`content_source='db'`).

## 15.2 Добавление новой версии локально

Клиентский поток:
1. Пользователь открывает карточку сущности и нажимает `Create local version`.
2. UI предлагает:
   - `version` (предзаполненный bump),
   - `environment` (`dev|prod`),
   - обязательные метаполя (`team_code`, `platform_code`, `tags`, `kind` и т.д.).
3. После подтверждения создается локальная версия:
   - `approval_status='draft'`,
   - `content_source='db'`,
   - контент доступен в редакторе.
4. Пользователь сохраняет изменения и может запускать runtime локально на этой версии.

Правила:
- в git при этом ничего не пишется;
- `source_ref/source_path = null` до момента публикации.

## 15.3 Публикация версии в git

Клиентский поток:
1. Пользователь открывает локальную версию и нажимает `Request publication`.
2. Статус версии: `pending_approval`.
3. `CATALOG_APPROVER` в UI подтверждает (`Approve`) или отклоняет (`Reject`).
4. При approve система выполняет публикацию:
   - формирует контентный файл версии,
   - формирует `metadata.yaml`,
   - пишет в git в зависимости от режима (`local`/`pr`).
5. После успешной публикации:
   - версия получает `approval_status='published'`,
   - `content_source='git'`,
   - фиксируются `source_ref`, `source_path`, `published_at`.

Результат по режимам:
- `local`: commit в локальную копию каталога.
- `pr`: feature branch + commit + push + PR, статус `published` после merge.

## 15.4 Сборка графа и правил в runtime при смешанных версиях (local + git)

Единый принцип резолва ссылок:
1. Runtime берет `flow`-версию (обычно по `canonical_name`).
2. Для каждого `rule_ref/skill_ref` runtime ищет версию в индексе БД.
3. Для найденной версии выбирает источник контента:
   - `content_source='git'` -> читать из локальной git-копии по `source_ref+source_path`,
   - `content_source='db'` -> читать из БД (локальная версия).
4. Собирает финальный граф исполнения с уже материализованным контентом всех ссылок.

Практическая интерпретация:
- если часть сущностей опубликована, а часть существует только локально, runtime корректно запускается на смешанном наборе;
- блокирующая ошибка возникает только если ссылка в flow указывает на версию, которой нет ни в git-индексе, ни в локальной БД.
