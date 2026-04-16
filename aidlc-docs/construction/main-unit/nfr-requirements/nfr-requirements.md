# NFR Requirements: Embeddings for Skills and Rules

**Document Version:** 1.0
**Date:** 2026-04-16
**Author:** AI NFR Requirements Agent
**Status:** Draft

---

## Overview

Документ содержит детальные нефункциональные требования (NFR) для функциональности "Embeddings for Skills and Rules". Требования организованы по ключевым категориям с измеримыми метриками и рекомендациями по реализации.

---

## Table of Contents

1. [Scalability](#scalability)
2. [Performance](#performance)
3. [Availability](#availability)
4. [Security](#security)
5. [Reliability](#reliability)
6. [Maintainability](#maintainability)

---

## Scalability

### SCAL-001: Масштабирование хранилища embeddings

**Требование:** Система должна поддерживать хранение embeddings для большого количества skills/rules без существенного ухудшения производительности.

**Метрики:**
- Поддержка до 100,000 версий skills/rules
- Размер embedding: 384 × 4 bytes = ~1.5 KB на запись
- Общий объём: ~150 MB для 100,000 записей (приемлемо)

**Риски:**
- Рост размера БД при увеличении размерности вектора (1536 для OpenAI = ~6 KB на запись)
- Фрагментация индекса IVFFlat при частых обновлениях

**Рекомендации:**
1. Использовать фиксированную размерность 384 (Sentence-BERT) как primary
2. Регулярная реиндексация IVFFlat для поддержки качества поиска
3. Мониторинг размера индекса через pg_stat_user_tables

---

### SCAL-002: Масштабирование нагрузки на поиск

**Требование:** Система должна поддерживать конкурентные запросы поиска похожих элементов без деградации времени отклика.

**Метрики:**
- Ожидаемая нагрузка: 10-50 запросов/сек (peak)
- Время отклика: < 500ms для 95 percentile (p95)
- Поддержка до 100 параллельных пользователей

**Риски:**
- Высокая CPU нагрузка при вычислении косинусного сходства
- Блокировки БД при одновременной генерации embeddings

**Рекомендации:**
1. Настройка connection pool для базы данных (HikariCP)
2. Использование индекса IVFFlat с параметром `lists = sqrt(N)` где N — количество записей
3. Асинхронная генерация embeddings для разгрузки основных операций

---

### SCAL-003: Масштабирование генерации embeddings

**Требование:** Система должна эффективно обрабатывать пакетную генерацию embeddings (миграция, bulk updates).

**Метрики:**
- Скорость генерации: ~10-50 embeddings/сек (Sentence-BERT local)
- Время миграции для 10,000 записей: ~3-17 минут

**Риски:**
- Длительная блокировка БД при массовой миграции
- Исчерпание памяти при загрузке модели Sentence-BERT

**Рекомендации:**
1. Batch обработка с размером батча 50-100 записей
2. Использование @Async с ограничением пула потоков
3. Паузы между батчами для освобождения ресурсов
4. Логирование прогресса каждые 10% обработанных записей

---

## Performance

### PERF-001: Время отклика API поиска

**Требование:** Поиск похожих элементов должен выполняться быстро для обеспечения хорошего UX.

**Метрики:**
- p50 (медиана): < 200ms
- p95: < 500ms
- p99: < 1000ms
- Target: < 300ms для типичного запроса

**Измерение:**
- Spring Boot Actuator metrics: `http.server.requests`
- Custom timer: `embedding.similarity.search.duration`

**Риски:**
- Медленный поиск без индекса (full table scan)
- Деградация при большом количестве записей (> 50,000)

**Рекомендации:**
1. Обязательное использование индекса IVFFlat
2. EXPLAIN ANALYZE для проверки использования индекса
3. Лимит результатов (top-10) для уменьшения объёма данных
4. Кэширование настроек (threshold, limit) в памяти

---

### PERF-002: Время генерации embedding

**Требование:** Генерация embedding не должна блокировать пользовательский интерфейс.

**Метрики:**
- Sentence-BERT local: 100-500ms per document
- OpenAI API: 200-1000ms (с учётом network latency)
- Асинхронная обработка: пользователь не ждёт

**Измерение:**
- Custom timer: `embedding.generation.duration`
- Логирование времени генерации при каждом вызове

**Риски:**
- Задержки при обращении к внешнему API (OpenAI)
- Истечение timeout при длинных документах

**Рекомендации:**
1. Обязательная асинхронная обработка (@Async)
2. Timeout: 10 секунд для локальной модели, 30 секунд для OpenAI
3. Retry логика для внешних API (3 попытки с exponential backoff)
4. Fallback на локальную модель при недоступности OpenAI

---

### PERF-003: Производительность миграции данных

**Требование:** Миграция существующих данных должна выполняться за приемлемое время без простоя системы.

**Метрики:**
- Скорость: 10-50 embeddings/сек
- Время для 10,000 записей: < 30 минут
- Прогресс: логирование каждые 10% или каждые 100 записей

**Измерение:**
- Логи миграции с timestamp
- SystemSetting для отметки завершения

**Риски:**
- Длительная миграция блокирует деплой
- Ошибки миграции требуют повторного запуска

**Рекомендации:**
1. Идемпотентная миграция (проверка `embedding_vector IS NULL`)
2. Обработка ошибок без прерывания (continue on error)
3. Возможность перезапуска миграции
4. Запуск миграции в @PostConstruct при старте приложения

---

### PERF-004: Оптимизация использования памяти

**Требование:** Система должна эффективно использовать память при загрузке ML-модели.

**Метрики:**
- Размер модели Sentence-BERT: ~400 MB (в RAM)
- Heap overhead: ~100-200 MB
- Total: ~500-600 MB дополнительной памяти

**Измерение:**
- JVM metrics: `jvm.memory.used`
- VisualVM или JProfiler для анализа heap dump

**Риски:**
- OutOfMemoryError при недостаточном heap
- Утечки памяти при повторной загрузке модели

**Рекомендации:**
1. Singleton для EmbeddingProvider (один экземпляр на приложение)
2. Lazy загрузка модели при первом использовании
3. Увеличение heap до 2GB для production
4. GC tuning: G1GC с настройками для low latency

---

## Availability

### AVAIL-001: Отказоустойчивость при сбое провайдера embeddings

**Требование:** Система должна оставаться функциональной при сбое провайдера embeddings.

**Метрики:**
- MTBF (Mean Time Between Failures): > 99.9% uptime
- MTTR (Mean Time To Recovery): < 5 минут (автоматический fallback)

**Риски:**
- Недоступность OpenAI API
- Ошибки локальной модели (OOM, corruption)

**Рекомендации:**
1. Multi-provider архитектура (Local + OpenAI)
2. Автоматический fallback: OpenAI → Local
3. Graceful degradation: null embedding не блокирует операции
4. Circuit Breaker для внешних API (Hystrix/Resilience4j)

---

### AVAIL-002: Обработка сбоев БД

**Требование:** Система должна корректно обрабатывать сбои подключения к БД.

**Метрики:**
- Connection pool retry: 3 попытки
- Retry interval: 1, 2, 4 секунды (exponential backoff)

**Риски:**
- Потеря подключений при high load
- Deadlocks при одновременной генерации embeddings

**Рекомендации:**
1. HikariCP connection pool с настройками:
   ```yaml
   spring.datasource.hikari.maximum-pool-size: 20
   spring.datasource.hikari.connection-timeout: 30000
   spring.datasource.hikari.connection-test-query: SELECT 1
   ```
2. Transaction rollback при ошибках генерации
3. Deadlock detection и retry логика

---

### AVAIL-003: Отказоустойчивость миграции

**Требование:** Миграция данных должна быть устойчивой к сбоям и восстанавливаемой.

**Метрики:**
- Идемпотентность: возможность перезапуска
- Checkpoint: логирование прогресса каждые 10%

**Риски:**
- Прерывание миграции при рестарте приложения
- Потеря прогресса при ошибке

**Рекомендации:**
1. SystemSetting для отметки завершения миграции
2. Проверка `embedding_vector IS NULL` вместо флагов
3. Логирование прогресса с возможностью резюме
4. Ручной запуск миграции через CLI (опционально)

---

## Security

### SEC-001: Защита API endpoints

**Требование:** API endpoints для поиска похожих элементов должны быть защищены от неавторизованного доступа.

**Метрики:**
- Все endpoint'ы требуют аутентификации
- HTTP 401 для неавторизованных запросов
- HTTP 403 для недостаточных прав

**Риски:**
- утечка информации о skills/rules через API
- DoS атаки через частые запросы поиска

**Рекомендации:**
1. Spring Security configuration:
   ```java
   @PreAuthorize("isAuthenticated()")
   public ResponseEntity<SimilarSkillsResponse> getSimilarSkills(@PathVariable UUID id)
   ```
2. Rate limiting: 100 запросов/минута на пользователя
3. Audit logging для всех запросов поиска

---

### SEC-002: Защита при генерации embeddings

**Требование:** Генерация embeddings должна требовать права на редактирование skills/rules.

**Метрики:**
- Проверка ролей: ROLE_FLOW_CONFIGURATOR или ROLE_ADMIN
- HTTP 403 для недостаточных прав

**Риски:**
- Несанкционированная генерация embeddings
- Использование ресурсов (CPU, RAM) неавторизованными пользователями

**Рекомендации:**
1. Проверка прав в SkillService/RuleService (уже реализовано)
2. Дополнительная проверка в EmbeddingService (defense in depth)

---

### SEC-003: Безопасность при использовании OpenAI API

**Требование:** API ключи OpenAI должны быть защищены и не попадать в логи.

**Метрики:**
- API ключи хранятся в environment variables
- API ключи не логируются
- HTTP headers фильтруются в логах

**Риски:**
- Утечка API ключа через логи или git
- Превышение лимитов OpenAI (rate limits, costs)

**Рекомендации:**
1. Хранение ключей в `application-secret.properties` (не в git)
2. Фильтрация в `logback-spring.xml`:
   ```xml
   <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
   ```
3. Использование proxied API (опционально) для скрытия ключа
4. Monitoring costs через OpenAI dashboard

---

### SEC-004: Валидация входных данных

**Требование:** Все входные данные должны быть валидированы для защиты от инъекций.

**Метрики:**
- Валидация через Bean Validation (@NotNull, @NotBlank, @Size)
- Sanitization markdown контента перед генерацией embedding

**Риски:**
- XSS через markdown контент
- SQL injection при поиске (mitigated через JPA/parameterized queries)

**Рекомендации:**
1. DTO с валидацией:
   ```java
   public class SimilarItemsSearchRequest {
       @NotBlank
       @Size(max = 10000)
       private String text;
   }
   ```
2. Sanitization через jsoup или类似的 библиотеки
3. Parameterized queries через JPA (защита от SQL injection)

---

### SEC-005: Защита от DoS при генерации embeddings

**Требование:** Система должна быть защищена от DoS атак через частую генерацию embeddings.

**Метрики:**
- Rate limiting: 10 генераций/минута на пользователя
- Queue depth: максимум 100 задач в очереди

**Риски:**
- Исчерпание ресурсов CPU/RAM при высокой нагрузке
- Отказ в обслуживании для легитимных пользователей

**Рекомендации:**
1. Rate limiting через Spring Security или Resilience4j
2. Bounded queue для @Async задач
3. Отказ с HTTP 429 при превышении лимита

---

## Reliability

### REL-001: Надёжность генерации embeddings

**Требование:** Генерация embedding должна быть надёжной и не прерывать основные операции.

**Метрики:**
- Успешность генерации: > 95%
- Graceful degradation: null embedding при ошибке
- Retry: 3 попытки с exponential backoff

**Риски:**
- Ошибки модели (corruption, OOM)
- Сетевые ошибки при обращении к OpenAI

**Рекомендации:**
1. Try-catch вокруг генерации:
   ```java
   try {
       float[] embedding = embeddingProvider.generateEmbedding(markdown);
       skill.setEmbeddingVector(embedding);
   } catch (Exception e) {
       log.error("Failed to generate embedding for skill {}: {}", skillId, e.getMessage());
       skill.setEmbeddingVector(null); // graceful degradation
   }
   ```
2. Retry logic для внешних API
3. Circuit breaker для OpenAI API

---

### REL-002: Надёжность поиска похожих элементов

**Требование:** Поиск должен быть надёжным и возвращать корректные результаты.

**Метрики:**
- Корректность: косинусное сходство вычисляется правильно
- Полнота: все похожие элементы найдены (precision/recall)
- Обработка null: пустой результат при null embedding

**Риски:**
- Некорректные вектора в БД (размерность, значения)
- Ошибки pgvector при операциях сравнения

**Рекомендации:**
1. Unit тесты для вычисления сходства
2. Integration тесты для pgvector queries
3. Validation векторов при сохранении:
   ```java
   if (vector.length != expectedDimension) {
       throw new ValidationException("Invalid vector dimension");
   }
   ```

---

### REL-003: Консистентность данных

**Требование:** Embeddings должны быть консистентны с markdown контентом.

**Метрики:**
- Embedding соответствует актуальному markdown
- Обновление embedding при изменении markdown

**Риски:**
- Рассинхрон embedding'а и markdown
- Устаревшие embedding'и после ручного обновления БД

**Рекомендации:**
1. Event-driven обновление: триггер при изменении markdown
2. Периодическая проверка консистентности (опционально)
3. Версионирование модели embedding для инвалидации

---

### REL-004: Обработка ошибок миграции

**Требование:** Миграция должна надёжно обрабатывать ошибки отдельных записей.

**Метрики:**
- Идемпотентность: возможность перезапуска
- Continue on error: ошибки не прерывают миграцию
- Логирование всех ошибок

**Риски:**
- Потеря данных при ошибке миграции
- Неполная миграция без уведомления

**Рекомендации:**
1. Try-catch для каждой записи:
   ```java
   for (SkillVersion skill : skills) {
       try {
           generateEmbedding(skill);
       } catch (Exception e) {
           log.error("Failed to migrate skill {}: {}", skill.getId(), e.getMessage());
           // continue to next
       }
   }
   ```
2. Финальный отчёт: {total, processed, failed, failedIds}
3. SystemSetting для отметки завершения

---

## Maintainability

### MAINT-001: Модульность архитектуры

**Требование:** Архитектура должна быть модульной для лёгкой замены компонентов.

**Метрики:**
- Interface segregation: отдельные интерфейсы для компонентов
- Strategy pattern для EmbeddingProvider
- Dependency Injection через Spring

**Риски:**
- Strong coupling к конкретному провайдеру
- Сложность замены модели embedding

**Рекомендации:**
1. Interface-based design:
   ```java
   public interface EmbeddingProvider {
       float[] generateEmbedding(String text);
       int getDimension();
       String getProviderName();
   }
   ```
2. Две реализации: LocalEmbeddingProvider, OpenAIEmbeddingProvider
3. Выбор провайдера через конфигурацию:
   ```yaml
   embedding.provider: local  # or openai
   ```

---

### MAINT-002: Тестируемость

**Требование:** Код должен быть легко тестируемым.

**Метрики:**
- Unit test coverage: > 80% для embedding логики
- Integration tests для API endpoints
- Mockability: внешние зависимости мокаются

**Риски:**
- Сложность мокирования EmbeddingProvider
- Зависимость от внешних API (OpenAI)

**Рекомендации:**
1. Mock EmbeddingProvider в unit тестах:
   ```java
   @Mock
   private EmbeddingProvider embeddingProvider;
   
   when(embeddingProvider.generateEmbedding(anyString()))
       .thenReturn(new float[]{0.1f, 0.2f, ...});
   ```
2. Testcontainers для интеграционных тестов с PostgreSQL+pgvector
3. Stub responses для OpenAI API

---

### MAINT-003: Отладочность и мониторинг

**Требование:** Система должна предоставлять достаточные инструменты для отладки.

**Метрики:**
- Логирование ключевых операций (генерация, поиск, миграция)
- Metrics через Spring Boot Actuator
- Tracing для distributed tracing (опционально)

**Риски:**
- Сложность диагностики проблем в production
- Отсутствие visibility в производительности embedding

**Рекомендации:**
1. Structured logging:
   ```java
   log.info("Generated embedding for skill {} using provider {} (dimension: {})",
       skillId, providerName, dimension);
   ```
2. Custom metrics:
   ```yaml
   management.metrics.tags.embedding.provider: local
   management.metrics.export.prometheus.enabled: true
   ```
3. Health check для EmbeddingProvider:
   ```java
   @Component
   public class EmbeddingProviderHealthIndicator implements HealthIndicator {
       public Health health() {
           // check model loaded, API available
       }
   }
   ```

---

### MAINT-004: Документация

**Требование:** Код и архитектура должны быть хорошо документированы.

**Метрики:**
- JavaDoc для public API
- README для embedding модуля
- Architecture Decision Records (ADRs) для ключевых решений

**Риски:**
- Потеря знания о архитектуре
- Сложность онбординга новых разработчиков

**Рекомендации:**
1. JavaDoc для классов и методов:
   ```java
   /**
    * Service for generating and managing embeddings for skills.
    *
    * <p>This service provides functionality for:
    * <ul>
    *   <li>Generating embeddings from markdown content</li>
    *   <li>Searching similar skills based on vector similarity</li>
    *   <li>Migrating existing data</li>
    * </ul>
    *
    * @author AI Agent
    * @since 1.0
    */
   ```
2. ADR для ключевых решений:
   - ADR-001: Выбор Sentence-BERT как primary model
   - ADR-002: Использование pgvector для vector similarity search
   - ADR-003: Асинхронная генерация embeddings

---

### MAINT-005: Конфигурируемость

**Требование:** Система должна быть легко конфигурируемой без пересборки.

**Метрики:**
- Все параметры через application.properties
- Runtime настройки через SystemSetting
- Валидация конфигурации при старте

**Риски:**
- Хардкод значений в коде
- Сложность изменения параметров в production

**Рекомендации:**
1. External configuration:
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
   ```
2. SystemSetting для runtime настроек:
   - embedding.similarity.threshold
   - embedding.max.results
   - embedding.provider

---

### MAINT-006: Обратная совместимость

**Требование:** Изменения должны быть обратно совместимы с существующими данными.

**Метрики:**
- Null safety: null embeddings корректно обрабатываются
- API versioning: новые endpoint'ы не ломают старые
- Migration path: путь миграции с одной размерности на другую

**Риски:**
- Слом существующих API при изменении responses
- Потеря данных при миграции на новую размерность

**Рекомендации:**
1. Null-safe операции:
   ```java
   if (skill.getEmbeddingVector() == null) {
       return SimilarSkillsResponse.empty();
   }
   ```
2. Новые endpoint'ы вместо изменения существующих
3. Migration script для изменения размерности (опционально):
   ```sql
   -- Миграция с 384 на 1536
   ALTER TABLE skills ALTER COLUMN embedding_vector TYPE vector(1536);
   -- Перегенерация embeddings...
   ```

---

## Summary Table

| Категория | Ключевые NFR | Приоритет | Сложность |
|-----------|--------------|-----------|-----------|
| **Scalability** | SCAL-001, SCAL-002, SCAL-003 | High | Medium |
| **Performance** | PERF-001, PERF-002, PERF-003, PERF-004 | High | Medium |
| **Availability** | AVAIL-001, AVAIL-002, AVAIL-003 | High | Low |
| **Security** | SEC-001, SEC-002, SEC-003, SEC-004, SEC-005 | High | Medium |
| **Reliability** | REL-001, REL-002, REL-003, REL-004 | High | Low |
| **Maintainability** | MAINT-001, MAINT-002, MAINT-003, MAINT-004, MAINT-005, MAINT-006 | Medium | Low |

---

## Appendix A: Measurable Metrics

### Performance Metrics

| Метрика | Target | Как измерять |
|---------|--------|--------------|
| API response time (p95) | < 500ms | Spring Boot Actuator metrics |
| Embedding generation time | < 500ms (local) | Custom timer |
| Migration throughput | 10-50 embeddings/sec | Логи миграции |
| Memory overhead | < 600 MB | JVM metrics |

### Reliability Metrics

| Метрика | Target | Как измерять |
|---------|--------|--------------|
| Embedding generation success rate | > 95% | Custom counter |
| API availability | > 99.9% | Uptime monitoring |
| Data consistency | 100% | Periodic validation job |

### Scalability Metrics

| Метрика | Target | Как измерять |
|---------|--------|--------------|
| Max records | 100,000 versions | DB size monitoring |
| Concurrent users | 100 | Connection pool metrics |
| Search throughput | 50 req/sec | Load testing |

---

**Document End**
