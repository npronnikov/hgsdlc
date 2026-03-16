# HGSDLC Runtime — минимальная спецификация v1

## 1. Назначение

Этот документ задает минимальную спецификацию runtime-слоя для исполнения flow в HGSDLC.

Цель v1:

1. Исполнять node-graph на published flow version.
2. Поддерживать `AI`, `External Command`, `human_input`, `human_approval`, `terminal`.
3. Проверять декларации node-конфигурации перед переходами.
4. Вести полный audit trail по run, node execution, gate decision и artifact versions.

## 2. Область и ограничения v1

Входит в v1:

1. Один orchestration engine.
2. Персистентное состояние в PostgreSQL.
3. Workspace для run с артефактами.
4. Идемпотентные переходы по state machine.
5. Resume после рестарта backend.

Не входит в v1:

1. Полная sandbox-изоляция выполнения команд.
2. Политики rollback/undo.
3. Условные выражения `OR/AND` над декларациями outputs (кроме явного расширения в будущем).

## 3. Термины

1. `Run` — экземпляр исполнения flow для проекта и ветки.
2. `Node execution` — одна попытка выполнения node в рамках run.
3. `Gate instance` — ожидание человека (`human_input` или `human_approval`) для конкретной node execution.
4. `Artifact version` — неизменяемая версия артефакта, созданная node или gate.
5. `Runtime snapshot` — текущее вычисленное состояние run для UI и recovery.

## 4. Runtime контракт

Runtime обязан:

1. Загружать pinned flow definition (published canonical name).
2. Выполнять только поведение node, заданное конфигурацией.
3. На каждом переходе проверять target node id существует.
4. Перед завершением node валидировать декларации outputs node.
5. Для gate переводить run в `waiting_gate` и ждать внешнего действия.
6. Писать audit event для каждого значимого шага.

## 5. Интерфейсы

### 5.1 Orchestrator API

1. `POST /api/runs`
2. `GET /api/runs/{runId}`
3. `POST /api/runs/{runId}/resume`
4. `POST /api/runs/{runId}/cancel`

### 5.2 Gate API

1. `POST /api/gates/{gateId}/submit-input`
2. `POST /api/gates/{gateId}/approve`
3. `POST /api/gates/{gateId}/request-rework`

### 5.3 Audit API

1. `GET /api/runs/{runId}/audit`
2. `GET /api/runs/{runId}/audit/{eventId}`

### 5.4 Runtime internal interfaces

1. `RunEngine.start(runId)`
2. `RunEngine.tick(runId)`
3. `NodeExecutor.execute(nodeExecutionId)`
4. `GateService.open(nodeExecutionId)`
5. `GateService.submit(gateId, payload, expectedGateVersion)`
6. `TransitionEngine.apply(runId, transition, expectedRunVersion)`
7. `ArtifactService.recordVersion(...)`
8. `AuditService.append(...)`

## 6. Структуры данных (минимум)

### 6.1 Run

Поля:

1. `run_id` (UUID)
2. `project_id`
3. `flow_canonical_name`
4. `flow_snapshot_json`
5. `status` (`created|running|waiting_gate|completed|failed|cancelled`)
6. `current_node_id`
7. `resource_version`
8. `created_at|started_at|finished_at`
9. `created_by`

### 6.2 NodeExecution

Поля:

1. `node_execution_id`
2. `run_id`
3. `node_id`
4. `node_kind`
5. `attempt_no`
6. `status` (`created|running|succeeded|failed|waiting_gate|cancelled`)
7. `started_at|finished_at`
8. `error_code|error_message`
9. `resource_version`

### 6.3 GateInstance

Поля:

1. `gate_id`
2. `run_id`
3. `node_execution_id`
4. `gate_kind` (`human_input|human_approval`)
5. `status` (`awaiting_input|submitted|awaiting_decision|approved|rework_requested|failed_validation|cancelled`)
6. `assignee_role`
7. `payload_json`
8. `resource_version`
9. `opened_at|closed_at`

