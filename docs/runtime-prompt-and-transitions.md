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

Важно: часть итогового prompt берётся не из flow/run, а из runtime-настроек локализации prompt.
Это именно файл `prompt-texts.ru.yaml`, из которого `AgentPromptBuilder` читает:
- заголовки секций (`Task`, `Instruction`, `Available inputs`, ...)
- footer
- текстовые шаблоны для `inputs`
- текстовые шаблоны для `expected_results`

Если в `prompt-texts.ru.yaml` отсутствует любой обязательный ключ или он пустой, builder падает при загрузке с `IllegalStateException` (prompt не будет собран).

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

### 8.6 Какие части prompt приходят из `prompt-texts.ru.yaml`

Из блока `sections`:
- `task_header` -> строка перед `run.featureRequest`
- `request_clarification_header` -> строка перед `pending_rework_instruction`
- `node_instruction_header` -> строка перед `node.instruction`
- `inputs_header` -> заголовок списка input-ов
- `expected_results_header` -> заголовок списка expected results
- `footer` -> финальная строка prompt (всегда)

Из блока `inputs`:
- `use_upstream_artifact_by_path`
- `use_upstream_artifact_by_key_and_path`
- `use_upstream_artifact_by_value`

Runtime подставляет плейсхолдеры:
- `{artifact_key}`
- `{path}`
- `{size_bytes}`
- `{content}`

Из блока `expected_results`:
- `required_artifacts` (с `{artifacts}`)
- `required_run_paths` (с `{paths}`)
- `required_mutations`
- `summary`

Следствие: изменение `prompt-texts.ru.yaml` меняет итоговый prompt глобально для всех AI-node без изменения flow YAML.

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

## 11. Сквозной пример flow разработки ПО (с полными prompt)

Ниже пример end-to-end flow под запрос пользователя:
`Добавь новую кнопку в интерфейс`.

Пример flow (логические node):
1. `ai-analyze-request` (AI): «Проанализируй запрос пользователя и задай 5 релевантных вопросов»
2. `human-answer-questions` (human_input gate): пользователь отвечает на вопросы
3. `ai-write-requirements` (AI): «На основании запроса пользователя и ответов напиши требования»
4. `ai-build-plan` (AI): «На базе требований создай план реализации»
5. `approve-plan` (human_approval gate)
6. `ai-implement` (AI): «Разработай функционал согласно запросу пользователя, требованиям и плану»
7. `approve-implementation` (human_approval gate, с rework)
8. `terminal-complete`

### 11.1 Принятые допущения по артефактам и transfer_mode

- `ai-analyze-request` создаёт `questions.md` (`scope=run`, `required=true`, `modifiable=true`).
- `human-answer-questions` редактирует `questions.md` и отдаёт обновлённый файл.
- В `ai-write-requirements` файл ответов передаётся **по ссылке** (`transfer_mode=by_ref`).
- Далее между AI-нодами артефакты передаются **по значению** (`by_value`):
  - `requirements.md` -> `ai-build-plan`
  - `requirements.md` и `plan.md` -> `ai-implement`
- Для `ai-implement` есть `expected_mutations`:
  - `scope=project`, `path=frontend/src/components/ActionButton.tsx`, `required=true`

### 11.2 Node 1: `ai-analyze-request` — полный итоговый prompt

`execution_context: []`, поэтому `Available inputs` отсутствует.

````text
[source: TASK_SECTION | sections.task_header + run.featureRequest]
Task:
Добавь новую кнопку в интерфейс

[source: NODE_INSTRUCTION_SECTION | sections.node_instruction_header + node.instruction]
Instruction:
Проанализируй запрос пользователя и задай 5 релевантных вопросов.
Сохрани результат в questions.md.

[source: EXPECTED_RESULTS_SECTION | sections.expected_results_header + expected_results.required_artifacts + expected_results.required_run_paths + expected_results.summary]
Expected result:
- Create and fill required artifacts: questions.
- Write generated artifacts strictly to these paths: .hgsdlc/nodes/ai-analyze-request/attempt-1/questions.md. Before finishing, verify each file exists and is not empty.
- Return a brief summary of completed work.

