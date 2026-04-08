---
id: java-backend-coding-standards
version: "1.0"
canonical_name: java-backend-coding-standards@1.0
title: Java Backend Coding Standards
description: >
  Core coding rules for Java/Spring Boot backend development:
  project structure, layering, naming, error handling, transactions,
  testing, and API design.
allowed_paths:
  - backend/src/main/java
  - backend/src/main/resources
  - backend/src/test
  - src/main/java
  - src/main/resources
  - src/test
forbidden_paths:
  - frontend/src
  - .github
allowed_commands:
  - ./gradlew test
  - ./gradlew build
  - mvn test
  - mvn package
require_structured_response: true
---

Это правило применяется ко всем задачам разработки на Java/Spring Boot бэкенде.
Оно определяет структуру пакетов, стиль кода, паттерны обработки ошибок и требования к тестированию.

---

## 1. Структура пакетов (Hexagonal / Layered Architecture)

Каждый модуль организован строго по слоям:

```
ru.{company}.{service}.{domain}/
  api/           ← REST-контроллеры, DTO запросов/ответов
  application/   ← Сервисы (use-cases), команды, порты
  domain/        ← Доменные сущности, value objects, доменные события
  infrastructure/
    persistence/ ← JPA-репозитории, Spring Data интерфейсы, маппинг
    messaging/   ← Kafka/RabbitMQ продьюсеры/консьюмеры
    external/    ← Клиенты внешних API
```

**Правила зависимостей:**
- `api` → `application` (только через команды/query objects)
- `application` → `domain`, `application.ports`
- `infrastructure` → `application.ports` (реализует интерфейсы)
- `domain` → ничего (чистая бизнес-логика без зависимостей на Spring)

---

## 2. Именование

| Элемент | Конвенция | Пример |
|---------|-----------|--------|
| Контроллеры | `{Domain}Controller` | `OrderController` |
| Сервисы (application) | `{Domain}Service` | `OrderService` |
| Репозитории | `{Domain}Repository` | `OrderRepository` |
| JPA-сущности | `{Domain}Entity` | `OrderEntity` |
| DTO запроса | `{Action}Request` | `CreateOrderRequest` |
| DTO ответа | `{Domain}Response` | `OrderResponse` |
| Команды | `{Action}Command` | `CreateOrderCommand` |
| Исключения | `{Reason}Exception` | `OrderNotFoundException` |
| Порты | `{Domain}Port` | `OrderNotificationPort` |

**Нейминг методов:**
- Получение: `get*`, `find*` (возвращает Optional если объект может отсутствовать), `list*`
- Создание: `create*`, `save*`
- Изменение: `update*`, `set*`, `change*`
- Удаление: `delete*`, `remove*`
- Проверки: `is*`, `has*`, `can*`

---

## 3. REST API

- URL в `kebab-case`, существительные во множественном числе: `/api/orders/{orderId}/items`
- HTTP-методы семантически корректны: `GET` (без side effects), `POST` (создание/действие), `PUT` (полная замена), `PATCH` (частичное обновление), `DELETE`
- Ответы всегда через `ResponseEntity<T>`: `200 OK`, `201 Created`, `204 No Content`, `400 Bad Request`, `404 Not Found`, `409 Conflict`
- Поля JSON в `snake_case`: `order_id`, `created_at`
- Использовать `@JsonProperty` для явного маппинга полей в record/DTO

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody @Valid CreateOrderRequest request,
            @AuthenticationPrincipal User user) {
        Order order = orderService.createOrder(toCommand(request), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }
}
```

---

## 4. Обработка ошибок

**Иерархия исключений:**
```java
// Базовый класс — в общем пакете
public class AppException extends RuntimeException { ... }

// Конкретные исключения
public class NotFoundException extends AppException { ... }      // 404
public class ValidationException extends AppException { ... }    // 400
public class ConflictException extends AppException { ... }      // 409
public class ForbiddenException extends AppException { ... }     // 403
```

**Обработчик:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
    }
    // ... остальные
}
```

