# API Documentation

## REST APIs

### Auth Controller

**Base Path:** `/api/auth`

#### POST /api/auth/login
- **Purpose:** Логин пользователя
- **Request:**
  ```json
  {
    "username": "string",
    "password": "string"
  }
  ```
- **Response:**
  ```json
  {
    "token": "string",
    "user": {
      "id": "uuid",
      "username": "string",
      "displayName": "string",
      "roles": ["ADMIN", "FLOW_CONFIGURATOR", "PRODUCT_OWNER", "TECH_APPROVER"]
    }
  }
  ```

#### POST /api/auth/logout
- **Purpose:** Логаут пользователя
- **Response:** 204 No Content

#### GET /api/auth/me
- **Purpose:** Получить текущего пользователя
- **Response:**
  ```json
  {
    "id": "uuid",
    "username": "string",
    "displayName": "string",
    "roles": ["ADMIN"]
  }
  ```

### Flow Controller

**Base Path:** `/api/flows`

#### GET /api/flows
- **Purpose:** Список всех flows
- **Response:**
  ```json
  [{
    "id": "uuid",
    "flowId": "string",
    "canonicalName": "flowId@version",
    "version": "1.0",
    "status": "DRAFT | PUBLISHED",
    "title": "string",
    "description": "string",
    "codingAgent": "CLAUDE | QWEN | GIGACODE"
  }]
  ```

#### GET /api/flows/query
- **Purpose:** Поиск с фильтрами (cursor pagination)
- **Query params:** status, codingAgent, tags, teamCode, platformCode, cursor, limit
- **Response:** Paginated results

#### GET /api/flows/{flowId}
- **Purpose:** Получить flow
- **Response:** Полная информация о flow с YAML

#### GET /api/flows/{flowId}/versions
- **Purpose:** Все версии flow
- **Response:** Список версий

#### GET /api/flows/{flowId}/versions/{version}
- **Purpose:** Конкретная версия flow
- **Response:** Полная информация о версии

#### POST /api/flows/{flowId}/save
- **Purpose:** Сохранить flow (создать draft или обновить)
- **Header:** `Idempotency-Key` (опционально)
- **Request:**
  ```json
  {
    "flowId": "string",
    "version": "1.0",
    "title": "string",
    "description": "string",
    "flowYaml": "string",
    "codingAgent": "CLAUDE",
    "tags": ["tag1"],
    "teamCode": "string",
    "platformCode": "FRONT | BACK | DATA"
  }
  ```
- **Response:** Сохранённая версия flow

#### POST /api/flows/{flowId}/deprecate
- **Purpose:** Запросить deprecation flow
- **Response:** 202 Accepted

### Rule Controller

**Base Path:** `/api/rules`

#### GET /api/rules
- **Purpose:** Список правил
- **Response:** Список rules (аналогично flows)

#### GET /api/rules/query
- **Purpose:** Поиск с фильтрами
- **Query params:** status, codingAgent, tags, teamCode, platformCode, cursor, limit

#### GET /api/rules/{ruleId}
- **Purpose:** Получить правило
- **Response:** Полная информация о правиле с markdown

#### GET /api/rules/{ruleId}/versions
- **Purpose:** Все версии правила

#### GET /api/rules/{ruleId}/versions/{version}
- **Purpose:** Конкретная версия правила

#### POST /api/rules/{ruleId}/save
- **Purpose:** Сохранить правило
- **Request:** Аналогично flow

#### POST /api/rules/{ruleId}/deprecate
- **Purpose:** Запросить deprecation правила

### Skill Controller

**Base Path:** `/api/skills`

#### GET /api/skills
- **Purpose:** Список скиллов
- **Response:** Список skills

#### GET /api/skills/query
- **Purpose:** Поиск с фильтрами
- **Query params:** status, codingAgent, tags, cursor, limit

#### GET /api/skills/tags
- **Purpose:** Все теги skills
- **Response:** `["tag1", "tag2"]`

#### GET /api/skills/pending-publication
- **Purpose:** Ожидающие публикации skills
- **Response:** Список skills в статусе pending_approval

#### GET /api/skills/{skillId}
- **Purpose:** Получить скилл
- **Response:** Полная информация о скилле

#### GET /api/skills/{skillId}/versions
- **Purpose:** Все версии скилла

#### GET /api/skills/{skillId}/versions/{version}
- **Purpose:** Конкретная версия скилла

