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

## 13. Улучшения промпта и runtime (по предложению)

Ниже целевая доработка prompt-контракта: сделать поведение агента более предсказуемым, а сам prompt — более понятным и менее «корявым».

### 13.1 Новая вводная (системная рамка)

Рекомендуется всегда начинать prompt с короткой системной вводной:

```text
Ты — система разработки ПО, выполняющая текущий шаг flow.
Flow состоит из шагов (nodes), каждый шаг имеет цель и ограничения.
Базовые термины:
- User Request: исходный запрос пользователя.
- Node Instruction: инструкция текущего шага.
- Rework Task: уточнение/исправление после ревью (если шаг запущен повторно).
- Input Artifacts: входные файлы для шага.
- Expected Artifacts: файлы, которые нужно получить по итогу шага.
Работай только в рамках текущего шага и его входных данных.
```

Зачем:
- сразу задаёт роль и границы;
- убирает двусмысленность между «ты чат-ассистент» и «ты runtime-исполнитель шага».

### 13.2 Явные приоритеты контекста: что важнее чего

В prompt нужно явно описать приоритет источников задачи.

Предлагаемый порядок приоритетов:
1. `Rework Task` (если есть) — самый высокий приоритет как корректировка прошлого результата.
2. `Node Instruction` — цель текущего шага.
3. `User Request` — глобальная цель флоу (контекст, который нельзя потерять).
4. `Input Artifacts` — фактические данные для выполнения.

Рекомендуемый блок правил поведения:

```text
Правила выполнения:
1) Если есть Rework Task, сначала выполни его требования.
2) Затем выполни Node Instruction.
3) Не противоречь User Request.
4) Используй только предоставленные Input Artifacts; не выдумывай отсутствующие данные.
5) Не изменяй файлы вне ожидаемого результата шага.
6) Если данных не хватает, зафиксируй это в summary и сделай минимально безопасный результат.
```

Что делать/что не делать:
- Делать:
  - опираться на входные файлы;
  - соблюдать приоритет rework;
  - выдавать только релевантные изменения текущему шагу.
- Не делать:
  - игнорировать rework при его наличии;
  - менять произвольные файлы «на будущее»;
  - подменять входные артефакты предположениями.

### 13.3 Упростить блоки входа/выхода (человеческий формат)

Текущий текст `expected_results.required_*` перегружен формулировками. Лучше перейти к прямым спискам.

Вместо:
- "Write generated artifacts strictly to these paths ..."

Предлагаемый формат:

```text
Input files:
- <path1>
- <path2>

Expected output files:
- <pathA>
- <pathB>
```

И отдельная строка для мутаций проекта:

```text
Required project changes:
- <path/to/file>
```

Плюс короткий policy:

```text
Ожидаю как результат только перечисленные output files и required project changes.
```

Идея по реализации без ломки модели:
- добавить новые ключи в `prompt-texts.ru.yaml`:
  - `inputs_header_simple`
  - `expected_output_files_header`
  - `required_project_changes_header`
- в `AgentPromptBuilder` рендерить «простую форму» для `inputs/expected`.

### 13.4 Уточнить, что такое "задача" шага

В документации и prompt явно зафиксировать:
- Текущая задача шага =
  - `Rework Task`, если шаг запущен после rework;
  - иначе `Node Instruction`.
- `User Request` всегда остаётся фоном и не должен теряться.

Это снимет путаницу, где «задача» одновременно и пользовательский запрос, и инструкция ноды.

### 13.5 Structured output шага (обязательный)

Предложение: в конце каждого AI-шага требовать структурированный summary в стабильном формате.

Пример целевого формата:

```text
STEP_SUMMARY_JSON:
{
  "step_id": "ai-build-plan",
  "attempt": 1,
  "status": "done",
  "completed_actions": [
    "Сформирован план реализации",
    "План сохранён в plan.md"
  ],
  "output_files": ["plan.md"],
  "project_changes": [],
  "open_questions": [],
  "risks": []
}
```

Минимальные требования:
- фиксированный префикс (`STEP_SUMMARY_JSON:`);
- валидный JSON;
- обязательные поля (`step_id`, `status`, `completed_actions`, `output_files`).

### 13.6 Протащить summary между шагами в runtime

Предложение по системе:
1. На завершении AI-node runtime парсит `STEP_SUMMARY_JSON`.
2. Сохраняет summary как:
- отдельный артефакт шага (`step-summary.json`), и/или
- запись в БД `node_execution_summary_json`.
3. При сборке следующего prompt добавляет блок:

```text
Workflow progress:
You are on step <N> (<step_id>).
Previous steps summary:
- Step 1: ...
- Step 2: ...
- Step 3: ...
```

Целевой эффект:
- агент понимает контекст цепочки без перечитывания всех файлов;
- меньше повторной работы и расхождений между шагами;
- rework становится более управляемым (агент видит, что уже делал раньше).

### 13.7 Минимальный план внедрения

1. Prompt-слой:
- добавить системную вводную;
- добавить блок приоритетов;
- упростить рендер input/output секций.

2. Summary-слой:
- добавить обязательный structured output в footer-инструкцию;
- добавить в runtime extraction + persistence summary.

3. Контекст следующего шага:
- добавить новую секцию `WORKFLOW_PROGRESS_SECTION` в `prompt-template.md`;
- заполнять её summary последних успешных шагов.

4. Тесты:
- snapshot-тесты нового prompt-формата;
- тест на парсинг/сохранение summary;
- интеграционный тест: summary шага N появляется в prompt шага N+1.

