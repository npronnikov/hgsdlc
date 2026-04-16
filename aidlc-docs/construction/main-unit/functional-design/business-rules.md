# Business Rules: Embeddings for Skills and Rules

**Document Version:** 1.0
**Date:** 2026-04-16
**Author:** AI Functional Design Agent
**Status:** Draft

---

## Overview

Документ описывает бизнес-правила для функциональности "Embeddings for Skills and Rules". Правила организованы по прецедентам использования с указанием предусловий, постусловий и обработкой ошибочных ситуаций.

---

## Table of Contents

1. [Генерация Embeddings](#генерация-embeddings)
2. [Поиск Похожих Элементов](#поиск-похожих-элементов)
3. [Миграция Существующих Данных](#миграция-существующих-данных)
4. [Обновление Embeddings](#обновление-embeddings)
5. [Фильтрация и Ранжирование](#фильтрация-и-ранжирование)

---

## Генерация Embeddings

### BR-001: Генерация Embedding при Создании Skill

**Описание:** При сохранении нового skill со статусом DRAFT должен быть сгенерирован embedding.

**Предусловия:**
- PRE-001: Skill создан через UI или API
- PRE-002: Поле `skillMarkdown` содержит markdown контент
- PRE-003: Пользователь имеет права на сохранение skill

**Постусловия:**
- POST-001: В колонке `embedding_vector` сохранён вектор размером 384
- POST-002: Embedding соответствует контенту `skillMarkdown`
- POST-003: Skill сохранён в базе данных
- POST-004: Логировано событие `embedding_generated`

**Основной сценарий:**
1. Пользователь сохраняет skill через `POST /api/skills/{id}/save`
2. `SkillService.saveDraft()` вызывается
3. После сохранения в БД, вызывается `SkillEmbeddingService.generateEmbedding(skillId)`
4. `EmbeddingProvider.generateEmbedding(skillMarkdown)` возвращает вектор
5. Вектор сохраняется в `skillVersion.embeddingVector`
6. Логируется успешная генерация

**Альтернативные сценарии:**

**ALT-001: Пустой markdown**
- **Condition:** `skillMarkdown` is null or blank
- **Action:** Генерация пропускается, `embeddingVector` остаётся null
- **Log:** Warning "Skipped embedding generation: empty markdown"

**ALT-002: Ошибка провайдера embeddings**
- **Condition:** Провайдер выбрасывает исключение (API недоступен, ошибка модели)
- **Action:** Skill сохраняется успешно, `embeddingVector` остаётся null
- **Log:** Error "Failed to generate embedding: {error}"
- **User Notification:** Нет (non-blocking)

**ALT-003: Слишком длинный markdown**
- **Condition:** `skillMarkdown` превышает лимит токенов модели
- **Action:** Markdown обрезается до лимита токенов
- **Log:** Warning "Truncated markdown to {maxTokens} tokens"

---

### BR-002: Генерация Embedding при Создании Rule

**Описание:** При сохранении нового rule со статусом DRAFT должен быть сгенерирован embedding.

**Предусловия:**
- PRE-001: Rule создан через UI или API
- PRE-002: Поле `ruleMarkdown` содержит markdown контент
- PRE-003: Пользователь имеет права на сохранение rule

**Постусловия:**
- POST-001: В колонке `embedding_vector` сохранён вектор размером 384
- POST-002: Embedding соответствует контенту `ruleMarkdown`
- POST-003: Rule сохранён в базе данных
- POST-004: Логировано событие `embedding_generated`

**Основной сценарий:**
Аналогично BR-001, но для Rule.

**Альтернативные сценарии:**
Аналогично BR-001 (ALT-001, ALT-002, ALT-003).

---

### BR-003: Асинхронная Генерация

**Описание:** Генерация embedding должна выполняться асинхронно, чтобы не блокировать сохранение skill/rule.

**Предусловия:**
- PRE-001: Skill/Rule сохранён в БД
- PRE-002: Вызван метод генерации embedding

**Постусловия:**
- POST-001: Операция сохранения возвращает управление немедленно
- POST-002: Генерация выполняется в фоновом потоке
- POST-003: Пользователь видит результаты поиска после завершения генерации

**Основной сценарий:**
1. `SkillService.saveDraft()` сохраняет skill в БД
2. `SkillService` вызывает `SkillEmbeddingService.generateEmbeddingAsync(skillId)`
3. Метод помечен `@Async` и возвращает управление немедленно
4. Генерация выполняется в отдельном потоке
5. После завершения, `embeddingVector` обновляется в БД
6. Frontend может перезагрузить similar items через несколько секунд

**Альтернативные сценарии:**

**ALT-001: Ошибка при асинхронной генерации**
- **Condition:** Исключение в фоновом потоке
- **Action:** Логируется ошибка, skill/rule остаётся без embedding
- **Log:** Error "Async embedding generation failed: {error}"
- **User Notification:** Нет

---

## Поиск Похожих Элементов

### BR-004: Поиск Похожих Skills по ID

**Описание:** Поиск топ-10 похожих PUBLISHED skills на основе косинусного сходства embeddings.

**Предусловия:**
- PRE-001: Skill с ID `{id}` существует
- PRE-002: У skill есть `embeddingVector` (не null)
- PRE-003: Пользователь аутентифицирован

**Постусловия:**
- POST-001: Возвращается список `SimilarSkill` DTO
- POST-002: Список отсортирован по убыванию `similarityScore`
- POST-003: Только элементы с `similarityScore > 0.6`
- POST-004: Текущий элемент исключён из результатов
- POST-005: Только элементы со статусом `PUBLISHED`

**Основной сценарий:**
1. Пользователь вызывает `GET /api/skills/{id}/similar`
2. `SkillController.similarSkills(id)` вызывается
3. `SkillSimilarityService.findSimilar(skillId, threshold=0.6, limit=10)`
4. Запрос к БД с использованием pgvector:
   ```sql
   SELECT s.id, s.skill_id, s.version, s.name, s.description,
          1 - (s.embedding_vector <=> :queryVector) as similarity
   FROM skills s
   WHERE s.status = 'PUBLISHED'
     AND s.id != :currentId
     AND 1 - (s.embedding_vector <=> :queryVector) > 0.6
   ORDER BY s.embedding_vector <=> :queryVector
   LIMIT 10
   ```
5. Результаты маппятся в `SimilarSkill` DTO
6. Возвращается `200 OK` с `{ similarSkills: [...], total: N }`

**Альтернативные сценарии:**

**ALT-001: Skill не найден**
- **Condition:** Skill с ID `{id}` не существует
- **Action:** Возвращается `404 Not Found`
- **Response:** `{ "error": "Skill not found" }`

**ALT-002: Нет embedding**
- **Condition:** У skill нет `embeddingVector` (null)
- **Action:** Возвращается пустой список
- **Response:** `{ "similarSkills": [], "total": 0 }`

**ALT-003: Нет похожих элементов**
- **Condition:** Нет элементов с `similarityScore > 0.6`
- **Action:** Возвращается пустой список
- **Response:** `{ "similarSkills": [], "total": 0 }`

**ALT-004: Ошибка БД**
- **Condition:** SQL exception или ошибка pgvector
- **Action:** Логируется ошибка, возвращается `500 Internal Server Error`
- **Response:** `{ "error": "Failed to search similar skills" }`

---

### BR-005: Поиск Похожих Rules по ID

**Описание:** Поиск топ-10 похожих PUBLISHED rules на основе косинусного сходства embeddings.

**Предусловия:**
- PRE-001: Rule с ID `{id}` существует
- PRE-002: У rule есть `embeddingVector` (не null)
- PRE-003: Пользователь аутентифицирован

**Постусловия:**
- POST-001: Возвращается список `SimilarRule` DTO
- POST-002: Список отсортирован по убыванию `similarityScore`
- POST-003: Только элементы с `similarityScore > 0.6`
- POST-004: Текущий элемент исключён из результатов
- POST-005: Только элементы со статусом `PUBLISHED`

**Основной сценарий:**
Аналогично BR-004, но для rules.

**Альтернативные сценарии:**
Аналогично BR-004 (ALT-001, ALT-002, ALT-003, ALT-004).

---

### BR-006: Поиск Похожих Skills по Тексту

**Описание:** Поиск топ-5 похожих PUBLISHED skills на основе произвольного текста (для формы создания).

**Предусловия:**
- PRE-001: Текст `text` предоставлен в query параметре
- PRE-002: Текст не пустой
- PRE-003: Пользователь аутентифицирован

**Постусловия:**
- POST-001: Возвращается список `SimilarSkill` DTO
- POST-002: Список отсортирован по убыванию `similarityScore`
- POST-003: Только элементы с `similarityScore > 0.6`
- POST-004: Максимум 5 результатов

**Основной сценарий:**
1. Пользователь вводит markdown в форме создания skill
2. Frontend вызывает `GET /api/skills/similar?text={markdown}` (debounce)
3. `SkillController.similarSkillsByText(text)` вызывается
4. `SkillSimilarityService.findSimilarByText(text, threshold=0.6, limit=5)`
5. Генерируется временный embedding для текста
6. Запрос к БД аналогично BR-004
7. Возвращается `200 OK` с `{ similarSkills: [...], total: N }`

**Альтернативные сценарии:**

**ALT-001: Пустой текст**
- **Condition:** Параметр `text` отсутствует или пустой
- **Action:** Возвращается `400 Bad Request`
- **Response:** `{ "error": "Text parameter is required" }`

**ALT-002: Нет похожих элементов**
- **Condition:** Нет элементов с `similarityScore > 0.6`
- **Action:** Возвращается пустой список
- **Response:** `{ "similarSkills": [], "total": 0 }`

---

### BR-007: Поиск Похожих Rules по Тексту

**Описание:** Поиск топ-5 похожих PUBLISHED rules на основе произвольного текста (для формы создания).

**Предусловия:**
- PRE-001: Текст `text` предоставлен в query параметре
- PRE-002: Текст не пустой
- PRE-003: Пользователь аутентифицирован

**Постусловия:**
- POST-001: Возвращается список `SimilarRule` DTO
- POST-002: Список отсортирован по убыванию `similarityScore`
- POST-003: Только элементы с `similarityScore > 0.6`
- POST-004: Максимум 5 результатов

**Основной сценарий:**
Аналогично BR-006, но для rules.

**Альтернативные сценарии:**
Аналогично BR-006 (ALT-001, ALT-002).

---

## Миграция Существующих Данных

### BR-008: Миграция PUBLISHED Skills

**Описание:** Все существующие PUBLISHED skills должны получить embeddings после развертывания функции.

**Предусловия:**
- PRE-001: Миграция БД выполнена (колонка `embedding_vector` создана)
- PRE-002: Провайдер embeddings доступен
- PRE-003: Существуют skills со статусом `PUBLISHED`

**Постусловия:**
- POST-001: Все PUBLISHED skills имеют `embeddingVector`
- POST-002: Логировано количество обработанных skills
- POST-003: Ошибки не прерывают миграцию

**Основной сценарий:**
1. Liquibase миграция `047-embeddings-support.sql` выполняется при старте
2. Spring Boot `@PostConstruct` bean запускает migration job
3. Job выбирает все PUBLISHED skills без embedding:
   ```sql
   SELECT id FROM skills WHERE status = 'PUBLISHED' AND embedding_vector IS NULL
   ```
4. Для каждого skill вызывается `generateEmbedding(skillId)`
5. Обработанные skills сохраняются
6. Логируется прогресс: "Processed {count}/{total} skills"
7. По завершении логируется "Migration completed: {total} skills processed"

**Альтернативные сценарии:**

**ALT-001: Ошибка генерации для отдельного skill**
- **Condition:** Исключение при генерации embedding
- **Action:** Skill пропускается, логируется ошибка, миграция продолжается
- **Log:** Error "Failed to generate embedding for skill {id}: {error}"

**ALT-002: Ошибка провайдера (массовая)**
- **Condition:** Провайдер недоступен
- **Action:** Миграция прерывается, логируется критическая ошибка
- **Log:** Error "Migration failed: embedding provider unavailable"
- **User Action:** Требуется перезапуск с доступным провайдером

---

### BR-009: Миграция PUBLISHED Rules

**Описание:** Все существующие PUBLISHED rules должны получить embeddings после развертывания функции.

**Предусловия:**
- PRE-001: Миграция БД выполнена (колонка `embedding_vector` создана)
- PRE-002: Провайдер embeddings доступен
- PRE-003: Существуют rules со статусом `PUBLISHED`

**Постусловия:**
- POST-001: Все PUBLISHED rules имеют `embeddingVector`
- POST-002: Логировано количество обработанных rules
- POST-003: Ошибки не прерывают миграцию

**Основной сценарий:**
Аналогично BR-008, но для rules.

**Альтернативные сценарии:**
Аналогично BR-008 (ALT-001, ALT-002).

---

## Обновление Embeddings

### BR-010: Пересчёт при Изменении Markdown

**Описание:** При изменении markdown контента skill/rule embedding должен быть пересчитан.

**Предусловия:**
- PRE-001: Пользователь редактирует существующий skill/rule
- PRE-002: Поле `skillMarkdown`/`ruleMarkdown` изменено
- PRE-003: Пользователь сохраняет изменения

**Постусловия:**
- POST-001: Skill/rule сохранён с новым markdown
- POST-002: `embeddingVector` обновлён (асинхронно)
- POST-003: Старый embedding заменён новым

**Основной сценарий:**
1. Пользователь редактирует skill через UI
2. Вызывается `POST /api/skills/{id}/save`
3. `SkillService.updateDraft()` обновляет `skillMarkdown`
4. Вызывается `SkillEmbeddingService.generateEmbeddingAsync(skillId)`
5. Генерируется новый embedding
6. `embeddingVector` обновляется в БД
7. Логируется "Embedding regenerated for skill {id}"

**Альтернативные сценарии:**

**ALT-001: Markdown не изменился**
- **Condition:** `skillMarkdown` идентичен предыдущему
- **Action:** Пересчёт не выполняется (оптимизация)
- **Log:** Debug "Markdown unchanged, skipping embedding regeneration"

**ALT-002: Ошибка генерации**
- **Condition:** Исключение при генерации
- **Action:** Skill сохранён успешно, старый embedding остаётся
- **Log:** Error "Failed to regenerate embedding: {error}"

---

## Фильтрация и Ранжирование

### BR-011: Фильтрация по Metadata

**Описание:** Поиск похожих элементов может быть дополнительно отфильтрован по tags, teamCode, scope.

**Предусловия:**
- PRE-001: Базовый поиск выполняется
- PRE-002: Пользователь предоставил фильтры (опционально)

**Постусловия:**
- POST-001: Результаты отфильтрованы по metadata
- POST-002: Фильтрация применяется после ранжирования

**Основной сценарий:**
1. Запрос к БД включает условия фильтрации:
   ```sql
   WHERE s.status = 'PUBLISHED'
     AND s.id != :currentId
     AND 1 - (s.embedding_vector <=> :queryVector) > 0.6
     AND (:tags IS NULL OR s.tags && :tags)          -- optional
     AND (:teamCode IS NULL OR s.team_code = :teamCode)  -- optional
     AND (:scope IS NULL OR s.scope = :scope)        -- optional
   ```
2. Результаты возвращаются с учётом фильтров

**Альтернативные сценарии:**

**ALT-001: Нет фильтров**
- **Condition:** Параметры фильтрации не предоставлены
- **Action:** Фильтрация не применяется

---

### BR-012: Настраиваемый Порог Сходства

**Описание:** Порог сходства (`threshold`) должен быть настраиваемым через SystemSetting.

**Предусловия:**
- PRE-001: Системная настройка `embedding.similarity.threshold` существует
- PRE-002: Значение в диапазоне [0.0, 1.0]

**Постусловия:**
- POST-001: Поиск использует значение из SystemSetting
- POST-002: Значение по умолчанию: 0.6

**Основной сценарий:**
1. `SystemSettingService.getValue("embedding.similarity.threshold", "0.6")`
2. Полученное значение используется в WHERE clause
3. Admin может изменить настройку через Settings UI

**Альтернативные сценарии:**

**ALT-001: Настройка отсутствует**
- **Condition:** Настройка не найдена в БД
- **Action:** Используется значение по умолчанию (0.6)
- **Log:** Debug "Using default similarity threshold: 0.6"

---

### BR-013: Настраиваемый Лимит Результатов

**Описание:** Лимит количества результатов (`limit`) должен быть настраиваемым через SystemSetting.

**Предусловия:**
- PRE-001: Системная настройка `embedding.max.results` существует
- PRE-002: Значение в диапазоне [1, 100]

**Постусловия:**
- POST-001: Поиск использует значение из SystemSetting
- POST-002: Значение по умолчанию: 10 (для поиска по ID), 5 (для поиска по тексту)

**Основной сценарий:**
1. `SystemSettingService.getValue("embedding.max.results", "10")`
2. Полученное значение используется в LIMIT clause
3. Admin может изменить настройку через Settings UI

**Альтернативные сценарии:**

**ALT-001: Настройка отсутствует**
- **Condition:** Настройка не найдена в БД
- **Action:** Используется значение по умолчанию (10 или 5)
- **Log:** Debug "Using default max results: 10"

---

## Безопасность и Доступ

### BR-014: Авторизация для Чтения

**Описание:** Все API endpoint'ы для поиска похожих элементов должны требовать аутентификацию.

**Предусловия:**
- PRE-001: Пользователь не аутентифицирован

**Постусловия:**
- POST-001: Возвращается `401 Unauthorized`
- POST-002: Перенаправление на login page (для frontend)

**Основной сценарий:**
1. Пользователь вызывает `GET /api/skills/{id}/similar` без аутентификации
2. Spring Security перехватывает запрос
3. Возвращается `401 Unauthorized`

---

### BR-015: Авторизация для Записи

**Описание:** Генерация embedding (при сохранении) должна требовать права на редактирование skill/rule.

**Предусловия:**
- PRE-001: Пользователь аутентифицирован
- PRE-002: Пользователь не имеет роль `FLOW_CONFIGURATOR` или `ADMIN`

**Постусловия:**
- POST-001: Возвращается `403 Forbidden`
- POST-002: Embedding не генерируется

**Основной сценарий:**
1. Пользователь с ролью `PRODUCT_OWNER` пытается сохранить skill
2. Spring Security проверяет роль через `@PreAuthorize`
3. Возвращается `403 Forbidden`

---

## Обработка Ошибок

### BR-016: Логирование Ошибок Генерации

**Описание:** Все ошибки генерации embedding должны быть логированы без прерывания основной операции.

**Предусловия:**
- PRE-001: Происходит ошибка при генерации

**Постусловия:**
- POST-001: Ошибка логируется с уровнем ERROR
- POST-002: Операция сохранения skill/rule завершается успешно
- POST-003: `embeddingVector` остаётся null

**Основной сценарий:**
1. `EmbeddingProvider.generateEmbedding()` выбрасывает исключение
2. `try-catch` блок перехватывает исключение
3. Логируется: `log.error("Failed to generate embedding: {}", error.getMessage(), error)`
4. Метод завершается успешно (null embedding)

---

### BR-017: Обработка Ошибок pgvector

**Описание:** Ошибки pgvector (некорректный вектор, операции) должны корректно обрабатываться.

**Предусловия:**
- PRE-001: Вектор в БД имеет некорректную размерность
- PRE-002: Операция сравнения векторов не удаётся

**Постусловия:**
- POST-001: Возвращается `500 Internal Server Error`
- POST-002: Ошибка логируется
- POST-003: Пользователь видит сообщение об ошибке

**Основной сценарий:**
1. SQL запрос выбрасывает исключение (например, "vector dimension mismatch")
2. Exception handler перехватывает исключение
3. Логируется: `log.error("Vector operation failed: {}", error.getMessage(), error)`
4. Возвращается `500` с `{ "error": "Failed to search similar items" }`

---

**Document End**