[source: FOOTER_SECTION | sections.footer]
Use repository rules and available skills.
````

### 11.3 Node 2: `human-answer-questions` — prompt не строится

Для `human_input` runtime prompt не генерирует. Открывается gate с payload:
- `human_input_artifacts` (редактируемая копия `questions.md`)
- `git_summary`/`git_changes`
- `user_instructions` из `node.instruction`

### 11.4 Node 3: `ai-write-requirements` (ответы по ссылке by_ref) — полный итоговый prompt

Предположим `execution_context` содержит `artifact_ref` на `questions.md` из `human-answer-questions`, `transfer_mode=by_ref`, `required=true`.

````text
[source: TASK_SECTION | sections.task_header + run.featureRequest]
Task:
Добавь новую кнопку в интерфейс

[source: NODE_INSTRUCTION_SECTION | sections.node_instruction_header + node.instruction]
Instruction:
На основании запроса пользователя и ответов напиши требования.
Сохрани требования в requirements.md.

[source: INPUTS_SECTION | sections.inputs_header + inputs.use_upstream_artifact_by_key_and_path + resolvedContext.artifact_ref]
Available inputs:
- Use the input artifact 'questions' by full path '/workspace/<run-id>/.hgsdlc/nodes/human-answer-questions/attempt-1/questions.md'.

[source: EXPECTED_RESULTS_SECTION | sections.expected_results_header + expected_results.required_artifacts + expected_results.required_run_paths + expected_results.summary]
Expected result:
- Create and fill required artifacts: requirements.
- Write generated artifacts strictly to these paths: .hgsdlc/nodes/ai-write-requirements/attempt-1/requirements.md. Before finishing, verify each file exists and is not empty.
- Return a brief summary of completed work.

[source: FOOTER_SECTION | sections.footer]
Use repository rules and available skills.
````

Почему контент не в prompt: для `by_ref` builder добавляет путь, но не inline содержимое.

### 11.5 Node 4: `ai-build-plan` (requirements по значению by_value) — полный итоговый prompt

Предположим контент `requirements.md`:
`- Добавить кнопку "Отправить"\n- Расположить справа от поля ввода\n- Поддержать disabled/loading`.

````text
[source: TASK_SECTION | sections.task_header + run.featureRequest]
Task:
Добавь новую кнопку в интерфейс

[source: NODE_INSTRUCTION_SECTION | sections.node_instruction_header + node.instruction]
Instruction:
На базе требований создай план реализации.
Сохрани план в plan.md.

[source: INPUTS_SECTION | sections.inputs_header + inputs.use_upstream_artifact_by_value + resolvedContext.artifact_ref]
Available inputs:
- Use inline artifact content 'requirements' (path '/workspace/<run-id>/.hgsdlc/nodes/ai-write-requirements/attempt-1/requirements.md', size 112 bytes):
```text
- Добавить кнопку "Отправить"
- Расположить справа от поля ввода
- Поддержать disabled/loading
```

[source: EXPECTED_RESULTS_SECTION | sections.expected_results_header + expected_results.required_artifacts + expected_results.required_run_paths + expected_results.summary]
Expected result:
- Create and fill required artifacts: plan.
- Write generated artifacts strictly to these paths: .hgsdlc/nodes/ai-build-plan/attempt-1/plan.md. Before finishing, verify each file exists and is not empty.
- Return a brief summary of completed work.

[source: FOOTER_SECTION | sections.footer]
Use repository rules and available skills.
````

Что важно: для `by_value` в prompt попадает inline контент и размер (`size_bytes`); при размере > 64KB будет `EXECUTION_CONTEXT_TOO_LARGE`.

### 11.6 Node 5: `approve-plan` — prompt не строится

Для `human_approval` runtime prompt не генерирует.

### 11.7 Node 6: `ai-implement` (requirements+plan по значению) — полный итоговый prompt

Предположим `execution_context`: `requirements.md` by_value и `plan.md` by_value.