#### GET /api/skills/{skillId}/versions/{version}/files
- **Purpose:** Метаданные файлов версии
- **Response:**
  ```json
  [{
    "path": "skill.md",
    "role": "SKILL_MARKDOWN",
    "sizeBytes": 1024,
    "checksum": "sha256:..."
  }]
  ```

#### POST /api/skills/{skillId}/versions/{version}/files/content
- **Purpose:** Контент файлов
- **Request:** `["skill.md", "context.txt"]`
- **Response:** Map с base64 контентом

#### POST /api/skills/{skillId}/save
- **Purpose:** Сохранить скилл
- **Request:** Аналогично flow + files

#### POST /api/skills/{skillId}/deprecate
- **Purpose:** Запросить deprecation скилла

#### POST /api/skills/{skillId}/versions/{version}/approve
- **Purpose:** Approve публикацию
- **Request:**
  ```json
  {
    "comment": "string"
  }
  ```

#### POST /api/skills/{skillId}/versions/{version}/reject
- **Purpose:** Reject публикация
- **Request:**
  ```json
  {
    "comment": "string"
  }
  ```

### Runtime Controller

**Base Path:** `/api`

#### POST /api/runs
- **Purpose:** Создать запуск flow
- **Request:**
  ```json
  {
    "projectId": "uuid",
    "targetBranch": "string",
    "flowCanonicalName": "flowId@version",
    "featureRequest": "string",
    "aiSessionMode": "ISOLATED_ATTEMPT_SESSIONS | SHARED_RUN_SESSION",
    "publishMode": "LOCAL | PUSH | PR",
    "prCommitStrategy": "SQUASH | INDIVIDUAL",
    "skipGates": false
  }
  ```
- **Response:**
  ```json
  {
    "runId": "uuid",
    "status": "CREATED | RUNNING | WAITING_GATE | COMPLETED | FAILED"
  }
  ```

#### GET /api/runs/{runId}
- **Purpose:** Получить запуск
- **Response:** Полная информация о run

#### GET /api/runs
- **Purpose:** Список запусков
- **Query params:** projectId, status, limit, offset

#### POST /api/runs/{runId}/resume
- **Purpose:** Продолжить запуск (после gate)
- **Response:** Обновлённый run

#### POST /api/runs/{runId}/cancel
- **Purpose:** Отменить запуск
- **Response:** 202 Accepted

#### POST /api/runs/{runId}/publish/retry
- **Purpose:** Повторить публикацию
- **Response:** 202 Accepted

#### GET /api/runs/{runId}/nodes
- **Purpose:** Список нод запуска
- **Response:**
  ```json
  [{
    "id": "uuid",
    "nodeId": "string",
    "nodeKind": "ai | human_approval | human_input | command | terminal",
    "status": "PENDING | RUNNING | COMPLETED | FAILED | CANCELLED",
    "attemptNo": 1,
    "startedAt": "2024-01-01T00:00:00Z",
    "finishedAt": "2024-01-01T00:01:00Z"
  }]
  ```

#### GET /api/runs/{runId}/artifacts
- **Purpose:** Список артефактов
- **Query params:** scope (project | run)
- **Response:**
  ```json
  [{
    "id": "uuid",
    "artifactKey": "string",
    "kind": "FILE | DIRECTORY | DIFF | SNAPSHOT",
    "scope": "PROJECT | RUN",
    "path": "string",
    "sizeBytes": 1024
  }]
  ```

#### GET /api/runs/{runId}/artifacts/{artifactVersionId}/content
- **Purpose:** Контент артефакта
- **Response:** base64 контент

#### GET /api/runs/{runId}/gates/current
- **Purpose:** Текущий гейт
- **Response:** Информация о гейте

#### GET /api/gates/inbox
- **Purpose:** Inbox гейтов (требующих внимания)
- **Query params:** status, assigneeRole
- **Response:** Список gates

#### POST /api/gates/{gateId}/submit-input
- **Purpose:** Submit input в human_input gate
- **Request:**
  ```json
  {
    "expectedGateVersion": 1,
    "input": {},
    "comment": "string"
  }
  ```
- **Response:** Результат операции

#### POST /api/gates/{gateId}/approve
- **Purpose:** Approve human_approval gate
- **Request:**
  ```json
  {
    "expectedGateVersion": 1,
    "comment": "string",
    "reviewedArtifactVersionIds": ["uuid"]
  }
  ```