**Правила:**
- Никогда не кидать `RuntimeException` напрямую — только типизированные исключения
- Не логировать исключение и бросать его повторно (double logging)
- Сообщения об ошибках — на английском, без деталей реализации

---

## 5. Транзакции

- `@Transactional` только на методах **сервисного** слоя, никогда на контроллерах и репозиториях
- `readOnly = true` для всех читающих методов: `@Transactional(readOnly = true)`
- Для независимых операций, которые не должны откатываться вместе: `Propagation.REQUIRES_NEW`
- Не вызывать `@Transactional` методы внутри того же Spring-бина (self-invocation, bypass proxy)

```java
@Service
@Transactional(readOnly = true)  // default для класса
public class OrderService {

    @Transactional  // override для мутирующих методов
    public Order createOrder(CreateOrderCommand cmd, User actor) { ... }

    public Optional<Order> findById(UUID id) { ... }  // наследует readOnly
}
```

---

## 6. Валидация

- Входные данные валидировать через Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, `@Size`)
- Бизнес-валидацию (не покрытую аннотациями) выполнять в сервисном слое, бросая `ValidationException`
- Не дублировать валидацию — она происходит один раз при входе в сервис
- Record-based DTO поддерживают `@Valid` через `@Valid` на параметре контроллера

---

## 7. Логирование

- Использовать **SLF4J + Logback**, без прямых вызовов `System.out`
- Уровни: `DEBUG` для диагностики, `INFO` для бизнес-событий, `WARN` для ожидаемых проблем, `ERROR` для непредвиденных
- Логировать `INFO` при старте и завершении бизнес-операций:
  ```java
  log.info("Creating order: userId={}, itemCount={}", userId, items.size());
  ```
- Не логировать пароли, токены, PII-данные
- Использовать structured logging (MDC) для traceId/requestId в микросервисах

---

## 8. Тестирование

**Обязательные тесты для каждого нового кода:**

| Слой | Тип теста | Инструмент |
|------|-----------|------------|
| Controller | `@WebMvcTest` slice test | MockMvc |
| Service | Unit-тест | JUnit 5 + Mockito |
| Repository | `@DataJpaTest` | H2 / Testcontainers |
| Integration | `@SpringBootTest` | Testcontainers (Postgres) |

**Конвенция именования тестов:**
```java
// формат: {метод}_{условие}_{ожидаемыйРезультат}
@Test
void createOrder_whenUserNotFound_throwsNotFoundException() { ... }

@Test
void createOrder_withValidRequest_returnsCreatedOrder() { ... }
```

**Правила:**
- Каждый новый публичный метод сервиса — минимум 2 теста (success + failure)
- Моки только на внешние зависимости (репозитории, HTTP-клиенты), не на бизнес-логику
- Использовать `@ParameterizedTest` для тестирования граничных случаев
- Test coverage нового кода >= 80%

---

## 9. Java-специфика

- Использовать **Java records** для DTO, команд, value objects (иммутабельность из коробки)
- Предпочитать `Optional<T>` вместо `null` в возвращаемых значениях
- Использовать `var` для локальных переменных когда тип очевиден из правой части
- Stream API для коллекций — без промежуточных `for`-циклов на трансформациях
- `instanceof` pattern matching (Java 16+): `if (obj instanceof String s) { ... }`
- Sealed classes/interfaces для доменных алтернатив (Java 17+)

---

## 10. Liquibase и схема БД

- Все изменения схемы — через Liquibase changeset, никаких `ddl-auto: create/update`
- Имена changesets: `{timestamp}-{краткое-описание}.yaml` или `.xml`
- Таблицы в `snake_case`: `order_items`, `payment_transactions`
- Колонки в `snake_case`: `order_id`, `created_at`
- Всегда добавлять индексы на foreign keys и часто используемые фильтры
- Нельзя удалять/переименовывать колонки в одном changeset с добавлением новых — разбивать на этапы

---

## Проверка результата

После реализации задачи:
- `./gradlew test` проходит без ошибок
- Нет новых предупреждений компилятора
- Все новые публичные методы сервисов покрыты тестами
- Структура пакетов соответствует схеме выше
- JSON-поля в `snake_case`
- Исключения типизированы