````text
[source: TASK_SECTION | sections.task_header + run.featureRequest]
Task:
Добавь новую кнопку в интерфейс

[source: NODE_INSTRUCTION_SECTION | sections.node_instruction_header + node.instruction]
Instruction:
Разработай функционал согласно запросу пользователя, требованиям и плану.

[source: INPUTS_SECTION | sections.inputs_header + inputs.use_upstream_artifact_by_value + resolvedContext.artifact_ref]
Available inputs:
- Use inline artifact content 'requirements' (path '/workspace/<run-id>/.hgsdlc/nodes/ai-write-requirements/attempt-1/requirements.md', size 112 bytes):
```text
- Добавить кнопку "Отправить"
- Расположить справа от поля ввода
- Поддержать disabled/loading
```
- Use inline artifact content 'plan' (path '/workspace/<run-id>/.hgsdlc/nodes/ai-build-plan/attempt-1/plan.md', size 167 bytes):
```text
1. Обновить компонент формы.
2. Добавить кнопку ActionButton.
3. Добавить состояния disabled/loading.
4. Обновить тесты UI.
```

[source: EXPECTED_RESULTS_SECTION | sections.expected_results_header + expected_results.required_mutations + expected_results.summary]
Expected result:
- Apply repository changes required for this task.
- Return a brief summary of completed work.

[source: FOOTER_SECTION | sections.footer]
Use repository rules and available skills.
````

### 11.8 Node 7: `approve-implementation` (rework с комментарием к строкам 1-10) — prompt не строится

Пользователь отправляет rework instruction: `Переведи данный текст на русский` (к строкам 1-10 выбранного файла).

### 11.9 Повтор Node 6 после rework — полный итоговый prompt (attempt-2)

Ключевое отличие: появляется секция clarification.

````text
[source: TASK_SECTION | sections.task_header + run.featureRequest]
Task:
Добавь новую кнопку в интерфейс

[source: REQUEST_CLARIFICATION_SECTION | sections.request_clarification_header + run.pendingReworkInstruction]
Request clarification:
Файл frontend/src/components/ActionButton.tsx, строки 1-10: Переведи данный текст на русский.

[source: NODE_INSTRUCTION_SECTION | sections.node_instruction_header + node.instruction]
Instruction:
Разработай функционал согласно запросу пользователя, требованиям и плану.

[source: INPUTS_SECTION | sections.inputs_header + inputs.use_upstream_artifact_by_value + resolvedContext.artifact_ref]
Available inputs:
- Use inline artifact content 'requirements' (path '/workspace/<run-id>/.hgsdlc/nodes/ai-write-requirements/attempt-1/requirements.md', size 112 bytes):
```text
- Добавить кнопку "Отправить"
- Расположить справа от поля ввода
- Поддержать disabled/loading
```
- Use inline artifact content 'plan' (path '/workspace/<run-id>/.hgsdlc/nodes/ai-build-plan/attempt-1/plan.md', size 167 bytes):
```text
1. Обновить компонент формы.
2. Добавить кнопку ActionButton.
3. Добавить состояния disabled/loading.
4. Обновить тесты UI.
```

[source: EXPECTED_RESULTS_SECTION | sections.expected_results_header + expected_results.required_mutations + expected_results.summary]
Expected result:
- Apply repository changes required for this task.
- Return a brief summary of completed work.

[source: FOOTER_SECTION | sections.footer]
Use repository rules and available skills.
````

### 11.10 Node 8: `terminal-complete`

Prompt не строится. Node переводит run в `WAITING_PUBLISH` (или `FAILED`, если terminal достигнут после `on_failure`), далее publish pipeline.

## 12. Наглядный алгоритм формирования prompt

### 12.1 Пошаговый pipeline builder

1. Берётся шаблон `prompt-template.md` с токенами секций.
2. Загружаются текстовые настройки из `prompt-texts.ru.yaml` (headers/footer/inputs/expected_results шаблоны).
3. Формируется `AgentInput`:
- `task` = `run.featureRequest`
- `requestClarification` = `run.pendingReworkInstruction`
- `nodeInstruction` = `node.instruction`
- `inputs` = summary `artifact_ref` из `resolvedContext`
- `expectedResults` = required outputs/mutations + summary
4. Для каждой секции проверяется условие включения.
5. Токены в шаблоне заменяются текстом секций.
6. Нормализация: `\r\n -> \n`, затем схлопывание лишних пустых строк.
7. Считается `sha256(prompt)` и пишется audit `prompt_package_built`.

