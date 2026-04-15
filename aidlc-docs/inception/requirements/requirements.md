# Requirements Document: Embeddings for Skills and Rules

**Document Version:** 1.0  
**Date:** 2026-04-15  
**Author:** AI Requirements Agent  
**Status:** Draft

---

## 1. Request Classification

| Attribute | Value |
|-----------|-------|
| **Clarity** | Medium (partially clarified through questions) |
| **Type** | New Feature |
| **Scope** | Multiple Components (backend API, database, frontend UI) |
| **Complexity** | Complex (involves ML/embeddings integration) |

**Original Request:**  
Добавь embeddings для скилов и рулов, чтобы показывать "похожие" скилы

---

## 2. Summary of Understanding

Пользователь хочет добавить функционал поиска "похожих" навыков (skills) и правил (rules) с использованием векторных embeddings. Система должна:

1. Генерировать embeddings на основе контента skills/rules (markdown документации)
2. Хранить embeddings в базе данных
3. Предоставлять API для поиска похожих элементов
4. Показывать "похожие" элементы в UI (в детальной карточке)
5. Помогать пользователям избегать дублирования при создании новых skills/rules

**Key Decisions from Requirements Clarification:**

- **Data Source:** Только `skillMarkdown`/`ruleMarkdown` (без name/description)
- **Generation Timing:** При сохранении draft (статус DRAFT)
- **UI Placement:** Только в детальной карточке skill/rule
- **Result Format:** Список с процентом сходства (similarity score)
- **Creation Mode:** Показывать похожие при создании/редактировании как подсказку
- **Ask Agent Integration:** Нет, только статический список
- **Scope:** Только skills и rules (flows не включены)

**Assumptions for Unanswered Questions:**

Для вопросов, на которые не были получены ответы, установлены следующие значения по умолчанию:

- **Embedding Service:** Внешний API (OpenAI/Cohere) с fallback на локальную модель (Sentence-BERT)
- **Max Generation Time:** 5-10 секунд (асинхронная обработка)
- **Max Results:** Топ-10 похожих элементов
- **Similarity Threshold:** > 0.6 (средний порог)
- **Multilingual:** Использовать multilingual модель
- **Quality Control:** Дополнительная фильтрация по metadata (tags, teamCode, scope)
- **User Intent:** Для информирования (reference, inspiration) и для выбора существующего вместо создания нового
- **No Results:** Показывать "No similar skills found" message
- **Access Control:** Всем аутентифицированным пользователям
- **Feedback:** Implicit analytics (какие рекомендации кликают)
- **Business Goal:** Уменьшение дублирования + улучшение reuse
- **Cost Constraints:** Использовать бесплатную локальную модель (Sentence-BERT)
- **Success Metric:** Time saving при создании новых навыков
- **Monetization:** Только внутреннее использование
- **Storage:** Новая колонка `embedding_vector` в таблицах `skills` и `rules` (тип vector с pgvector)
- **PostgreSQL Extension:** Да, использовать pgvector для vector similarity search
- **API Endpoint:** Новый endpoint `GET /api/skills/{id}/similar` (и для rules)
- **Batch Processing:** Да, нужна миграция для всех существующих PUBLISHED элементов
- **Update Strategy:** Пересчитывать embedding при каждом save (event-driven)
- **Caching:** Нет, вычислять каждый раз заново (real-time)
- **Backward Compatibility:** Расширить существующие responses с null/empty values для старых элементов
- **Quality Testing:** Manual testing с curated dataset
- **Maintainability:** Flexible architecture для легкой замены модели

---

## 3. Affected Files

### Backend

#### New Files to Create

```
backend/src/main/java/ru/hgd/sdlc/skill/application/SkillEmbeddingService.java
backend/src/main/java/ru/hgd/sdlc/rule/application/RuleEmbeddingService.java
backend/src/main/java/ru/hgd/sdlc/common/embedding/EmbeddingService.java
backend/src/main/java/ru/hgd/sdlc/common/embedding/EmbeddingProvider.java
backend/src/main/java/ru/hgd/sdlc/common/embedding/LocalEmbeddingProvider.java
backend/src/main/java/ru/hgd/sdlc/common/embedding/OpenAIEmbeddingProvider.java
backend/src/main/java/ru/hgd/sdlc/common/embedding/SimilarityCalculator.java
backend/src/main/resources/config/embedding.properties
```

#### Files to Modify

