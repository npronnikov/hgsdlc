# Code Generation Plan — embeddings-for-skills-and-rules

## Unit Context
- **Stories covered**: FR-1, FR-2 (генерация embeddings), FR-3, FR-4 (поиск похожих), FR-8 (миграция), FR-9 (обновление embeddings)
- **Dependencies on other units**: Нет (самодостаточная функциональность)
- **Workspace root**: /tmp/workspace/78d41bb5-f87e-419e-a8ad-558b137d4b64
- **Project type**: Brownfield (расширение существующего Spring Boot + React приложения)

---

## Step 1 — Schema Migration (complexity: medium)
**Goal**: Добавить поддержку векторов в таблицы skills и rules с использованием pgvector

- [x] **Create** `backend/src/main/resources/db/changelog/048-embeddings-support.sql`
  - Установить расширение pgvector: `CREATE EXTENSION IF NOT EXISTS vector;`
  - Добавить колонку в таблицу skills: `ALTER TABLE skills ADD COLUMN embedding_vector vector(384);`
  - Добавить колонку в таблицу rules: `ALTER TABLE rules ADD COLUMN embedding_vector vector(384);`
  - Создать индекс для skills: `CREATE INDEX idx_skills_embedding_vector ON skills USING ivfflat (embedding_vector vector_cosine_ops) WITH (lists = 100);`
  - Создать индекс для rules: `CREATE INDEX idx_rules_embedding_vector ON rules USING ivfflat (embedding_vector vector_cosine_ops) WITH (lists = 100);`

- [x] **Modify** `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
  - Добавить include для `048-embeddings-support.sql`

**Verification**: Liquibase применяет миграцию без ошибок на чистой БД

---

## Step 2 — Domain Entities (complexity: low)
**Goal**: Расширить существующие сущности для поддержки embedding vectors

- [x] **Modify** `backend/src/main/java/ru/hgd/sdlc/skill/domain/SkillVersion.java`
  - Добавить поле `private float[] embeddingVector;`
  - Добавить аннотацию `@Column(name = "embedding_vector", columnDefinition = "vector")`
  - Добавить getter и setter для `embeddingVector`

- [x] **Modify** `backend/src/main/java/ru/hgd/sdlc/rule/domain/RuleVersion.java`
  - Добавить поле `private float[] embeddingVector;`
  - Добавить аннотацию `@Column(name = "embedding_vector", columnDefinition = "vector")`
  - Добавить getter и setter для `embeddingVector`

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/common/embedding/domain/EntityType.java`
  - Enum: `SKILL`, `RULE`

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/common/embedding/domain/EmbeddingProviderType.java`
  - Enum: `LOCAL` (Sentence-BERT), `OPENAI`

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/common/embedding/domain/SimilarItem.java`
  - Record с полями: `id`, `itemId`, `version`, `name`, `description`, `similarityScore`, `tags`, `teamCode`, `scope`

**Verification**: `./gradlew compileJava` выполняется успешно

---

## Step 3 — Repository Layer (complexity: medium)
**Goal**: Добавить методы для векторного поиска в существующие репозитории

- [x] **Modify** `backend/src/main/java/ru/hgd/sdlc/skill/infrastructure/SkillVersionRepository.java`
  - Добавить метод для поиска похожих по ID (native SQL query с pgvector):
    ```java
    @Query(value = """
        SELECT s.id, s.skill_id, s.version, s.name, s.description,
               1 - (s.embedding_vector <=> CAST(:queryVector AS vector)) as similarity
        FROM skills s
        WHERE s.status = 'PUBLISHED'
          AND s.id != :currentId
          AND s.embedding_vector IS NOT NULL
          AND 1 - (s.embedding_vector <=> CAST(:queryVector AS vector)) > :threshold
        ORDER BY s.embedding_vector <=> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSimilarById(@Param("currentId") UUID currentId,
                                    @Param("queryVector") String queryVector,
                                    @Param("threshold") float threshold,
                                    @Param("limit") int limit);
    ```
  - Добавить метод для поиска по произвольному тексту (аналогичный запрос)

- [x] **Modify** `backend/src/main/java/ru/hgd/sdlc/rule/infrastructure/RuleVersionRepository.java`
  - Добавить аналогичные методы для rules

**Verification**: Компиляция проходит успешно

---

## Step 4 — Service Layer (complexity: high)
**Goal**: Реализовать бизнес-логику генерации embeddings и поиска похожих элементов

### 4.1 Embedding Provider Infrastructure

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/common/embedding/application/EmbeddingProvider.java`
  - Interface с методами: `float[] generateEmbedding(String text)`, `int getDimension()`, `String getProviderName()`

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/common/embedding/application/LocalEmbeddingProvider.java`
  - Реализация для Sentence-BERT (384-dim)
  - Использует DL4J или Python bridge для генерации

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/common/embedding/application/OpenAIEmbeddingProvider.java`
  - Реализация для OpenAI API (1536-dim)
  - Вызов `text-embedding-ada-002`

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/common/embedding/application/EmbeddingService.java`
  - Сервис с multi-provider стратегией и fallback
  - Метод: `float[] generateEmbedding(String text)` с retry логикой

