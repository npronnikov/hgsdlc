# Technology Stack Decisions: Embeddings for Skills and Rules

**Document Version:** 1.0
**Date:** 2026-04-16
**Author:** AI NFR Requirements Agent
**Status:** Draft

---

## Overview

Документ содержит решения о технологическом стеке для функциональности "Embeddings for Skills and Rules". Каждое решение включает обоснование (rationale), альтернативы и компромиссы (trade-offs).

---

## Table of Contents

1. [Backend Stack](#backend-stack)
2. [Database Stack](#database-stack)
3. [ML/Embedding Stack](#mlembedding-stack)
4. [Frontend Stack](#frontend-stack)
5. [Infrastructure Stack](#infrastructure-stack)
6. [Testing Stack](#testing-stack)

---

## Backend Stack

### DEC-001: Java 21 + Spring Boot 3.3.0

**Decision:** Использовать существующий стек Java 21 и Spring Boot 3.3.0

**Rationale:**
- **Соответствие существующей архитектуре:** Проект уже использует Java 21 и Spring Boot 3.3.0
- **Virtual Threads:** Java 21 предоставляет virtual threads для эффективной асинхронной обработки
- **Pattern Matching:** Упрощает код при работе с embeddings
- **LTS поддержка:** Java 21 — LTS версия с долгосрочной поддержкой

**Alternatives:**
- **Kotlin:** Более лаконичный синтаксис, но требует обучения команды
- **Java 17:** Предыдущая LTS, но не имеет virtual threads
- **Java 25:** Новейшая версия, но ещё не LTS

**Trade-offs:**
- ✅ **Consistency:** Единый стек со всем проектом
- ✅ **Features:** Virtual threads для асинхронной генерации
- ✅ **Tooling:** Зрелая экосистема инструментов
- ❌ **Learning curve:** Virtual threads — новая фича (требует обучения)

---

### DEC-002: Spring @Async для асинхронной генерации

**Decision:** Использовать Spring @Async для асинхронной генерации embeddings

**Rationale:**
- **Встроенная поддержка:** Spring предоставляет @Async из коробки
- **Простота:** Минимум кода для асинхронной обработки
- **Интеграция:** Легко интегрируется с Spring Boot
- **Мониторинг:** Можно отслеживать через Spring Boot Actuator

**Alternatives:**
- **Project Loom (Virtual Threads):** Новая фича Java 21, но ещё экспериментальная
- **CompletableFuture:** Более низкоуровневый API, больше кода
- **Spring WebFlux:** Reactive подход, но сложнее в отладке
- **Message Queue (RabbitMQ/Kafka):** Избыточно для этой задачи

**Trade-offs:**
- ✅ **Simplicity:** Минимум кода, легко понять
- ✅ **Integration:** Единая экосистема со Spring
- ✅ **Testing:** Легко мокать и тестировать
- ❌ **Scalability:** Ограничена размером thread pool
- ❌ **Reliability:** Потеря задач при рестарте приложения

**Implementation:**
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "embeddingTaskExecutor")
    public Executor embeddingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("embedding-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

@Service
public class SkillEmbeddingService {
    @Async("embeddingTaskExecutor")
    public void generateEmbedding(UUID skillId) {
        // Асинхронная генерация
    }
}
```

---

### DEC-003: Spring Data JPA для работы с embeddings

**Decision:** Использовать Spring Data JPA для работы с embeddings в БД

**Rationale:**
- **Существующая инфраструктура:** Проект уже использует Spring Data JPA
- **Type Safety:** JPA обеспечивает типобезопасность при работе с embeddings
- **Миграции:** Легко интегрируется с Liquibase
- **Repositories:** Удобный API для CRUD операций

**Alternatives:**
- **JdbcTemplate:** Более производительный, но больше кода
- **Spring Data JDBC:** Проще, но меньше возможностей
- **jOOQ:** Типобезопасный SQL builder, но другая парадигма

**Trade-offs:**
- ✅ **Productivity:** Меньше кода благодаря абстракциям
- ✅ **Type Safety:** Компиляция проверяет типы
- ✅ **Ecosystem:** Богатая экосистема Spring Data
- ❌ **Overhead:** Дополнительный слой абстракции
- ❌ **Complexity:** Сложноdebug при проблемах с JPA

**Implementation:**
```java
@Entity
@Table(name = "skills")
public class SkillVersion {
    // ... existing fields ...

    @Column(name = "embedding_vector", columnDefinition = "vector")
    private float[] embeddingVector;

    // getters/setters
}

public interface SkillVersionRepository extends JpaRepository<SkillVersion, UUID> {
    @Query("""
        SELECT s FROM SkillVersion s
        WHERE s.status = 'PUBLISHED'
          AND s.embeddingVector IS NOT NULL
          AND s.id != :currentId
        ORDER BY s.embeddingVector <=> :queryVector
        """)
    List<SkillVersion> findSimilar(
        @Param("currentId") UUID currentId,
        @Param("queryVector") float[] queryVector,
        Pageable pageable
    );
}
```

---

## Database Stack

### DEC-004: PostgreSQL + pgvector extension

**Decision:** Использовать PostgreSQL с расширением pgvector для хранения и поиска embeddings

**Rationale:**
- **Существующая инфраструктура:** Проект уже использует PostgreSQL
- **pgvector extension:** Специализированное расширение для vector operations
- **Производительность:** IVFFlat индекс для быстрого поиска
- **Надёжность:** PostgreSQL — надёжная БД с ACID гарантиями
- **Open Source:** Бесплатный и open source

**Alternatives:**
- **Separate Vector DB (Pinecone, Weaviate, Qdrant):** Более производительно для large scale, но:
  - ❌ Дополнительная инфраструктура
  - ❌ Сложность деплоя и мониторинга
  - ❌ Additional cost (для managed solutions)
- **Elasticsearch с dense_vector:** Хорошо для полнотекстового + vector search, но:
  - ❌ Избыточно для только vector search
  - ❌ Больше потребление ресурсов
- **MongoDB:** Поддерживает vector search, но:
  - ❌ Не соответствует существующей архитектуре (реляционная БД)
  - ❌ Меньше опыта в команде

**Trade-offs:**
- ✅ **Integration:** Легкая интеграция с существующей БД
- ✅ **Cost:** Нет дополнительных затрат (vs managed vector DB)
- ✅ **Simplicity:** Единая БД для всех данных
- ✅ **Consistency:** ACID гарантии для embeddings
- ❌ **Scalability:** Ограничена масштабируемостью vs dedicated vector DB
- ❌ **Performance:** Медленнее specialized vector DB при > 1M records

**Implementation:**
```sql
-- Установка расширения
CREATE EXTENSION IF NOT EXISTS vector;

-- Добавление колонки
ALTER TABLE skills ADD COLUMN embedding_vector vector(384);

-- Создание индекса
CREATE INDEX idx_skills_embedding_vector ON skills
USING ivfflat (embedding_vector vector_cosine_ops)
WITH (lists = 100);

-- Поиск похожих
SELECT s.id, s.skill_id, s.name, s.description,
       1 - (s.embedding_vector <=> '[0.1, 0.2, ...]') as similarity
FROM skills s
WHERE s.status = 'PUBLISHED'
  AND s.id != 'current-id'
  AND s.embedding_vector IS NOT NULL
ORDER BY s.embedding_vector <=> '[0.1, 0.2, ...]'
LIMIT 10;
```

---

### DEC-005: IVFFlat индекс для быстрого поиска

**Decision:** Использовать IVFFlat (Inverted File with Flat compression) индекс для ускорения vector similarity search

**Rationale:**
- **Производительность:** Существенное ускорение поиска (10-100x)
- **Баланс:** Баланс между точностью и скоростью
- **Поддержка в pgvector:** Встроенная поддержка в pgvector
- **Настраиваемость:** Параметр `lists` для trade-off точности/скорости

**Alternatives:**
- **Без индекса (full scan):** Просто, но очень медленно при большом количестве записей
- **HNSW индекс:** Более точный и быстрый, но:
  - ❌ Больше памяти
  - ❌ Медленнее обновления (bad для frequent updates)
- **IVFADC индекс:** Более компактный, но:
  - ❌ Меньше точности
  - ❌ Сложнее в настройке

**Trade-offs:**
- ✅ **Performance:** 10-100x быстрее full scan
- ✅ **Memory:** Меньше памяти чем HNSW
- ✅ **Updates:** Быстрее обновления чем HNSW
- ❌ **Accuracy:** Небольшая потеря точности (~1-5%)
- ❌ **Tuning:** Требует настройки параметра `lists`

**Implementation:**
```sql
-- Создание индекса
-- lists = sqrt(N) где N — количество записей
-- Для 10,000 записей: lists = 100
CREATE INDEX idx_skills_embedding_vector ON skills
USING ivfflat (embedding_vector vector_cosine_ops)
WITH (lists = 100);

-- Перестроение индекса при значительном росте данных
REINDEX INDEX idx_skills_embedding_vector;
```

---

## ML/Embedding Stack

### DEC-006: Sentence-BERT (Local) как Primary Provider

**Decision:** Использовать Sentence-BERT (all-MiniLM-L6-v2) как primary провайдер для генерации embeddings

**Rationale:**
- **Cost-free:** Нет затрат на API calls
- **Privacy:** Данные не покидают сервер
- **Performance:** Быстрая генерация (100-500ms per document)
- **Quality:** Хорошее качество для семантического поиска
- **Multilingual:** Поддержка множества языков
- **Size:** Небольшой размер модели (~400 MB в RAM)
- **Open Source:** Бесплатный и open source

**Alternatives:**
- **OpenAI text-embedding-ada-002:** Более качественные embeddings, но:
  - ❌ Cost: $0.0001 per 1K tokens (adds up)
  - ❌ Latency: Сетевые задержки (200-1000ms)
  - ❌ Privacy: Данные отправляются в OpenAI
  - ❌ Rate limits: Ограничения на количество запросов
- **Cohere embed-multilingual-v3.0:** Альтернатива OpenAI, но:
  - ❌ Те же минусы (cost, latency, privacy)
- **DistilBERT:** Меньше размер, но хуже качество
- **RoBERTa:** Лучше качество, но больше размер

**Trade-offs:**
- ✅ **Cost:** Free (no API costs)
- ✅ **Privacy:** Данные на сервере
- ✅ **Latency:** Локальная генерация (без network)
- ✅ **Control:** Полный контроль над моделью
- ❌ **Quality:** Чуть хуже чем OpenAI (но достаточно для use case)
- ❌ **Maintenance:** Требует обновления модели
- ❌ **Resource:** Использует RAM (~400 MB)

**Implementation:**
```java
@Component
@ConditionalOnProperty(name = "embedding.provider", havingValue = "local")
public class LocalEmbeddingProvider implements EmbeddingProvider {

    private final SentenceEmbedder embedder;

    public LocalEmbeddingProvider() {
        // Загрузка модели при старте
        this.embedder = SentenceEmbedder.load("all-MiniLM-L6-v2");
    }

    @Override
    public float[] generateEmbedding(String text) {
        // Генерация embedding
        return embedder.embed(text);
    }

    @Override
    public int getDimension() {
        return 384; // all-MiniLM-L6-v2 dimension
    }
}
```

---

### DEC-007: OpenAI как Fallback Provider

**Decision:** Использовать OpenAI API (text-embedding-ada-002) как fallback провайдер

**Rationale:**
- **Higher Quality:** Лучшее качество embeddings (1536-dim)
- **Scalability:** Не использует ресурсы сервера
- **Reliability:** Fallback при проблемах с локальной моделью
- **Future-proof:** Возможность переключения в будущем

**Alternatives:**
- **Только локальная модель:** Проще, но нет fallback
- **Только OpenAI:** Качественнее, но дороже и privacy concerns
- **Cohere:** Альтернатива OpenAI, но меньше experience

**Trade-offs:**
- ✅ **Quality:** Лучшее качество embeddings
- ✅ **Scalability:** Не использует ресурсы сервера
- ✅ **Fallback:** Резервный вариант
- ❌ **Cost:** Дополнительные затраты
- ❌ **Latency:** Сетевые задержки
- ❌ **Privacy:** Данные отправляются в OpenAI

**Implementation:**
```java
@Component
@ConditionalOnProperty(name = "embedding.provider", havingValue = "openai")
public class OpenAIEmbeddingProvider implements EmbeddingProvider {

    private final OpenAiApiService openAiApi;

    @Override
    public float[] generateEmbedding(String text) {
        // Вызов OpenAI API
        EmbeddingResponse response = openAiApi.createEmbedding(
            "text-embedding-ada-002",
            text
        );
        return response.getData()[0].getEmbedding();
    }

    @Override
    public int getDimension() {
        return 1536; // text-embedding-ada-002 dimension
    }
}
```

---

### DEC-008: Стратегия выбора провайдера (Multi-Provider)

**Decision:** Реализовать стратегию multi-provider с автоматическим fallback

**Rationale:**
- **Reliability:** Fallback при недоступности основного провайдера
- **Flexibility:** Возможность переключения без пересборки
- **Cost Control:** Использовать локальную модель по умолчанию
- **Quality Enhancement:** Опционально использовать OpenAI для critical cases

**Alternatives:**
- **Один провайдер:** Проще, но менее надёжно
- **Ручное переключение:** Требует вмешательства

**Trade-offs:**
- ✅ **Reliability:** Automatic fallback
- ✅ **Flexibility:** Легко переключать
- ✅ **Cost:** Использовать local по умолчанию
- ❌ **Complexity:** Дополнительная логика
- ❌ **Testing:** Нужно тестировать оба провайдера

**Implementation:**
```java
@Service
public class EmbeddingService {

    private final EmbeddingProvider primaryProvider;
    private final EmbeddingProvider fallbackProvider;

    @Retryable(
        maxAttempts = 2,
        backoff = @Backoff(delay = 1000)
    )
    public float[] generateEmbedding(String text) {
        try {
            return primaryProvider.generateEmbedding(text);
        } catch (Exception e) {
            log.warn("Primary provider failed, using fallback: {}", e.getMessage());
            return fallbackProvider.generateEmbedding(text);
        }
    }
}
```

---

### DEC-009: Библиотека для Sentence-BERT

**Decision:** Использовать DL4J (DeepLearning4J) или Python bridge для Sentence-BERT

**Rationale:**
- **Native Java:** DL4J — native Java библиотека
- **Integration:** Легкая интеграция с Spring Boot
- **Performance:** Хорошая производительность

**Alternatives:**
- **Python bridge (ProcessBuilder):** Вызов Python скрипта из Java
  - ✅ Доступ к полному Python ecosystem
  - ❌ Overhead на процесс
  - ❌ Сложнее debugging
- **ONNX Runtime:** Оптимизированный runtime для ML моделей
  - ✅ Быстрее DL4J
  - ❌ Меньше community support
- **TorchServe:** Отдельный сервис для PyTorch моделей
  - ✅ Масштабируемо
  - ❌ Additional infrastructure

**Trade-offs:**
- ✅ **Native:** Pure Java решение
- ✅ **Integration:** Легкая интеграция
- ✅ **Support:** Активная community
- ❌ **Size:** Большой размер JAR (~200 MB)
- ❌ **Performance:** Медленнее Python-native решений

**Implementation:**
```gradle
// build.gradle.kts
implementation("org.deeplearning4j:deeplearning4j-core:1.0.0-M2.1")
implementation("org.deeplearning4j:deeplearning4j-modelimport:1.0.0-M2.1")
```

```java
@Component
public class LocalEmbeddingProvider implements EmbeddingProvider {

    private final SentenceEmbedder embedder;

    @PostConstruct
    public void init() {
        // Загрузка модели из classpath
        File modelFile = new ClassPathResource("models/all-MiniLM-L6-v2.pt").getFile();
        this.embedder = new SentenceEmbedder(modelFile);
    }
}
```

---

## Frontend Stack

### DEC-010: React 18 + Ant Design 5 для UI

**Decision:** Использовать существующий стек React 18 и Ant Design 5 для UI компонентов

**Rationale:**
- **Consistency:** Единый UI со всем проектом
- **Components:** Готовые компоненты (List, Card, Tag)
- **Theming:** Поддержка тем (light/dark mode)
- **Performance:** React 18 с Concurrent Mode

**Alternatives:**
- **Material-UI:** Альтернатива, но не соответствует существующему UI
- **Chakra UI:** Более современный, но меньше experience

**Trade-offs:**
- ✅ **Consistency:** Единый стиль приложения
- ✅ **Productivity:** Готовые компоненты
- ✅ **Experience:** Команда знает Ant Design
- ❌ **Bundle Size:** Ant Design тяжеловесен

**Implementation:**
```jsx
import { List, Card, Tag, Progress } from 'antd';

function SimilarSkills({ similarSkills }) {
  return (
    <Card title="Similar Skills">
      <List
        dataSource={similarSkills}
        renderItem={(skill) => (
          <List.Item>
            <List.Item.Meta
              title={skill.name}
              description={skill.description}
            />
            <div>
              <Progress
                type="circle"
                percent={skill.similarityPercent}
                width={60}
              />
            </div>
          </List.Item>
        )}
      />
    </Card>
  );
}
```

---

### DEC-011: Debounce для поиска по тексту

**Decision:** Использовать lodash/debounce для debounce при вводе текста

**Rationale:**
- **Performance:** Уменьшает количество API calls
- **UX:** Плавный UX без задержек
- **Simple:** Простая реализация

**Alternatives:**
- **Custom debounce implementation:** Больше контроля, но больше кода
- **React useMemo:** Другой use case

**Trade-offs:**
- ✅ **Performance:** Меньше API calls
- ✅ **UX:** Плавный интерфейс
- ✅ **Simple:** Готовое решение
- ❌ **Dependency:** Еще одна зависимость

**Implementation:**
```jsx
import { debounce } from 'lodash';

function SkillEditor() {
  const [similarSkills, setSimilarSkills] = useState([]);

  const debouncedSearch = useMemo(
    () => debounce(async (markdown) => {
      const response = await api.getSimilarSkillsByText(markdown);
      setSimilarSkills(response.data.similarSkills);
    }, 500),
    []
  );

  const handleMarkdownChange = (e) => {
    const markdown = e.target.value;
    setSkillMarkdown(markdown);
    debouncedSearch(markdown);
  };

  return <TextArea onChange={handleMarkdownChange} />;
}
```

---

## Infrastructure Stack

### DEC-012: Docker Compose для локальной разработки

**Decision:** Использовать Docker Compose для запуска PostgreSQL с pgvector

**Rationale:**
- **Consistency:** Единое окружение для всех разработчиков
- **Isolation:** Изоляция от системной БД
- **Easy Setup:** Простой старт через docker compose up

**Alternatives:**
- **Локальная установка PostgreSQL:** Сложнее setup
- **Cloud PostgreSQL:** Дорого и требует internet

**Trade-offs:**
- ✅ **Consistency:** Единое окружение
- ✅ **Isolation:** Изоляция от системы
- ✅ **Easy:** Простой старт
- ❌ **Overhead:** Дополнительный layer

**Implementation:**
```yaml
# deploy/compose.dev.yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: hgsdlc
      POSTGRES_USER: hgsdlc
      POSTGRES_PASSWORD: hgsdlc
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U hgsdlc"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

---

### DEC-013: Spring Boot Actuator для Health Checks

**Decision:** Использовать Spring Boot Actuator для health checks и metrics

**Rationale:**
- **Built-in:** Уже встроен в Spring Boot
- **Standard:** Стандарт для Spring приложений
- **Monitoring:** Легкая интеграция с Prometheus/Grafana

**Alternatives:**
- **Custom health checks:** Больше контроля, но больше кода
- **Micrometer напрямую:** Низкоуровневый API

**Trade-offs:**
- ✅ **Built-in:** Уже доступен
- ✅ **Standard:** Стандартный подход
- ✅ **Integration:** Легко интегрируется
- ❌ **Overhead:** Небольшой overhead

**Implementation:**
```java
@Component
public class EmbeddingProviderHealthIndicator implements HealthIndicator {

    private final EmbeddingProvider embeddingProvider;

    @Override
    public Health health() {
        try {
            int dimension = embeddingProvider.getDimension();
            return Health.up()
                .withDetail("provider", embeddingProvider.getProviderName())
                .withDetail("dimension", dimension)
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

---

## Testing Stack

### DEC-014: Testcontainers для Integration Tests

**Decision:** Использовать Testcontainers для integration tests с PostgreSQL+pgvector

**Rationale:**
- **Real Database:** Настоящая PostgreSQL с pgvector
- **Isolation:** Изолированные тесты
- **Docker-based:** Использует Docker для контейнеров
- **Community:** Большой community support

**Alternatives:**
- **H2 in-memory:** Не поддерживает pgvector
- **Embedded PostgreSQL:** Сложнее setup
- **Mock repositories:** Не тестирует реальную БД

**Trade-offs:**
- ✅ **Realism:** Настоящая БД
- ✅ **Isolation:** Изолированные тесты
- ✅ **Docker:** Использует Docker
- ❌ **Performance:** Медленнее H2
- ❌ **Docker:** Требует Docker daemon

**Implementation:**
```java
@Testcontainers
@SpringBootTest
public class SkillEmbeddingServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "pgvector/pgvector:pg16"
    )
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void shouldGenerateEmbedding() {
        // Test code
    }
}
```

---

### DEC-015: Mock EmbeddingProvider для Unit Tests

**Decision:** Использовать Mockito для мокирования EmbeddingProvider в unit тестах

**Rationale:**
- **Speed:** Быстрые unit тесты
- **Isolation:** Изолированная логика
- **Standard:** Стандарт для Java тестов

**Alternatives:**
- **Real provider:** Медленно и зависит от внешних ресурсов
- **Stub implementation:** Больше кода

**Trade-offs:**
- ✅ **Speed:** Быстрые тесты
- ✅ **Isolation:** Изолированная логика
- ✅ **Standard:** Стандартный подход
- ❌ **Coverage:** Не тестирует реального provider

**Implementation:**
```java
@ExtendWith(MockitoExtension.class)
public class SkillEmbeddingServiceTest {

    @Mock
    private EmbeddingProvider embeddingProvider;

    @InjectMocks
    private SkillEmbeddingService service;

    @Test
    void shouldGenerateEmbedding() {
        // Arrange
        String markdown = "# Test skill";
        float[] expectedEmbedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingProvider.generateEmbedding(markdown))
            .thenReturn(expectedEmbedding);

        // Act
        float[] result = service.generateEmbedding(markdown);

        // Assert
        assertArrayEquals(expectedEmbedding, result, 0.001f);
    }
}
```

---

## Summary Table

| Категория | Решение | Primary Benefit | Key Trade-off |
|-----------|---------|-----------------|---------------|
| **Backend** | Java 21 + Spring Boot 3.3.0 | Consistency | Virtual threads learning |
| **Async** | Spring @Async | Simplicity | Limited scalability |
| **ORM** | Spring Data JPA | Productivity | Overhead |
| **Database** | PostgreSQL + pgvector | Integration | Limited scalability |
| **Index** | IVFFlat | Performance | Accuracy loss |
| **ML** | Sentence-BERT (local) | Cost-free | Lower quality |
| **Fallback** | OpenAI API | Quality | Cost + privacy |
| **Strategy** | Multi-provider | Reliability | Complexity |
| **Library** | DL4J | Native Java | Size |
| **Frontend** | React 18 + Ant Design 5 | Consistency | Bundle size |
| **Debounce** | lodash/debounce | Performance | Dependency |
| **Infrastructure** | Docker Compose | Consistency | Overhead |
| **Monitoring** | Spring Boot Actuator | Built-in | Overhead |
| **Testing** | Testcontainers | Realism | Slower |
| **Mocking** | Mockito | Speed | No real provider |

---

## Appendix A: Configuration Examples

### application.yml

```yaml
# Embedding configuration
embedding:
  # Primary provider: local or openai
  provider: local

  # Local provider config
  local:
    model-name: all-MiniLM-L6-v2
    dimension: 384
    model-path: classpath:/models/all-MiniLM-L6-v2.pt

  # OpenAI provider config
  openai:
    api-key: ${OPENAI_API_KEY}
    model: text-embedding-ada-002
    dimension: 1536
    timeout: 30000
    max-retries: 3

  # Similarity search config
  similarity:
    threshold: 0.6
    max-results: 10

  # Async config
  async:
    core-pool-size: 4
    max-pool-size: 8
    queue-capacity: 100

# Spring config
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/hgsdlc
    username: hgsdlc
    password: hgsdlc

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

# Actuator config
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      embedding.provider: ${embedding.provider}
```

---

## Appendix B: System Settings

Runtime настройки через SystemSetting:

| Key | Default | Description |
|-----|---------|-------------|
| `embedding.provider` | `local` | Primary provider (local/openai) |
| `embedding.similarity.threshold` | `0.6` | Similarity threshold (0.0-1.0) |
| `embedding.max.results` | `10` | Max results for search |
| `embedding.skills.migrated` | `false` | Migration completed flag |
| `embedding.rules.migrated` | `false` | Migration completed flag |

---

**Document End**
