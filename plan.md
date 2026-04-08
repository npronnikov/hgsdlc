# План реализации

## Краткое резюме
Реализуем режим запуска для Product Owner через флаг `skip-gates`: он сохраняется в `run`, читается runtime и автоматически пропускает human-gates, кроме тех, где разрешена роль `PRODUCT_OWNER`. На фронтенде ссылка меню `#/run-launch` ведет в `#/product-pipeline` только для пользователя Product Owner без роли `ADMIN`; для `admin` и остальных ролей остается `#/run-launch`. План состоит из 7 атомарных шагов с backend-first порядком и отдельным блоком UI для inline-гейтов и последовательного редактирования артефактов.

## Архитектурные решения
- **Решение 1**: Флаг назвать именно `skip-gates` (в коде/БД: `skipGates` / `skip_gates`).
  Причина: прямое соответствие бизнес-правилу «пропускать гейты».
- **Решение 2**: Флаг хранить в `runs` как snapshot состояния на момент старта run.
  Причина: поведение не меняется, даже если роли пользователя изменятся после запуска.
- **Решение 3**: Логику skip реализовать в `RunStepService` до `openGate(...)`.
  Причина: единая серверная точка принятия решения, независимая от UI.
- **Решение 4**: Менять только целевую ссылку пункта меню `run-launch` для Product Owner без роли `ADMIN`, без глобального редиректа `run-console`.
  Причина: минимальные изменения навигации и меньше риск регрессий.
- **Решение 5**: Для `human_input` использовать `HumanFormViewer` для `form.json` (`kind: human-form`), остальные файлы редактировать в Monaco.
  Причина: переиспользование уже работающей валидации и редактора.

---

## Шаги реализации

### Шаг 1 — Добавить флаг `skip-gates` в runtime-модель (сложность: medium)
**Цель**: Ввести источник истины для runtime-режима пропуска гейтов.

**Файлы**:
- `backend/src/main/resources/db/changelog/041-runtime-skip-gates.sql` — добавить колонку `skip_gates BOOLEAN NOT NULL DEFAULT FALSE` в таблицу `runs`.
- `backend/src/main/resources/db/changelog/db.changelog-master.yaml` — подключить migration `041`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/domain/RunEntity.java` — добавить поле `skipGates`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/RuntimeStepTxService.java` — расширить `createRun(...)` параметром `skipGates`.

**Детали реализации**:
```java
// RunEntity.java
@Column(name = "skip_gates", nullable = false)
@Builder.Default
private boolean skipGates = false;

// RuntimeStepTxService.createRun(...)
RunEntity entity = RunEntity.builder()
    // ...
    .skipGates(skipGates)
    .build();
```

**Зависит от**: нет зависимостей

**Проверка**:
- `cd backend && ./gradlew compileJava`
- Старт приложения с Liquibase без ошибок.

---

### Шаг 2 — Прокинуть `skip-gates` в run при создании (сложность: medium)
**Цель**: Правильно устанавливать флаг в момент старта run.

**Файлы**:
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunLifecycleService.java` — вычислять `skipGates` по роли пользователя.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/RuntimeStepTxService.java` — принять и сохранить `skipGates`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/api/RuntimeController.java` — при необходимости вернуть `skip_gates` в `RunResponse` (для дебага/контракта).

**Детали реализации**:
```java
private boolean resolveSkipGates(User user) {
    // Базовое правило: true для PRODUCT_OWNER, false для остальных.
    // Если нужен stricter-режим (только PO-only), фиксируем отдельным решением до кода.
}

