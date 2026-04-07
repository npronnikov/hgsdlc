# План реализации

## Краткое резюме
Нужно превратить текущий MVP `#/product-pipeline` в рабочий интерфейс для Product Owner, добавить типизированный режим `human_input` (форма вместо редактирования текста) и ввести режим запуска `idea_to_dev`, в котором автоматически пропускаются нецелевые gates. План состоит из 6 шагов: от изменения runtime-модели и политики gates до обновления Flow Editor и HumanGate UI. Основной риск — неоднозначность правила «запросы к владельцу проекта» и обратная совместимость существующих flow YAML.

## Архитектурные решения
- **Решение 1**: не вводить новый тип ноды для форм (`human_form_input`), а расширить существующий `human_input` опциональным контрактом формы.
Причина: runtime-логика переходов (`on_submit`) и ответственность ноды остаются прежними; минимальные изменения в роутинге нод и меньше риск регрессий.
- **Решение 2**: ввести явный `launch_mode` у run (`standard | idea_to_dev`) вместо эвристик по flow ID/route.
Причина: правило пропуска gates должно зависеть от режима запуска, а не от структуры flow; это прозрачно в API, аудитах и тестах.
- **Решение 3**: критерий «запрос к владельцу проекта» зафиксировать через `allowed_roles` (`PRODUCT_OWNER`) на gate-ноде.
Причина: механизм уже есть в backend (`assignee_role`, `enforceGateRole`), его нужно довести до editor/UI и использовать в gate-policy.

---

## Шаги реализации

### Шаг 1 — Режим запуска `idea_to_dev` в runtime API и БД (high)
**Цель**: добавить технический флаг запуска, от которого будет зависеть политика gates и поведение Product Pipeline.

**Файлы**:
- `backend/src/main/resources/db/changelog/041-runtime-launch-mode.sql` — добавить колонку `runs.launch_mode` (`VARCHAR(32) NOT NULL DEFAULT 'STANDARD'`).
- `backend/src/main/resources/db/changelog/db.changelog-master.yaml` — подключить новый changeset.
- `backend/src/main/java/ru/hgd/sdlc/runtime/domain/RunEntity.java` — добавить поле `launchMode`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/domain/RunLaunchMode.java` — новый enum.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/command/CreateRunCommand.java` — добавить `launchMode`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/api/RuntimeController.java` — расширить `RunCreateRequest`/`RunResponse` полем `launch_mode`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunLifecycleService.java` — нормализация и сохранение `launch_mode`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/RuntimeStepTxService.java` — запись `launch_mode` в `run_created` audit payload.

**Детали реализации**:
```java
public enum RunLaunchMode { STANDARD, IDEA_TO_DEV }

public record CreateRunCommand(..., String launchMode) {}

RunLaunchMode mode = resolveLaunchMode(command.launchMode());
entity.setLaunchMode(mode);
```

**Зависит от**: нет зависимостей

**Проверка**: API принимает и возвращает `launch_mode`.
- `cd backend && ./gradlew compileJava` — компилируется
- Интеграционный тест POST `/api/runs` c `launch_mode=idea_to_dev` создаёт run c нужным полем

---

### Шаг 2 — Политика автопропуска gates для `idea_to_dev` (high)
**Цель**: реализовать правило: в `idea_to_dev` пропускаем все gates, кроме `human_approval` и `human_input` для `PRODUCT_OWNER`.

**Файлы**:
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunStepService.java` — добавить `shouldSkipGate(...)` и ветку auto-continue в `openGate(...)`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/HumanInputNodeExecutor.java` — без изменения контракта, но покрыть новый сценарий через `openGate`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/HumanApprovalNodeExecutor.java` — убедиться, что approval никогда не авто-скипается.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/RuntimeStepTxService.java` — добавить audit event `gate_auto_skipped` (или использовать `appendAudit`).

**Детали реализации**:
```java
boolean skip = run.getLaunchMode() == RunLaunchMode.IDEA_TO_DEV
    && gateKind == GateKind.HUMAN_INPUT
    && !nodeAllowsRole(node, "PRODUCT_OWNER");