### 6.4 ArtifactVersion

Поля:

1. `artifact_version_id`
2. `run_id`
3. `node_id`
4. `artifact_key`
5. `path`
6. `scope` (`project|run`)
7. `kind` (`produced|mutation|human_input|system`)
8. `checksum`
9. `size_bytes`
10. `supersedes_artifact_version_id`
11. `created_at`

### 6.5 AuditEvent

Поля:

1. `event_id`
2. `run_id`
3. `node_execution_id` (nullable)
4. `gate_id` (nullable)
5. `event_type`
6. `event_time`
7. `actor_type` (`system|agent|human`)
8. `actor_id`
9. `payload_json`

## 7. State machine

### 7.1 Run states

1. `created -> running`
2. `running -> waiting_gate`
3. `waiting_gate -> running`
4. `running -> completed`
5. `running -> failed`
6. `waiting_gate -> failed`
7. `created|running|waiting_gate -> cancelled`

### 7.2 Node execution states

1. `created -> running`
2. `running -> waiting_gate` (только gate node)
3. `running -> succeeded`
4. `running -> failed`
5. `created|running|waiting_gate -> cancelled`

### 7.3 Gate states

1. `human_input`: `awaiting_input -> submitted|failed_validation|cancelled`
2. `human_approval`: `awaiting_decision -> approved|rework_requested|cancelled`

## 8. Логика исполнения node

### 8.1 Общий алгоритм tick

1. Проверить блокировку run и `resource_version`.
2. Найти `current_node_id`.
3. Создать `NodeExecution(attempt_no+1)`.
4. Выполнить node executor по `node_kind`.
5. Проверить outputs node по конфигурации.
6. Вычислить transition.
7. Обновить `run.current_node_id` или terminal status.
8. Записать audit events.

### 8.2 `ai` node

1. Собрать `execution_context`.
2. Подготовить prompt package.
3. Вызвать coding agent.
4. Синхронизировать созданные/измененные файлы в artifact registry.
5. Проверить `produced_artifacts` и `expected_mutations`.
6. При успехе идти `on_success`, при ошибке `on_failure`.

### 8.3 `command` node

1. Выполнить process в workspace.
2. Собрать результат stdout/stderr/exit code.
3. Проверить декларации outputs.
4. При успехе `on_success`.

### 8.4 `human_input` gate

1. Открыть gate instance.
2. Перевести run в `waiting_gate`.
3. На `submit-input` сохранить входные файлы как `ArtifactVersion(kind=human_input)`.
4. Проверить декларации outputs этой node.
5. При успехе закрыть gate и продолжить по `on_submit`.
6. При неуспехе `failed_validation`, gate остается переоткрываемым.

### 8.5 `human_approval` gate

1. Открыть gate instance.
2. Перевести run в `waiting_gate`.
3. На `approve` перейти по `on_approve`.
4. На `rework` перейти по `on_rework` или `on_rework_routes[mode]`.
5. Зафиксировать comment и reviewed artifact versions.

### 8.6 `terminal` node

1. Нода не имеет transitions.
2. Run переводится в `completed`.

## 9. Проверки конфигурации в рантайме

### 9.1 Проверка `execution_context`

1. Для каждого `file_ref` и `directory_ref` путь должен существовать.
2. Для `artifact_ref` должна существовать последняя `ArtifactVersion` по ключу.
3. Для `required=true` отсутствие контекста приводит к ошибке node.

### 9.2 Проверка `produced_artifacts`

1. Для каждого required entry файл должен существовать по `path` после выполнения node.
2. Каждое найденное значение фиксируется как новая `ArtifactVersion`.
3. Отсутствие required entry завершает node как `failed`.

### 9.3 Проверка `expected_mutations`

1. Для каждой required mutation runtime должен обнаружить фактическое изменение цели (`path`) относительно snapshot до начала node.
2. Если mutation не подтверждена, node завершается `failed`.
3. Подтвержденные mutations пишутся в `ArtifactVersion(kind=mutation)`.

