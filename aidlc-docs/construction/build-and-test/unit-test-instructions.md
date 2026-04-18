# Unit Test Instructions

## Overview

В этом проекте используется многоуровневая архитектура тестирования для функциональности "Embeddings for Skills and Rules". Unit тесты покрывают бизнес-логику генерации embeddings, поиска похожих элементов и обработки ошибок.

---

## Run All Unit Tests

### Backend (JUnit 5 + Spring Boot Test)

```bash
cd backend
./gradlew test
```

**Ожидаемый результат:**
- Все тесты выполняются
- Отчёт создаётся в `backend/build/reports/tests/test/index.html`
- Code coverage (если настроен JaCoCo): `backend/build/reports/jacoco/test/html/index.html`

**Фильтрация по определённым тестам:**
```bash
# Только embedding тесты
./gradlew test --tests "*Embedding*"

# Только тесты навыков (skills)
./gradlew test --tests "*Skill*Test"

# Исключить интеграционные тесты
./gradlew test --exclude-task "*IntegrationTest"
```

---

## Per-Unit Test Execution

### Unit: Embedding Infrastructure

**Тестовый класс:** `backend/src/test/java/ru/hgd/sdlc/common/embedding/application/EmbeddingServiceTest.java`

**Запуск:**
```bash
cd backend
./gradlew test --tests "EmbeddingServiceTest"
```

**Проверяемые сценарии:**
1. Генерация embedding с локальным провайдером (Sentence-BERT)
2. Fallback логика: OpenAI → Local при ошибке
3. Обработка пустых или null значений
4. Retry логика для внешних API
5. Валидация размерности вектора

**Моки:**
- `EmbeddingProvider` (Local и OpenAI реализации)
- External API responses

---

### Unit: Skills Embedding Service

**Тестовый класс:** `backend/src/test/java/ru/hgd/sdlc/skill/application/SkillEmbeddingServiceTest.java`

**Запуск:**
```bash
cd backend
./gradlew test --tests "SkillEmbeddingServiceTest"
```

**Проверяемые сценарии:**
1. Поиск похожих skills по ID
   - Корректное вычисление косинусного сходства
   - Применение threshold фильтра
   - Лимитирование результатов
2. Поиск похожих skills по произвольному тексту
   - Генерация embedding для текста запроса
   - Поиск в БД с векторным индексом
3. Обработка отсутствия embedding (null vector)
   - Возврат пустого списка
4. Асинхронная генерация embedding
   - Проверка @Async выполнения
   - Обработка ошибок без прерывания

**Моки:**
- `SkillVersionRepository` (returns mock data)
- `EmbeddingProvider` (returns predefined vectors)
- `SkillService` (for markdown retrieval)

---

### Unit: Rules Embedding Service

**Тестовый класс:** `backend/src/test/java/ru/hgd/sdlc/rule/application/RuleEmbeddingServiceTest.java`

**Запуск:**
```bash
cd backend
./gradlew test --tests "RuleEmbeddingServiceTest"
```

**Проверяемые сценарии:**
1. Поиск похожих rules по ID
2. Поиск похожих rules по тексту
3. Обработка null embeddings
4. Асинхронная генерация

**Моки:**
- `RuleVersionRepository`
- `EmbeddingProvider`
- `RuleService`

---

### Unit: Migration Service

**Тестовый класс:** `backend/src/test/java/ru/hgd/sdlc/common/embedding/application/EmbeddingMigrationServiceTest.java`

**Запуск:**
```bash
cd backend
./gradlew test --tests "EmbeddingMigrationServiceTest"
```

**Проверяемые сценарии:**
1. Миграция опубликованных skills
   - Пропуск записей с существующим embedding
   - Логирование прогресса
   - Обработка ошибок без прерывания
2. Миграция опубликованных rules
3. Идемпотентность (повторный запуск не создаёт дубликаты)
4. Проверка флага завершения через SystemSetting

**Моки:**
- `SkillVersionRepository` / `RuleVersionRepository` (returns paginated data)
- `EmbeddingService`
- `SystemSettingRepository`

---

### Unit: API Controllers

**Тестовый класс:** `backend/src/test/java/ru/hgd/sdlc/skill/api/SkillControllerTest.java`

**Запуск:**
```bash
cd backend
./gradlew test --tests "SkillControllerTest"
```

**Проверяемые сценарии:**
1. Endpoint `GET /api/skills/{id}/similar`
   - HTTP 200: успешный возврат похожих skills
   - HTTP 404: skill не найден
   - HTTP 500: внутренняя ошибка сервера
2. Endpoint `GET /api/skills/similar?text=...`
   - Валидация входных параметров
   - Обработка пустого текста (@NotBlank)
3. Проверка прав доступа (@PreAuthorize)

**Моки:**
- `SkillEmbeddingService`
- Spring Security context

**Аналогично для RuleController:**
```bash
./gradlew test --tests "RuleControllerTest"
```

---

## Expected Results

