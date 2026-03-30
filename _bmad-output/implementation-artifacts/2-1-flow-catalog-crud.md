---
story_id: "2.1"
story_key: "2-1-flow-catalog-crud"
epic: 2
status: "ready-for-dev"
date_created: "2026-03-30"
---

# Story 2-1: Flow Catalog CRUD — 2-уровневый каталог

## Контекст эпика

**Epic 2: Каталог воркфлоу** — Пользователи могут просматривать, искать и управлять версионированными шаблонами воркфлоу в каталоге организации и своего workspace.

**FRs этой истории:** FR15, FR16, FR17, FR18

### Текущее состояние (brownfield)

| FR | Статус | Детали |
|----|--------|--------|
| FR15 | ⚠️ | Flow CRUD реализован (`/api/flows/{flowId}/save`); публикация есть; RBAC-проверок нет |
| FR16 | ❌ | Workspace-уровень каталога отсутствует — нет колонок `scope` и `workspace_id` в таблице `flows` |
| FR17 | ✅ | Просмотр воркфлоу с описанием и версиями — работает |
| FR18 | ✅ | Полная история версий, опубликованные неизменяемы — работает |

**Что делает эта история:** добавляет 2-уровневый каталог (SYSTEM / WORKSPACE) к существующему flow-модулю. RBAC деферрируется на Epic 1.

---

## Пользовательская история

**Как** Пользователь платформы,
**Я хочу** видеть воркфлоу каталога организации (SYSTEM) и каталога своего workspace (WORKSPACE) отдельно,
**Чтобы** Team Lead мог создавать приватные шаблоны внутри своего workspace, не загромождая общий каталог.

---

## Критерии приёмки (BDD)

### Сценарий 1: scope=SYSTEM сохраняется и возвращается

**Given** пользователь сохраняет flow с `"scope": "SYSTEM"`
**When** `GET /api/flows/{flowId}`
**Then** в ответе `"scope": "SYSTEM"`, `"workspace_id": null`

### Сценарий 2: scope=WORKSPACE сохраняется и возвращается

**Given** пользователь сохраняет flow с `"scope": "WORKSPACE"` и `"workspace_id": "550e8400-e29b-41d4-a716-446655440000"`
**When** `GET /api/flows/{flowId}`
**Then** в ответе `"scope": "WORKSPACE"`, `"workspace_id": "550e8400-e29b-41d4-a716-446655440000"`

### Сценарий 3: scope=WORKSPACE без workspace_id → 400

**Given** запрос save с `"scope": "WORKSPACE"` и без `"workspace_id"`
**When** `POST /api/flows/{flowId}/save`
**Then** HTTP 400 с сообщением об обязательном workspace_id

### Сценарий 4: фильтрация по scope=SYSTEM

**Given** в БД есть flow `f1` (scope=SYSTEM) и `f2` (scope=WORKSPACE, workspace_id=W1)
**When** `GET /api/flows/query?scope=SYSTEM`
**Then** ответ содержит `f1`; не содержит `f2`

### Сценарий 5: фильтрация по workspace_id возвращает SYSTEM + свои WORKSPACE

**Given** flows: `f1` (SYSTEM), `f2` (WORKSPACE, workspace_id=W1), `f3` (WORKSPACE, workspace_id=W2)
**When** `GET /api/flows/query?workspaceId=W1`
**Then** ответ содержит `f1` и `f2`; не содержит `f3`

### Сценарий 6: без фильтров возвращает всё

**When** `GET /api/flows/query` (без scope/workspaceId)
**Then** ответ содержит flows любого scope

### Сценарий 7: существующие flows после миграции — scope=SYSTEM

**Given** существующие flows в БД до применения миграции 033
**When** Liquibase применяет `033-flows-scope.sql`
**Then** у всех строк `scope = 'SYSTEM'`, `workspace_id = NULL`

### Сценарий 8: frontend отображает scope-тег

**Given** Flows-страница загружена
**When** список flows отображается
**Then** каждый flow имеет Ant Design `Tag`: SYSTEM → синий (`blue`), WORKSPACE → зелёный (`green`)

---

## Технические задачи

### 1. Liquibase-миграция 033

**Файл:** `backend/src/main/resources/db/changelog/033-flows-scope.sql`

```sql
-- Добавить поддержку 2-уровневого каталога (SYSTEM / WORKSPACE) к таблице flows
-- workspace_id FK constraint НЕ добавляется здесь — таблица workspaces
-- будет создана в Epic 1, Story 1-1 (миграция 034+)

ALTER TABLE flows ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE flows ADD COLUMN workspace_id UUID;

CREATE INDEX idx_flows_scope ON flows(scope);
CREATE INDEX idx_flows_workspace_id ON flows(workspace_id);
```