if (skip) {
    createHumanInputOutputFiles(...);
    runtimeStepTxService.markNodeExecutionSucceeded(...);
    runtimeStepTxService.appendAudit(..., "gate_auto_skipped", ...);
    applyTransition(run, execution, null, node.getOnSubmit(), "on_submit");
    return true;
}
```

**Зависит от**: шаг 1

**Проверка**: разные типы gates ведут себя по политике.
- `cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeRegressionFlowTest`
- Новый тест: `idea_to_dev + human_input (FLOW_CONFIGURATOR)` => gate не открывается
- Новый тест: `idea_to_dev + human_input (PRODUCT_OWNER)` => gate открывается
- Новый тест: `idea_to_dev + human_approval` => gate открывается

---

### Шаг 3 — Контракт типизированной формы для `human_input` (backend/schema) (medium)
**Цель**: дать `human_input` режим формы (JSON-данные + schema), сохранив совместимость с текущим текстовым режимом.

**Файлы**:
- `backend/src/main/java/ru/hgd/sdlc/flow/domain/NodeModel.java` — добавить поля `input_form_schema` и `input_form_ui_schema` (`JsonNode`).
- `backend/src/main/resources/schemas/node-human-input-gate.schema.json` — описать новые опциональные поля.
- `backend/src/main/java/ru/hgd/sdlc/flow/application/FlowValidator.java` — проверка согласованности: если есть `input_form_schema`, то это объект, и `produced_artifacts` совместимы с JSON-выводом формы.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunStepService.java` — прокинуть `input_form_schema`/`input_form_ui_schema` в gate payload.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/GateDecisionService.java` — (опционально для этапа 1 формы) валидировать submitted JSON against schema.

**Детали реализации**:
```java
@JsonProperty("input_form_schema")
private JsonNode inputFormSchema;

if (node.getInputFormSchema() != null) {
    payload.put("input_form_schema", node.getInputFormSchema());
    payload.put("input_form_ui_schema", node.getInputFormUiSchema());
}
```

**Зависит от**: шаг 1

**Проверка**: flow c `input_form_schema` валиден и schema приходит в `/api/runs/{id}` -> `current_gate.payload`.
- `cd backend && ./gradlew test --tests ru.hgd.sdlc.flow.FlowValidatorTest`
- Контрактный тест runtime response с `input_form_schema`

---

### Шаг 4 — Flow Editor: настройка роли gate и режима формы (frontend) (high)
**Цель**: сделать конфигурацию формы и `allowed_roles` доступной из UI, чтобы flow не требовал ручного редактирования YAML.

**Файлы**:
- `frontend/src/utils/flowSerializer.js` — parse/serialize для `allowed_roles`, `input_form_schema`, `input_form_ui_schema`.
- `frontend/src/hooks/useFlowEditor.js` — включить новые поля в `buildNodeData` и `buildFlowYaml`.
- `frontend/src/components/flow/NodeEditPanel.jsx` — добавить:
  - выбор `allowed_roles` для gates,
  - переключатель режима `human_input`: `text` / `form`,
  - JSON editors для schema/ui-schema.
- `frontend/src/utils/flowValidator.js` — клиентские проверки схемы формы и ролей.

**Детали реализации**:
```javascript
if (selectedNodeKind === 'human_input') {
  // UI mode
  // inputMode: 'text' | 'form'
  // allowedRoles: ['PRODUCT_OWNER']
}

if (data.inputFormSchema) {
  lines.push('    input_form_schema:');
  JSON.stringify(data.inputFormSchema, null, 2).split('\n').forEach(...);
}
```

**Зависит от**: шаг 3

**Проверка**: editor сохраняет и повторно загружает flow без потери новых полей.
- `cd frontend && npm run build`
- Ручная проверка: создать `human_input` с `allowed_roles=[PRODUCT_OWNER]` и schema, сохранить, открыть снова

---

### Шаг 5 — HumanGate UI: форма вместо текстового редактора для `human_input` (medium)
**Цель**: на активном gate показывать динамическую форму, если node сконфигурирован как form-input.

**Файлы**:
- `frontend/src/pages/HumanGate.jsx` — условный рендер формового режима по `gate.payload.input_form_schema`.
- `frontend/src/components/ActionCenter.jsx` — синхронно поддержать form-mode для текущего gate summary card (если используется).
- `frontend/src/styles.css` — стили form-layout (валидаторы, ошибки, группировка).

**Детали реализации**:
```javascript
const formSchema = gate?.payload?.input_form_schema;
const isFormInput = isInput && !!formSchema;