Примечание v1:
Поддерживается только `ALL required`. Логика `OR` между выходами не поддерживается моделью v1.

## 10. Transition policy

1. Transition target обязан существовать в flow snapshot.
2. Для `ai` fallback по ошибке только в `on_failure`.
3. Для gate transition выбирается только после валидного human action.
4. Для `terminal` переход запрещен.

## 11. Аудит (обязательно)

### 11.1 Минимальный набор событий

1. `run_created`
2. `run_started`
3. `node_execution_started`
4. `prompt_package_built` (для `ai`)
5. `agent_invocation_started|agent_invocation_finished` (для `ai`)
6. `command_invocation_started|command_invocation_finished` (для `command`)
7. `artifact_version_created`
8. `node_validation_failed`
9. `node_execution_succeeded|node_execution_failed`
10. `gate_opened`
11. `gate_input_submitted`
12. `gate_approved|gate_rework_requested`
13. `transition_applied`
14. `run_waiting_gate`
15. `run_completed|run_failed|run_cancelled`

### 11.2 Audit payload требования

Payload должен включать:

1. Версии сущностей (`flow_canonical_name`, `node_id`, `node_kind`).
2. Входы node (`execution_context` resolved list).
3. Выходы node (created artifact versions, mutation checks).
4. Gate решения (actor, decision, comment, route).
5. Причину ошибок (`error_code`, `error_message`).
6. Idempotency ключ операции, если применимо.

### 11.3 Нефункциональные требования к audit

1. События неизменяемые, только append.
2. Полный порядок внутри `run_id` по `event_time` + sequence number.
3. Любой state transition обязан сопровождаться audit event.

## 12. Concurrency и idempotency

1. Один активный run на `project_id + target_branch`.
2. Все write-операции с optimistic lock по `resource_version`.
3. Gate submit/approve/rework принимает `expectedGateVersion`.
4. Повторный запрос с тем же idempotency key возвращает тот же результат.

## 13. Recovery semantics

1. При рестарте backend runtime поднимает `running|waiting_gate` runs.
2. Для `waiting_gate` восстановление без повторного открытия gate.
3. Для `running` допускается безопасный retry последнего шага при idempotent границе.
4. После recovery обязателен audit event `run_recovered`.

## 14. Минимальные acceptance criteria

1. Run на flow из 3 node (`ai -> human_input -> ai`) проходит до `completed`.
2. При отсутствии required output run получает `failed`, audit содержит `node_validation_failed`.
3. Gate action с неверной `gate_version` отклоняется с conflict.
4. После рестарта run в `waiting_gate` остается ожидающим и доступным в UI.
5. По `GET /api/runs/{runId}/audit` видна полная цепочка решений и переходов.

## 15. Пример сценария (questions/answers)

1. `ai` node создает `questions.md` и декларирует `produced_artifacts`.
2. `human_input` node собирает ответ и сохраняет `answers.md` как `ArtifactVersion(kind=human_input)`.
3. Runtime проверяет required outputs этой node.
4. Следующая `ai` node получает `questions.md` и `answers.md` через `execution_context` (`artifact_ref`/`file_ref`).

Ограничение v1:
Требование вида "изменен `questions.md` ИЛИ создан `answers.md`" требует расширения модели outputs (например, `any_of` groups) и в v1 не является нативным контрактом.

## 16. API схемы (нормативно)

### 16.1 `POST /api/runs`

Request:

```json
{
  "project_id": "uuid",
  "target_branch": "main",
  "flow_canonical_name": "flow-id@1.3",
  "context_root_dir": "relative/path",
  "feature_request": "string",
  "idempotency_key": "string"
}
```

Response `201`:

```json
{
  "run_id": "uuid",
  "status": "created",
  "flow_canonical_name": "flow-id@1.3",
  "resource_version": 0
}
```

Ошибки:

1. `400` invalid request.
2. `404` project or flow not found.
3. `409` active run already exists for `project_id + target_branch`.
4. `409` idempotency conflict (same key, different payload hash).

