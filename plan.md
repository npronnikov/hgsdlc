# План реализации

## Краткое резюме
Реализуем retry для уже упавшего `run` при валидации AI-ноды: тот же `run_id`, новая попытка (`attempt_no + 1`) на той же ноде, без изменения instruction и входов. План состоит из 6 шагов (backend API/logic, frontend диалог, тесты, документация). Основной риск: повторный запуск из «грязного» workspace может снова падать по тем же причинам; в MVP фиксируем поведение как in-place retry и явно покрываем это тестами.

## Архитектурные решения
- **Решение 1**: Не создаем новый `run`, используем тот же `run_id`, а новую попытку выражаем через новый `node_execution` с увеличенным `attempt_no`.
  Причина: это уже встроено в модель runtime (`node_executions.attempt_no`) и сохраняет сквозной аудит в рамках одного запуска.
- **Решение 2**: Добавляем отдельный endpoint `POST /api/runs/{runId}/retry`.
  Причина: не перегружаем `resume` (другая семантика) и не смешиваем с `publish/retry`.
- **Решение 3**: В MVP retry не меняет instruction/input и не открывает форму редактирования.
  Причина: соответствует текущему требованию «просто другая попытка» и минимизирует UI/контракт.
- **Решение 4**: Строгий backend guard: retry разрешен только при `run.status=FAILED`, `run.error_code=NODE_VALIDATION_FAILED`, и последняя failed execution для текущей ноды — `node_kind=ai`.
  Причина: кнопка должна работать только для целевого сценария и быть защищена сервером.

---

## Шаги реализации

### Шаг 1 — Добавить backend-команду retry failed validation (сложность: medium)
**Цель**: Добавить серверный use-case «перезапустить run с той же AI-ноды после validation failure».

**Файлы**:
- `backend/src/main/java/ru/hgd/sdlc/runtime/api/RuntimeController.java` — добавить endpoint `POST /runs/{runId}/retry`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RuntimeCommandService.java` — добавить метод `retryRun(UUID runId, User user)`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunLifecycleService.java` — добавить orchestration-метод retry.

**Детали реализации**:
```java
@PostMapping("/runs/{runId}/retry")
public RunResponse retryRun(@PathVariable UUID runId, @AuthenticationPrincipal User user) {
    RunEntity run = runtimeCommandService.retryRun(runId, user);
    runtimeCommandService.dispatchProcessRunStep(runId);
    return RunResponse.from(run, runtimeQueryService.findCurrentGate(runId).map(this::toGateSummary).orElse(null));
}
```

**Зависит от**: нет зависимостей

**Проверка**:
- `cd backend && ./gradlew compileJava` — компиляция без ошибок.
- `POST /api/runs/{runId}/retry` возвращает `200` и `run.status=running` для валидного кейса.

---

### Шаг 2 — Реализовать серверные preconditions и перевод run в RUNNING (сложность: high)
**Цель**: Безопасно переводить только подходящий failed run обратно в выполнение и запускать новую attempt.

**Файлы**:
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/RuntimeStepTxService.java` — добавить транзакционный метод reset для retry + audit событие.
- `backend/src/main/java/ru/hgd/sdlc/runtime/infrastructure/NodeExecutionRepository.java` — добавить query для последней failed AI execution по run/node.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunLifecycleService.java` — проверка условий и вызов tx-метода.

**Детали реализации**:
```java
// RunLifecycleService
if (run.getStatus() != RunStatus.FAILED || !"NODE_VALIDATION_FAILED".equals(run.getErrorCode())) {
    throw new ConflictException("Retry is allowed only for failed AI node validation");
}
NodeExecutionEntity failedAi = nodeExecutionRepository
    .findFirstByRunIdAndNodeIdAndNodeKindAndStatusAndErrorCodeOrderByAttemptNoDesc(
        run.getId(), run.getCurrentNodeId(), "ai", NodeExecutionStatus.FAILED, "NODE_VALIDATION_FAILED")
    .orElseThrow(() -> new ConflictException("No failed AI validation execution found for retry"));

runtimeStepTxService.resetRunForValidationRetry(run.getId(), failedAi.getNodeId(), actorId, failedAi.getAttemptNo());
```

```java
// RuntimeStepTxService
run.setStatus(RunStatus.RUNNING);
run.setCurrentNodeId(nodeId);
run.setErrorCode(null);
run.setErrorMessage(null);
run.setFinishedAt(null);
appendAuditInternal(runId, null, null, "run_retry_requested", ActorType.HUMAN, actorId,
    Map.of("reason", "node_validation_failed", "node_id", nodeId, "previous_attempt_no", previousAttemptNo));
```

**Зависит от**: шаг 1

**Проверка**:
- Повторный `POST /retry` на уже запущенном run получает `409`.
- После retry создается новый `node_execution` с `attempt_no = previous + 1`.

---

### Шаг 3 — Добавить кнопку и confirm-диалог в Run Console (сложность: medium)
**Цель**: Дать пользователю простой UX для retry без редактирования instruction/input.