if (isFormInput) {
  // render JSON-schema form
  // on submit -> serialize answers to JSON artifact, content_base64
} else {
  // existing editable text artifact mode
}
```

**Зависит от**: шаг 3

**Проверка**: `human_input` без schema работает по-старому, со schema — как форма.
- `cd frontend && npm run build`
- Ручной e2e: запуск flow с формой, submit input, переход по `on_submit`

---

### Шаг 6 — Product Owner UX для `#/product-pipeline` и запуск в `idea_to_dev` (medium)
**Цель**: сделать `product-pipeline` реальным входом для PO и связать его с новым launch mode.

**Файлы**:
- `frontend/src/App.jsx` — role-guard для route `product-pipeline` (`PRODUCT_OWNER`, `ADMIN`), опционально default redirect для PO.
- `frontend/src/components/AppShell.jsx` — показывать пункт меню `Idea to Dev` только разрешённым ролям.
- `frontend/src/pages/ProductPipelineMvp.jsx` — заменить mock data на API (`/projects`, `/flows`), запуск через `POST /runs` с `launch_mode: "idea_to_dev"`.
- `frontend/src/pages/RunLaunch.jsx` — опционально добавить selector `launch_mode` для унификации API (или оставить `standard` по умолчанию).

**Детали реализации**:
```javascript
await apiRequest('/runs', {
  method: 'POST',
  body: JSON.stringify({
    project_id,
    flow_canonical_name,
    feature_request,
    publish_mode,
    launch_mode: 'idea_to_dev',
    idempotency_key: crypto.randomUUID(),
  }),
});
```

**Зависит от**: шаги 1 и 2

**Проверка**: PO запускает run из `#/product-pipeline`, runtime применяет policy gates.
- `cd frontend && npm run build`
- Ручная проверка с пользователем `product_owner/admin`

---

## Риски и способы их снижения

| Риск | Вероятность | Воздействие | Способ снижения |
|------|-------------|-------------|-----------------|
| Нечёткая трактовка «запросы к владельцу проекта» (по роли/по типу ноды/по проекту) | med | high | Зафиксировать в ТЗ: это `human_input` + `allowed_roles` содержит `PRODUCT_OWNER` |
| Добавление `launch_mode` ломает snapshot/API тесты | high | med | Обновить baseline snapshots централизованно, добавить backward-совместимый default `STANDARD` |
| Flow Editor может потерять поля при сохранении (allowed_roles/form schema) | high | high | Добавить round-trip тесты serialize/parse и ручной сценарий save->reload |
| Форма в `HumanGate` невалидно сериализует payload | med | med | Валидация schema на фронте и backend-проверка JSON перед `submit-input` |
| Автопропуск `human_input` сломает downstream ожидания артефактов | med | high | При skip всегда выполнять `createHumanInputOutputFiles` + интеграционный тест на downstream node |

## Критические точки

- Решение по семантике «запрос к владельцу проекта» (роль `PRODUCT_OWNER` в `allowed_roles`) должно быть утверждено до шага 2.
- Миграция БД (`launch_mode`) блокирует backend-часть и e2e запуск `idea_to_dev`.
- Round-trip сохранения Flow Editor (новые поля) критичен до включения feature для пользователей.

## Итоговые критерии готовности

- [ ] `cd backend && ./gradlew test` — все backend тесты проходят
- [ ] `cd frontend && npm run build` — frontend собирается без ошибок
- [ ] `#/product-pipeline` доступен только `PRODUCT_OWNER`/`ADMIN` и запускает run с `launch_mode=idea_to_dev`
- [ ] В `idea_to_dev` открываются только `human_approval` и `human_input` для `PRODUCT_OWNER`; остальные human_input автопропускаются
- [ ] `human_input` поддерживает 2 режима: legacy text-edit и typed form (по schema)
- [ ] Новые поля flow (`allowed_roles`, `input_form_schema`, `input_form_ui_schema`) не теряются при save/reload