```
backend/src/main/java/ru/hgd/sdlc/skill/api/SkillController.java
backend/src/main/java/ru/hgd/sdlc/skill/application/SkillService.java
backend/src/main/java/ru/hgd/sdlc/skill/domain/SkillVersion.java
backend/src/main/java/ru/hgd/sdlc/skill/infrastructure/SkillVersionRepository.java

backend/src/main/java/ru/hgd/sdlc/rule/api/RuleController.java
backend/src/main/java/ru/hgd/sdlc/rule/application/RuleService.java
backend/src/main/java/ru/hgd/sdlc/rule/domain/RuleVersion.java
backend/src/main/java/ru/hgd/sdlc/rule/infrastructure/RuleVersionRepository.java

backend/build.gradle.kts
backend/src/main/resources/db/changelog/db.changelog-master.yaml
```

#### Liquibase Migration

```
backend/src/main/resources/db/changelog/047-embeddings-support.sql
```

### Frontend

#### New Files to Create

```
frontend/src/components/SimilarSkills.jsx
frontend/src/components/SimilarRules.jsx
frontend/src/api/skills.js (extend)
frontend/src/api/rules.js (extend)
```

#### Files to Modify

```
frontend/src/pages/SkillEditor.jsx
frontend/src/pages/RuleEditor.jsx
```

---

## 4. Functional Requirements

### FR-1: Генерация Embeddings для Skills

**Description:** Система должна генерировать векторное представление (embedding) для каждого skill на основе его `skillMarkdown`.

**Acceptance Criteria:**
- Embedding генерируется при сохранении skill со статусом DRAFT
- Используется только контент из поля `skillMarkdown`
- Embedding сохраняется в колонку `embedding_vector` таблицы `skills`
- Если `skillMarkdown` пустой, embedding не генерируется
- Размер вектора: 384 (для Sentence-BERT) или 1536 (для OpenAI)

**Priority:** High

---

### FR-2: Генерация Embeddings для Rules

**Description:** Система должна генерировать векторное представление (embedding) для каждого rule на основе его `ruleMarkdown`.

**Acceptance Criteria:**
- Embedding генерируется при сохранении rule со статусом DRAFT
- Используется только контент из поля `ruleMarkdown`
- Embedding сохраняется в колонку `embedding_vector` таблицы `rules`
- Если `ruleMarkdown` пустой, embedding не генерируется
- Размер вектора: 384 (для Sentence-BERT) или 1536 (для OpenAI)

**Priority:** High

---

### FR-3: Поиск Похожих Skills

**Description:** Система должна предоставлять API для поиска похожих skills на основе косинусного сходства их embeddings.

**Acceptance Criteria:**
- Endpoint: `GET /api/skills/{id}/similar`
- Возвращает топ-10 похожих PUBLISHED skills
- Каждый результат содержит: id, name, description, similarityScore
- Исключает сам элемент из результатов
- Фильтрация по: tags, teamCode, scope (опционально)
- Порог схожести: > 0.6 (настраиваемый)
- Сортировка по убыванию similarityScore

**Priority:** High

---

### FR-4: Поиск Похожих Rules

**Description:** Система должна предоставлять API для поиска похожих rules на основе косинусного сходства их embeddings.

**Acceptance Criteria:**
- Endpoint: `GET /api/rules/{id}/similar`
- Возвращает топ-10 похожих PUBLISHED rules
- Каждый результат содержит: id, name, description, similarityScore
- Исключает сам элемент из результатов
- Фильтрация по: tags, teamCode, scope (опционально)
- Порог схожести: > 0.6 (настраиваемый)
- Сортировка по убыванию similarityScore

**Priority:** High

---

### FR-5: Отображение Похожих Skills в UI

**Description:** В детальной карточке skill должна отображаться секция с похожими skills.

**Acceptance Criteria:**
- Секция "Similar Skills" в SkillEditor
- Показывает список из топ-10 похожих PUBLISHED skills
- Каждый элемент: карточка с name, description, similarityScore (%)
- Ссылка на детальную страницу похожего skill
- Если результатов нет: "No similar skills found"
- Загрузка данных через API при открытии skill

**Priority:** Medium

---

### FR-6: Отображение Похожих Rules в UI

**Description:** В детальной карточке rule должна отображаться секция с похожими rules.

**Acceptance Criteria:**
- Секция "Similar Rules" в RuleEditor
- Показывает список из топ-10 похожих PUBLISHED rules
- Каждый элемент: карточка с name, description, similarityScore (%)
- Ссылка на детальную страницу похожего rule
- Если результатов нет: "No similar rules found"
- Загрузка данных через API при открытии rule

**Priority:** Medium

---

### FR-7: Похожие Элементы при Создании

**Description:** При создании нового skill/rule система должна показывать похожие существующие элементы как подсказку для избежания дублирования.