### 12.2 Визуальная схема (что включится)

```text
TASK_SECTION                  <- featureRequest != null
REQUEST_CLARIFICATION_SECTION <- pendingReworkInstruction != null
NODE_INSTRUCTION_SECTION      <- node.instruction != null
INPUTS_SECTION                <- resolvedContext has artifact_ref entries
EXPECTED_RESULTS_SECTION      <- always at least summary (для AI node)
FOOTER_SECTION                <- always
```

### 12.3 Правила by_ref vs by_value

- `by_ref`: в prompt попадает только путь.
- `by_value`: в prompt попадают путь + размер + полный inline контент.
- `by_value` полезен для точного контекста, но риск:
  - переполнение лимита `64KB`;
  - избыточный prompt-size.

### 12.4 Почему в gate-нодах нет prompt

- `human_input` и `human_approval` не используют `AgentPromptBuilder`.
- Вместо этого runtime открывает gate и передаёт payload для UI/reviewer.
- Следующий prompt появится только когда flow перейдёт в AI node.

---

## 13. Целевая архитектура prompt (v2)

Цель — сделать prompt структурированным, с явными приоритетами, чистым форматом ввода/вывода и накапливаемым контекстом между шагами.

### 13.1 Новый шаблон (целевой prompt-template.md)

```text
{{SYSTEM_INTRO_SECTION}}
{{WORKFLOW_PROGRESS_SECTION}}
{{CONTEXT_SECTION}}
{{TASK_SECTION}}
{{INPUTS_SECTION}}
{{EXPECTED_OUTPUTS_SECTION}}
{{STRUCTURED_OUTPUT_SECTION}}
{{FOOTER_SECTION}}
```

Сравнение со старым шаблоном:

| Старый токен | Новый токен | Изменение |
|---|---|---|
| `{{TASK_SECTION}}` | `{{CONTEXT_SECTION}}` | Переименован; содержит только `featureRequest` как фоновый контекст |
| `{{REQUEST_CLARIFICATION_SECTION}}` | убран | Поглощён `{{TASK_SECTION}}` |
| `{{NODE_INSTRUCTION_SECTION}}` | `{{TASK_SECTION}}` | Стал основной задачей; объединяет instruction и rework |
| `{{INPUTS_SECTION}}` | `{{INPUTS_SECTION}}` | Формат упрощён |
| `{{EXPECTED_RESULTS_SECTION}}` | `{{EXPECTED_OUTPUTS_SECTION}}` | Формат упрощён; файлы и мутации разделены |
| — | `{{SYSTEM_INTRO_SECTION}}` | Новый; статичный |
| — | `{{WORKFLOW_PROGRESS_SECTION}}` | Новый; summary прошлых шагов из runtime |
| — | `{{STRUCTURED_OUTPUT_SECTION}}` | Новый; инструкция по формату вывода |
| `{{FOOTER_SECTION}}` | `{{FOOTER_SECTION}}` | Без изменений |

---

### 13.2 SYSTEM_INTRO_SECTION — системная вводная (всегда)

Статичный текст, всегда первый. Источник: ключ `sections.system_intro` в `prompt-texts.ru.yaml`.

Целевой текст секции:

```text
You are an automated software development system executing a single step in a multi-step development flow.

Terms:
- User Request: the original goal for this flow run (background context only).
- Node Instruction: the specification for this step.
- Rework Task: a reviewer correction — present only when the previous result was rejected.
- Input Files: files provided for this step.
- Output Files: files you must produce as the step result.
- Project Changes: repository file modifications required by this step.
- Step Summary: a structured report you must produce after completing work.

Execution rules:
1. Your primary task is the Rework Task (if present), otherwise the Node Instruction.
2. The User Request is background context — stay aligned with it, but do not re-execute it.
3. Use only the provided Input Files. Do not invent or assume missing data.
4. Produce only the listed Output Files and Project Changes — no extra files.
5. If a Rework Task is present: address it first, then verify the Node Instruction is still satisfied.
6. If required data is missing: note it in the Step Summary and produce the safest minimal result.
```

