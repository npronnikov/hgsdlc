# Инструкция для coding-агента: как делать Flow и Skill в Human Guided SDLC

Документ собран по текущему проекту и рабочим примерам из `docs/examples`.

Важно: в проекте папка называется `docs/examples` (не `docs/exampes`).

## 1. Что есть что

- `Flow` — сценарий выполнения задачи (YAML-граф из нод).
- `Rule` — глобальные правила для всех AI-нод flow.
- `Skill` — узкоспециализированная инструкция для конкретной AI-ноды.

Принцип:
- Rule отвечает на вопрос "как работать в проекте вообще".
- Skill отвечает на вопрос "как выполнить конкретный шаг".

## 2. Где хранить артефакты

Используй структуру как в `docs/examples`:

```text
flows/<flow-id>/<version>/
  FLOW.yaml
  metadata.yaml

skills/<skill-id>/<version>/
  SKILL.md
  metadata.yaml
```

Примеры в репозитории:
- `docs/examples/flows/java-development-flow/1.0/FLOW.yaml`
- `docs/examples/skills/generate-system-requirements/1.0/SKILL.md`

## 3. Как создавать Flow: рабочий алгоритм

## Шаг 1. Определи тип сценария

Обычно есть 3 паттерна:

1. "Уточнить и реализовать" (AI -> human_input -> AI -> human_approval)
2. "Полный цикл" (вопросы -> требования -> план -> реализация -> ревью)
3. "Аналитика/документация" (AI -> human_approval -> terminal)

## Шаг 2. Собери скелет FLOW.yaml

Минимальные обязательные поля верхнего уровня:

- `id`
- `version`
- `canonical_name`
- `title`
- `start_node_id`
- `nodes`

Рекомендуется всегда добавлять:

- `description`
- `rule_refs`
- `fail_on_missing_declared_output`
- `fail_on_missing_expected_mutation`

Пример скелета:

```yaml
id: my-feature-flow
version: "1.0"
canonical_name: my-feature-flow@1.0
title: Реализация фичи с проверкой
description: >
  Агент уточняет требования, реализует и проходит ревью.
start_node_id: ai-analyze
fail_on_missing_declared_output: true
fail_on_missing_expected_mutation: false
rule_refs:
  - java-backend-coding-standards@1.0

nodes: []
```

## Шаг 3. Спроектируй ноды и переходы

Поддерживаемые типы:

- `ai`
- `command`
- `human_input`
- `human_approval`
- `terminal`

Критично по валидации:

- `ai` и `command` требуют `on_success`.
- `ai` дополнительно требует `on_failure`.
- `human_input` требует `instruction`, `execution_context`, `on_submit`.
- `human_approval` требует `on_approve` и `on_rework.next_node`.
- `terminal` не должен иметь переходов.

## Шаг 4. Правильно опиши execution_context

Общее правило:
- Для AI/approval можно использовать `artifact_ref` (а также поддерживаются другие типы, но в UI обычно используется `artifact_ref`).
- Для `human_input` только `artifact_ref` со строгими ограничениями:
  - `scope: run`
  - `transfer_mode: by_ref`
  - обязательно `node_id`
  - обязательно `modifiable`

Для AI:
- `transfer_mode: by_value` допустим только на AI-нодах.

## Шаг 5. Объяви артефакты

- `produced_artifacts` — что нода должна создать.
- `expected_mutations` — какие файлы должны быть изменены.

Практика:
- Для AI, создающих документы (`questions.md`, `requirements.md`, `plan.md`), всегда ставь `required: true`.
- Для `human_input` в `produced_artifacts` должен быть тот же набор редактируемых артефактов, который пришел из predecessor (иначе будет ошибка валидации).

## Шаг 6. Добавь контрольные точки

Для AI/command обычно ставь:

```yaml
checkpoint_before_run: true
```

Это дает безопасный rework с откатом изменений.

## Шаг 7. Добавь metadata.yaml

Пример (flow):

