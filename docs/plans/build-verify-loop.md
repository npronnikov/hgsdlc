# Build-verify loop — план реализации

## Суть

После того как агент реализовал фичу, перед человеческим approve запускается сборка.
Если она упала — stdout/stderr возвращается агенту, тот фиксит, сборка повторяется.
Петля повторяется до успеха или до превышения лимита попыток.

```
ai-implement → build-verify ──success──→ approve-implementation
                    │
                  failure
                    ↓
              ai-fix-build-errors → build-verify (снова)
```

Полный `npm run build` / `./gradlew bootJar` — **только один раз** в конце флоу,
не в каждой итерации агента. Машина не перегружается.

---

## Что сейчас не работает (и почему)

### Проблема 1 — `on_failure` для `command` нод игнорируется

Файл: `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunStepService.java`, строка 177.

```java
// сейчас: on_failure работает только для ai-нод
if ("ai".equals(nodeKind) && node.getOnFailure() != null && !node.getOnFailure().isBlank()) {
    applyTransition(run, execution, null, node.getOnFailure(), "on_failure");
    return true;
}
failRun(run, ex.errorCode, ex.getMessage()); // ← command-нода всегда падает насмерть
```

### Проблема 2 — `artifact_ref` не видит файлы упавшей `command`-ноды

Файл: `RunStepService.java`, строка 1362.

`resolveArtifactRefPath` ищет только `NodeExecutionStatus.SUCCEEDED` выполнения.
Когда сборка упала — её `command.stderr.log` существует на диске, но AI-нода
не может его прочитать через `execution_context`, потому что статус `FAILED`.

---

## Изменения

### 1. `RunStepService.java` — два точечных правки

**Правка 1** (строка 177): распространить `on_failure` на все типы нод, не только `ai`.

```java
// было:
if ("ai".equals(nodeKind) && node.getOnFailure() != null && !node.getOnFailure().isBlank()) {

// стало:
if (node.getOnFailure() != null && !node.getOnFailure().isBlank()) {
```

**Правка 2** (строка 1362): при `artifact_ref` — если `SUCCEEDED`-выполнение не найдено,
брать самое последнее (в том числе `FAILED`), чтобы AI-нода могла прочитать stderr сборки.

```java
// было:
NodeExecutionEntity sourceExecution = nodeExecutionRepository
        .findFirstByRunIdAndNodeIdAndStatusOrderByAttemptNoDesc(
                run.getId(), sourceNodeId, NodeExecutionStatus.SUCCEEDED)
        .orElse(null);

// стало:
NodeExecutionEntity sourceExecution = nodeExecutionRepository
        .findFirstByRunIdAndNodeIdAndStatusOrderByAttemptNoDesc(
                run.getId(), sourceNodeId, NodeExecutionStatus.SUCCEEDED)
        .or(() -> nodeExecutionRepository
                .findFirstByRunIdAndNodeIdOrderByAttemptNoDesc(run.getId(), sourceNodeId))
        .orElse(null);
```

`findFirstByRunIdAndNodeIdOrderByAttemptNoDesc` уже есть в `NodeExecutionRepository` — новых методов не нужно.

---

### 2. `node-command.schema.json` — добавить `on_failure`

Файл: `backend/src/main/resources/schemas/node-command.schema.json`

```json
"on_failure": { "type": "string" }
```

Добавить рядом с `"on_success"`. Поле уже есть в `NodeModel.java` (строка 89) — только схема отстаёт.

---

### 3. Паттерн в FLOW.yaml

Вставить две ноды между `ai-implement` и `approve-implementation`:

```yaml
  # ── Step N: Verify build ───────────────────────────────────────────────────
  - id: build-verify
    title: Verify project builds
    type: command
    checkpoint_before_run: false
    # Команда зависит от стека проекта. Примеры:
    #   Java backend:    cd backend && ./gradlew --no-daemon bootJar
    #   React frontend:  cd frontend && npm run build
    #   Оба:             cd backend && ./gradlew --no-daemon bootJar && cd ../frontend && npm run build
    instruction: |
      cd backend && ./gradlew --no-daemon bootJar
    on_success: approve-implementation
    on_failure: ai-fix-build-errors

  # ── Step N+1: Fix build errors ─────────────────────────────────────────────
  - id: ai-fix-build-errors
    title: Fix build errors
    type: ai
    checkpoint_before_run: true
    execution_context:
      - type: artifact_ref
        node_id: build-verify      # берём из упавшей build-verify ноды
        path: command.stderr.log   # stderr сборки
        scope: run
        required: false
        transfer_mode: by_value
      - type: artifact_ref
        node_id: build-verify
        path: command.stdout.log   # stdout (gradle пишет ошибки туда тоже)
        scope: run
        required: false
        transfer_mode: by_value
    instruction: |
      Сборка проекта завершилась с ошибкой.
      Вывод сборки доступен в контексте выполнения (command.stderr.log, command.stdout.log).

      Изучи ошибки компиляции и исправь их в кодовой базе.
      Не меняй логику — только исправляй ошибки сборки.
      Не создавай новых файлов вне уже затронутых.
    skill_refs: []
    produced_artifacts: []
    expected_mutations: []
    on_success: build-verify       # после фикса — снова проверяем сборку
    on_failure: terminal-complete  # если агент сам упал — завершаем флоу
```

**Изменение в `ai-implement`:** поменять `on_success` с `approve-implementation` на `build-verify`.

```yaml
  - id: ai-implement
    ...
    on_success: build-verify       # было: approve-implementation
    on_failure: terminal-complete
```

---

## Ограничение числа попыток

Встроенного счётчика петли в runtime нет. Два варианта защиты от бесконечного цикла:

**Вариант A (рекомендуется сейчас):** Настроить `settingsService.getAiTimeoutSeconds()` — агент
получит таймаут и завершится. `on_failure` у `ai-fix-build-errors` уведёт в `terminal-complete`.

**Вариант B (future):** Добавить `max_attempts` в `NodeModel` + счётчик в `RunStepService`.
Отдельная задача, не блокирует текущую реализацию.

---

## Нагрузка на машину

| Что | Время | Когда |
|-----|-------|-------|
| `./gradlew compileJava` | ~20с | **внутри** инструкции агента, на каждом шаге |
| `./gradlew bootJar` | ~2-3 мин | **один раз** в `build-verify` перед approve |
| `npm run build` | ~30-60с | **один раз** в `build-verify` перед approve |

При 10-20 параллельных ранах: `bootJar` и `npm run build` запускаются только в конце флоу,
не во время основной работы агентов. Пик нагрузки — управляемый.

---

## Порядок реализации

1. `RunStepService.java` — правка 1 (1 строка)
2. `RunStepService.java` — правка 2 (~5 строк)
3. `node-command.schema.json` — добавить `on_failure`
4. Обновить `java-development-flow/1.0/FLOW.yaml` — добавить две ноды

**Проверка:**
- `cd backend && ./gradlew compileJava` — компилируется
- Запустить флоу с намеренно сломанным кодом → `build-verify` падает → `ai-fix-build-errors` получает stderr → фиксит → `build-verify` проходит → `approve-implementation`

---

## Файлы затронутые изменениями

| Файл | Изменение |
|------|-----------|
| `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunStepService.java` | строки 177 и 1362 |
| `backend/src/main/resources/schemas/node-command.schema.json` | добавить `on_failure` |
| `demo/flows/java-development-flow/1.0/FLOW.yaml` | добавить 2 ноды, изменить `on_success` у `ai-implement` |