### 4.2 Embedding Services for Skills

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/skill/application/SkillEmbeddingService.java`
  - Метод `@Async void generateEmbedding(UUID skillId)` - асинхронная генерация
  - Метод `List<SimilarItem> findSimilar(UUID skillId, float threshold, int limit)` - поиск похожих
  - Метод `List<SimilarItem> findSimilarByText(String text, float threshold, int limit)` - поиск по тексту
  - Обработка ошибок (non-blocking)

### 4.3 Embedding Services for Rules

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/rule/application/RuleEmbeddingService.java`
  - Аналогично SkillEmbeddingService, но для rules

### 4.4 Migration Service

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/common/embedding/application/EmbeddingMigrationService.java`
  - Метод `@PostConstruct void migratePublishedSkills()` - миграция skills
  - Метод `@PostConstruct void migratePublishedRules()` - миграция rules
  - Проверка флага завершения миграции через SystemSetting
  - Логирование прогресса каждые 10 элементов
  - Обработка ошибок без прерывания миграции

### 4.5 Update Existing Services

- [x] **Modify** `backend/src/main/java/ru/hgd/sdlc/skill/application/SkillService.java`
  - Вызов `skillEmbeddingService.generateEmbeddingAsync(skillId)` после сохранения
  - Проверка изменения markdown перед регенерацией

- [x] **Modify** `backend/src/main/java/ru/hgd/sdlc/rule/application/RuleService.java`
  - Аналогичные изменения для rules

**Verification**: Компиляция проходит успешно

---

## Step 5 — API Layer (complexity: medium)
**Goal**: Создать REST endpoints для поиска похожих элементов

- [ ] **Modify** `backend/src/main/java/ru/hgd/sdlc/skill/api/SkillController.java`
  - Добавить record `SimilarSkillsResponse(List<SimilarItem> similarSkills, int total)`
  - Добавить endpoint `GET /api/skills/{id}/similar` - поиск похожих по ID
  - Добавить endpoint `GET /api/skills/similar?text=...` - поиск похожих по тексту
  - Добавить record `SimilarSkillsByTextRequest(String text)`

- [ ] **Modify** `backend/src/main/java/ru/hgd/sdlc/rule/api/RuleController.java`
  - Аналогичные endpoints для rules

**Verification**: `./gradlew bootRun` запускается успешно, endpoints доступны

---

## Step 6 — Unit Tests (complexity: medium)
**Goal**: Покрыть бизнес-логику тестами

- [ ] **Create** `backend/src/test/java/ru/hgd/sdlc/common/embedding/application/EmbeddingServiceTest.java`
  - Тест генерации embedding
  - Тест fallback логики
  - Тест обработки пустых значений

- [ ] **Create** `backend/src/test/java/ru/hgd/sdlc/skill/application/SkillEmbeddingServiceTest.java`
  - Тест поиска похожих по ID
  - Тест поиска похожих по тексту
  - Тест обработки отсутствия embedding
  - Mock репозиториев и EmbeddingProvider

- [ ] **Create** `backend/src/test/java/ru/hgd/sdlc/rule/application/RuleEmbeddingServiceTest.java`
  - Аналогичные тесты для rules

**Verification**: `./gradlew test --tests "*Embedding*"` выполняется успешно

---

## Step 7 — Integration Tests (complexity: high)
**Goal**: Проверить работу с реальной БД (PostgreSQL + pgvector)

- [ ] **Create** `backend/src/test/java/ru/hgd/sdlc/skill/application/SkillEmbeddingServiceIntegrationTest.java`
  - Использовать Testcontainers с `pgvector/pgvector:pg16`
  - Тест создания skill с генерацией embedding
  - Тест поиска похожих (с реальными векторами)
  - Тест миграции существующих skills

- [ ] **Create** `backend/src/test/java/ru/hgd/sdlc/skill/api/SkillControllerIntegrationTest.java`
  - Тест endpoint `GET /api/skills/{id}/similar`
  - Тест endpoint `GET /api/skills/similar?text=...`
  - Проверка статус кодов (200, 404, 500)

- [ ] **Create** `backend/src/test/java/ru/hgd/sdlc/rule/api/RuleControllerIntegrationTest.java`
  - Аналогичные тесты для rules

**Verification**: `./gradlew test --tests "*IntegrationTest"` выполняется успешно

---

## Step 8 — Configuration (complexity: low)
**Goal**: Настроить приложение для работы с embeddings

- [x] **Modify** `backend/src/main/resources/application.yml`
  - Добавить секцию `embedding:` с настройками:
    ```yaml
    embedding:
      provider: local
      local:
        model-name: all-MiniLM-L6-v2
        dimension: 384
      openai:
        api-key: ${OPENAI_API_KEY}
        model: text-embedding-ada-002
        dimension: 1536
      similarity:
        threshold: 0.6
        max-results: 10
      async:
        core-pool-size: 4
        max-pool-size: 8
        queue-capacity: 100
    ```

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/common/embedding/infrastructure/AsyncConfig.java`
  - Конфигурация `@EnableAsync`
  - Bean `embeddingTaskExecutor` с настройками из application.yml

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/common/embedding/infrastructure/EmbeddingConfig.java`
  - Конфигурация выбора провайдера через `@ConditionalOnProperty`

- [x] **Create** `backend/src/main/java/ru/hgd/sdlc/common/embedding/infrastructure/EmbeddingProviderHealthIndicator.java`
  - Health check для Actuator

**Verification**: `./gradlew bootRun` запускается успешно, конфигурация загружается

---

## Step 9 — Frontend Components (complexity: medium)
**Goal**: Создать UI для отображения похожих элементов

- [ ] **Create** `frontend/src/components/SimilarSkills.jsx`
  - Компонент `SimilarSkills` с props: `skillId`, `onSkillSelect`
  - Использовать `List` и `Card` из Ant Design
  - Отображать `similarityPercent` через `Progress`
  - Обработка пустого списка

- [ ] **Create** `frontend/src/components/SimilarRules.jsx`
  - Аналогичный компонент для rules

- [ ] **Modify** `frontend/src/api/skills.js`
  - Добавить функцию `getSimilarSkills(skillId)` - `GET /api/skills/{id}/similar`
  - Добавить функцию `getSimilarSkillsByText(text)` - `GET /api/skills/similar?text=...`

- [ ] **Modify** `frontend/src/api/rules.js`
  - Аналогичные функции для rules

- [ ] **Modify** `frontend/src/pages/SkillEditor.jsx`
  - Добавить компонент `<SimilarSkills skillId={skillId} />` в детальную карточку
  - Загрузка данных при `useEffect`

- [ ] **Modify** `frontend/src/pages/RuleEditor.jsx`
  - Добавить компонент `<SimilarRules ruleId={ruleId} />` в детальную карточку

**Verification**: `npm run dev` запускается успешно, UI отображается корректно

---

## Step 10 — Dependencies Configuration (complexity: low)
**Goal**: Добавить необходимые зависимости

- [ ] **Modify** `backend/build.gradle.kts`
  - Добавить зависимость для DL4J или Python bridge (для Sentence-BERT)
  - Добавить зависимость для OpenAI SDK (опционально)

**Verification**: `./gradlew build` выполняется успешно

---

## Story Traceability
| Story | Implemented in Steps |
|-------|---------------------|
| FR-1: Генерация Embeddings для Skills | 2, 4, 5, 8, 10 |
| FR-2: Генерация Embeddings для Rules | 2, 4, 5, 8, 10 |
| FR-3: Поиск Похожих Skills | 3, 4, 5, 6, 7 |
| FR-4: Похожих Rules | 3, 4, 5, 6, 7 |
| FR-8: Миграция Существующих Данных | 1, 4, 7 |
| FR-9: Обновление Embeddings | 4, 5 |
| FR-5: Отображение Похожих Skills в UI | 9 |
| FR-6: Отображение Похожих Rules в UI | 9 |

---

## Risk Notes
- **Risk 1: pgvector extension** — Убедиться, что Docker Compose использует образ `pgvector/pgvector:pg16`
  - **Mitigation**: Проверить `deploy/compose.dev.yaml` перед запуском

- **Risk 2: Sentence-BERT model size** — Модель занимает ~400 MB RAM
  - **Mitigation**: Мониторинг использования памяти, настройка heap size

- **Risk 3: Асинхронная генерация** — Потенциальная потеря задач при рестарте
  - **Mitigation**: Логирование стартованных задач, проверка наличия embedding при миграции

- **Risk 4: Векторная размерность** — При смене провайдера требуется миграция данных
  - **Mitigation**: Зафиксировать один провайдер (LOCAL) как primary, хранить версию в SystemSetting

- **Risk 5: Testcontainers performance** — Интеграционные тесты медленнее
  - **Mitigation**: Параллельное выполнение тестов, reuse контейнеров

---

## Execution Notes
- **Order**: Выполнять шаги строго по номерам (1 → 2 → ... → 10)
- **Verification**: После каждого шага запускать указанную команду верификации
- **Testing**: Unit тесты (шаг 6) писать параллельно с сервисами (шаг 4)
- **Configuration**: Шаг 8 выполнить перед шагом 4 (сервисы зависят от конфигурации)
- **Frontend**: Шаг 9 можно выполнять параллельно с backend шагами (независимый код)

---

**Document End**
