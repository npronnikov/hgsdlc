# Plan: реализация prompt v2

Цель: реализовать новую архитектуру prompt (раздел 13 `runtime-prompt-and-transitions.md`).

Ключевые изменения:
- 8 новых токенов вместо 6 в шаблоне
- `TASK_SECTION` объединяет instruction + rework (единая «задача»)
- `SYSTEM_INTRO_SECTION` — статичная вводная с терминами и правилами
- `WORKFLOW_PROGRESS_SECTION` — накапливаемые summary предыдущих AI-шагов
- `STRUCTURED_OUTPUT_SECTION` — инструкция по формату `STEP_SUMMARY:`
- Упрощённый формат inputs/outputs (прямые пути без обёрток)

---

## Затрагиваемые файлы

```
backend/src/main/resources/db/changelog/039-node-execution-step-summary.sql  [NEW]
backend/src/main/resources/db/changelog/db.changelog-master.yaml             [edit]
backend/src/main/java/ru/hgd/sdlc/runtime/domain/NodeExecutionEntity.java    [edit]
backend/src/main/java/ru/hgd/sdlc/runtime/infrastructure/NodeExecutionRepository.java [edit]
backend/src/main/java/ru/hgd/sdlc/runtime/application/RuntimeStepTxService.java      [edit]
backend/src/main/java/ru/hgd/sdlc/runtime/application/CodingAgentStrategy.java       [edit]
backend/src/main/java/ru/hgd/sdlc/runtime/application/QwenCodingAgentStrategy.java   [edit]
backend/src/main/java/ru/hgd/sdlc/runtime/application/AgentPromptBuilder.java        [edit]
backend/src/main/resources/runtime/prompt-template.md                                [edit]
backend/src/main/resources/runtime/prompt-texts.ru.yaml                              [edit]
backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunStepService.java    [edit]
```

---

## Шаг 1 — DB migration

**Файл:** `backend/src/main/resources/db/changelog/039-node-execution-step-summary.sql`

```sql
-- liquibase formatted sql

-- changeset runtime:039
ALTER TABLE node_executions ADD COLUMN step_summary_json TEXT;
```

**Файл:** `db.changelog-master.yaml` — добавить запись на `039-node-execution-step-summary.sql` в конец списка `includeAll` / `include`.

---

## Шаг 2 — NodeExecutionEntity

**Файл:** `NodeExecutionEntity.java`

Добавить поле после `checkpointCreatedAt`:

```java
@Column(name = "step_summary_json", columnDefinition = "TEXT")
private String stepSummaryJson;
```

---

## Шаг 3 — NodeExecutionRepository

**Файл:** `NodeExecutionRepository.java`

Добавить метод для загрузки AI-шагов с сохранённым summary (для `WORKFLOW_PROGRESS_SECTION`):

```java
List<NodeExecutionEntity> findByRunIdAndNodeKindAndStatusAndStepSummaryJsonIsNotNullOrderByStartedAtAsc(
        UUID runId,
        String nodeKind,
        NodeExecutionStatus status
);
```

Используется в `RunStepService` перед сборкой prompt: загрузить все `node_kind='ai'`, `status=SUCCEEDED`, `step_summary_json IS NOT NULL`.

---

## Шаг 4 — RuntimeStepTxService

**Файл:** `RuntimeStepTxService.java`

Изменить сигнатуру `markNodeExecutionSucceeded()` — добавить параметр `stepSummaryJson`:

```java
// было:
public NodeExecutionEntity markNodeExecutionSucceeded(UUID runId, UUID executionId, String nodeId)

// стало:
public NodeExecutionEntity markNodeExecutionSucceeded(
        UUID runId, UUID executionId, String nodeId, @Nullable String stepSummaryJson)
```

Внутри, перед `save`: `execution.setStepSummaryJson(stepSummaryJson);`

Все текущие вызовы `markNodeExecutionSucceeded` из `RunStepService` и `GateDecisionService` передают `null` (behaviour для не-AI нод не меняется).

---

## Шаг 5 — CodingAgentStrategy.MaterializationRequest

**Файл:** `CodingAgentStrategy.java`

Добавить поле `workflowProgress` в record `MaterializationRequest`:

```java
record MaterializationRequest(
        RunEntity run,
        FlowModel flowModel,
        NodeModel node,
        NodeExecutionEntity execution,
        List<Map<String, Object>> resolvedContext,
        Path projectRoot,
        Path nodeExecutionRoot,
        List<AgentPromptBuilder.WorkflowProgressEntry> workflowProgress  // NEW
)
```

---

## Шаг 6 — QwenCodingAgentStrategy

**Файл:** `QwenCodingAgentStrategy.java`

Обновить вызов `agentPromptBuilder.build()` — передать `request.workflowProgress()`:

```java
AgentPromptBuilder.AgentPromptPackage promptPackage = agentPromptBuilder.build(
        run,
        flowModel,
        node,
        request.execution(),
        request.resolvedContext(),
        request.workflowProgress()   // NEW
);
```

---

## Шаг 7 — AgentPromptBuilder (основной рефакторинг)

**Файл:** `AgentPromptBuilder.java`

### 7.1 Новые токены

```java
private static final String SYSTEM_INTRO_SECTION_TOKEN        = "{{SYSTEM_INTRO_SECTION}}";
private static final String WORKFLOW_PROGRESS_SECTION_TOKEN   = "{{WORKFLOW_PROGRESS_SECTION}}";
private static final String CONTEXT_SECTION_TOKEN             = "{{CONTEXT_SECTION}}";
// TASK_SECTION_TOKEN остаётся, но семантика меняется
// REQUEST_CLARIFICATION_SECTION_TOKEN  — удалить
// NODE_INSTRUCTION_SECTION_TOKEN       — удалить
private static final String EXPECTED_OUTPUTS_SECTION_TOKEN    = "{{EXPECTED_OUTPUTS_SECTION}}";
// EXPECTED_RESULTS_SECTION_TOKEN       — удалить
private static final String STRUCTURED_OUTPUT_SECTION_TOKEN   = "{{STRUCTURED_OUTPUT_SECTION}}";
```

### 7.2 Новый AgentInput

```java
public record AgentInput(
    boolean startNode,
    String context,                                // run.featureRequest (фон)
    String task,                                   // instruction или rework
    boolean taskIsRework,                          // true если task = rework
    String nodeInstructionContext,                 // node.instruction (только при rework)
    List<String> inputs,
    List<String> outputFiles,                      // run-scope пути артефактов
    List<String> projectChangePaths,               // пути expected_mutations
    boolean hasProjectChanges,
    List<WorkflowProgressEntry> workflowProgress,
    String stepId,
    int attemptNo
)

public record WorkflowProgressEntry(int stepNo, String stepId, String summary) {}
```

### 7.3 Новый PromptTexts

```java
private record PromptTexts(
    String systemIntro,
    String contextHeader,
    String taskHeader,
    String reworkTaskHeader,
    String nodeInstructionContextHeader,
    String workflowProgressHeader,          // плейсхолдеры: {step_no}, {step_id}
    String workflowProgressStep,            // плейсхолдеры: {step_no}, {step_id}, {summary}
    String inputsHeader,
    String outputFilesHeader,
    String projectChangesHeader,
    String structuredOutput,                // плейсхолдеры: {step_id}, {attempt_no}
    String footer,
    // inputs:
    String useUpstreamArtifactByPath,
    String useUpstreamArtifactByKeyAndPath,
    String useUpstreamArtifactByValue
)
```

### 7.4 Обновлённый build()

```java
public AgentPromptPackage build(
        RunEntity run,
        FlowModel flowModel,
        NodeModel node,
        NodeExecutionEntity execution,
        List<Map<String, Object>> resolvedContext,
        List<WorkflowProgressEntry> workflowProgress   // NEW
) {
    boolean startNode = isStartNode(flowModel, node);
    String context = trimToNull(run.getFeatureRequest());
    String rework = trimToNull(run.getPendingReworkInstruction());
    String instruction = trimToNull(node.getInstruction());
    boolean taskIsRework = rework != null;
    String task = taskIsRework ? rework : instruction;
    String nodeInstructionContext = taskIsRework ? instruction : null;

    List<String> inputs = summarizePromptInputs(resolvedContext);
    List<String> outputFiles = summarizeOutputFiles(node, execution);
    List<String> projectChangePaths = summarizeProjectChangePaths(node);
    boolean hasProjectChanges = hasRequiredMutations(node.getExpectedMutations());

    AgentInput agentInput = new AgentInput(
        startNode, context, task, taskIsRework, nodeInstructionContext,
        inputs, outputFiles, projectChangePaths, hasProjectChanges,
        workflowProgress == null ? List.of() : workflowProgress,
        node.getId(), execution.getAttemptNo()
    );
    String prompt = renderPrompt(agentInput);
    return new AgentPromptPackage(agentInput, prompt, ChecksumUtil.sha256(prompt));
}
```