```yaml
entity_type: flow
id: my-feature-flow
version: "1.0"
canonical_name: my-feature-flow@1.0
display_name: "Реализация фичи с проверкой"
description: >
  Полный цикл реализации: уточнение, выполнение, ревью.
coding_agent: qwen
team_code: Demo
platform_code: BACK
tags:
  - full-cycle
  - backend
flow_kind: delivery
risk_level: medium
scope: organization
approval_status: published
lifecycle_status: active
source_ref: main
source_path: "flows/my-feature-flow/1.0"
```

## 4. Полный пример Flow

Пример на основе рабочих паттернов из `docs/examples/flows/*`:

```yaml
id: feature-with-clarification
version: "1.0"
canonical_name: feature-with-clarification@1.0
title: Уточнение и реализация фичи
description: >
  Агент сначала задает вопросы, затем реализует фичу по ответам.
start_node_id: ai-ask
fail_on_missing_declared_output: true
fail_on_missing_expected_mutation: false
rule_refs:
  - java-backend-coding-standards@1.0

nodes:
  - id: ai-ask
    title: Сформировать уточняющие вопросы
    type: ai
    checkpoint_before_run: true
    execution_context:
      - type: user_request
        required: true
    instruction: |
      Изучи запрос пользователя и подготовь вопросы.
      Сохрани questions.md.
    skill_refs:
      - analyze-and-ask-clarifying-questions@1.0
    produced_artifacts:
      - scope: run
        path: questions.md
        required: true
        modifiable: false
    expected_mutations: []
    on_success: human-answer
    on_failure: terminal-complete

  - id: human-answer
    title: Ответить на вопросы
    type: human_input
    execution_context:
      - type: artifact_ref
        node_id: ai-ask
        path: questions.md
        scope: run
        required: true
        modifiable: true
        transfer_mode: by_ref
    instruction: |
      Заполни ответы в questions.md и нажми Submit.
    produced_artifacts:
      - scope: run
        path: questions.md
        required: true
    expected_mutations: []
    on_submit: ai-implement

  - id: ai-implement
    title: Реализовать по ответам
    type: ai
    checkpoint_before_run: true
    execution_context:
      - type: user_request
        required: true
      - type: artifact_ref
        node_id: human-answer
        path: questions.md
        scope: run
        required: true
        transfer_mode: by_value
    instruction: |
      Реализуй задачу по запросу пользователя и заполненным ответам.
      Обнови тесты.
    skill_refs: []
    produced_artifacts: []
    expected_mutations: []
    on_success: approve-impl
    on_failure: terminal-complete

  - id: approve-impl
    title: Проверка реализации
    type: human_approval
    execution_context: []
    instruction: |
      Проверь изменения и прими решение.
    produced_artifacts: []
    expected_mutations: []
    on_approve: terminal-complete
    on_rework:
      next_node: ai-implement

  - id: terminal-complete
    title: Завершение
    type: terminal
    execution_context: []
    produced_artifacts: []
    expected_mutations: []
```

## 5. Как создавать Skill: рабочий алгоритм

## Шаг 1. Определи назначение skill

Хороший skill:
- узкий;
- с конкретной целью;
- с четким форматом результата;
- с ограничениями (что не делать).

Плохой skill:
- "сделай всё как надо";
- без формата output;
- без проверки и ограничений.

## Шаг 2. Создай SKILL.md с frontmatter

Для Qwen/Gigacode обязательно наличие frontmatter и полей:
- `name`
- `description`

Если frontmatter отсутствует, публикация будет падать с ошибкой.

Базовый шаблон:

```markdown
---
name: build-implementation-plan
description: >
  По утвержденным требованиям построить пошаговый план реализации с
  файлами, рисками и критериями приемки.
---

# Build implementation plan

## Goal
...

## Steps
1. ...
2. ...

## Output format
...

## Constraints
...
```

## Шаг 3. Структура skill должна быть детерминированной

Рекомендуемая структура (по действующим примерам):

1. `Goal` / `Цель`
2. Пошаговый workflow (`Шаг 1`, `Шаг 2`, ...)
3. `Формат вывода` с шаблоном файла
4. `Ограничения`

## Шаг 4. Привязывай skill к конкретному output-файлу

Примеры из проекта:
- `questions.md`
- `questions.html` + `answers.md`
- `requirements.md`
- `plan.md`
- `docs/architecture.md`