**Acceptance Criteria:**
- В форме создания skill/rule добавлена секция "Similar existing skills"
- Показывается после ввода markdown контента (debounce)
- Использует временный embedding для текущего черновика
- Показывает топ-5 похожих PUBLISHED элементов
- Позволяет перейти к существующему элементу вместо создания нового

**Priority:** Low

---

### FR-8: Миграция Существующих Данных

**Description:** Все существующие PUBLISHED skills/rules должны получить embeddings после развертывания функции.

**Acceptance Criteria:**
- Liquibase миграция добавляет колонку `embedding_vector`
- Batch job генерирует embeddings для всех PUBLISHED элементов
- Логирование прогресса миграции
- Обработка ошибок без прерывания миграции

**Priority:** High

---

### FR-9: Обновление Embeddings

**Description:** При изменении markdown контента skill/rule embedding должен быть пересчитан.

**Acceptance Criteria:**
- Пересчитывается при каждом save (event-driven)
- Асинхронная обработка (не блокирует save)
- Обновление колонки `embedding_vector` в БД
- Логирование ошибок генерации

**Priority:** High

---

## 5. API Requirements

### API-1: GET /api/skills/{id}/similar

**Description:** Получить список похожих skills

**Request:**
```
GET /api/skills/{id}/similar
```

**Response (200 OK):**
```json
{
  "similarSkills": [
    {
      "id": "uuid",
      "skillId": "string",
      "version": "string",
      "name": "string",
      "description": "string",
      "similarityScore": 0.85,
      "tags": ["string"],
      "teamCode": "string",
      "scope": "string"
    }
  ],
  "total": 10
}
```

**Error Responses:**
- 404 Not Found: Skill не найден
- 500 Internal Server Error: Ошибка поиска

---

### API-2: GET /api/rules/{id}/similar

**Description:** Получить список похожих rules

**Request:**
```
GET /api/rules/{id}/similar
```

**Response (200 OK):**
```json
{
  "similarRules": [
    {
      "id": "uuid",
      "ruleId": "string",
      "version": "string",
      "name": "string",
      "description": "string",
      "similarityScore": 0.85,
      "tags": ["string"],
      "teamCode": "string",
      "scope": "string"
    }
  ],
  "total": 10
}
```

**Error Responses:**
- 404 Not Found: Rule не найден
- 500 Internal Server Error: Ошибка поиска

---

### API-3: GET /api/skills/similar (для создания)

**Description:** Поиск похожих skills на основе произвольного текста (для формы создания)

**Request:**
```
GET /api/skills/similar?text={markdown}
```

**Response (200 OK):**
```json
{
  "similarSkills": [
    {
      "id": "uuid",
      "skillId": "string",
      "name": "string",
      "description": "string",
      "similarityScore": 0.85
    }
  ],
  "total": 5
}
```

**Error Responses:**
- 400 Bad Request: Текст не указан
- 500 Internal Server Error: Ошибка поиска

---

### API-4: GET /api/rules/similar (для создания)

**Description:** Поиск похожих rules на основе произвольного текста (для формы создания)

**Request:**
```
GET /api/rules/similar?text={markdown}
```

**Response (200 OK):**
```json
{
  "similarRules": [
    {
      "id": "uuid",
      "ruleId": "string",
      "name": "string",
      "description": "string",
      "similarityScore": 0.85
    }
  ],
  "total": 5
}
```

**Error Responses:**
- 400 Bad Request: Текст не указан
- 500 Internal Server Error: Ошибка поиска

---

## 6. Data Model Requirements

### DM-1: Расширение Таблицы Skills

**Table:** `skills`

**New Column:**
```sql
ALTER TABLE skills ADD COLUMN embedding_vector vector(384);
CREATE INDEX idx_skills_embedding_vector ON skills USING ivfflat (embedding_vector vector_cosine_ops) WITH (lists = 100);
```

**Description:** Векторное представление markdown контента skill

---

### DM-2: Расширение Таблицы Rules

**Table:** `rules`

**New Column:**
```sql
ALTER TABLE rules ADD COLUMN embedding_vector vector(384);
CREATE INDEX idx_rules_embedding_vector ON rules USING ivfflat (embedding_vector vector_cosine_ops) WITH (lists = 100);
```

**Description:** Векторное представление markdown контента rule

---

### DM-3: JPA Entity Changes

**SkillVersion Entity:**
```java
@Entity
@Table(name = "skills")
public class SkillVersion {
    // ... existing fields ...
    
    @Column(name = "embedding_vector", columnDefinition = "vector")
    private float[] embeddingVector;
    
    public float[] getEmbeddingVector() {
        return embeddingVector;
    }
    
    public void setEmbeddingVector(float[] embeddingVector) {
        this.embeddingVector = embeddingVector;
    }
}
```

