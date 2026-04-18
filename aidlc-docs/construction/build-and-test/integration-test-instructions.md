# Integration Test Instructions

## Purpose

Интеграционные тесты проверяют корректность работы функциональности "Embeddings for Skills and Rules" с реальной базой данных (PostgreSQL + pgvector) и проверяют cross-layer взаимодействия (API → Service → Repository → Database).

Основные сценарии:
1. **End-to-end генерация embedding** с реальной ML-моделью
2. **Векторный поиск** через pgvector индекс
3. **Миграция данных** на реальной БД
4. **API endpoints** с HTTP layer

---

## Setup

### 1. Start Test Database

Интеграционные тесты используют Testcontainers для автоматического запуска PostgreSQL с pgvector.

**Докер доступен?**
```bash
docker --version
# Expect: Docker version 20.x.x or higher
```

**Если Docker не доступен:**
```bash
# Запустить PostgreSQL вручную для тестов
docker run -d --name pgvector-test \
  -e POSTGRES_USER=test \
  -e POSTGRES_PASSWORD=test \
  -e POSTGRES_DB=test \
  -p 5433:5432 \
  pgvector/pgvector:pg16

# И задать переменные окружения для тестов
export TEST_DB_URL=jdbc:postgresql://localhost:5433/test
export TEST_DB_USER=test
export TEST_DB_PASSWORD=test
```

---

### 2. Configure Testcontainers

Testcontainers автоматически конфигурируется в тестах. Проверьте:

**Файл:** `backend/src/test/resources/application-test.yml`
```yaml
spring:
  test:
    database:
      replace: none  # Testcontainers заменит datasource

  datasource:
    url: jdbc:tc:postgresql:16:///test?TC_DAEMON=true&TC_IMAGE_TAG=pg16
    username: test
    password: test

  jpa:
    hibernate:
      ddl-auto: validate
```

---

## Test Scenarios

### Scenario 1: Embedding Generation (Skills)

**Тестовый класс:** `backend/src/test/java/ru/hgd/sdlc/skill/application/SkillEmbeddingServiceIntegrationTest.java`

**Что проверяется:**
1. Создание skill через API
2. Автоматическая генерация embedding (через @Async)
3. Сохранение вектора в БД (колонка `embedding_vector`)
4. Проверка размерности вектора (384 для Sentence-BERT)

**Подготовка данных:**
```java
@BeforeEach
void setUp() {
    // Создаём тестовый skill с markdown контентом
    SkillVersion skill = new SkillVersion();
    skill.setName("Test Skill");
    skill.setDescription("Description");
    skill.setMarkdown("# Test Skill\n\nThis is a test skill for embeddings.");
    skill.setStatus(SkillStatus.PUBLISHED);
    skill = skillRepository.save(skill);
}
```

**Выполнение:**
```java
@Test
void testGenerateEmbeddingForSkill() {
    // Генерация embedding
    UUID skillId = skill.getId();
    skillEmbeddingService.generateEmbedding(skillId);

    // Ожидание завершения асинхронной задачи
    await().atMost(10, TimeUnit.SECONDS)
        .until(() -> {
            SkillVersion updated = skillRepository.findById(skillId).orElse(null);
            return updated != null && updated.getEmbeddingVector() != null;
        });

    // Проверка
    SkillVersion updated = skillRepository.findById(skillId).orElseThrow();
    assertNotNull(updated.getEmbeddingVector());
    assertEquals(384, updated.getEmbeddingVector().length); // Sentence-BERT dimension
}
```

**Запуск:**
```bash
cd backend
./gradlew test --tests "SkillEmbeddingServiceIntegrationTest"
```

**Ожидаемый результат:**
- ✅ Embedding сгенерирован
- ✅ Вектор сохранён в БД
- ✅ Размерность корректна (384)
- ✅ Тест выполняется < 10 секунд

---

### Scenario 2: Vector Similarity Search (Skills)

**Тестовый класс:** `backend/src/test/java/ru/hgd/sdlc/skill/application/SkillEmbeddingServiceIntegrationTest.java`