- **Response:** Результат операции

#### POST /api/gates/{gateId}/request-rework
- **Purpose:** Запросить доработку (rework)
- **Request:**
  ```json
  {
    "expectedGateVersion": 1,
    "comment": "string",
    "instruction": "что переделать",
    "keepChanges": true,
    "sessionPolicy": "ISOALTED_ATTEMPT_SESSIONS | SHARED_RUN_SESSION"
  }
  ```
- **Response:** Результат операции

#### GET /api/gates/{gateId}/changes
- **Purpose:** Изменения в гейте (git diff)
- **Response:** Список изменённых файлов

#### GET /api/gates/{gateId}/diff
- **Purpose:** Diff по конкретному пути
- **Query params:** path
- **Response:** Git diff

#### GET /api/gates/{gateId}/chat
- **Purpose:** Чат гейта
- **Response:** Сообщения чата

#### POST /api/gates/{gateId}/ask
- **Purpose:** Задать вопрос AI о гейте
- **Request:**
  ```json
  {
    "question": "string"
  }
  ```
- **Response:** Ответ от AI

#### GET /api/runs/{runId}/audit
- **Purpose:** Аудит события запуска
- **Response:** Список audit events

#### GET /api/runs/{runId}/audit/query
- **Purpose:** Query аудит с фильтрами
- **Query params:** eventType, actorType, limit, offset
- **Response:** Пагинированный аудит

#### GET /api/runs/{runId}/nodes/{nodeExecutionId}/log
- **Purpose:** Логи ноды
- **Response:** Логи выполнения ноды

### Project Controller

**Base Path:** `/api/projects`

#### GET /api/projects
- **Purpose:** Список проектов
- **Response:**
  ```json
  [{
    "id": "uuid",
    "name": "string",
    "repoUrl": "string",
    "defaultBranch": "main",
    "status": "ACTIVE | ARCHIVED",
    "lastRunId": "uuid"
  }]
  ```

#### POST /api/projects
- **Purpose:** Создать проект
- **Request:**
  ```json
  {
    "name": "string",
    "repoUrl": "string",
    "defaultBranch": "main"
  }
  ```
- **Response:** Созданный проект

#### GET /api/projects/{projectId}
- **Purpose:** Получить проект
- **Response:** Полная информация о проекте

#### PATCH /api/projects/{projectId}
- **Purpose:** Обновить проект
- **Request:**
  ```json
  {
    "name": "string",
    "repoUrl": "string",
    "defaultBranch": "string"
  }
  ```
- **Response:** Обновлённый проект

#### POST /api/projects/{projectId}/archive
- **Purpose:** Архивировать проект
- **Response:** 202 Accepted

#### DELETE /api/projects/{projectId}
- **Purpose:** Удалить проект
- **Response:** 204 No Content

#### GET /api/projects/{projectId}/runs
- **Purpose:** Запуски проекта
- **Response:** Список run для проекта

### Publication Controller

**Base Path:** `/api/publications`

#### GET /api/publications/requests
- **Purpose:** Список запросов на публикацию
- **Query params:** status, entityType, cursor, limit
- **Response:** Список requests

#### GET /api/publications/jobs
- **Purpose:** Список jobs публикации
- **Query params:** status, requestId
- **Response:** Список jobs

#### GET /api/publications/requests/{requestId}/jobs
- **Purpose:** Jobs по запросу
- **Response:** Список jobs для request

#### POST /api/publications/skills/{skillId}/versions/{version}/approve
- **Purpose:** Approve публикацию skill
- **Request:**
  ```json
  {
    "comment": "string"
  }
  ```
- **Response:** 202 Accepted

#### POST /api/publications/skills/{skillId}/versions/{version}/reject
- **Purpose:** Reject публикация skill
- **Request:**
  ```json
  {
    "comment": "string"
  }
  ```
- **Response:** 202 Accepted

#### POST /api/publications/skills/{skillId}/versions/{version}/retry
- **Purpose:** Retry публикация skill
- **Response:** 202 Accepted

#### POST /api/publications/rules/{ruleId}/versions/{version}/approve
- **Purpose:** Approve публикация rule

#### POST /api/publications/rules/{ruleId}/versions/{version}/reject
- **Purpose:** Reject публикация rule

#### POST /api/publications/rules/{ruleId}/versions/{version}/retry
- **Purpose:** Retry публикация rule

