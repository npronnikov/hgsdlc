# Runtime: актуальная механика переходов и построения prompt

Документ отражает текущее поведение после рефакторинга runtime (router + executors + publish pipeline).

Ключевые классы:
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunStepService.java`
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/NodeExecutionRouter.java`
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/GateDecisionService.java`
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunLifecycleService.java`
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunPublishService.java`
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/AgentPromptBuilder.java`
- `backend/src/main/java/ru/hgd/sdlc/flow/application/FlowValidator.java`

---

## 1. Архитектура исполнения (что поменялось)

Сейчас `RuntimeService` — thin facade, а логика разделена:
- `RunLifecycleService`: create/start/resume/cancel/recover, checkout workspace.
- `RunStepService`: tick loop и исполнение текущей node.
- `NodeExecutionRouter` + `NodeExecutor`: маршрутизация по `node_kind` (`ai`, `command`, `human_input`, `human_approval`, `terminal`).
- `GateDecisionService`: `submit-input`, `approve`, `request-rework`.
- `RunPublishService`: post-terminal публикация (`LOCAL`/`PR`) и retry.

---

## 2. Полный список статусов и переходов run

Текущие `RunStatus`:
- `CREATED`
- `RUNNING`
- `WAITING_GATE`
- `WAITING_PUBLISH`
- `PUBLISH_FAILED`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

### 2.1 Жизненный цикл

1. `createRun` -> `CREATED`
2. `startRun` -> `RUNNING` (после `markRunStarted`)
3. Во время исполнения node:
- gate-node -> `WAITING_GATE`
- executor-node -> остаётся `RUNNING` и двигается по transition
4. На terminal:
- если ветка пришла через `on_failure` -> `FAILED`
- иначе -> `WAITING_PUBLISH` и запускается publish pipeline
5. Publish:
- успех -> `COMPLETED`
- ошибка -> `PUBLISH_FAILED`
- retry publish -> обратно в `WAITING_PUBLISH` -> снова pipeline
6. В любой активной фазе user cancel -> `CANCELLED`

---

## 3. Переходы между node (все сценарии)

## 3.1 Поддерживаемые маршруты по типу node

- `ai`
  - `on_success` (обязателен валидатором)
  - `on_failure` (обязателен валидатором для ai)
- `command`
  - `on_success` (обязателен)
- `human_input`
  - `on_submit` (обязателен)
- `human_approval`
  - `on_approve` (обязателен)
  - `on_rework.next_node` (обязателен)
- `terminal`
  - переходов нет

### 3.2 Что делает runtime на ошибке node

- Любая ошибка внутри node -> `markNodeExecutionFailed(...)`.
- Спец-ветка для AI:
  - если есть `on_failure`, runtime применяет `transition_applied` с `transition=on_failure`.
  - иначе run падает в `FAILED`.
- Для `command`/gate/terminal fallback-а на `on_failure` нет.

### 3.3 Валидация target перехода

Любой transition проходит через проверку:
- target пуст -> `INVALID_TRANSITION`
- target не найден в snapshot flow -> `INVALID_TRANSITION`

---

## 4. Сценарии по node_kind

## 4.1 `ai`

Поток:
1. `resolveExecutionContext(...)`
2. materialize агентского workspace (`QwenCodingAgentStrategy`)
3. build prompt (`AgentPromptBuilder`)
4. запуск агента (`qwen ...`)
5. `validateNodeOutputs(...)`
6. `node_execution_succeeded` + transition `on_success`

Важные ошибки:
- `CODING_AGENT_MISMATCH`
- `UNSUPPORTED_CODING_AGENT`
- `MISSING_EXECUTION_CONTEXT`
- `EXECUTION_CONTEXT_TOO_LARGE`
- `AGENT_EXECUTION_FAILED`
- `NODE_VALIDATION_FAILED`

## 4.2 `command`

Поток:
1. `resolveExecutionContext(...)`
2. запуск `zsh -lc <instruction>`
3. materialize declared artifacts (markdown-заглушка для `produced_artifacts`)
4. `validateNodeOutputs(...)`
5. `node_execution_succeeded` + transition `on_success`

Важные ошибки:
- `COMMAND_EXECUTION_FAILED`
- `NODE_VALIDATION_FAILED`

## 4.3 `human_input`