**Что проверяется:**
1. Создание нескольких skills с похожим контентом
2. Генерация embeddings для всех
3. Поиск похожих по ID
4. Проверка корректности косинусного сходства

**Подготовка данных:**
```java
@BeforeEach
void setUpData() {
    // Skill 1: "Java Spring Boot Tutorial"
    createSkillWithEmbedding("Java Spring", "Guide to Spring Boot framework");

    // Skill 2: "Spring Boot Getting Started" (похож на Skill 1)
    createSkillWithEmbedding("Spring Boot", "Getting started with Spring");

    // Skill 3: "Python Flask Tutorial" (не похож)
    createSkillWithEmbedding("Python Flask", "Guide to Flask framework");

    // Skill 4: "React.js Guide" (совсем не похож)
    createSkillWithEmbedding("React JS", "Frontend framework guide");
}
```

**Выполнение:**
```java
@Test
void testFindSimilarSkills() {
    UUID skillId = skill1.getId();

    // Поиск похожих
    List<SimilarItem> similar = skillEmbeddingService.findSimilar(
        skillId,
        0.6f,  // threshold
        10     // limit
    );

    // Проверка: Skill 2 должен быть в топе (похож контент)
    assertFalse(similar.isEmpty());
    assertTrue(similar.stream().anyMatch(item -> item.itemId().equals(skill2Id)));

    // Проверка: Skill 3 и 4 не должны быть в результатах (низкое сходство)
    assertFalse(similar.stream().anyMatch(item -> item.itemId().equals(skill3Id)));
    assertFalse(similar.stream().anyMatch(item -> item.itemId().equals(skill4Id)));

    // Проверка: сам Skill 1 не должен быть в результатах
    assertFalse(similar.stream().anyMatch(item -> item.itemId().equals(skillId)));

    // Проверка сортировки (по убыванию сходства)
    float prevScore = 1.0f;
    for (SimilarItem item : similar) {
        assertTrue(item.similarityScore() <= prevScore);
        prevScore = item.similarityScore();
    }
}
```

**Запуск:**
```bash
cd backend
./gradlew test --tests "SkillEmbeddingServiceIntegrationTest.testFindSimilarSkills"
```

**Ожидаемый результат:**
- ✅ Найдены похожие skills
- ✅ Сортировка по убыванию сходства
- ✅ Threshold фильтр работает
- ✅ Текущий skill исключён из результатов

---

### Scenario 3: Search by Arbitrary Text

**Тестовый класс:** `backend/src/test/java/ru/hgd/sdlc/skill/application/SkillEmbeddingServiceIntegrationTest.java`

**Что проверяется:**
1. Создание навыков с разным контентом
2. Поиск по произвольному тексту запроса
3. Генерация embedding для текста запроса
4. Корректность ранжирования

**Выполнение:**
```java
@Test
void testFindSimilarByText() {
    // Созданы навыки: "Java Tutorial", "Python Guide", "React Intro"

    // Поиск по тексту "backend framework"
    List<SimilarItem> similar = skillEmbeddingService.findSimilarByText(
        "backend framework",
        0.6f,
        10
    );

    // Проверка: "Java Tutorial" должен быть в топе (backend related)
    assertFalse(similar.isEmpty());
    assertTrue(similar.get(0).similarityScore() > 0.7f);
}
```

**Запуск:**
```bash
cd backend
./gradlew test --tests "SkillEmbeddingServiceIntegrationTest.testFindSimilarByText"
```

---

### Scenario 4: Data Migration

**Тестовый класс:** `backend/src/test/java/ru/hgd/sdlc/common/embedding/application/EmbeddingMigrationServiceIntegrationTest.java`

**Что проверяется:**
1. Создание навыков без embeddings (старые данные)
2. Запуск миграции
3. Проверка прогресса (логирование каждые 10%)
4. Проверка завершения (SystemSetting флаг)
5. Идемпотентность (повторный запуск)

**Подготовка данных:**
```java
@BeforeEach
void setUpOldSkills() {
    // Создаём 50 навыков без embeddings (старые данные)
    for (int i = 0; i < 50; i++) {
        SkillVersion skill = new SkillVersion();
        skill.setName("Legacy Skill " + i);
        skill.setMarkdown("Content for skill " + i);
        skill.setStatus(SkillStatus.PUBLISHED);
        skill.setEmbeddingVector(null);  // Старые данные без embedding
        skillRepository.save(skill);
    }
}
```