Если skill не фиксирует output, AI будет давать разнородный результат.

## Шаг 5. Добавь metadata.yaml

Пример (skill):

```yaml
entity_type: skill
id: build-implementation-plan
version: "1.0"
canonical_name: build-implementation-plan@1.0
display_name: "Построение плана реализации"
description: >
  По требованиям формирует детальный план реализации.
coding_agent: qwen
team_code: Demo
platform_code: BACK
tags:
  - planning
  - requirements
skill_kind: analysis
scope: organization
approval_status: published
lifecycle_status: active
source_ref: main
source_path: "skills/build-implementation-plan/1.0"
```

## 6. Полный пример Skill

Ниже пример в стиле текущего каталога:

```markdown
---
name: Generate API requirements
description: >
  Проанализируй кодовую базу и запрос пользователя, сформируй требования к API,
  модели данных, авторизации и тестам. Сохрани в requirements.md.
---

# Generate API requirements

## Goal
Сформировать верифицируемые требования с привязкой к конкретным файлам и классам.

## Шаг 1 — Анализ текущего состояния
1. Найди релевантные контроллеры, сервисы, репозитории.
2. Определи текущие API-контракты.
3. Зафиксируй затрагиваемые файлы.

## Шаг 2 — Формирование требований
Для каждого требования укажи:
- что нужно сделать;
- где именно в коде;
- как проверить;
- какие риски.

## Формат вывода
Сохрани строго в `requirements.md`:
- Контекст запроса
- Список затрагиваемых файлов
- FR-* требования
- API-* требования
- DM-* требования
- AUTH-* требования
- TEST-* требования
- Критерии приемки

## Ограничения
- Не писать код.
- Не ссылаться на несуществующие файлы.
- Не добавлять требования, не вытекающие из запроса.
```

## 7. Как связать Flow и Skill правильно

Паттерн:

1. Skill создаёт артефакт `X` в AI-ноде.
2. Следующая нода получает `X` через `execution_context`.
3. Human gate редактирует `X` (если это `human_input`).
4. Следующая AI-нода забирает `X` через `transfer_mode: by_value`.

Пример связки:

- `ai-analyze-and-ask` + skill `analyze-and-ask-questions-html@1.0`
  -> produced: `questions.html`, `answers.md`
- `human-answer-questions`
  -> модифицирует `answers.md`
- `ai-implement`
  -> использует `answers.md` как `by_value`

## 8. Частые ошибки и как избежать

1. Нет `on_rework` у `human_approval`.
- Исправление: добавь `on_rework.next_node`.

2. `human_input` с `transfer_mode: by_value`.
- Исправление: для `human_input` только `by_ref`.

3. `human_input` с `scope: project`.
- Исправление: для `human_input` только `scope: run`.

4. Указан `skill_refs` на не-AI ноде.
- Исправление: оставляй `skill_refs` только на `type: ai`.

5. Отсутствует frontmatter в `SKILL.md`.
- Исправление: добавь блок `--- ... ---` с `name` и `description`.

6. Необъявленные артефакты.
- Исправление: всё, что ожидаешь получить, явно укажи в `produced_artifacts`.

## 9. Проверочный чеклист перед публикацией

Для Flow:

- [ ] Все ноды достижимы от `start_node_id`
- [ ] У всех переходов target существует
- [ ] `human_input` и `human_approval` имеют обязательные transition-поля
- [ ] `skill_refs` только на AI-нодах
- [ ] Артефакты объявлены и согласованы между нодами

Для Skill:

- [ ] Есть frontmatter
- [ ] Есть `name` и `description`
- [ ] Есть явный output format
- [ ] Есть ограничения
- [ ] Текст не противоречит реальному стеку проекта

## 10. Рекомендуемая стратегия для нового агента

1. Скопируй ближайший пример из `docs/examples/flows/*` или `docs/examples/skills/*`.
2. Переименуй `id`, `canonical_name`, `title`, `description`.
3. Адаптируй `instruction` и `skill_refs` под задачу.
4. Проверь переходы и контракты артефактов.
5. Только после этого публикуй.

Это снижает риск невалидного YAML и несовместимости с runtime.
