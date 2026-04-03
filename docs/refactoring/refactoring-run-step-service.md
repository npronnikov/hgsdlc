# Рефакторинг RunStepService

## Проблема

`RunStepService` (~1842 LOC, 14 зависимостей) — центральный God Object рантайма. В нём живёт:
- основной execution loop (`processRunStep`, `executeCurrentNode`)
- логика выполнения AI-ноды (`executeAiNode`) — самая сложная часть
- логика выполнения command-ноды (`executeCommandNode`)
- разрешение execution context (`resolveExecutionContext`, `resolveArtifact`, etc.)
- управление артефактами (сохранение, загрузка, проверка size)
- git-операции для diff/snapshot (`snapshotMutations`, `expandGitPaths`)
- переходы между нодами (`applyTransition`)
- fail/cancel логика

При этом уже есть `NodeExecutor` интерфейс и `NodeExecutionRouter`, но они **не делают ничего** — `AiNodeExecutor.execute()` и `CommandNodeExecutor.execute()` просто вызывают `stepService.executeAiNode(run, node, execution)` обратно в RunStepService. Инфраструктура для декомпозиции готова, но не используется.

---

## Цель

Переместить логику выполнения конкретных типов нод из `RunStepService` в соответствующие `NodeExecutor`-реализации. `RunStepService` остаётся координатором (loop, переходы, fail/cancel), но перестаёт знать, как именно работает каждый тип ноды.

---

## Что НЕ трогаем

- `NodeExecutionRouter` — уже хорош, менять не нужно
- `TerminalNodeExecutor`, `HumanInputNodeExecutor`, `HumanApprovalNodeExecutor` — они мелкие, пусть остаются как есть
- `RuntimeStepTxService` — транзакционная обёртка, не трогаем
- `processRunStep` и `executeCurrentNode` — остаются в RunStepService
- Внешний API (RuntimeController, RuntimeService) — не меняется

---

## Архитектура после рефакторинга

```
RunStepService
  processRunStep()        ← loop, tick overflow
  executeCurrentNode()    ← dispatch через NodeExecutionRouter
  applyTransition()       ← переходы между нодами
  failRun()               ← terminal failure
  createNodeExecution()   ← создание записи выполнения

NodeExecutionContext      ← новый record: всё что нужно executor-у
  RunEntity run
  FlowModel flowModel
  NodeModel node
  NodeExecutionEntity execution
  // методы-помощники через делегирование в RunStepService

AiNodeExecutor            ← вся логика executeAiNode
  execute(ctx)            ← больше не вызывает stepService напрямую

CommandNodeExecutor       ← вся логика executeCommandNode
  execute(ctx)

ArtifactManager           ← управление артефактами (выделяем из RunStepService)
  resolveExecutionContext()
  saveArtifact()
  loadArtifact()
  checkContextSize()
```

---

## Этапы

### Этап 1 — Ввести `NodeExecutionContext`

Создать record/класс, который инкапсулирует всё нужное для executor-а и предоставляет методы через делегирование:

```java
public class NodeExecutionContext {
    private final RunStepService stepService; // временно, пока не вынесем всё
    private final RunEntity run;
    private final FlowModel flowModel;
    private final NodeModel node;
    private final NodeExecutionEntity execution;

    // делегирующие методы:
    public List<Map<String, Object>> resolveExecutionContext() {
        return stepService.resolveExecutionContext(run, node);
    }
    public void appendAudit(String eventType, ...) { ... }
    public Map<String, String> snapshotMutations() { ... }
    // и т.д.
}
```

Изменить `NodeExecutor` интерфейс:
```java
public interface NodeExecutor {
    String nodeKind();
    boolean execute(NodeExecutionContext ctx); // было: (RunStepService, run, node, execution)
}
```

Изменить `NodeExecutionRouter.execute(...)` соответственно.

**Важно:** на этом этапе реализации executor-ов остаются неизменными — просто вместо `stepService.executeXxx(run, node, execution)` они вызывают `ctx.stepService.executeXxx(...)`. Код компилируется, тесты проходят. Это чисто структурное изменение.

---

### Этап 2 — Выделить `ArtifactManager`

Из `RunStepService` извлечь всё, что касается хранения и загрузки артефактов:

**Методы для переноса:**
- `resolveExecutionContext(run, node)` — собирает список артефактов для передачи в промпт
- `resolveArtifact(run, entry)` — загружает конкретный артефакт из БД или файла
- `saveRunScopeArtifact(run, execution, node, path)` — сохраняет артефакт
- `checkContextSizeAndTruncate(...)` — проверка 64KB лимита
- `MAX_INLINE_ARTIFACT_BYTES` константа

```java
@Service
public class ArtifactManager {
    private final ArtifactVersionRepository artifactVersionRepository;
    private final WorkspacePort workspacePort;

    public List<Map<String, Object>> resolveExecutionContext(RunEntity run, NodeModel node) { ... }
    public void saveArtifact(RunEntity run, NodeExecutionEntity execution, NodeModel node, String path) { ... }
}
```

`NodeExecutionContext` получает `ArtifactManager` и предоставляет его методы напрямую.