---

### 13.3 WORKFLOW_PROGRESS_SECTION — прогресс флоу

Присутствует если есть хотя бы один завершённый AI-шаг с сохранённым summary.

Источник данных: `node_execution.step_summary_json` всех предшествующих AI-нод этого run с `status=SUCCEEDED`, в порядке выполнения.

Целевой формат:

```text
Workflow progress — you are on step {N} ({current_step_id}):
- Step 1 — {step_id}: {one-liner из actions[0]}
- Step 2 — {step_id}: {one-liner из actions[0]}
```

Ключи в yaml: `sections.workflow_progress_header` (с плейсхолдерами `{step_no}`, `{step_id}`), `sections.workflow_progress_step` (с плейсхолдерами `{step_no}`, `{step_id}`, `{summary}`).

Если у шага нет сохранённого summary — шаг пропускается (graceful degradation).

---

### 13.4 CONTEXT_SECTION — запрос пользователя (фоновый контекст)

Присутствует только если `featureRequest != null`.

Источник: `sections.context_header` + `run.featureRequest`.

Формат:

```text
User Request:
{featureRequest}
```

---

### 13.5 TASK_SECTION — задача шага (ключевое изменение)

**Задача шага = Rework Task если есть, иначе Node Instruction.**

Логика формирования секции:

Если `pendingReworkInstruction == null` (нормальный запуск):
```text
Task:
{node.instruction}
```

Если `pendingReworkInstruction != null` (повтор после rework):
```text
Rework Task:
{pendingReworkInstruction}

Node Instruction (context):
{node.instruction}
```

Rework Task — основная задача попытки. Node Instruction показывается как контекст, чтобы агент не вышел за область ответственности шага.

Ключи в yaml: `sections.task_header`, `sections.rework_task_header`, `sections.node_instruction_context_header`.

---

### 13.6 INPUTS_SECTION — входные файлы (упрощённый формат)

Присутствует только если есть `artifact_ref` в `resolvedContext`.

Источник: `sections.inputs_header` + шаблоны из блока `inputs`.

Целевой формат:

```text
Input files:
- {artifact_key}: {path}
- {artifact_key} ({size_bytes} bytes):
```text
{content}
```
```

Изменения в yaml (блок `inputs`):

```yaml
inputs:
  use_upstream_artifact_by_path: "{path}"
  use_upstream_artifact_by_key_and_path: "{artifact_key}: {path}"
  use_upstream_artifact_by_value: "{artifact_key} ({size_bytes} bytes):\n```text\n{content}\n```"
```

---

### 13.7 EXPECTED_OUTPUTS_SECTION — ожидаемые результаты (упрощённый формат)

Заменяет старый `EXPECTED_RESULTS_SECTION`. Выводятся только конкретные пути — без обёрток типа «Create and fill required artifacts».

Секция `Output files:` — run-scope пути required `produced_artifacts` (через `runScopeArtifactPath()`). Выводится только если есть такие пути.

Секция `Project changes:` — пути required `expected_mutations`. Выводится только если есть required мутации.

Целевой формат:

```text
Output files:
- .hgsdlc/nodes/{node_id}/attempt-{N}/{path}

Project changes:
- {project-scoped path}
```

Если нет ни output files, ни project changes — `EXPECTED_OUTPUTS_SECTION` пустой (summary переходит в `STRUCTURED_OUTPUT_SECTION`).

Поле `output_artifact` на ноде: устаревает — не даёт пути, не может быть показано в упрощённом формате. Следует использовать `produced_artifacts` с явными путями.

Ключи в yaml: `sections.output_files_header`, `sections.project_changes_header`.
Удалить старые ключи из блока `expected_results`: `required_artifacts`, `required_run_paths`, `required_mutations`, `summary`.

