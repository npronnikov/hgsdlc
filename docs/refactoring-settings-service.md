# Рефакторинг SettingsService

## Проблема

`SettingsService` (~1500 LOC) делает слишком много:
- хранит и обновляет runtime-настройки (workspace_root, coding_agent, timeout, prompt_language)
- хранит и обновляет catalog-настройки (repo_url, branch, git credentials)
- клонирует/синхронизирует зеркало каталога через git
- сканирует файлы в mirror, парсит метаданные
- валидирует контент rule/skill/flow против JSON-схем
- делает upsert rule/skill/flow в БД с идемпотентностью по checksum
- управляет repair-lock и repair-режимами (FROM_SCRATCH, INCREMENTAL)

Это осложняет тестирование и создаёт риск регрессий в runtime settings при изменениях каталога.

---

## Целевая архитектура

```
SettingsService          ← остаётся, только runtime + catalog URL/credentials
CatalogService           ← новый фасад: orchestrate repair, dispatch по entity type
CatalogValidationService ← валидация схем rule/skill/flow
CatalogUpsertService     ← upsert rule/skill/flow + checksum-идемпотентность
CatalogGitService        ← git clone/fetch/reset mirror
```

Внешний API (`/api/settings/catalog/repair`) не меняется.

---

## Этапы

### Этап 1 — Выделить `CatalogGitService` (самый изолированный)

**Что переносим из SettingsService:**
- `syncMirror(repoUrl, branch, mirrorRoot)` и всё что она вызывает
- `resolveCatalogMirrorPath(workspaceRoot, repoUrl)`
- все вспомогательные git-методы (clone, fetch, reset)

**Новый класс:**
```java
@Service
public class CatalogGitService {
    private final SettingsService settingsService; // только для getWorkspaceRoot()

    public Path syncAndGetMirrorPath(String repoUrl, String branch) { ... }
    // внутри: resolveCatalogMirrorPath + syncMirror
}
```

**SettingsService после:** вызывает `catalogGitService.syncAndGetMirrorPath(...)` в `repairCatalog`.

**Риски:**
- git-операции не оборачиваются в `@Transactional` — убедиться, что это сохраняется
- `resolveCatalogMirrorPath` использует `getWorkspaceRoot()` — пробрасывать значение параметром, не через сервис

---

### Этап 2 — Выделить `CatalogValidationService`

**Что переносим:**
- `validateRuleContentAgainstSchema(ParsedMetadata)` (строки ~687-710)
- `validateSkillContentAgainstSchema(ParsedMetadata)` (строки ~712+)
- `validateFlowContentAgainstSchema(ParsedMetadata)` (строки ~678-685)
- зависимости: `FlowYamlParser`, `JsonSchemaValidator`, `MarkdownFrontmatterParser`

**Новый класс:**
```java
@Service
public class CatalogValidationService {
    private final FlowYamlParser flowYamlParser;
    private final JsonSchemaValidator schemaValidator;
    private final MarkdownFrontmatterParser frontmatterParser;

    public void validateRule(ParsedMetadata metadata) { ... }
    public void validateSkill(ParsedMetadata metadata) { ... }
    public void validateFlow(ParsedMetadata metadata) { ... }
}
```

**Риски:**
- Дублирование с frontend-валидацией (`validateFlow` в FlowEditor.jsx) — это нормально, они разные уровни
- Убедиться, что `FlowYamlParser` и `MarkdownFrontmatterParser` не тянут за собой circular dependency через SettingsService

---

### Этап 3 — Выделить `CatalogUpsertService`

**Что переносим:**
- `upsertRule(ParsedMetadata, actorId)` + `applyRuleFields(...)` (строки ~478-527)
- `upsertSkill(ParsedMetadata, actorId)` + `applySkillFields(...)` + `replaceSkillFilesFromCatalog(...)` (строки ~529-617)
- `upsertFlow(ParsedMetadata, actorId)` + `applyFlowFields(...)` (строки ~619-676)
- зависимости: `RuleVersionRepository`, `SkillVersionRepository`, `SkillFileRepository`, `FlowVersionRepository`, `SkillPackageService`

