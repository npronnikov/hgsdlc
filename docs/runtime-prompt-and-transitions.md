# Механика построения prompt и переходов между node в runtime

Документ описывает фактическое поведение runtime по коду:
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/RuntimeService.java`
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/AgentPromptBuilder.java`
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/QwenCodingAgentStrategy.java`
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/RuntimeStepTxService.java`
- `backend/src/main/java/ru/hgd/sdlc/flow/application/FlowValidator.java`

## 1) Какие переходы вообще поддерживаются

### 1.1 Переходы по типу node

- `ai`
  - `on_success` -> обязательный переход (валидатор это требует)
  - `on_failure` -> обязательный для AI по валидатору; runtime применяет, только если указан
- `command`
  - `on_success` -> обязательный
  - `on_failure` -> не поддерживается (валидатор ругается)
- `human_input`
  - `on_submit` -> обязательный
- `human_approval`
  - `on_approve` -> обязательный
  - `on_rework.next_node` -> обязательный
- `terminal`
  - переходов быть не должно

Технически любой переход применяется через `applyTransition(...)`:
- если target пустой -> `INVALID_TRANSITION`
- если target node не существует в snapshot flow -> `INVALID_TRANSITION`
- при успехе run переводится в `RUNNING`, `current_node_id` меняется, пишется audit `transition_applied`

### 1.2 Схема исполнения тика

`tick()` циклически вызывает `executeCurrentNode()` пока run в `RUNNING`.

Для текущей node runtime делает:
1. создаёт `NodeExecution` (новая попытка);
2. при необходимости создаёт checkpoint (`checkpoint_before_run=true`, только для `ai/command`);
3. исполняет логику по node_kind;
4. на успехе применяет переход;
5. на ошибке:
   - для `ai` с заполненным `on_failure` -> переход `on_failure`;
   - иначе `run_failed`.

### 1.3 Переходы в gate-нодах

- `human_input`:
  - при `submitInput` -> закрывается gate, node = succeeded, переход `on_submit`
- `human_approval`:
  - при `approveGate` -> закрывается gate, node = succeeded, переход `on_approve`
  - при `requestRework` -> закрывается gate, node = succeeded, переход `on_rework`

## 2) Артефакты и execution_context: как это влияет на переходы

## 2.1 Без артефактов

Сценарий: `execution_context: []`, `produced_artifacts: []`, `expected_mutations: []`.

Поведение:
- prompt не получает секцию `Available inputs`;
- в expected results остаётся только summary (см. раздел 3);
- переход выполняется только по статусу node (`on_success/on_submit/on_approve/...`).

## 2.2 С артефактами (artifact_ref)

Поддерживаемый runtime-контекст:
- `type=user_request`
- `type=artifact_ref`

Для `artifact_ref`:
- `scope=project` -> путь ищется в project root
- `scope=run` -> нужен `node_id`, берётся последний `SUCCEEDED` execution source-node

`transfer_mode`:
- `by_ref` (default): в prompt передаётся ссылка/путь
- `by_value`: файл встраивается в prompt целиком (ограничение `<= 64KB`, иначе ошибка `EXECUTION_CONTEXT_TOO_LARGE`)

Если `required=true` и артефакт не найден -> ошибка `MISSING_EXECUTION_CONTEXT`.

## 2.3 produced_artifacts / expected_mutations

После выполнения node runtime валидирует выходы:
- `produced_artifacts.required=true` -> файл обязан существовать
- `expected_mutations.required=true` -> checksum должен измениться

Нарушение даёт `NODE_VALIDATION_FAILED`.

При наличии файлов runtime пишет версии артефактов в `artifact_versions`:
- `kind=produced` для `produced_artifacts`
- `kind=mutation` для `expected_mutations`
- `kind=human_input` для артефактов, отредактированных на `human_input`

## 2.4 Особый поток `human_input`

`human_input` принимает только `execution_context` типа `artifact_ref` с `scope=run` и `transfer_mode=by_ref`.

До открытия gate:
- runtime копирует source run-artifacts в рабочую папку текущей попытки `human_input`;
- эти копии становятся редактируемыми output данного node;
- пишется версия `artifact_kind=human_input`.

При submit:
- пользователь присылает base64-контент только разрешённых артефактов;
- required-артефакты проверяются на:
  - существование
  - непустое содержимое
  - изменение checksum относительно pre-submit
- при провале -> gate `FAILED_VALIDATION`.

## 3) Как строится prompt (AI node)

## 3.1 Где именно строится

1. `RuntimeService.executeAiNode()` резолвит context.
2. `QwenCodingAgentStrategy.materializeWorkspace()` вызывает `AgentPromptBuilder.build(...)`.
3. Шаблон: `backend/src/main/resources/runtime/prompt-template.md`:

```md
{{TASK_SECTION}}{{REQUEST_CLARIFICATION_SECTION}}{{NODE_INSTRUCTION_SECTION}}{{INPUTS_SECTION}}{{EXPECTED_RESULTS_SECTION}}{{FOOTER_SECTION}}
```

4. Тексты секций: `backend/src/main/resources/runtime/prompt-texts.ru.yaml`.

## 3.2 Состав `AgentInput`