Поток открытия gate:
1. runtime копирует modifiable run-artifacts из `execution_context` в текущий `attempt-N` директории node
2. открывает gate `AWAITING_INPUT`, run -> `WAITING_GATE`

Поток submit:
1. проверка версии gate и ролей
2. проверка что отправлены только разрешённые артефакты
3. запись контента, версия артефактов `kind=human_input`
4. валидация required:
- файл существует
- не пустой
- checksum изменился
5. success -> `on_submit`
6. fail -> gate `FAILED_VALIDATION`

## 4.4 `human_approval`

Поток approve:
- `gate_approved` -> `on_approve`

Поток rework:
- вычисляется target = `on_rework.next_node`
- параметр API: `keep_changes` (Boolean)
- если `keep_changes=false` -> rollback к checkpoint + `git clean -fd`
- если `keep_changes=true` -> откат не выполняется
- если requested discard, но checkpoint фактически недоступен -> `ValidationException` (`REWORK_RESET_FAILED` / `CHECKPOINT_NOT_FOUND_FOR_REWORK`)
- instruction:
  - если rework target == `start_node_id`: instruction добавляется в `feature_request`
  - иначе кладётся в `pending_rework_instruction`
- затем `on_rework`

## 4.5 `terminal`

`terminal` больше не завершает run напрямую (в happy-path).

Логика:
- если последний transition = `on_failure` -> run становится `FAILED`
- иначе:
  - run -> `WAITING_PUBLISH`
  - запускается `RunPublishService.dispatchPublish(...)`

---

## 5. Артефакты и execution_context (все текущие варианты)

Поддерживаемые `execution_context.type` runtime-исполнением:
- `user_request`
- `artifact_ref`

`artifact_ref`:
- `scope=project` -> файл из project root
- `scope=run` -> файл из последней `SUCCEEDED` попытки source node (`node_id` обязателен)

`transfer_mode`:
- `by_ref` (default)
- `by_value` (встраивание контента в context/prompt, лимит `64KB`)

Поведение required:
- required artifact отсутствует -> `MISSING_EXECUTION_CONTEXT`

`produced_artifacts` и `expected_mutations`:
- required produced missing -> `NODE_VALIDATION_FAILED`
- required mutation not changed -> `NODE_VALIDATION_FAILED`
- при изменениях/наличии пишутся версии артефактов (`produced` / `mutation` / `human_input`)

---

## 6. Gate payload (что реально отдаётся фронту)

При открытии gate payload включает:
- `git_changes` + `git_summary`
- для `human_input`: `human_input_artifacts`
- для `human_approval`: execution context artifacts + данные rework-режима

### 6.1 Rework payload поля (новое)

Для `human_approval` runtime отдаёт:
- `rework_mode`
- `rework_keep_changes`
- `rework_keep_changes_selectable`
- `rework_discard_available`
- `rework_discard_unavailable_reason` (если есть)

Коды причин недоступности discard:
- `rework_target_missing`
- `rework_target_kind_unsupported`
- `rework_target_checkpoint_disabled`
- `target_checkpoint_not_found`

---

## 7. Publish pipeline после terminal

## 7.1 Режимы

- `publish_mode=local`
- `publish_mode=pr`

## 7.2 Этапы

1. `publish_started`
2. final commit (`publish_commit_succeeded` / `publish_commit_skipped`)
3. для PR режима:
- push branch (`publish_push_succeeded`)
- create/find PR (`publish_pr_succeeded`)
4. финал:
- успех -> `run_completed`, `COMPLETED`
- ошибка -> `publish_failed`, `PUBLISH_FAILED`

Retry доступен только из `PUBLISH_FAILED` или `WAITING_PUBLISH`.

---

## 8. Как строится prompt сейчас (фактический алгоритм)

Шаблон:
- `backend/src/main/resources/runtime/prompt-template.md`

Секции:
- `{{TASK_SECTION}}`
- `{{REQUEST_CLARIFICATION_SECTION}}`
- `{{NODE_INSTRUCTION_SECTION}}`
- `{{INPUTS_SECTION}}`
- `{{EXPECTED_RESULTS_SECTION}}`
- `{{FOOTER_SECTION}}`

Тексты секций:
- `backend/src/main/resources/runtime/prompt-texts.ru.yaml`

### 8.1 Источники полей

`AgentInput`:
- `task` <- `run.featureRequest`
- `requestClarification` <- `run.pendingReworkInstruction`
- `nodeInstruction` <- `node.instruction`
- `inputs` <- `resolvedContext` (`artifact_ref`)
- `expectedResults` <- required outputs/mutations + summary