**Регистрация в мастер-changelog:**
Файл: `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
Добавить в конец списка (после строки с `032-rules-flows-publication-pipeline.sql`):
```yaml
  - include:
      file: db/changelog/033-flows-scope.sql
```

### 2. Новый enum FlowScope

**Файл:** `backend/src/main/java/ru/hgd/sdlc/flow/domain/FlowScope.java`

```java
package ru.hgd.sdlc.flow.domain;

public enum FlowScope {
    SYSTEM,
    WORKSPACE
}
```

### 3. Обновление сущности FlowVersion

**Файл:** `backend/src/main/java/ru/hgd/sdlc/flow/domain/FlowVersion.java`

Добавить поля после `lifecycleStatus` (перед полями publication pipeline):

```java
@Enumerated(EnumType.STRING)
@Column(name = "scope", nullable = false, length = 16)
private FlowScope scope;

@Column(name = "workspace_id")
private UUID workspaceId;
```

**СТОП — НЕ ТРОГАТЬ:** поле `resourceVersion` с `@Version` — это существующий brownfield-код. НЕ удалять `@Version`, несмотря на правило anti-pattern в project-context.md. Правило применяется к НОВЫМ сущностям.

### 4. Обновление FlowSaveRequest

**Файл:** `backend/src/main/java/ru/hgd/sdlc/flow/api/FlowSaveRequest.java`

Добавить поля в конец record (после `resource_version`):

```java
@JsonProperty("scope") String scope,              // опционально; default SYSTEM
@JsonProperty("workspace_id") String workspaceId  // требуется если scope=WORKSPACE
```

### 5. Обновление FlowResponse и FlowSummaryResponse

**Файл FlowResponse:** `backend/src/main/java/ru/hgd/sdlc/flow/api/FlowResponse.java`

Добавить поля (после `saved_at`):
```java
@JsonProperty("scope") String scope,
@JsonProperty("workspace_id") UUID workspaceId
```

Обновить статический фабричный метод `FlowResponse.from(FlowVersion version, FlowModel model)`:
```java
.scope(version.getScope() != null ? version.getScope().name() : "SYSTEM")
.workspaceId(version.getWorkspaceId())
```

**Файл FlowSummaryResponse:** `backend/src/main/java/ru/hgd/sdlc/flow/api/FlowSummaryResponse.java`

Аналогично — добавить `scope` и `workspace_id`; обновить метод `from(FlowVersion version, Integer nodeCount)`.

### 6. Обновление FlowCatalogQuery (nested record в FlowService)

**Файл:** `backend/src/main/java/ru/hgd/sdlc/flow/application/FlowService.java`

`FlowCatalogQuery` — это nested record внутри `FlowService`. Добавить поля в конец:

```java
public record FlowCatalogQuery(
    // ... существующие поля без изменений ...
    String cursor,
    Integer limit,
    String search,
    String codingAgent,
    String teamCode,
    String platformCode,
    String flowKind,
    String riskLevel,
    String environment,
    String approvalStatus,
    String contentSource,
    String visibility,
    String lifecycleStatus,
    String tag,
    String status,
    String version,
    Boolean hasDescription,
    // --- НОВЫЕ ПОЛЯ ---
    FlowScope scope,       // nullable; если задан — строгая фильтрация по scope
    UUID workspaceId       // nullable; если задан — вернуть SYSTEM + WORKSPACE для этого workspace
) {}
```

### 7. Обновление FlowService

**Метод `queryLatestForCatalog()`:** передать `query.scope()` и `query.workspaceId()` в вызов репозитория.

**Метод `save()`:** добавить логику scope перед созданием/обновлением entity:

```java
// Определить scope
FlowScope scopeValue = (request.scope() != null && !request.scope().isBlank())
    ? FlowScope.valueOf(request.scope().toUpperCase())
    : FlowScope.SYSTEM;

// Валидация: WORKSPACE требует workspace_id
if (scopeValue == FlowScope.WORKSPACE && (request.workspaceId() == null || request.workspaceId().isBlank())) {
    throw new ValidationException("workspace_id обязателен при scope=WORKSPACE");
}

UUID workspaceUuid = (scopeValue == FlowScope.WORKSPACE)
    ? UUID.fromString(request.workspaceId())
    : null;