### 16.2 `POST /api/gates/{gateId}/submit-input`

Request:

```json
{
  "expected_gate_version": 3,
  "artifacts": [
    {
      "artifact_key": "answers",
      "path": "answers.md",
      "scope": "run",
      "content_base64": "..."
    }
  ],
  "comment": "optional"
}
```

Response `200`:

```json
{
  "gate_id": "uuid",
  "status": "submitted",
  "resource_version": 4,
  "next_run_status": "running"
}
```

Ошибки:

1. `400` invalid payload.
2. `403` actor is not allowed for gate.
3. `404` gate not found.
4. `409` `expected_gate_version` mismatch.
5. `422` validation failed for required outputs.

### 16.3 `POST /api/gates/{gateId}/approve`

Request:

```json
{
  "expected_gate_version": 2,
  "comment": "approved",
  "reviewed_artifact_version_ids": ["uuid", "uuid"]
}
```

Response `200`:

```json
{
  "gate_id": "uuid",
  "status": "approved",
  "resource_version": 3,
  "transition": "on_approve"
}
```

### 16.4 `POST /api/gates/{gateId}/request-rework`

Request:

```json
{
  "expected_gate_version": 2,
  "mode": "requirements",
  "comment": "needs changes",
  "reviewed_artifact_version_ids": ["uuid"]
}
```

Response `200`:

```json
{
  "gate_id": "uuid",
  "status": "rework_requested",
  "resource_version": 3,
  "transition": "on_rework:requirements"
}
```

## 17. Формальные переходы и guards

### 17.1 Run transition rules

1. `created -> running`
Guard:
`run.resource_version` matches expected and no active gate for run.
Side effects:
set `started_at`, append `run_started`.

2. `running -> waiting_gate`
Guard:
current node kind is gate and gate instance created.
Side effects:
set `run.current_node_id` same node until gate resolved.

3. `waiting_gate -> running`
Guard:
gate in terminal decision state (`submitted|approved|rework_requested`) and transition resolved.
Side effects:
advance `current_node_id` to transition target.

4. `running -> completed`
Guard:
current node is terminal and node execution succeeded.

5. `running|waiting_gate -> failed`
Guard:
non-recoverable node error or gate fatal validation policy.

6. `created|running|waiting_gate -> cancelled`
Guard:
explicit cancel command and run not terminal.

### 17.2 Node transition rules

1. `created -> running` only once per `attempt_no`.
2. `running -> succeeded` only if runtime checks passed.
3. `running -> failed` on execution error or failed runtime checks.
4. `running -> waiting_gate` only for gate kinds.

### 17.3 Атомарность

Каждый transition выполняется в одной транзакции:

1. optimistic lock by `resource_version`.
2. state update.
3. audit append.
4. artifact version writes (if any).

## 18. Детерминированные проверки и resolution

### 18.1 Path policy

1. Все пути нормализуются (`/`, `..`, symlink resolution).
2. Путь должен оставаться внутри разрешенного root (`project` scope root или `.hgsdlc/{runId}`).
3. Нарушение policy приводит к `failed` с `error_code=PATH_POLICY_VIOLATION`.

### 18.2 `execution_context` resolution

1. `file_ref`:
resolve absolute path by scope + validate existence.
2. `directory_ref`:
resolve directory + validate existence.
3. `artifact_ref`:
resolve latest `ArtifactVersion` by `artifact_key` and optional scope.
4. `user_request`:
берется только из `run.feature_request`.

Правило приоритета:

1. explicit scope in node entry.
2. default scope from node schema.
3. если ambiguity, ошибка `CONTEXT_AMBIGUOUS`.

### 18.3 Mutation detection (`expected_mutations`)

Алгоритм:

1. До node execution снять `pre_snapshot` для каждого configured `path`:
`exists`, `checksum`, `size`, `mtime`.
2. После node execution снять `post_snapshot`.
3. Mutation считается подтвержденной, если:
path existed both and checksum changed, или file created/deleted when policy allows it.
4. Rename не поддерживается как отдельный first-class event v1 и трактуется как delete+create.