**RuleVersion Entity:**
```java
@Entity
@Table(name = "rules")
public class RuleVersion {
    // ... existing fields ...
    
    @Column(name = "embedding_vector", columnDefinition = "vector")
    private float[] embeddingVector;
    
    public float[] getEmbeddingVector() {
        return embeddingVector;
    }
    
    public void setEmbeddingVector(float[] embeddingVector) {
        this.embeddingVector = embeddingVector;
    }
}
```

---

## 7. Authentication & Authorization

### AUTH-1: Доступ к API Поиска

**Requirement:** Все аутентифицированные пользователи должны иметь доступ к endpoint'ам поиска похожих элементов.

**Implementation:**
- Endpoint'ы защищены Spring Security (требуется аутентификация)
- Нет дополнительных ролевых ограничений
- Чтение данных (GET) разрешено всем аутентифицированным пользователям

---

### AUTH-2: Генерация Embeddings

**Requirement:** Только пользователи с правами редактирования skills/rules могут инициировать генерацию embeddings (при сохранении).

**Implementation:**
- Генерация происходит автоматически при сохранении
- Проверка прав на редактирование уже существует в SkillService/RuleService
- Дополнительной проверки не требуется

---

## 8. Testing Requirements

### TEST-1: Unit Tests для EmbeddingService

**Coverage:**
- Генерация вектора из markdown контента
- Вычисление косинусного сходства
- Обработка пустых и null значений
- Переключение между провайдерами (OpenAI vs Local)

---

### TEST-2: Integration Tests для API

**Coverage:**
- `GET /api/skills/{id}/similar` — успешный поиск, пустой результат
- `GET /api/rules/{id}/similar` — успешный поиск, пустой результат
- `GET /api/skills/similar?text=...` — поиск по тексту
- `GET /api/rules/similar?text=...` — поиск по тексту
- Ошибка 404 для несуществующего id

---

### TEST-3: Migration Test

**Coverage:**
- Миграция для PUBLISHED skills/rules
- Проверка что embedding_vector создана
- Проверка что данные заполнены

---

### TEST-4: Manual Testing с Curated Dataset

**Coverage:**
- Создать набор тестовых skills/rules с известной схожестью
- Проверить что "похожие" элементы действительно похожи
- Проверить что непохожие элементы не попадают в результаты
- Проверить пороги фильтрации

---

## 9. Constraints

### CNSTR-1: PostgreSQL pgvector Extension

**Constraint:** Система требует установленного расширения pgvector в PostgreSQL

**Impact:** 
- Необходима установка расширения перед миграцией
- Нельзя использовать H2 in-memory для тестирования этой функции

**Mitigation:**
- Docker compose должен включать установку pgvector
- Integration tests должны использовать Testcontainers с pgvector

---

### CNSTR-2: Размер Вектора

**Constraint:** Размер вектора фиксирован (384 для Sentence-BERT, 1536 для OpenAI)

**Impact:**
- При смене провайдера требуется миграция данных
- Нельзя смешивать вектора разных размеров

**Mitigation:**
- Использовать один провайдер (Sentence-BERT) как primary
- Хранить версию модели в SystemSetting

---

### CNSTR-3: Асинхронная Генерация

**Constraint:** Генерация embedding может занять 1-5 секунд

**Impact:**
- Не должно блокировать UI при сохранении
- Требуется асинхронная обработка

**Mitigation:**
- Использовать @Async для генерации
- Сохранять skill/rule сначала, затем обновлять embedding

---

### CNSTR-4: Обратная Совместимость

**Constraint:** Существующие API не должны сломаться

**Impact:**
- Старые навыки без embedding должны корректно обрабатываться
- API responses должны быть обратно совместимы

**Mitigation:**
- Новые endpoint'ы вместо изменения существующих
- Null/empty values для старых элементов

---

## 10. Acceptance Criteria

### AC-1: Базовый Функционал

**Given:** Существующий PUBLISHED skill с embedding  
**When:** Пользователь открывает детальную карточку skill  
**Then:** Отображается секция "Similar Skills" с топ-10 похожими skills  
**And:** Каждый элемент показывает similarityScore в процентах  
**And:** Ссылки ведут на детальные страницы похожих skills

---

### AC-2: Поиск при Создании

**Given:** Пользователь создаёт новый skill  
**When:** Пользователь вводит markdown контент  
**Then:** Отображается секция "Similar existing skills" с топ-5 похожими  
**And:** Пользователь может перейти к существующему skill вместо создания дубликата

