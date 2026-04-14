---
name: Implement integration with Transactional Outbox pattern
description: >
  Implement reliable event publishing for Java/Spring Boot using the
  Transactional Outbox pattern: domain change + outbox write in one
  transaction, scheduled relay publishes events to message broker.
---

# Implement integration with Transactional Outbox pattern

## Goal
Реализовать надёжную публикацию событий во внешние системы (Kafka, HTTP-вебхуки и т.д.)
без риска потери события при сбое после записи в БД или до публикации в брокер.

---

## Паттерн Transactional Outbox — суть

**Проблема**: запись в БД и публикация в брокер — две разные операции.
Если приложение упадёт между ними, событие потеряется.

**Решение**: писать событие в таблицу `outbox` **в той же транзакции**, что и доменное изменение.
Отдельный процесс (@Scheduled) читает непубликованные записи и отправляет их.

```
[Транзакция]
  INSERT INTO domain_table ...
  INSERT INTO outbox_event (event_type, payload, ...) ← атомарно с доменным изменением

[@Scheduled каждые N секунд]
  SELECT * FROM outbox_event WHERE processed = false ORDER BY created_at LIMIT 100
  FOR EACH event:
    publish to broker / call webhook
    UPDATE outbox_event SET processed = true, processed_at = NOW()
```

---

## Шаг 1 — Изучить контекст

Перед реализацией изучи:
1. Какой сервис/операция должна публиковать событие? Найди соответствующий `@Service`
2. Куда публикуется: Kafka topic? HTTP-вебхук? Другое?
3. Есть ли уже Kafka/HTTP конфигурация? Проверь `application.yml` и зависимости
4. Есть ли уже outbox-таблица или инфраструктура? Если да — переиспользуй
5. Какие данные должно содержать событие?

---

## Шаг 2 — Схема БД

Создать Liquibase changeset для таблицы `outbox_event`:

```yaml
# src/main/resources/db/changelog/YYYYMMDD-add-outbox-event-table.yaml
databaseChangeLog:
  - changeSet:
      id: YYYYMMDD-add-outbox-event-table
      author: dev
      changes:
        - createTable:
            tableName: outbox_event
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: event_type
                  type: varchar(128)
                  constraints:
                    nullable: false
              - column:
                  name: aggregate_type
                  type: varchar(128)
                  constraints:
                    nullable: false
              - column:
                  name: aggregate_id
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: payload
                  type: text
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: timestamp
                  constraints:
                    nullable: false
              - column:
                  name: processed
                  type: boolean
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
              - column:
                  name: processed_at
                  type: timestamp
              - column:
                  name: attempt_count
                  type: int
                  defaultValueNumeric: 0
                  constraints:
                    nullable: false
              - column:
                  name: last_error
                  type: text
        - createIndex:
            indexName: idx_outbox_event_processed_created
            tableName: outbox_event
            columns:
              - column:
                  name: processed
              - column:
                  name: created_at
```

---

## Шаг 3 — JPA-сущность и репозиторий

```java
// OutboxEventEntity.java
@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {
    @Id
    private UUID id;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    @Column(columnDefinition = "text")
    private String payload;       // JSON
    private Instant createdAt;
    private boolean processed;
    private Instant processedAt;
    private int attemptCount;
    private String lastError;
    // getters/setters или builder
}

// OutboxEventRepository.java
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEventEntity> findTop100ByProcessedFalseOrderByCreatedAtAsc();
}
```

---

## Шаг 4 — Запись в outbox в транзакции доменной операции

```java
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public Order createOrder(CreateOrderCommand cmd) {
        // 1. Сохранить доменный объект
        Order order = orderRepository.save(new Order(cmd));

        // 2. В ТОЙ ЖЕ транзакции записать в outbox
        outboxRepository.save(buildOutboxEvent(order));

        return order;
    }

    private OutboxEventEntity buildOutboxEvent(Order order) {
        String payload = objectMapper.writeValueAsString(new OrderCreatedEvent(
            order.getId(), order.getStatus(), order.getCreatedAt()
        ));
        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(UUID.randomUUID());
        event.setEventType("ORDER_CREATED");
        event.setAggregateType("Order");
        event.setAggregateId(order.getId().toString());
        event.setPayload(payload);
        event.setCreatedAt(Instant.now());
        event.setProcessed(false);
        return event;
    }
}
```

---

## Шаг 5 — Relay (публикатор событий)

```java
@Component
@Slf4j
public class OutboxEventRelay {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;  // или WebClient для HTTP
    private final Map<String, String> eventTypeToTopic = Map.of(
        "ORDER_CREATED", "orders.created",
        "ORDER_UPDATED", "orders.updated"
        // добавить по необходимости
    );

    @Scheduled(fixedDelay = 5000)     // каждые 5 секунд
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEventEntity> events =
            outboxRepository.findTop100ByProcessedFalseOrderByCreatedAtAsc();

        for (OutboxEventEntity event : events) {
            try {
                publish(event);
                event.setProcessed(true);
                event.setProcessedAt(Instant.now());
            } catch (Exception ex) {
                log.warn("Failed to publish outbox event {}: {}", event.getId(), ex.getMessage());
                event.setAttemptCount(event.getAttemptCount() + 1);
                event.setLastError(ex.getMessage());
                // не пробрасываем — продолжаем следующее событие
            }
            outboxRepository.save(event);
        }
    }

    private void publish(OutboxEventEntity event) {
        String topic = eventTypeToTopic.get(event.getEventType());
        if (topic == null) {
            throw new IllegalStateException("Unknown event type: " + event.getEventType());
        }
        kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).join();
    }
}
```

**Конфигурация в `application.yml`:**
```yaml
spring:
  scheduling:
    enabled: true
```

**`@EnableScheduling` на `@SpringBootApplication` или отдельном `@Configuration`.**

---

## Шаг 6 — Идемпотентность на потребителе

На стороне консьюмера (если управляем им):

```java
@KafkaListener(topics = "orders.created", groupId = "order-processor")
public void handleOrderCreated(String payload, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
    if (processedEventRepository.existsByEventKey(key)) {
        log.debug("Duplicate event, skipping: {}", key);
        return;
    }
    // обработка
    processedEventRepository.save(new ProcessedEvent(key, Instant.now()));
}
```

---

## Шаг 7 — Тесты

**Unit-тест сервиса**: убедиться что `outboxRepository.save()` вызван с правильными данными.

```java
@Test
void createOrder_savesOutboxEvent() {
    when(orderRepository.save(any())).thenReturn(order);
    service.createOrder(cmd);
    verify(outboxRepository).save(argThat(event ->
        event.getEventType().equals("ORDER_CREATED") &&
        event.getAggregateId().equals(order.getId().toString())
    ));
}
```

**Integration-тест relay**: в `@DataJpaTest` или `@SpringBootTest` создать непубликованное событие,
вызвать `processOutboxEvents()`, проверить что событие стало `processed = true`.

---

## Ограничения

- **Атомарность гарантируется только если outbox и доменная таблица — в одной БД**
- Не использовать `@Async` в relay — это нарушает транзакционность
- Не удалять обработанные события сразу — оставлять на N дней для отладки (добавить scheduled cleanup позже)
- Количество попыток `attempt_count >= 10` — алертить или переводить в dead letter queue
- PESSIMISTIC_WRITE в репозитории предотвращает параллельную обработку одного события несколькими подами