run = runtimeStepTxService.createRun(
  // ...,
  resolveSkipGates(user),
  // ...
);
```

**Зависит от**: шаг 1

**Проверка**:
- `cd backend && ./gradlew compileJava`
- Контракт `POST /api/runs` сохраняет run с ожидаемым значением `skip_gates`.

---

### Шаг 3 — Runtime: читать `skip-gates` и скипать не-PO ворота (сложность: high)
**Цель**: Автоматически пропускать human-gates, если run в `skip-gates` режиме и gate не назначен на `PRODUCT_OWNER`.

**Файлы**:
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunStepService.java` — добавить `shouldSkipGate(run, node)` и ветку auto-skip перед `openGate(...)`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/HumanInputNodeExecutor.java` — использовать новую ветку (через `RunStepService`).
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/HumanApprovalNodeExecutor.java` — использовать новую ветку (через `RunStepService`).
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/RuntimeStepTxService.java` — audit `gate_auto_skipped`.

**Детали реализации**:
```java
private boolean shouldSkipGate(RunEntity run, NodeModel node) {
    if (!run.isSkipGates()) return false;
    List<String> allowed = normalizedAllowedRoles(node.getAllowedRoles());
    return !allowed.contains("PRODUCT_OWNER");
}

// human_input skip:
createHumanInputOutputFiles(...);
markNodeExecutionSucceeded(...);
applyTransition(..., node.getOnSubmit(), "auto_on_submit");

// human_approval skip:
markNodeExecutionSucceeded(...);
applyTransition(..., node.getOnApprove(), "auto_on_approve");
```

**Зависит от**: шаги 1-2

**Проверка**:
- `cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeRegressionFlowTest`
- Кейсы:
  - `skip_gates=true` + gate `allowed_roles=[TECH_APPROVER]` => gate не открывается.
  - `skip_gates=true` + gate `allowed_roles=[PRODUCT_OWNER]` => run уходит в `WAITING_GATE`.

---

### Шаг 4 — Меню: `run-launch` -> `product-pipeline` только для Product Owner (сложность: low)
**Цель**: Выполнить требование навигации без изменения остальных маршрутов.

**Файлы**:
- `frontend/src/components/AppShell.jsx` — формировать `navItems` динамически: для Product Owner без роли `ADMIN` у пункта `Run Launch` ключ `'/product-pipeline'`, иначе `'/run-launch'`.
- `frontend/src/App.jsx` — роуты оставить существующими (`/run-launch` и `/product-pipeline`).

**Детали реализации**:
```jsx
const isProductOwnerOnly = userRoles.includes('PRODUCT_OWNER') && !userRoles.includes('ADMIN');
const BASE_NAV_ITEMS = [
  // ...
  { key: isProductOwnerOnly ? '/product-pipeline' : '/run-launch', icon: <PlayCircleOutlined />, label: 'Run Launch' },
  // ...
];
```

**Зависит от**: нет зависимостей

**Проверка**:
- `cd frontend && npm run build`
- Ручной сценарий:
  - `product_owner` (без `ADMIN`) видит `Run Launch`, переход ведет на `#/product-pipeline`.
  - `admin` и остальные роли по этому же пункту идут на `#/run-launch`.

---

### Шаг 5 — Inline human gates в центре `ProductPipeline` (сложность: high)
**Цель**: Показывать `human_input`/`human_approval` прямо в центре блока `Pipeline status`.

**Файлы**:
- `frontend/src/pages/ProductPipelineMvp.jsx` — вместо кнопки перехода рендерить inline panel для текущего gate.
- `frontend/src/styles.css` — стили для inline формы/редактора/кнопок в `.vp-live-run-panel`.
- `frontend/src/components/HumanFormViewer.jsx` — переиспользовать без изменения API.

**Детали реализации**:
```jsx
if (currentGate?.gate_kind === 'human_input') {
  return <ProductOwnerInputGatePanel gate={currentGate} runId={activeRunId} onDone={reloadRun} />;
}
if (currentGate?.gate_kind === 'human_approval') {
  return <ProductOwnerApprovalGatePanel gate={currentGate} runId={activeRunId} onDone={reloadRun} />;
}
```

**Зависит от**: шаг 4

**Проверка**:
- `cd frontend && npm run build`
- Активный gate не ведет на отдельную страницу, а отрисовывается в центре pipeline.

---

### Шаг 6 — Последовательное редактирование expected output для `human_input` (сложность: high)
**Цель**: Пользователь должен пройти все ожидаемые артефакты перед `submit-input`.