#### POST /api/publications/flows/{flowId}/versions/{version}/approve
- **Purpose:** Approve публикация flow

#### POST /api/publications/flows/{flowId}/versions/{version}/reject
- **Purpose:** Reject публикация flow

#### POST /api/publications/flows/{flowId}/versions/{version}/retry
- **Purpose:** Retry публикация flow

### Benchmark Controller

**Base Path:** `/api/benchmark`

#### POST /api/benchmark/cases
- **Purpose:** Создать benchmark case
- **Request:**
  ```json
  {
    "name": "string",
    "instruction": "string",
    "projectId": "uuid",
    "artifactType": "RUN",
    "artifactId": "uuid"
  }
  ```
- **Response:** Созданный case

#### DELETE /api/benchmark/cases/{caseId}
- **Purpose:** Удалить case
- **Response:** 204 No Content

#### GET /api/benchmark/cases
- **Purpose:** Список cases
- **Response:** Список benchmark cases

#### GET /api/benchmark/cases/{caseId}/runs
- **Purpose:** Runs по case
- **Response:** Список runs

#### POST /api/benchmark/runs
- **Purpose:** Запустить benchmark run
- **Request:**
  ```json
  {
    "caseId": "uuid",
    "codingAgent": "CLAUDE",
    "artifactBId": "uuid"
  }
  ```
- **Response:** Созданный run

#### GET /api/benchmark/runs
- **Purpose:** Список всех runs
- **Response:** Список benchmark runs

#### GET /api/benchmark/runs/{runId}
- **Purpose:** Получить run
- **Response:** Полная информация о benchmark run

#### GET /api/benchmark/runs/{runId}/file-comparison
- **Purpose:** Сравнение файлов
- **Response:** Diff между файлами

#### POST /api/benchmark/runs/{runId}/verdict
- **Purpose:** Submit verdict
- **Request:**
  ```json
  {
    "verdict": "A_BETTER | B_BETTER | TIE",
    "reviewComment": "string",
    "lineComments": {}
  }
  ```
- **Response:** 202 Accepted

### Settings Controller

**Base Path:** `/api/settings`

#### GET /api/settings/runtime
- **Purpose:** Runtime настройки
- **Response:**
  ```json
  {
    "maxInlineArtifactTokens": 16384,
    "agentCommands": {
      "CLAUDE": "claude ...",
      "QWEN": "qwen ..."
    }
  }
  ```

#### GET /api/settings/runtime/agent-command
- **Purpose:** Agent команды
- **Query params:** agent
- **Response:** Команда для агента

#### PUT /api/settings/runtime
- **Purpose:** Обновить runtime настройки
- **Request:**
  ```json
  {
    "maxInlineArtifactTokens": 16384,
    "agentCommands": {}
  }
  ```
- **Response:** Обновлённые настройки

#### PUT /api/settings/catalog
- **Purpose:** Обновить catalog настройки
- **Request:**
  ```json
  {
    "repoUrl": "string",
    "defaultBranch": "main"
  }
  ```
- **Response:** Обновлённые настройки

#### PUT /api/settings/catalog/repair
- **Purpose:** Ремонт каталога
- **Response:** 202 Accepted

### Overview Controller

**Base Path:** `/api/overview`

#### GET /api/overview
- **Purpose:** Dashboard overview
- **Response:**
  ```json
  {
    "metrics": {
      "totalRuns": 100,
      "activeRuns": 5,
      "completedRuns": 90,
      "failedRuns": 5
    },
    "recentRuns": [],
    "gateInbox": []
  }
  ```

### User Management Controller

**Base Path:** `/api/admin/users` (только ADMIN)

#### GET /api/admin/users
- **Purpose:** Список пользователей
- **Response:** Список users

#### POST /api/admin/users
- **Purpose:** Создать пользователя
- **Request:**
  ```json
  {
    "username": "string",
    "displayName": "string",
    "password": "string",
    "roles": ["ADMIN"]
  }
  ```
- **Response:** Созданный пользователь

#### PATCH /api/admin/users/{userId}
- **Purpose:** Обновить пользователя
- **Request:**
  ```json
  {
    "displayName": "string",
    "roles": ["ADMIN"],
    "enabled": true
  }
  ```
- **Response:** Обновлённый пользователь

#### PUT /api/admin/users/{userId}/password
- **Purpose:** Сменить пароль
- **Request:**
  ```json
  {
    "newPassword": "string"
  }
  ```