```

Установить поля в entity: `entity.setScope(scopeValue)`, `entity.setWorkspaceId(workspaceUuid)`.

### 8. Обновление FlowVersionRepository

**Файл:** `backend/src/main/java/ru/hgd/sdlc/flow/infrastructure/FlowVersionRepository.java`

Метод `queryLatestForCatalog()` — добавить параметры и логику фильтрации в native SQL:

**Добавить параметры в сигнатуру метода:**
```java
@Param("scope") FlowScope scope,
@Param("workspaceId") UUID workspaceId
```

**Добавить в SQL WHERE-блок** (перед `ORDER BY`):
```sql
AND (
    (:workspaceId IS NULL AND (:scope IS NULL OR scope = :scope))
    OR
    (:workspaceId IS NOT NULL AND (scope = 'SYSTEM' OR workspace_id = :workspaceId))
)
```

**Критично:** `workspace_id` передаётся как `UUID` параметр — Spring Data конвертирует его корректно без `::cast`. Проверено совместимо с H2 MODE=PostgreSQL и PostgreSQL. НЕ использовать `::cast`, `ILIKE`.

### 9. Обновление FlowController

**Файл:** `backend/src/main/java/ru/hgd/sdlc/flow/api/FlowController.java`

В методе `query()` добавить параметры:

```java
@RequestParam(required = false) FlowScope scope,
@RequestParam(required = false) UUID workspaceId
```

Пробросить в `FlowService.FlowCatalogQuery(...)` — добавить `scope` и `workspaceId` в конструктор record.

### 10. Интеграционный тест

**Файл:** `backend/src/test/java/ru/hgd/sdlc/flow/FlowCatalogScopeTest.java`

Аннотации: `@SpringBootTest`, Testcontainers PostgreSQL (НЕ H2).

Тест-кейсы:
1. `saveSYSTEM_scope_persisted()` — сохранить flow scope=SYSTEM, get → scope=SYSTEM, workspace_id=null
2. `saveWORKSPACE_scope_persisted()` — сохранить flow scope=WORKSPACE + workspaceId, get → оба поля корректны
3. `saveWORKSPACE_without_workspaceId_returns400()` — ValidationException при scope=WORKSPACE без workspaceId
4. `query_scopeSystem_excludes_workspace_flows()` — query?scope=SYSTEM не возвращает WORKSPACE flow
5. `query_workspaceId_returns_system_plus_own_workspace()` — query?workspaceId=X возвращает SYSTEM + WORKSPACE(X), НЕ возвращает WORKSPACE(Y)
6. `idempotency_save_same_key_returns_same_result()` — повторный POST с тем же Idempotency-Key

---

### 11. Frontend — Flows.jsx

**Файл:** `frontend/src/pages/Flows.jsx`

**Добавить scope-фильтр** в панель фильтров рядом с существующими:
```jsx
<Select
  allowClear
  placeholder="Scope"
  style={{ width: 120 }}
  value={filters.scope}
  onChange={(v) => setFilters(f => ({ ...f, scope: v }))}
  options={[
    { value: 'SYSTEM', label: 'SYSTEM' },
    { value: 'WORKSPACE', label: 'WORKSPACE' },
  ]}
/>
```

**Добавить scope-тег** на каждую карточку/строку flow:
```jsx
{flow.scope === 'WORKSPACE'
  ? <Tag color="green">WORKSPACE</Tag>
  : <Tag color="blue">SYSTEM</Tag>
}
```

**Передать scope в query-параметры** при вызове `apiRequest('/api/flows/query', ...)`.

### 12. Frontend — FlowEditor.jsx

**Файл:** `frontend/src/pages/FlowEditor.jsx`

Добавить в форму сохранения (рядом с полем `visibility`):

```jsx
<Form.Item label="Scope">
  <Select
    value={formData.scope || 'SYSTEM'}
    onChange={(v) => setFormData(d => ({ ...d, scope: v, workspace_id: v === 'SYSTEM' ? null : d.workspace_id }))}
    options={[
      { value: 'SYSTEM', label: 'SYSTEM — общий каталог' },
      { value: 'WORKSPACE', label: 'WORKSPACE — каталог workspace' },
    ]}
  />
</Form.Item>