**Файлы**:
- `frontend/src/pages/ProductPipelineMvp.jsx` (или новый `frontend/src/components/pipeline/ProductOwnerInputGatePanel.jsx`) — очередь файлов, шаги `Next/Prev`, `editedByPath`.
- `frontend/src/components/HumanFormViewer.jsx` — для `form.json` (`kind: human-form`).
- `frontend/src/utils/monacoTheme.js` — Monaco для не-form файлов.

**Детали реализации**:
```jsx
const artifacts = gate.payload?.human_input_artifacts || [];
const [editedByPath, setEditedByPath] = useState({});
const allEdited = artifacts.every((a) => {
  const next = editedByPath[a.path];
  return next !== undefined && next !== (a.content || '');
});

<Button type="primary" disabled={!allEdited} onClick={submitInput}>Submit input</Button>
```

**Зависит от**: шаг 5

**Проверка**:
- Нельзя отправить input, пока не изменены все expected файлы.
- Для `form.json` включается `validateHumanForm`, и незаполненные required блокируют submit.

---

### Шаг 7 — Тесты и контракт (сложность: medium)
**Цель**: Закрепить behavior `skip-gates` и фронтовую навигацию.

**Файлы**:
- `backend/src/test/java/ru/hgd/sdlc/runtime/RuntimeRegressionFlowTest.java` — сценарии skip/не-skip по `allowed_roles`.
- `backend/src/test/java/ru/hgd/sdlc/runtime/RuntimeApiContractTest.java` — контракт run response (если добавляем `skip_gates`).
- `frontend/src/components/AppShell.jsx` (тест/ручной чек-лист) — проверка target menu-link по ролям.

**Детали реализации**:
```java
assertThat(run.isSkipGates()).isTrue();
assertThat(runtimeQueryService.findCurrentGate(runId)).isEmpty(); // для non-PO gate

assertThat(getRunEntity(runId).getStatus()).isEqualTo(RunStatus.WAITING_GATE); // для PO gate
```

**Зависит от**: шаги 1-6

**Проверка**:
- `cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeRegressionFlowTest`
- `cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeApiContractTest`
- `cd frontend && npm run build`

---

## Риски и способы их снижения

| Риск | Вероятность | Воздействие | Способ снижения |
|------|-------------|-------------|-----------------|
| Неопределено правило для multi-role пользователей (`ADMIN` + `PRODUCT_OWNER`) | low | high | Зафиксировано: при наличии `ADMIN` использовать `#/run-launch`; покрыть тестом menu routing. |
| Auto-skip нарушит обязательные переходы `on_submit/on_approve` | med | high | Делать skip через те же transition-ветки, что и ручное прохождение gate; проверить интеграционными тестами. |
| Меню-ссылка изменится не только для Product Owner | low | med | Внести условие строго в `userRoles.includes('PRODUCT_OWNER')` и проверить роль-зависимые сценарии вручную. |
| Пункт `4)` в запросе не задан | high | low | Считать как отсутствующее требование и не добавлять лишний scope без уточнения. |

## Критические точки

- Критическая точка 1: синхронизировать правило multi-role для меню и `skip-gates` (при `ADMIN` не включать PO-flow без явного решения).
- Критическая точка 2: корректный auto-skip для `human_input` (создание/фиксация артефактов + переход по `on_submit`).
- Критическая точка 3: UX последовательного редактирования expected output в `ProductPipelineMvp`.

## Итоговые критерии готовности

- [ ] `cd backend && ./gradlew test` — backend тесты проходят.
- [ ] `cd frontend && npm run build` — frontend собирается без ошибок.
- [ ] Для `PRODUCT_OWNER` без `ADMIN` пункт меню `Run Launch` ведет на `#/product-pipeline`.
- [ ] Для `admin` и остальных ролей пункт меню `Run Launch` ведет на `#/run-launch`.
- [ ] В `runs` хранится флаг `skip_gates`, и он корректно выставляется при создании run.
- [ ] Runtime при `skip_gates=true` пропускает human gates, кроме gates с `allowed_roles` включающим `PRODUCT_OWNER`.
- [ ] `human_input` в pipeline требует редактирования всех expected output перед submit.
- [ ] Для `form.json` рендерится форма, для остальных файлов используется Monaco editor.