**Почему это важно:** после этого `executeAiNode` больше не нужен прямой доступ к `ArtifactVersionRepository` — он работает через `ctx.artifacts.resolveExecutionContext(...)`.

---

### Этап 3 — Переместить логику в `AiNodeExecutor`

Сейчас `AiNodeExecutor.execute()`:
```java
public boolean execute(RunStepService stepService, RunEntity run, NodeModel node, NodeExecutionEntity execution) {
    return stepService.executeAiNode(run, node, execution); // ← просто редирект
}
```

После этапа — `AiNodeExecutor` содержит саму логику `executeAiNode` (сейчас ~120 строк в RunStepService).

Зависимости `AiNodeExecutor`:
```java
@Service
public class AiNodeExecutor implements NodeExecutor {
    private final ArtifactManager artifactManager;
    private final AgentPromptBuilder promptBuilder;
    private final RuntimeStepTxService runtimeStepTxService;
    private final ExecutionTraceBuilder executionTraceBuilder;
    private final Map<String, CodingAgentStrategy> codingAgentStrategies;

    @Override
    public boolean execute(NodeExecutionContext ctx) {
        // весь код из RunStepService.executeAiNode()
        // ctx.run, ctx.node, ctx.execution вместо параметров
        // artifactManager.resolveExecutionContext() вместо stepService.resolveExecutionContext()
    }
}
```

`RunStepService.executeAiNode()` можно оставить как `@Deprecated` делегат на один цикл, затем удалить.

---

### Этап 4 — Переместить логику в `CommandNodeExecutor`

Аналогично этапу 3, но для command-нод. Логика проще: запуск bash-команды, сохранение stdout/stderr, переход.

Зависимости `CommandNodeExecutor`:
```java
@Service
public class CommandNodeExecutor implements NodeExecutor {
    private final ProcessExecutionPort processExecutionPort;
    private final ArtifactManager artifactManager;
    private final RuntimeStepTxService runtimeStepTxService;
    private final SettingsService settingsService; // только getWorkspaceRoot()

    @Override
    public boolean execute(NodeExecutionContext ctx) { ... }
}
```

---

### Этап 5 — Зачистка RunStepService

После переноса из RunStepService удаляем:
- `executeAiNode()` — перенесено
- `executeCommandNode()` — перенесено
- `resolveExecutionContext()` и связанные — в ArtifactManager
- прямые зависимости на репозитории артефактов
- `Map<String, CodingAgentStrategy> codingAgentStrategiesByAgentId` — в AiNodeExecutor

RunStepService остаётся с:
- `processRunStep()` / `executeCurrentNode()` / `processCurrentNode()`
- `applyTransition()`, `failRun()`, `createCheckpointBeforeExecution()`
- `createNodeExecution()`, `getRunEntity()`, `parseFlowSnapshot()`
- Зависимости: RunRepository, NodeExecutionRepository, RuntimeStepTxService, NodeExecutionRouter, ClockPort, RunPublishService, WorkspacePort (для git-операций переходов)

Итоговый размер: ~500-600 LOC.

---

## `NodeFailureException` — оставляем в RunStepService

`NodeFailureException` — inner class RunStepService, и `NodeExecutionRouter` ссылается на неё через `RunStepService.NodeFailureException`. Вынести в отдельный файл или в пакет можно, но это не критично — достаточно просто не удалять.

---

## Порядок выполнения

```
Этап 1: NodeExecutionContext    — структурное, без изменения логики
Этап 2: ArtifactManager        — изолированная зависимость
Этап 3: AiNodeExecutor         — основной перенос (самая сложная нода)
Этап 4: CommandNodeExecutor    — проще, по образцу этапа 3
Этап 5: зачистка               — удалить перенесённые методы
```

После каждого этапа: запустить `./gradlew test`. Интеграционные тесты рантайма важнее всего.

---

## Что это даёт

| До | После |
|----|-------|
| AiNodeExecutor.execute() → stepService.executeAiNode() (6 строк, нет смысла) | AiNodeExecutor содержит всю логику AI, тестируется с 5 зависимостями вместо 14 |
| CommandNodeExecutor аналогично | CommandNodeExecutor самодостаточен |
| RunStepService знает о конкретных агентах, bash, артефактах | RunStepService — только координатор цикла |
| 14 зависимостей в одном классе | RunStepService ~7, каждый executor ~4-5 |

---

## Риски

| Риск | Митигация |
|------|-----------|
| `NodeFailureException` используется в executor-ах через `RunStepService.NodeFailureException` | Вынести в отдельный файл до переноса логики (Этап 0) |
| Исполнение loop (`processRunStep`) зависит от поведения executor-ов — неочевидные изменения | Этап 1 не меняет логику вообще, только сигнатуры — безопасен |
| `resolveCodingAgentStrategy` в RunStepService — нужна AiNodeExecutor | Передать `Map<String, CodingAgentStrategy>` в конструктор AiNodeExecutor (Spring соберёт через `List<CodingAgentStrategy>`) |
| context size truncation — 64KB лимит | Константа и логика переезжают в ArtifactManager, поведение не меняется |