---

### 13.8 STRUCTURED_OUTPUT_SECTION — структурированный вывод (всегда)

Всегда присутствует для AI-нод. Статичный шаблон с плейсхолдерами `{step_id}` и `{attempt_no}`, которые runtime подставляет при рендере.

Источник: `sections.structured_output` в yaml.

Целевой текст секции:

````text
After completing your work, output a step summary in this exact format:

STEP_SUMMARY:
```json
{
  "step_id": "{step_id}",
  "attempt": {attempt_no},
  "status": "done",
  "actions": ["describe each main action taken"],
  "output_files": ["relative/path/to/produced/file"],
  "project_changes": ["relative/path/to/changed/file"],
  "issues": []
}
```
````

Требования к формату:
- Фиксированный префикс `STEP_SUMMARY:` перед JSON-блоком.
- Валидный JSON.
- Обязательные поля: `step_id`, `attempt`, `status`, `actions`.
- `status`: `"done"` если всё выполнено; `"partial"` если часть не выполнена — описать в `issues`.
- Если `STEP_SUMMARY` отсутствует в stdout — runtime логирует warning, нода считается успешной (graceful degradation).

---

### 13.9 Изменения в prompt-texts.ru.yaml

Новая структура:

```yaml
sections:
  system_intro: |
    You are an automated software development system...
    (полный текст из 13.2)
  context_header: "User Request:"
  task_header: "Task:"
  rework_task_header: "Rework Task:"
  node_instruction_context_header: "Node Instruction (context):"
  workflow_progress_header: "Workflow progress — you are on step {step_no} ({step_id}):"
  workflow_progress_step: "- Step {step_no} — {step_id}: {summary}"
  inputs_header: "Input files:"
  output_files_header: "Output files:"
  project_changes_header: "Project changes:"
  structured_output: |
    After completing your work, output a step summary in this exact format:
    (полный текст из 13.8)
  footer: "Use repository rules and available skills."

inputs:
  use_upstream_artifact_by_path: "{path}"
  use_upstream_artifact_by_key_and_path: "{artifact_key}: {path}"
  use_upstream_artifact_by_value: "{artifact_key} ({size_bytes} bytes):\n```text\n{content}\n```"
```

Удалить: `request_clarification_header`, `node_instruction_header`, `expected_results_header`.
Удалить весь старый блок `expected_results`.

---

### 13.10 Изменения в AgentInput и AgentPromptBuilder

**Новая структура `AgentInput`:**

```java
public record AgentInput(
    boolean startNode,                            // для audit (без изменений)
    String context,                               // run.featureRequest (фоновый контекст)
    String task,                                  // rework instruction или node.instruction
    boolean taskIsRework,                         // true если task = rework
    String nodeInstructionContext,                // node.instruction при rework (для контекста)
    List<String> inputs,                          // строки входных файлов
    List<String> outputFiles,                     // run-scope пути ожидаемых артефактов
    List<String> projectChangePaths,              // пути expected_mutations (если известны)
    boolean hasProjectChanges,                    // есть required mutations
    List<WorkflowProgressEntry> workflowProgress, // summary прошлых шагов
    String stepId,                                // для STRUCTURED_OUTPUT_SECTION
    int attemptNo
)

public record WorkflowProgressEntry(int stepNo, String stepId, String summary) {}
```

Изменения в `build()`:
- `context` ← `run.featureRequest`
- `task` ← `run.pendingReworkInstruction` если есть, иначе `node.instruction`
- `taskIsRework` ← `pendingReworkInstruction != null`
- `nodeInstructionContext` ← `node.instruction` только если `taskIsRework == true`
- `workflowProgress` ← передаётся из `RunStepService` (загружается перед вызовом builder)
- `stepId` ← `node.getId()`
- `attemptNo` ← `execution.getAttemptNo()`

---

### 13.11 Runtime: извлечение и хранение step summary

**После успешного завершения AI-ноды в `executeAiNode()`:**