**Новый класс:**
```java
@Service
public class CatalogUpsertService {
    // репозитории для rule/skill/flow
    // + CatalogValidationService

    @Transactional
    public UpsertOutcome upsertRule(ParsedMetadata metadata, String actorId) { ... }

    @Transactional
    public UpsertOutcome upsertSkill(ParsedMetadata metadata, String actorId) { ... }

    @Transactional
    public UpsertOutcome upsertFlow(ParsedMetadata metadata, String actorId) { ... }
}
```

**Транзакционная граница:** каждый upsert — своя `@Transactional`. Общая repair-транзакция не нужна и не была нужна (repair в SettingsService не помечен как одна транзакция по факту из-за NOT_SUPPORTED).

**Попутная правка — убрать дублирование `applyXFields`:**

`applyRuleFields`, `applySkillFields`, `applyFlowFields` идентичны на 80%. Общие поля:
`id`, `version`, `canonicalName`, `status`, `title`, `description`, `codingAgent`, `checksum`, `teamCode`, `platformCode`, `tags`, `scope`, `approvalStatus`, `approvedBy`, `approvedAt`, `publishedAt`, `sourceRef`, `sourcePath`, `forkedFrom`, `forkedBy`, `lifecycleStatus`, `savedBy`, `savedAt`

Вариант: `interface CatalogEntity` + метод `applyCommonCatalogFields(CatalogEntity, ParsedMetadata, String actorId)`. Специфичные поля (content, startNodeId, ruleRefs) — в конкретных методах. Это не обязательно делать сразу, но удобно при переносе.

---

### Этап 4 — Выделить `CatalogService` как фасад

**Что переносим из SettingsService:**
- `repairCatalog(actorId, modeRaw)` (строки ~354-476) — основная repair-оркестрация
- `purgeCatalogIndex()` (если есть)
- `scanMetadataFiles(mirrorRoot)`, `parseMetadata(...)` и вспомогательные парсеры
- `repairLock` — переезжает сюда

**Новый класс:**
```java
@Service
public class CatalogService {
    private final SettingsService settingsService;     // getWorkspaceRoot(), getCatalogRepoUrl(), etc.
    private final CatalogGitService catalogGitService;
    private final CatalogUpsertService catalogUpsertService;
    private final ReentrantLock repairLock = new ReentrantLock();

    public RepairResult repairCatalog(String actorId, String modeRaw) {
        // lock → git sync → optional purge → scan → dispatch upsert → return result
    }
}
```

**SettingsController** (`/api/settings/catalog/repair`) переключается с `SettingsService` на `CatalogService`. Это единственное изменение во внешнем API.

---

### Этап 5 — Зачистка SettingsService

После переноса SettingsService содержит только:
- константы ключей настроек
- геттеры/сеттеры для runtime-настроек (workspace, codingAgent, aiTimeout, promptLanguage)
- геттеры/сеттеры для catalog-настроек (repoUrl, branch, credentials, localGit)
- `getRuntimeSettings()` / `updateRuntimeSettings()` / `updateCatalogSettings()`
- `upsert(key, value, actorId)` — вспомогательный приватный метод

Итоговый размер: ~350-400 LOC.

---

## Порядок выполнения

```
Этап 1: CatalogGitService       — нет зависимостей на других новых классах
Этап 2: CatalogValidationService — нет зависимостей на других новых классах
Этап 3: CatalogUpsertService    — зависит от CatalogValidationService
Этап 4: CatalogService          — зависит от 1, 3
Этап 5: зачистка SettingsService — последним
```

После каждого этапа: запустить `./gradlew test`, проверить repair через API вручную.

---

## Риски и предосторожности

| Риск | Митигация |
|------|-----------|
| repair-lock переезжает — разные инстансы CatalogService | lock объявить как `@Bean` с `@Singleton`, либо оставить как поле — Spring по умолчанию singleton |
| `@Transactional` на `repairCatalog` конфликтует с git-операциями | В SettingsService нет `@Transactional` на `repairCatalog` — проверить и сохранить это поведение |
| SettingsController инжектирует SettingsService напрямую | После Этапа 4 добавить `CatalogService` как второй инжект в контроллер, не удалять `SettingsService` |
| Circular dependency: CatalogService → SettingsService → CatalogService | Не возникает, т.к. SettingsService не знает о CatalogService |
| Дублирование валидации с FlowYamlParser | FlowYamlParser делает структурную валидацию YAML, CatalogValidationService — schema-валидацию. Оба нужны, дублирования нет |