---

### AC-3: Генерация при Сохранении

**Given:** Пользователь сохраняет новый skill со статусом DRAFT  
**When:** Операция save завершена  
**Then:** Embedding сгенерирован и сохранён в БД  
**And:** Skill доступен для поиска в "похожих"

---

### AC-4: Миграция Данных

**Given:** Система с существующими PUBLISHED skills/rules  
**When:** Выполняется миграция БД  
**Then:** Колонка `embedding_vector` создана  
**And:** Все PUBLISHED элементы имеют embeddings  
**And:** Migration лог показывает количество обработанных элементов

---

### AC-5: Обработка Пустых Результатов

**Given:** Skill с уникальным контентом  
**When:** Пользователь открывает детальную карточку  
**Then:** Отображается сообщение "No similar skills found"  
**And:** Секция не скрывается

---

### AC-6: Ошибки Генерации

**Given:** Сбой в провайдере embeddings  
**When:** Пользователь сохраняет skill  
**Then:** Skill сохраняется успешно  
**And:** Embedding остаётся null  
**And:** Логируется ошибка генерации

---

## 11. Non-Functional Requirements (Derived)

### NFR-1: Производительность

**Requirement:** Поиск похожих элементов должен выполняться < 1 секунда

**Implementation:**
- Использование pgvector индекса (IVFFlat)
- Лимит результатов (топ-10)
- Кэширование не требуется (real-time вычисление)

---

### NFR-2: Надёжность

**Requirement:** Сбой в провайдере embeddings не должен прерывать сохранение skills/rules

**Implementation:**
- Try-catch вокруг генерации embedding
- Логирование ошибок
- Null embedding не блокирует операции

---

### NFR-3: Обслуживаемость

**Requirement:** Архитектура должна допускать лёгкую замену модели embeddings

**Implementation:**
- Интерфейс EmbeddingProvider
- Две реализации: LocalEmbeddingProvider, OpenAIEmbeddingProvider
- Выбор провайдера через конфигурацию

---

## 12. Future Considerations

### Phase 2 Potential Features:

1. **Embeddings для Flows** — расширение функционала на flows
2. **Ask Agent Integration** — интерактивный поиск похожих элементов
3. **Feedback Loop** — thumbs up/down для улучшения качества
4. **Advanced Filtering** — фильтрация по нескольким измерениям
5. **Hybrid Search** — комбинация embeddings и текстового поиска
6. **Vector Database** — вынос в отдельную vector DB (Pinecone, Weaviate)

---

## 13. Open Questions

| Question | Status | Notes |
|----------|--------|-------|
| Выбор primary embedding model | Open | Используется Sentence-BERT (multilingual) |
| Настройка similarity threshold | Open | По умолчанию 0.6, хранить в SystemSetting |
| Лимит результатов | Open | По умолчанию 10, хранить в SystemSetting |
| Batch size для миграции | Open | Определить при реализации |
| Логирование качества рекомендаций | Open | Аналитика кликов по рекомендациям |

---

## Appendix A: Technical Implementation Notes

### A.1: pgvector Setup

```sql
-- Установка расширения (в Dockerfile)
CREATE EXTENSION IF NOT EXISTS vector;

-- Создание индекса для быстрого поиска
CREATE INDEX idx_skills_embedding_vector ON skills 
USING ivfflat (embedding_vector vector_cosine_ops) 
WITH (lists = 100);
```

### A.2: Similarity Search Query (JPQL/Native SQL)

```sql
SELECT s.id, s.skill_id, s.version, s.name, s.description, 
       1 - (s.embedding_vector <=> :queryVector) as similarity
FROM skills s
WHERE s.status = 'PUBLISHED'
  AND s.id != :currentId
  AND 1 - (s.embedding_vector <=> :queryVector) > :threshold
ORDER BY s.embedding_vector <=> :queryVector
LIMIT 10;
```

### A.3: Embedding Generation (Pseudo-code)

```java
@Service
public class SkillEmbeddingService {
    
    private final EmbeddingProvider embeddingProvider;
    private final SkillVersionRepository repository;
    
    @Async
    public void generateEmbedding(UUID skillId) {
        SkillVersion skill = repository.findById(skillId).orElseThrow();
        
        if (skill.getSkillMarkdown() == null || skill.getSkillMarkdown().isBlank()) {
            return;
        }
        
        float[] embedding = embeddingProvider.generateEmbedding(
            skill.getSkillMarkdown()
        );
        
        skill.setEmbeddingVector(embedding);
        repository.save(skill);
    }
}
```

---

**Document End**