### 7.5 Новые/изменённые методы рендера

| Метод | Статус | Описание |
|---|---|---|
| `buildSystemIntroSection()` | NEW | Возвращает `promptTexts.systemIntro() + "\n\n"` (всегда) |
| `buildWorkflowProgressSection(List<WorkflowProgressEntry>)` | NEW | Пустая строка если список пуст; иначе заголовок + строки шагов |
| `buildContextSection(String context)` | NEW | `context_header + "\n" + context + "\n\n"` или `""` |
| `buildTaskSection(String task, boolean isRework, String nodeCtx)` | REWRITE | Заголовок меняется по `isRework`; при rework добавляет `Node Instruction (context):` |
| `buildInputsSection(List<String>)` | UPDATE | Заголовок меняется на `inputs_header` из нового yaml |
| `buildExpectedOutputsSection(List<String>, List<String>, boolean)` | REWRITE | Отдельные секции `Output files:` и `Project changes:` |
| `buildStructuredOutputSection(String stepId, int attemptNo)` | NEW | Шаблон `structured_output` с подстановкой `{step_id}` и `{attempt_no}` |
| `summarizeOutputFiles(NodeModel, NodeExecutionEntity)` | NEW | Только run-scope пути (из `produced_artifacts` с `scope != project`) |
| `summarizeProjectChangePaths(NodeModel)` | NEW | Пути из `expected_mutations` (или пустой список если нет явных путей) |
| `buildTaskSection(String task)` | DELETE | Заменён |
| `buildRequestClarificationSection(String)` | DELETE | Удалить |
| `buildNodeInstructionSection(String)` | DELETE | Удалить |
| `buildExpectedResultsSection(List<String>)` | DELETE | Заменён |
| `summarizeExpectedResults(NodeModel, NodeExecutionEntity)` | DELETE | Заменён |

### 7.6 Обновлённый renderPrompt()

```java
private String renderPrompt(AgentInput a) {
    String rendered = promptTemplate
        .replace(SYSTEM_INTRO_SECTION_TOKEN,      buildSystemIntroSection())
        .replace(WORKFLOW_PROGRESS_SECTION_TOKEN, buildWorkflowProgressSection(a.workflowProgress()))
        .replace(CONTEXT_SECTION_TOKEN,           buildContextSection(a.context()))
        .replace(TASK_SECTION_TOKEN,              buildTaskSection(a.task(), a.taskIsRework(), a.nodeInstructionContext()))
        .replace(INPUTS_SECTION_TOKEN,            buildInputsSection(a.inputs()))
        .replace(EXPECTED_OUTPUTS_SECTION_TOKEN,  buildExpectedOutputsSection(a.outputFiles(), a.projectChangePaths(), a.hasProjectChanges()))
        .replace(STRUCTURED_OUTPUT_SECTION_TOKEN, buildStructuredOutputSection(a.stepId(), a.attemptNo()))
        .replace(FOOTER_SECTION_TOKEN,            promptTexts.footer() + "\n");
    return normalizePrompt(rendered);
}
```

---

## Шаг 8 — prompt-template.md

**Файл:** `backend/src/main/resources/runtime/prompt-template.md`

Полная замена содержимого:

```
{{SYSTEM_INTRO_SECTION}}
{{WORKFLOW_PROGRESS_SECTION}}
{{CONTEXT_SECTION}}
{{TASK_SECTION}}
{{INPUTS_SECTION}}
{{EXPECTED_OUTPUTS_SECTION}}
{{STRUCTURED_OUTPUT_SECTION}}
{{FOOTER_SECTION}}
```

---

## Шаг 9 — prompt-texts.ru.yaml

**Файл:** `backend/src/main/resources/runtime/prompt-texts.ru.yaml`

Полная замена содержимого:

```yaml
# Runtime prompt texts for AI nodes (v2).

sections:
  system_intro: |
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

  # Inserted when featureRequest is not null.
  context_header: "User Request:"

  # Inserted always; header changes based on whether task is rework.
  task_header: "Task:"
  rework_task_header: "Rework Task:"
  node_instruction_context_header: "Node Instruction (context):"

  # Inserted when previous AI steps have saved summaries.
  # Placeholders: {step_no}, {step_id}
  workflow_progress_header: "Workflow progress — you are on step {step_no} ({step_id}):"
  # Placeholders: {step_no}, {step_id}, {summary}
  workflow_progress_step: "- Step {step_no} — {step_id}: {summary}"

  # Inserted when resolvedContext has artifact_ref entries.
  inputs_header: "Input files:"

  # Inserted when run-scope output files exist.
  output_files_header: "Output files:"
  # Inserted when required expected_mutations exist.
  project_changes_header: "Project changes:"

  # Always inserted before footer. Placeholders: {step_id}, {attempt_no}
  structured_output: |
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

  # Always last.
  footer: "Use repository rules and available skills."

inputs:
  # artifact_ref, no artifact_key. Placeholder: {path}
  use_upstream_artifact_by_path: "{path}"
  # artifact_ref with artifact_key. Placeholders: {artifact_key}, {path}
  use_upstream_artifact_by_key_and_path: "{artifact_key}: {path}"
  # artifact_ref by_value. Placeholders: {artifact_key}, {path}, {size_bytes}, {content}
  use_upstream_artifact_by_value: "{artifact_key} ({size_bytes} bytes):\n```text\n{content}\n```"
```

---

## Шаг 10 — RunStepService

**Файл:** `RunStepService.java`

### 10.1 executeAiNode() — подготовка workflowProgress

В начале метода, перед вызовом `strategy.materializeWorkspace(...)`:

```java
List<AgentPromptBuilder.WorkflowProgressEntry> workflowProgress = loadWorkflowProgress(run.getId());
```

Обновить вызов `strategy.materializeWorkspace(...)` — добавить `workflowProgress` в `MaterializationRequest`:

```java
agentInvocationContext = strategy.materializeWorkspace(new CodingAgentStrategy.MaterializationRequest(
        run, flowModel, node, execution,
        resolvedContext, resolveProjectRoot(run), resolveNodeExecutionRoot(run, execution),
        workflowProgress   // NEW
));
```

### 10.2 executeAiNode() — сохранение step summary

После успешной валидации (`validateNodeOutputs`) и перед `markNodeExecutionSucceeded`:

```java
String stepSummaryJson = extractStepSummary(agentResult.stdout());
if (stepSummaryJson == null) {
    runtimeStepTxService.appendAudit(run.getId(), execution.getId(), null,
            "step_summary_missing", ActorType.SYSTEM, "runtime",
            Map.of("node_id", node.getId()));
}
```

Обновить вызов:

```java
runtimeStepTxService.markNodeExecutionSucceeded(
        run.getId(), execution.getId(), node.getId(), stepSummaryJson);
```

### 10.3 Новый приватный метод loadWorkflowProgress()

```java
private List<AgentPromptBuilder.WorkflowProgressEntry> loadWorkflowProgress(UUID runId) {
    List<NodeExecutionEntity> executions =
            nodeExecutionRepository
                .findByRunIdAndNodeKindAndStatusAndStepSummaryJsonIsNotNullOrderByStartedAtAsc(
                        runId, "ai", NodeExecutionStatus.SUCCEEDED);
    List<AgentPromptBuilder.WorkflowProgressEntry> result = new ArrayList<>();
    for (int i = 0; i < executions.size(); i++) {
        NodeExecutionEntity exec = executions.get(i);
        String summary = extractFirstAction(exec.getStepSummaryJson());
        if (summary != null) {
            result.add(new AgentPromptBuilder.WorkflowProgressEntry(i + 1, exec.getNodeId(), summary));
        }
    }
    return result;
}

// Извлекает actions[0] из JSON или null при ошибке
private String extractFirstAction(String stepSummaryJson) {
    try {
        JsonNode root = objectMapper.readTree(stepSummaryJson);
        JsonNode actions = root.path("actions");
        if (actions.isArray() && actions.size() > 0) {
            String action = actions.get(0).asText("").trim();
            return action.isEmpty() ? null : action;
        }
    } catch (Exception ignored) {}
    return null;
}
```