- **Response:** 204 No Content

#### DELETE /api/admin/users/{userId}
- **Purpose:** Удалить пользователя
- **Response:** 204 No Content

## Internal APIs

### RuntimeCommandService

**Package:** `ru.hgd.sdlc.runtime.application`

#### createRun(CreateRunCommand command)
- **Purpose:** Создать запуск flow
- **Parameters:**
  - `projectId` — UUID проекта
  - `targetBranch` — ветка для изменений
  - `flowCanonicalName` — flow@version
  - `featureRequest` — описание задачи
  - `aiSessionMode` — режим AI сессии
  - `publishMode` — режим публикации
- **Returns:** UUID созданного run

#### resumeRun(UUID runId, String user)
- **Purpose:** Продолжить run после gate
- **Parameters:**
  - `runId` — UUID run
  - `user` — username
- **Returns:** Обновлённый run

#### cancelRun(UUID runId, String user)
- **Purpose:** Отменить run
- **Parameters:**
  - `runId` — UUID run
  - `user` — username
- **Returns:** Отменённый run

### RuntimeQueryService

**Package:** `ru.hgd.sdlc.runtime.application`

#### findRun(UUID runId)
- **Purpose:** Получить run по ID
- **Returns:** RunEntity или NotFoundException

#### findNodeExecutions(UUID runId)
- **Purpose:** Получить все ноды run
- **Returns:** List<NodeExecutionEntity>

#### findArtifacts(UUID runId, ArtifactScope scope)
- **Purpose:** Получить артефакты run
- **Parameters:**
  - `runId` — UUID run
  - `scope` — PROJECT или RUN
- **Returns:** List<ArtifactVersionEntity>

### FlowService

**Package:** `ru.hgd.sdlc.flow.application`

#### saveFlow(String flowId, String version, String flowYaml, String user)
- **Purpose:** Сохранить или создать flow
- **Parameters:**
  - `flowId` — ID flow
  - `version` — semver версия
  - `flowYaml` — YAML определение
  - `user` — username
- **Returns:** Сохранённая FlowVersion

#### findFlowByCanonicalName(String canonicalName)
- **Purpose:** Найти flow по canonical name
- **Parameters:**
  - `canonicalName` — flowId@version
- **Returns:** FlowVersion или пустой Optional

## Data Models

### FlowVersion (Entity)

**Table:** `flows`

**Fields:**
- `id` — UUID (PK)
- `flowId` — String (business key)
- `version` — String (semver)
- `canonicalName` — String (flowId@version)
- `status` — FlowStatus (DRAFT, PUBLISHED)
- `title` — String
- `description` — String
- `startNodeId` — String
- `ruleRefs` — List<String>
- `codingAgent` — String (CLAUDE, QWEN, GIGACODE)
- `flowYaml` — String (YAML)
- `checksum` — String
- `teamCode` — String
- `platformCode` — String (FRONT, BACK, DATA)
- `tags` — List<String>
- `flowKind` — String
- `riskLevel` — String
- `scope` — String (team, organization)
- `approvalStatus` — RuleApprovalStatus
- `approvedBy` — String
- `approvedAt` — Instant
- `publishedAt` — Instant
- `sourceRef` — String
- `sourcePath` — String
- `lifecycleStatus` — FlowLifecycleStatus
- `publicationStatus` — PublicationStatus
- `publishedCommitSha` — String
- `publishedPrUrl` — String
- `lastPublishError` — String
- `forkedFrom` — String
- `forkedBy` — String
- `savedBy` — String
- `savedAt` — Instant
- `resourceVersion` — Long (optimistic locking)

**Relationships:**
- Нет (denormalized)

**Validation:**
- `flowId` — не null
- `version` — не null, формат semver
- `flowYaml` — валидный YAML
- `canonicalName` — уникальный

### RunEntity (Entity)

**Table:** `runs`