**Выполнение:**
```java
@Test
void testMigration() {
    // Запуск миграции
    embeddingMigrationService.migratePublishedSkills();

    // Ожидание завершения (может занять время)
    await().atMost(5, TimeUnit.MINUTES)
        .until(() -> {
            // Проверка флага завершения
            Optional<SystemSetting> flag = systemSettingRepository.findByKey(
                "embedding.migration.skills.completed"
            );
            return flag.isPresent() && flag.get().getValue().equals("true");
        });

    // Проверка: все навыки должны иметь embeddings
    List<SkillVersion> all = skillRepository.findAll();
    assertTrue(all.stream().allMatch(s -> s.getEmbeddingVector() != null));

    // Проверка идемпотентности
    long countBefore = skillRepository.count();
    embeddingMigrationService.migratePublishedSkills();  // Повторный запуск
    long countAfter = skillRepository.count();
    assertEquals(countBefore, countAfter);  // Дубликаты не созданы
}
```

**Запуск:**
```bash
cd backend
./gradlew test --tests "EmbeddingMigrationServiceIntegrationTest"
```

**Ожидаемый результат:**
- ✅ Все навыки мигрированы
- ✅ Логи прогресса присутствуют
- ✅ Флаг завершения установлен
- ✅ Повторный запуск не создаёт дубликаты
- ✅ Время миграции < 5 минут для 50 записей

---

### Scenario 5: API Endpoints

**Тестовый класс:** `backend/src/test/java/ru/hgd/sdlc/skill/api/SkillControllerIntegrationTest.java`

**Что проверяется:**
1. HTTP response codes
2. JSON response structure
3. Error handling (404, 500)
4. Authentication/authorization