Для `objectMapper` — инжектировать через конструктор (`ObjectMapper` уже используется в проекте).

### 10.4 Новый приватный метод extractStepSummary()

```java
// Ищет последнее вхождение "STEP_SUMMARY:" в stdout и парсит следующий ```json блок.
// Возвращает JSON-строку или null если не найдено / невалидно.
private String extractStepSummary(String stdout) {
    if (stdout == null) return null;
    int markerIdx = stdout.lastIndexOf("STEP_SUMMARY:");
    if (markerIdx < 0) return null;
    String after = stdout.substring(markerIdx + "STEP_SUMMARY:".length());
    int jsonStart = after.indexOf("```json");
    if (jsonStart < 0) return null;
    int contentStart = after.indexOf('\n', jsonStart) + 1;
    int jsonEnd = after.indexOf("```", contentStart);
    if (contentStart <= 0 || jsonEnd < 0) return null;
    String json = after.substring(contentStart, jsonEnd).trim();
    try {
        JsonNode root = objectMapper.readTree(json);
        // Проверка обязательных полей
        if (root.path("step_id").isMissingNode()
                || root.path("attempt").isMissingNode()
                || root.path("status").isMissingNode()
                || root.path("actions").isMissingNode()) {
            return null;
        }
        return json;
    } catch (Exception e) {
        return null;
    }
}
```

---

## Шаг 11 — Тесты

### 11.1 AgentPromptBuilderTest — snapshot новых форматов

Новые тест-кейсы (заменяют существующие если таковые есть):

| Тест | Что проверяет |
|---|---|
| `testPromptNoReworkNoProgress` | шаг 1: нет rework, нет прогресса → Task:, SYSTEM_INTRO, STRUCTURED_OUTPUT |
| `testPromptWithRework` | при rework → заголовок `Rework Task:` + `Node Instruction (context):` |
| `testPromptWithWorkflowProgress` | 2 entry в workflowProgress → секция `Workflow progress` в промпте |
| `testPromptInputsByRef` | `artifact_ref by_ref` → `key: path` (новый формат) |
| `testPromptInputsByValue` | `artifact_ref by_value` → `key (N bytes):\n\`\`\`text\n...\n\`\`\`` |
| `testPromptOutputFilesOnly` | run-scope артефакты → `Output files:`, нет `Project changes:` |
| `testPromptProjectChangesOnly` | только mutations → нет `Output files:`, есть `Project changes:` |
| `testPromptNoExpectedOutputs` | нет артефактов и мутаций → `EXPECTED_OUTPUTS_SECTION` пустой |

### 11.2 StepSummaryExtractorTest (unit)

| Тест | Что проверяет |
|---|---|
| `testExtractValid` | стандартный STEP_SUMMARY: → правильный JSON |
| `testExtractLastOccurrence` | два STEP_SUMMARY в stdout → берётся последний |
| `testExtractMissingMarker` | нет STEP_SUMMARY: → null |
| `testExtractMissingRequiredField` | JSON без `step_id` → null |
| `testExtractInvalidJson` | неверный JSON → null |
| `testExtractEmptyStdout` | null/пустой stdout → null |

### 11.3 RuntimeRegressionFlowTest — обновление

- Добавить assertion: после первого AI-шага `node_execution.step_summary_json IS NOT NULL`
- Добавить assertion: prompt второго AI-шага содержит `Workflow progress`
- Проверить что при отсутствии `STEP_SUMMARY:` в stdout — нода всё равно `SUCCEEDED`

---

## Порядок выполнения

```
1  → DB migration (шаг 1)
2  → NodeExecutionEntity + Repository (шаги 2–3)
3  → RuntimeStepTxService (шаг 4)
4  → CodingAgentStrategy.MaterializationRequest (шаг 5)
5  → prompt-template.md + prompt-texts.ru.yaml (шаги 8–9)
6  → AgentPromptBuilder (шаг 7) — компилируется, старые тесты сломаются
7  → QwenCodingAgentStrategy (шаг 6) — исправляет вызов build()
8  → RunStepService (шаг 10) — финальная интеграция
9  → Тесты (шаг 11)
```

Шаги 1–5 независимы и могут идти параллельно. Шаг 6 (builder) является блокером для 7 и 8.