`AgentPromptBuilder` собирает:
- `startNode` — текущая node == `flow.start_node_id`
- `task` — `run.featureRequest`
- `requestClarification` — `run.pendingReworkInstruction`
- `nodeInstruction` — `node.instruction`
- `inputs` — summary из `resolvedContext`
- `expectedResults` — summary из required outputs/mutations

Потом рендерит секции + считает checksum (`sha256(prompt)`).

## 3.3 Правила включения секций

- `Task:` — если `task != null`
- `Request clarification:` — если есть `pendingReworkInstruction`
- `Instruction:` — если есть `node.instruction`
- `Available inputs:` — если есть хотя бы один input
- `Expected result:` — если есть хотя бы один expected result
- Footer (`Use repository rules and available skills.`) — всегда

После рендера выполняется нормализация:
- `\r\n -> \n`
- серии из 3+ переводов строки схлопываются в двойной `\n\n`

## 3.4 Как формируются `inputs`

`inputs` строятся только из `resolvedContext` записей `type=artifact_ref`:

- `by_ref`, без `artifact_key`:
  - `Use the input artifact by path '{path}'.`
- `by_ref`, с `artifact_key`:
  - `Use the input artifact '{artifact_key}' by full path '{path}'.`
- `by_value`:
  - в prompt вставляется блок с inline content и `size_bytes`

Список `inputs` дедуплицируется (`distinct`).

`type=user_request` в `resolvedContext` не превращается в `inputs` — исходный запрос идёт отдельной секцией `Task`.

## 3.5 Как формируются `expectedResults`

`expectedResults` включает:
1. `required_artifacts` — если есть обязательные артефакты:
   - `node.output_artifact` (если задан)
   - все `produced_artifacts` с `required=true` (ключ выводится из имени файла)
2. `required_run_paths` — если обязательные `produced_artifacts` со scope != `project`:
   - путь формата `.hgsdlc/nodes/{nodeId}/attempt-{n}/{artifactPath}`
3. `required_mutations` — если есть хотя бы один `expected_mutations.required=true`
4. `summary` — всегда

Списки артефактов и путей также дедуплицируются.

## 3.6 Что дополнительно материализуется рядом с prompt

Перед запуском агента runtime создаёт:
- `.qwen/QWEN.md` — flow-level rules
- `.qwen/skills/<skillCanonical>/SKILL.md` — node-level skills
- `prompt.md` в папке attempt node

И пишет audit:
- `rules_materialized`
- `skills_materialized`
- `prompt_package_built` (в payload полный rendered prompt + checksum + resolved_context)
- `agent_invocation_started/finished`

## 4) Реворк: полная механика

Реворк доступен только на `human_approval` gate через `requestRework(...)`.

Шаги:
1. Проверка gate статуса/версии/роли.
2. Берётся `transitionTarget = node.on_rework.next_node`.
3. Выбор режима изменений:
   - явный `mode=keep` -> сохраняем изменения
   - явный `mode=discard` -> делаем rollback
   - иначе берётся `on_rework.keep_changes` из flow
4. Если rollback нужен:
   - runtime ищет последнюю execution target-node
   - берёт её `checkpoint_commit_sha`
   - выполняет `git reset --hard <checkpoint>`
5. Сохраняется rework instruction:
   - если target == `start_node_id`: instruction добавляется в `feature_request` (append), `pending_rework_instruction` очищается
   - иначе instruction кладётся в `pending_rework_instruction`
6. Node gate помечается succeeded, применяется `on_rework` transition.

Влияние на prompt следующего AI:
- если instruction лежит в `pending_rework_instruction`, появится секция `Request clarification:`
- после успешного AI выполнения instruction consumption очищает pending (`rework_instruction_consumed`)
- если реворк вернул на start-node, instruction уже в `Task`, а не в `Request clarification`

## 5) Таблица переходов (быстрый справочник)

| node_kind | Событие | Переход | Что влияет на prompt |
|---|---|---|---|
| `ai` | успешный exit + прошла валидация outputs | `on_success` | формируется новый prompt следующего AI
| `ai` | ошибка выполнения/валидации | `on_failure` (если указан), иначе `run_failed` | при переходе на AI prompt будет строиться для target node
| `command` | успешный exit + прошла валидация outputs | `on_success` | prompt только на следующих AI
| `human_input` | submit валиден | `on_submit` | изменённые run-артефакты могут попасть в следующий AI как `artifact_ref`
| `human_approval` | approve | `on_approve` | без спец-изменений prompt, кроме новых артефактов/мутаций по цепочке
| `human_approval` | rework | `on_rework.next_node` | может добавить `Request clarification` или расширить `Task` (если target=start)
| `terminal` | вход в node | переходов нет, run завершается | prompt не строится |

## 6) Практический вывод

- Переходы в runtime полностью event-driven по полям `on_*` и типу node.
- Prompt не хранит отдельную "логику графа"; он собирается из текущего run state (`featureRequest`, `pendingReworkInstruction`), текущей node и resolved execution context.
- Наличие/отсутствие артефактов меняет prompt через секции `Available inputs` и `Expected result`, а реворк меняет prompt через `Task`/`Request clarification`.