{formData.scope === 'WORKSPACE' && (
  <Form.Item label="Workspace ID">
    <Input
      placeholder="UUID workspace"
      value={formData.workspace_id || ''}
      onChange={(e) => setFormData(d => ({ ...d, workspace_id: e.target.value }))}
    />
  </Form.Item>
)}
```

Добавить `scope` и `workspace_id` в тело запроса `FlowSaveRequest`.

---

## Архитектурные гарантии — что обязательно соблюдать

| Правило | Применение |
|---------|------------|
| Пакеты `ru.hgd.sdlc.flow.{api,application,domain,infrastructure}` | Все новые файлы только в этих пакетах |
| `@Enumerated(EnumType.STRING)` | На поле `scope` в FlowVersion |
| `@JsonProperty("snake_case")` | `"scope"`, `"workspace_id"` |
| UUID параметры в native SQL без `::cast` | `workspace_id` — тип `UUID`, не `String` |
| H2/PostgreSQL совместимость | Проверить WHERE-блок на H2 (`LOWER() + LIKE`, не `ILIKE`) |
| Транзакции только в `application/` | save() в FlowService — `@Transactional` |
| Нет `@ControllerAdvice` | `@ExceptionHandler` в FlowController (уже есть) |
| Нет `@PreAuthorize` в этой истории | RBAC деферрируется на Epic 1 |
| Frontend: `apiRequest()`, не `fetch()` | Все API-вызовы через `src/api/request.js` |
| Frontend: Ant Design компоненты | `Tag`, `Select`, `Input`, `Form.Item` |
| Frontend: `.jsx`, не `.tsx` | Файлы Flows.jsx, FlowEditor.jsx |
| Маршруты только через App.jsx | Маршруты `/flows` и `/flows/:flowId` не меняются |

## Антипаттерны — ЗАПРЕЩЕНО

- **НЕ** добавлять FK constraint в миграции 033 — таблица `workspaces` ещё не существует
- **НЕ** удалять `@Version` с поля `resourceVersion` в FlowVersion — это brownfield-код
- **НЕ** редактировать существующие Liquibase SQL-файлы (032 и ниже)
- **НЕ** нумеровать новую миграцию ниже 033
- **НЕ** использовать `ILIKE` или `::cast` в нативном SQL
- **НЕ** создавать отдельный файл `FlowCatalogQuery.java` — это nested record в FlowService
- **НЕ** добавлять `@PreAuthorize`, `@RolesAllowed` — RBAC не в этой истории
- **НЕ** добавлять TypeScript, Redux/Zustand, новые npm-зависимости
- **НЕ** добавлять роуты минуя App.jsx

---

## Зависимости

| Зависимость | Тип | Статус |
|------------|-----|--------|
| Liquibase миграции 001–032 | Предшествующие | ✅ существуют |
| Таблица `workspaces` (Epic 1, Story 1-1) | FK constraint для `workspace_id` | ⏳ добавить FK в миграции 034+ после Story 1-1 |
| RBAC middleware (Epic 1, RBAC Story) | Проверки ролей на save/publish | ⏳ не в этой истории |

**Эта история не имеет блокирующих зависимостей.** Можно реализовывать немедленно.

---

## Контекст для dev-агента

**Обязателен к прочтению перед реализацией:** `_bmad-output/project-context.md`

**Ключевые файлы для изменения:**

| Файл | Действие |
|------|----------|
| `backend/src/main/resources/db/changelog/033-flows-scope.sql` | СОЗДАТЬ |
| `backend/src/main/resources/db/changelog/db.changelog-master.yaml` | ДОБАВИТЬ строку с 033 |
| `backend/src/main/java/ru/hgd/sdlc/flow/domain/FlowScope.java` | СОЗДАТЬ |
| `backend/src/main/java/ru/hgd/sdlc/flow/domain/FlowVersion.java` | ДОБАВИТЬ поля scope, workspace_id |
| `backend/src/main/java/ru/hgd/sdlc/flow/api/FlowSaveRequest.java` | ДОБАВИТЬ scope, workspace_id |
| `backend/src/main/java/ru/hgd/sdlc/flow/api/FlowResponse.java` | ДОБАВИТЬ scope, workspace_id; обновить from() |
| `backend/src/main/java/ru/hgd/sdlc/flow/api/FlowSummaryResponse.java` | ДОБАВИТЬ scope, workspace_id; обновить from() |
| `backend/src/main/java/ru/hgd/sdlc/flow/application/FlowService.java` | ДОБАВИТЬ поля в FlowCatalogQuery; обновить save(), queryLatestForCatalog() |
| `backend/src/main/java/ru/hgd/sdlc/flow/infrastructure/FlowVersionRepository.java` | ОБНОВИТЬ нативный SQL + параметры |
| `backend/src/main/java/ru/hgd/sdlc/flow/api/FlowController.java` | ДОБАВИТЬ scope, workspaceId в query() |
| `backend/src/test/java/ru/hgd/sdlc/flow/FlowCatalogScopeTest.java` | СОЗДАТЬ |
| `frontend/src/pages/Flows.jsx` | ДОБАВИТЬ scope-фильтр и scope-тег |
| `frontend/src/pages/FlowEditor.jsx` | ДОБАВИТЬ scope-поле и workspace_id-поле |

**Команды запуска для проверки:**
- Backend: `./gradlew bootRun` из `backend/` — H2 in-memory, порт 8080
- Frontend: `npm install && npm run dev` из `frontend/`
- Тесты: `./gradlew test` из `backend/` (интеграционные требуют Docker для Testcontainers)

---

**Status:** ready-for-dev
_Анализ выполнен на основе: epics.md, architecture.md, project-context.md, api-contracts-backend.md, data-models-backend.md, ui-components-frontend.md, FlowVersion.java, FlowController.java, FlowService.java (FlowCatalogQuery), FlowVersionRepository.java, db.changelog-master.yaml._