**Setup:**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
class SkillControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SkillRepository skillRepository;

    @BeforeEach
    void setUp() {
        // Создаём тестовые данные
        skillRepository.deleteAll();
        createTestSkillWithEmbedding();
    }
}
```

**Выполнение:**
```java
@Test
void testGetSimilarSkills() throws Exception {
    UUID skillId = testSkill.getId();

    mockMvc.perform(get("/api/skills/{id}/similar", skillId)
            .header("Authorization", "Bearer " + getTestToken())
            .param("threshold", "0.6")
            .param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.similarSkills").isArray())
        .andExpect(jsonPath("$.total").isNumber())
        .andExpect(jsonPath("$.similarSkills[0].similarityScore").isNumber());
}
```

**Запуск:**
```bash
cd backend
./gradlew test --tests "SkillControllerIntegrationTest"
```

**Ожидаемый результат:**
- ✅ HTTP 200 для успешных запросов
- ✅ HTTP 404 если skill не найден
- ✅ HTTP 401 если не авторизован
- ✅ JSON структура корректна

---

### Scenario 6: Rules Embedding (аналогично Skills)

**Тестовые классы:**
- `RuleEmbeddingServiceIntegrationTest.java`
- `RuleControllerIntegrationTest.java`

**Запуск:**
```bash
cd backend
./gradlew test --tests "*Rule*IntegrationTest"
```

**Сценарии такие же, как для Skills.**

---

## Run All Integration Tests

### Полный запуск
```bash
cd backend
./gradlew test --tests "*IntegrationTest"
```

### По модулю
```bash
# Только embedding интеграционные тесты
cd backend
./gradlew test --tests "*Embedding*IntegrationTest"

# Только API интеграционные тесты
cd backend
./gradlew test --tests "*Controller*IntegrationTest"

# Только миграция
cd backend
./gradlew test --tests "*Migration*IntegrationTest"
```

---

## Teardown

### Остановка Docker контейнеров
Testcontainers автоматически останавливает контейнеры после тестов.

**Ручная очистка (если остались контейнеры):**
```bash
docker ps -a | grep testcontainers
docker rm -f $(docker ps -a | grep testcontainers | awk '{print $1}')
```

### Очистка тестовой БД (если использовалась внешняя БД)
```bash
docker exec -it pgvector-test psql -U test -d test -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

---

## Expected Results Summary

| Сценарий | Количество тестов | Ожидаемое время | NFR Coverage |
|----------|-------------------|-----------------|--------------|
| Embedding Generation | 2-3 | ~10s | PERF-002, REL-001 |
| Vector Search | 3-5 | ~15s | PERF-001, REL-002 |
| Search by Text | 2-3 | ~10s | PERF-001 |
| Data Migration | 2-4 | ~3-5min | PERF-003, REL-004 |
| API Endpoints | 3-5 | ~20s | SEC-001, SEC-002 |
| **Итого** | **12-20** | **~4-6min** | |

---

## Performance Testing (Load Testing)

Хотя это интеграционные тесты, они также проверяют NFR performance требования:

### PERF-001: API Response Time

**Измерение через тест:**
```java
@Test
void testApiPerformance() {
    long start = System.currentTimeMillis();

    List<SimilarItem> result = skillEmbeddingService.findSimilar(skillId, 0.6f, 10);

    long duration = System.currentTimeMillis() - start;

    // NFR PERF-001: p95 < 500ms
    assertTrue(duration < 500, "Search took " + duration + "ms, expected < 500ms");
}
```

### PERF-003: Migration Throughput

**Измерение через тест:**
```java
@Test
void testMigrationPerformance() {
    int recordCount = 100;
    long start = System.currentTimeMillis();

    embeddingMigrationService.migratePublishedSkills();

    long duration = System.currentTimeMillis() - start;
    double throughput = (double) recordCount / (duration / 1000.0);

    // NFR PERF-003: 10-50 embeddings/sec
    assertTrue(throughput >= 10, "Throughput " + throughput + "/sec, expected >= 10");
}
```

---

## Troubleshooting

### Issue: Testcontainers Failed to Start

**Ошибка:**
```
org.testcontainers.containers.ContainerLaunchException: Container startup failed
```

**Решение:**
```bash
# Проверить Docker
docker ps

# Увеличить timeout
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
    .withStartupTimeoutSeconds(180);
```

---

### Issue: pgvector Extension Not Found

**Ошибка:**
```
ERROR: type "vector" does not exist
```

**Решение:**
```java
// Убедиться, что используется правильный Docker образ
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
    "pgvector/pgvector:pg16"  // <-- Образ с pgvector
);

// Или добавить миграцию для установки расширения
@SQL(scripts = "/sql/install-pgvector.sql")
```

---

### Issue: Embedding Generation Timeout

**Ошибка:**
```
ConditionTimeoutException: awaited but not completed
```

**Решение:**
```java
// Увеличить timeout
await().atMost(30, TimeUnit.SECONDS)  // вместо 10
    .until(() -> ...);

// Или проверить логи async тасков
tail -f logs/spring.log | grep "EmbeddingTaskExecutor"
```

---

## Continuous Integration

### Jenkins Pipeline

```groovy
stage('Integration Tests') {
    steps {
        script {
            try {
                sh 'cd backend && ./gradlew test --tests "*IntegrationTest"'
            } finally {
                junit 'backend/build/test-results/test/*.xml'
            }
        }
    }
}
```

### GitHub Actions

```yaml
- name: Run Integration Tests
  run: |
    cd backend
    ./gradlew test --tests "*IntegrationTest"
```

---

## Quick Reference

| Задача | Команда |
|--------|---------|
| Все integration тесты | `cd backend && ./gradlew test --tests "*IntegrationTest"` |
| Только embedding | `cd backend && ./gradlew test --tests "*Embedding*IntegrationTest"` |
| Только API | `cd backend && ./gradlew test --tests "*Controller*IntegrationTest"` |
| Только миграция | `cd backend && ./gradlew test --tests "*Migration*IntegrationTest"` |
| Детальные логи | `cd backend && ./gradlew test --info --tests "*IntegrationTest"` |
| Очистка Docker | `docker rm -f $(docker ps -a \| grep testcontainers \| awk '{print $1}')` |