### Total Tests (оценка)
| Категория | Количество тестов |
|-----------|-------------------|
| Embedding Infrastructure | ~5-10 |
| Skills Embedding Service | ~10-15 |
| Rules Embedding Service | ~10-15 |
| Migration Service | ~5-10 |
| API Controllers | ~5-10 |
| **Итого** | **~35-60** |

### Coverage Target (из NFR)
- **Unit test coverage**: > 80% для embedding логики (MAINT-002)
- **Branch coverage**: > 70% для критических путей
- **Critical paths coverage**: 100% (генерация embeddings, поиск)

### Report Location
- **HTML Report**: `backend/build/reports/tests/test/index.html`
- **XML Report (CI/CD)**: `backend/build/test-results/test/*.xml`
- **Console Output**: STDOUT с прогрессом

---

## Fixing Failures

### Step 1: Check Output

**Локация детальных логов:**
```bash
# HTML отчёт (открыть в браузере)
open backend/build/reports/tests/test/index.html  # macOS
xdg-open backend/build/reports/tests/test/index.html  # Linux

# Консольный вывод
./gradlew test --info  # Подробные логи
```

### Step 2: Identify Failing Test

**Пример вывода:**
```
SkillEmbeddingServiceTest > testFindSimilarById() FAILED
    java.lang.AssertionError: Expected: 5, Actual: 3
    at SkillEmbeddingServiceTest.testFindSimilarById(SkillEmbeddingServiceTest.java:45)

3 tests completed, 1 failed, 2 passed
```

**Анализ:**
1. Открыть файл теста
2. Проверить строку с ошибкой
3. Проанализировать логику теста и кода

### Step 3: Fix Code or Test

**Варианты:**
1. **Исправить код**, если логика неверна
2. **Исправить тест**, если assertion некорректен
3. **Обновить mock**, если данные неверны

**Пример исправления mock:**
```java
// Было
when(skillRepository.findSimilarById(any(), any(), anyFloat(), anyInt()))
    .thenReturn(Arrays.asList(skill1, skill2));

// Стало
when(skillRepository.findSimilarById(any(), any(), anyFloat(), anyInt()))
    .thenReturn(Arrays.asList(skill1, skill2, skill3, skill4, skill5));
```

### Step 4: Rerun Until Green

**Перезапуск только failing тестов:**
```bash
./gradlew test --tests "*SkillEmbeddingServiceTest" --rerun-tasks
```

**Повторный запуск всех тестов:**
```bash
./gradlew clean test
```

---

## Common Issues and Solutions

### Issue: EmbeddingProvider Mock Returns Null

**Ошибка:**
```
NullPointerException at EmbeddingService.generateEmbedding()
```

**Решение:**
```java
// Добавить mock return value
when(embeddingProvider.generateEmbedding(anyString()))
    .thenReturn(new float[]{0.1f, 0.2f, ...});
```

---

### Issue: Repository Returns Empty List

**Ошибка:**
```
AssertionError: Expected at least 1 similar skill, but got 0
```

**Решение:**
```java
// Проверить mock repository
when(skillRepository.findSimilarById(
    eq(skillId),
    anyString(),
    eq(0.6f),
    eq(10)
)).thenReturn(Arrays.asList(mockSkill1, mockSkill2));
```

---

### Issue: Async Method Not Called

**Ошибка:**
```
Expected embedding to be set, but it's null
```

**Решение:**
```java
// Для @Async методов использовать CompletableFuture или Awaitility
await().atMost(5, TimeUnit.SECONDS)
    .until(() -> skill.getEmbeddingVector() != null);
```

---

### Issue: Testcontainers Timeout (если используется в unit тестах)

**Ошибка:**
```
org.testcontainers.containers.ContainerLaunchException: Container startup failed
```

**Решение:**
```bash
# Увеличить timeout в тесте
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
    .withStartupTimeoutSeconds(120);
```

---

## Continuous Integration

### Jenkins / GitHub Actions

**Пример pipeline step:**
```groovy
stage('Unit Tests') {
    steps {
        sh 'cd backend && ./gradlew test'
    }
    post {
        always {
            junit 'backend/build/test-results/test/*.xml'
            publishHTML([
                reportDir: 'backend/build/reports/tests/test',
                reportFiles: 'index.html',
                reportName: 'Unit Test Report'
            ])
        }
    }
}
```

### Quality Gates

**Критерии успешного прохождения:**
- ✅ Все тесты pass (0 failures)
- ✅ Coverage > 80% (из NFR MAINT-002)
- ✅ Нет новых compilation warnings
- ✅ Build time < 5 минут (для CI/CD)

---

## Quick Reference

| Задача | Команда |
|--------|---------|
| Все unit тесты | `cd backend && ./gradlew test` |
| Только embedding тесты | `cd backend && ./gradlew test --tests "*Embedding*"` |
| Детальные логи | `cd backend && ./gradlew test --info` |
| Отчёт в браузере | `open backend/build/reports/tests/test/index.html` |
| Clean + test | `cd backend && ./gradlew clean test` |
| Повторный failing тест | `cd backend && ./gradlew test --tests "*MyTest" --rerun-tasks` |