1. Искать в stdout последнее вхождение строки `STEP_SUMMARY:`.
2. Извлечь JSON из следующего ` ```json ... ``` ` блока.
3. Валидировать обязательные поля (`step_id`, `attempt`, `status`, `actions`).
4. Сохранить в `NodeExecutionEntity.stepSummaryJson`.
5. Если не найдено или невалидно → `stepSummaryJson = null`, audit warning, нода успешна.

**При сборке prompt для следующей AI-ноды:**

1. Загрузить все `NodeExecutionEntity` с `stepSummaryJson != null` для текущего run, упорядоченные по `executedAt`.
2. Назначить порядковые номера (1-based).
3. Из каждого summary взять `actions[0]` как one-liner.
4. Передать список `WorkflowProgressEntry` в `AgentPromptBuilder.build()`.

**Схема данных:**

```sql
ALTER TABLE node_execution ADD COLUMN step_summary_json TEXT;
```

---

### 13.12 Матрица изменений

| Компонент | Изменение |
|---|---|
| `prompt-template.md` | Полностью переписать (8 новых токенов вместо 6) |
| `prompt-texts.ru.yaml` | Новые ключи, удалить старые |
| `AgentPromptBuilder` | Новая структура `AgentInput`, переработать все методы рендера |
| `RunStepService.executeAiNode()` | Загрузка `workflowProgress` перед вызовом builder + парсинг `STEP_SUMMARY` из stdout + сохранение |
| `NodeExecutionEntity` | Новое поле `stepSummaryJson TEXT` |
| DB migration | `ALTER TABLE node_execution ADD COLUMN step_summary_json TEXT` |
| Тесты | Snapshot новых форматов; тест парсинга `STEP_SUMMARY`; интеграционный тест: summary шага N появляется в prompt шага N+1 |

---

## 14. Примеры prompt в новом формате

Тот же сквозной пример из раздела 11 (`Добавь новую кнопку в интерфейс`), в новом формате v2.

### 14.1 Node 1: `ai-analyze-request` — шаг 1, нет прогресса

Первый шаг: нет предыдущих summary, нет rework, нет входных артефактов.

````text
[source: SYSTEM_INTRO_SECTION | sections.system_intro]
You are an automated software development system executing a single step in a multi-step development flow.

Terms:
- User Request: the original goal for this flow run (background context only).
- Node Instruction: the specification for this step.
- Rework Task: a reviewer correction — present only when the previous result was rejected.
- Input Files: files provided for this step.
- Output Files: files you must produce as the step result.
- Project Changes: repository file modifications required by this step.
- Step Summary: a structured report you must produce after completing work.

Execution rules:
1. Your primary task is the Rework Task (if present), otherwise the Node Instruction.
2. The User Request is background context — stay aligned with it, but do not re-execute it.
3. Use only the provided Input Files. Do not invent or assume missing data.
4. Produce only the listed Output Files and Project Changes — no extra files.
5. If a Rework Task is present: address it first, then verify the Node Instruction is still satisfied.
6. If required data is missing: note it in the Step Summary and produce the safest minimal result.

[source: CONTEXT_SECTION | sections.context_header + run.featureRequest]
User Request:
Добавь новую кнопку в интерфейс

[source: TASK_SECTION | sections.task_header + node.instruction]
Task:
Проанализируй запрос пользователя и задай 5 релевантных вопросов.
Сохрани результат в questions.md.

[source: EXPECTED_OUTPUTS_SECTION | sections.output_files_header + runScopeArtifactPath]
Output files:
- .hgsdlc/nodes/ai-analyze-request/attempt-1/questions.md

[source: STRUCTURED_OUTPUT_SECTION | sections.structured_output + {step_id} + {attempt_no}]
After completing your work, output a step summary in this exact format:

STEP_SUMMARY:
```json
{
  "step_id": "ai-analyze-request",
  "attempt": 1,
  "status": "done",
  "actions": ["describe each main action taken"],
  "output_files": [".hgsdlc/nodes/ai-analyze-request/attempt-1/questions.md"],
  "project_changes": [],
  "issues": []
}
```

[source: FOOTER_SECTION | sections.footer]
Use repository rules and available skills.
````