**Fields:**
- `id` — UUID (PK)
- `projectId` — UUID (FK)
- `targetBranch` — String
- `flowCanonicalName` — String
- `flowSnapshotJson` — String
- `aiSessionMode` — AiSessionMode
- `runSessionId` — String
- `pendingReworkSessionPolicy` — ReworkSessionPolicy
- `publishMode` — RunPublishMode (LOCAL, PUSH, PR)
- `workBranch` — String
- `prCommitStrategy` — PrCommitStrategy
- `publishStatus` — RunPublishStatus
- `pushStatus` — RunPublishStatus
- `prStatus` — RunPublishStatus
- `publishErrorStep` — String
- `publishCommitSha` — String
- `prUrl` — String
- `prNumber` — Integer
- `status` — RunStatus (CREATED, RUNNING, WAITING_GATE, WAITING_PUBLISH, PUBLISH_FAILED, COMPLETED, FAILED, CANCELLED)
- `currentNodeId` — String
- `featureRequest` — String
- `pendingReworkInstruction` — String
- `contextFileManifestJson` — String
- `workspaceRoot` — String
- `errorCode` — String
- `errorMessage` — String
- `skipGates` — Boolean
- `createdBy` — String
- `createdAt` — Instant
- `startedAt` — Instant
- `finishedAt` — Instant
- `resourceVersion` — Long

**Relationships:**
- `projectId` → Project.id

**Validation:**
- `projectId` — не null
- `flowCanonicalName` — не null
- `status` — не null

### GateInstanceEntity (Entity)

**Table:** `gate_instances`

**Fields:**
- `id` — UUID (PK)
- `runId` — UUID (FK)
- `nodeExecutionId` — UUID (FK)
- `nodeId` — String
- `gateKind` — GateKind (HUMAN_INPUT, HUMAN_APPROVAL)
- `status` — GateStatus (AWAITING_INPUT, SUBMITTED, AWAITING_DECISION, APPROVED, REWORK_REQUESTED, FAILED_VALIDATION, CANCELLED)
- `assigneeRole` — String
- `payloadJson` — String
- `openedAt` — Instant
- `closedAt` — Instant
- `resourceVersion` — Long

**Relationships:**
- `runId` → RunEntity.id
- `nodeExecutionId` → NodeExecutionEntity.id

**Validation:**
- `runId` — не null
- `gateKind` — не null
- `status` — не null

### ArtifactVersionEntity (Entity)

**Table:** `artifact_versions`

**Fields:**
- `id` — UUID (PK)
- `runId` — UUID (FK)
- `nodeExecutionId` — UUID (FK)
- `artifactKey` — String
- `kind` — ArtifactKind (FILE, DIRECTORY, DIFF, SNAPSHOT)
- `scope` — ArtifactScope (PROJECT, RUN)
- `path` — String
- `contentBase64` — String
- `sizeBytes` — Long
- `checksum` — String
- `createdAt` — Instant
- `resourceVersion` — Long

**Relationships:**
- `runId` → RunEntity.id
- `nodeExecutionId` → NodeExecutionEntity.id

**Validation:**
- `runId` — не null
- `artifactKey` — не null
- `kind` — не null
- `scope` — не null

### NodeModel (Domain Model)

**Purpose:** Модель ноды из YAML flow

**Fields:**
- `id` — String (ID ноды в графе)
- `title` — String
- `description` — String
- `nodeKind` — String (ai, human_approval, human_input, command, terminal)
- `gateKind` — String (input или approval для human_* нод)
- `executionContext` — List<ExecutionContextEntry>
- `instruction` — String (инструкция для AI)
- `skillRefs` — List<String> (ссылки на skills)
- `responseSchema` — JsonNode (JSON Schema для ответа)
- `producedArtifacts` — List<PathRequirement>
- `expectedMutations` — List<PathRequirement>
- `inputArtifact` — String (артефакт для command нод)
- `outputArtifact` — String (артефакт для command нод)
- `reviewArtifacts` — List<String> (для approval gates)
- `allowedActions` — List<String>
- `allowedRoles` — List<String>
- `completionPolicy` — JsonNode
- `userInstructions` — String
- `commandEngine` — String (shell, bash, etc.)
- `commandSpec` — JsonNode
- `successExitCodes` — List<Integer>
- `retryPolicy` — JsonNode
- `idempotent` — Boolean
- `checkpointBeforeRun` — Boolean
- `onSuccess` — String (ID следующей ноды)
- `onFailure` — String
- `onSubmit` — String
- `onApprove` — String
- `onRework` — OnRework

**Validation:**
- `id` — обязателен, уникален в графе
- `nodeKind` — обязателен
- Для `ai` нод: `instruction` или `skillRefs` обязателен
- Для `command` нод: `commandSpec` обязателен
- Для `human_*` нод: `gateKind` обязателен