### 18.4 Produced artifacts

1. Required artifact должен существовать после node execution.
2. Если файл существует и checksum совпадает с предыдущей версией, новая версия все равно создается с link на `supersedes_artifact_version_id`.
3. `artifact_key` uniqueness:
latest uniqueness на `(run_id, artifact_key, scope)` поддерживается через pointer на latest version.

## 19. Audit schema и операционные требования

### 19.1 Обязательные поля audit event

```json
{
  "event_id": "uuid",
  "run_id": "uuid",
  "sequence_no": 124,
  "event_type": "node_execution_started",
  "event_time": "2026-03-17T01:00:00Z",
  "actor_type": "system|agent|human",
  "actor_id": "string",
  "correlation_id": "uuid",
  "idempotency_key": "string|null",
  "payload_json": {}
}
```

### 19.2 Sequence semantics

1. `sequence_no` монотонен внутри `run_id`.
2. `(run_id, sequence_no)` уникален.
3. `event_time` не используется как единственный источник порядка.

### 19.3 Payload contract per event type

Минимально фиксируются:

1. `node_execution_started`:
`node_id`, `node_kind`, `attempt_no`, `resolved_context`.
2. `artifact_version_created`:
`artifact_version_id`, `artifact_key`, `path`, `scope`, `checksum`, `supersedes`.
3. `node_validation_failed`:
`check_type`, `path`, `expected`, `actual`.
4. `gate_*`:
`gate_id`, `decision`, `comment`, `reviewed_artifact_version_ids`.
5. `transition_applied`:
`from_node_id`, `transition`, `to_node_id`.

### 19.4 Retention и доступ

1. Audit immutable append-only.
2. Retention по умолчанию бессрочный для production.
3. API `/audit` поддерживает `limit`, `offset`, `event_type`, `from_time`, `to_time`.

## 20. Retry/timeout/cancel policy

### 20.1 AI node

1. `max_attempts` default `1` (в v1 без авто-retry по умолчанию).
2. `timeout_seconds` default `1800`.
3. Timeout -> `node_execution_failed` с `error_code=AI_TIMEOUT`.

### 20.2 Command node

1. `timeout_seconds` default `900`.
2. Non-zero exit code -> `failed` unless node config explicitly allows code list.
3. stdout/stderr сохраняются в audit payload с size cap.

### 20.3 Cancel semantics

1. Cancel выставляет `run=cancelled`, active `node_execution/gate` тоже переводятся в `cancelled`.
2. Если внешний процесс еще жив, отправляется stop signal.
3. После cancel никакие transitions не выполняются.

## 21. DDL ограничения и индексы (минимум)

### 21.1 Уникальности и FK

1. `runs(run_id)` PK.
2. `node_executions(node_execution_id)` PK, FK `run_id -> runs`.
3. `gate_instances(gate_id)` PK, FK `node_execution_id -> node_executions`.
4. `artifact_versions(artifact_version_id)` PK, FK `run_id -> runs`.
5. `audit_events(event_id)` PK, FK `run_id -> runs`.

### 21.2 Бизнес ограничения

1. Partial unique index для одного активного run:
`UNIQUE(project_id, target_branch) WHERE status IN ('created','running','waiting_gate')`.
2. `UNIQUE(run_id, sequence_no)` для audit.
3. `UNIQUE(run_id, node_id, attempt_no)` для node executions.

### 21.3 Индексы производительности

1. `audit_events(run_id, event_time desc)`.
2. `artifact_versions(run_id, artifact_key, created_at desc)`.
3. `node_executions(run_id, started_at desc)`.

## 22. RBAC для gate actions

1. `submit-input` разрешен только ролям, указанным в gate policy (`PRODUCT_OWNER` или explicit assignee).
2. `approve|request-rework` разрешены только `TECH_APPROVER` или указанной approval role flow.
3. Нарушение доступа логируется отдельным audit event `gate_action_denied`.