### 8.2 Включение секций

- `Task` только если есть `featureRequest`
- `Request clarification` только если есть pending rework instruction
- `Instruction` только если есть `node.instruction`
- `Available inputs` только если есть input items
- `Expected result` только если есть expected items
- Footer всегда

### 8.3 Что попадает в `inputs`

Только `artifact_ref`:
- by_ref: инструкция использовать путь/ключ+путь
- by_value: inline content с размером

`user_request` не попадает в `inputs`, потому что уже представлен как `Task`.

### 8.4 Что попадает в `expectedResults`

- required artifacts (`output_artifact` + required `produced_artifacts`)
- required run-scope paths (`.hgsdlc/nodes/<node>/attempt-<n>/...`)
- required mutations
- summary (всегда)

### 8.5 Нормализация

- `\r\n -> \n`
- 3+ пустых строк -> 2

---

## 9. Матрица «событие -> результат»

- AI успех -> `on_success`
- AI ошибка -> `on_failure` (если задан), иначе `FAILED`
- Command успех -> `on_success`
- Command ошибка -> `FAILED`
- Human input submit valid -> `on_submit`
- Human input submit invalid -> gate `FAILED_VALIDATION`
- Human approval approve -> `on_approve`
- Human approval rework keep -> `on_rework`, без rollback
- Human approval rework discard -> `on_rework`, с `git reset --hard` + `git clean -fd`
- Human approval rework discard без checkpoint -> ошибка в API, gate не переводится дальше
- Terminal после failure-ветки -> `FAILED`
- Terminal после success-ветки -> `WAITING_PUBLISH` -> `COMPLETED` или `PUBLISH_FAILED`

---

## 10. Что покрыто тестами (ключевые сценарии)

- `RuntimeRegressionFlowTest`:
  - ai success/failure
  - human_input submit
  - human_approval rework+approve
  - terminal + publish fail/retry
- `RuntimeGateDecisionServiceTest`:
  - version mismatch для submit/approve/rework
  - required human_input artifact must be modified
  - rework keep/discard фактически меняет/не меняет workspace

---

## 11. Улучшения (предлагаемые, приоритетно)

1. Ввести базовый системный prompt (обязательный префикс)

Сейчас prompt строится только из секций task/instruction/inputs/results + footer. Нет стабильной системной «рамы поведения». Рекомендуется добавить фиксированный системный блок в шаблон, например:

```text
Ты — система разработки ПО в runtime flow.
Работай строго в рамках текущей node и её переходов.
Считай execution_context единственным источником входных артефактов.
Не придумывай отсутствующие данные; при нехватке используй минимально безопасное действие.
Строго выполни expected results этой node.
Не меняй файлы вне разрешённых scope/path.
Верни краткий отчёт о результате и проверках.
```

2. Сделать prompt-builder детерминированным по режимам node

Добавить явный policy слой: `PromptPolicy` по `node_kind` + сценарий (`start`, `rework`, `normal`) с жёсткими правилами секций. Сейчас структура формируется условно и может быть недостаточно контролируемой.

3. Явно сериализовать «контракт node» в prompt

Добавлять машинно-стабильный блок (например YAML/JSON) со структурой:
- `node_id`, `node_kind`, `allowed_transitions`
- `required_outputs`, `required_mutations`
- `scope_policy`
Это снизит неоднозначность поведения агента.

4. Разделить human-review instruction и AI instruction

Сейчас используется `node.instruction` в разных контекстах (prompt и gate payload). Рекомендуется завести отдельные поля:
- `agent_instruction`
- `reviewer_instruction`
Чтобы не смешивать требования к агенту и человеку.

5. Расширить тестовый flow под контролируемый prompt

Сделать отдельный regression flow, где проверяется:
- start-node prompt с базовым системным блоком
- rework-переходы (target=start и target!=start)
- `by_ref` и `by_value`
- обязательные expected results
- неизменность формата prompt по snapshot-тесту

6. Добавить snapshot-тесты prompt на уровне builder

Тестировать `AgentPromptBuilder` таблицей кейсов:
- без контекста
- с `pending_rework_instruction`
- с `artifact_ref by_value`
- с required run-paths/mutations
и фиксировать финальный prompt текстом (golden files).