**Файлы**:
- `frontend/src/pages/RunConsole.jsx` — вычислить `canRetryAiValidation`, добавить кнопку и `Modal.confirm`, вызвать новый endpoint.

**Детали реализации**:
```jsx
const canRetryAiValidation = run.status === 'failed'
  && run.error_code === 'NODE_VALIDATION_FAILED'
  && latestFailedNode?.node_kind === 'ai';

Modal.confirm({
  title: 'Retry AI node attempt?',
  content: 'Runtime will start a new attempt with the same instruction and inputs.',
  okText: 'Retry',
  cancelText: 'Cancel',
  onOk: retryAiValidation,
});
```

**Зависит от**: шаги 1-2

**Проверка**:
- Кнопка видна только в целевом состоянии (`FAILED + NODE_VALIDATION_FAILED + failed AI node`).
- Диалог не содержит полей редактирования.
- После подтверждения UI обновляется и run переходит в `running`.

---

### Шаг 4 — Интеграционные тесты retry сценария (сложность: high)
**Цель**: Зафиксировать поведение retry на уровне runtime API и state transitions.

**Файлы**:
- `backend/src/test/java/ru/hgd/sdlc/runtime/RuntimeRegressionFlowTest.java` — добавить тест retry после AI validation failure.
- `backend/src/test/java/ru/hgd/sdlc/runtime/RuntimeApiContractTest.java` — добавить контрактные проверки нового endpoint (успех + отказ).

**Детали реализации**:
```java
// 1) Создать flow, где AI нода стабильно падает на validateNodeOutputs (required expected_mutation)
// 2) Дождаться FAILED + NODE_VALIDATION_FAILED
// 3) POST /api/runs/{runId}/retry
// 4) Проверить, что появился новый execution той же node_id с attempt_no=2
// 5) Проверить audit event run_retry_requested
```

**Зависит от**: шаги 1-2

**Проверка**:
- `cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeRegressionFlowTest`
- `cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeApiContractTest`

---

### Шаг 5 — Минимальная синхронизация документации runtime (сложность: low)
**Цель**: Обновить описание state machine и API с новым retry-переходом для failed validation.

**Файлы**:
- `docs/developer-onboarding.md` — добавить переход `FAILED -> RUNNING : retry (only NODE_VALIDATION_FAILED on AI node)` и endpoint `/api/runs/{runId}/retry`.

**Детали реализации**:
```markdown
FAILED --> RUNNING : POST /api/runs/{runId}/retry
note right of FAILED: only when error_code == NODE_VALIDATION_FAILED and failed node is AI
```

**Зависит от**: шаги 1-3

**Проверка**:
- Документация отражает фактический API и ограничения retry.

---

### Шаг 6 — Финальная проверка end-to-end (сложность: medium)
**Цель**: Убедиться, что backend+frontend работают согласованно и без регрессий publish retry.

**Файлы**:
- `backend/src/main/java/ru/hgd/sdlc/runtime/api/RuntimeController.java`
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunLifecycleService.java`
- `frontend/src/pages/RunConsole.jsx`

**Детали реализации**:
```bash
cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeRegressionFlowTest
cd frontend && npm run build
```

**Зависит от**: шаги 1-5

**Проверка**:
- Retry publish (`/publish/retry`) не сломан.
- Retry failed validation работает только для нужного кейса.
- Run history показывает attempts (`#1`, `#2`, ...).

---

## Риски и способы их снижения

| Риск | Вероятность | Воздействие | Способ снижения |
|------|-------------|-------------|-----------------|
| Ложноположительный показ кнопки в UI | med | med | Дублировать проверку на backend; в UI проверять и `run.error_code`, и `latestFailedNode.node_kind`. |
| Двойной клик по retry создаст гонку | med | med | Проверка `run.status==FAILED` в tx + optimistic locking; возвращать `409` при повторе. |
| Retry в «грязном» workspace снова фейлится | high | med | Явно задокументировать MVP-поведение (in-place retry), добавить аудит и диагностику по attempt. |
| Регрессия в существующем publish retry | low | high | Отдельные endpoint-и, отдельные тесты на оба сценария. |
| Отсутствует `requirements.md` как единый источник требований | high | low | Зафиксировать допущения из диалога в этом плане и подтвердить их перед реализацией. |

## Критические точки

- Корректный backend guard для retry: это главный барьер от неконсистентных state transitions.
- Точка запуска после retry: обязательно триггерить `dispatchProcessRunStep`, иначе run останется в `RUNNING` без прогресса.
- Тестовый сценарий validation failure должен быть детерминированным, иначе тесты будут flaky.

## Итоговые критерии готовности

- [ ] `POST /api/runs/{runId}/retry` реализован и документирован.
- [ ] Retry доступен только для `FAILED + NODE_VALIDATION_FAILED` и только когда упала AI-нода.
- [ ] Новый retry создает новую `attempt_no` для той же ноды, `run_id` не меняется.
- [ ] В UI есть confirm-диалог retry без полей редактирования instruction/input.
- [ ] `cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeRegressionFlowTest` проходит.
- [ ] `cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeApiContractTest` проходит.
- [ ] `cd frontend && npm run build` проходит.