### 14.2 Node 3: `ai-write-requirements` — шаг 2, есть прогресс, inputs by_ref

````text
[source: SYSTEM_INTRO_SECTION | sections.system_intro]
You are an automated software development system...
(полный текст вводной — см. 13.2)

[source: WORKFLOW_PROGRESS_SECTION | sections.workflow_progress_header + node_execution.step_summary_json]
Workflow progress — you are on step 2 (ai-write-requirements):
- Step 1 — ai-analyze-request: Generated 5 clarifying questions about UI button requirements.

[source: CONTEXT_SECTION | sections.context_header + run.featureRequest]
User Request:
Добавь новую кнопку в интерфейс

[source: TASK_SECTION | sections.task_header + node.instruction]
Task:
На основании запроса пользователя и ответов напиши требования.
Сохрани требования в requirements.md.

[source: INPUTS_SECTION | sections.inputs_header + inputs.use_upstream_artifact_by_key_and_path]
Input files:
- questions: /workspace/<run-id>/.hgsdlc/nodes/human-answer-questions/attempt-1/questions.md

[source: EXPECTED_OUTPUTS_SECTION | sections.output_files_header]
Output files:
- .hgsdlc/nodes/ai-write-requirements/attempt-1/requirements.md

[source: STRUCTURED_OUTPUT_SECTION]
After completing your work, output a step summary in this exact format:

STEP_SUMMARY:
```json
{
  "step_id": "ai-write-requirements",
  "attempt": 1,
  "status": "done",
  "actions": ["describe each main action taken"],
  "output_files": [".hgsdlc/nodes/ai-write-requirements/attempt-1/requirements.md"],
  "project_changes": [],
  "issues": []
}
```

[source: FOOTER_SECTION]
Use repository rules and available skills.
````

### 14.3 Node 6: `ai-implement` — шаг 5, rework attempt-2

Ключевое отличие: `TASK_SECTION` меняет заголовок на `Rework Task:`, ниже — `Node Instruction (context):`.

````text
[source: SYSTEM_INTRO_SECTION]
You are an automated software development system...

[source: WORKFLOW_PROGRESS_SECTION]
Workflow progress — you are on step 5 (ai-implement):
- Step 1 — ai-analyze-request: Generated 5 clarifying questions about UI button requirements.
- Step 2 — ai-write-requirements: Wrote requirements.md with 3 requirements.
- Step 3 — ai-build-plan: Created plan.md with 4 implementation steps.

[source: CONTEXT_SECTION | sections.context_header + run.featureRequest]
User Request:
Добавь новую кнопку в интерфейс

[source: TASK_SECTION | sections.rework_task_header + run.pendingReworkInstruction + sections.node_instruction_context_header + node.instruction]
Rework Task:
Файл frontend/src/components/ActionButton.tsx, строки 1-10: Переведи данный текст на русский.

Node Instruction (context):
Разработай функционал согласно запросу пользователя, требованиям и плану.

[source: INPUTS_SECTION | sections.inputs_header + inputs.use_upstream_artifact_by_value]
Input files:
- requirements (112 bytes):
```text
- Добавить кнопку "Отправить"
- Расположить справа от поля ввода
- Поддержать disabled/loading
```
- plan (167 bytes):
```text
1. Обновить компонент формы.
2. Добавить кнопку ActionButton.
3. Добавить состояния disabled/loading.
4. Обновить тесты UI.
```

[source: EXPECTED_OUTPUTS_SECTION | sections.project_changes_header + expected_mutations paths]
Project changes:
- frontend/src/components/ActionButton.tsx

[source: STRUCTURED_OUTPUT_SECTION]
After completing your work, output a step summary in this exact format:

STEP_SUMMARY:
```json
{
  "step_id": "ai-implement",
  "attempt": 2,
  "status": "done",
  "actions": ["describe each main action taken"],
  "output_files": [],
  "project_changes": ["frontend/src/components/ActionButton.tsx"],
  "issues": []
}
```

[source: FOOTER_SECTION]
Use repository rules and available skills.
````

