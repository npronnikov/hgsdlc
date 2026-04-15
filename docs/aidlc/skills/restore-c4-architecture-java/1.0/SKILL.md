---
name: Restore C4 architecture diagrams for Java project
description: >
  Analyze a Java/Spring Boot project and produce C4 model architecture
  documentation with Mermaid diagrams: System Context, Container, and
  Component levels.
---

# Restore C4 architecture diagrams for Java project

## Goal
По кодовой базе восстановить архитектурную документацию в нотации C4,
представив её в виде Mermaid-диаграмм и текстовых описаний.

---

## C4 Model — уровни

| Уровень | Что описывает | Аудитория |
|---------|--------------|-----------|
| **C1 — System Context** | Система в окружении: пользователи, внешние системы | Все, в т.ч. нетехнические |
| **C2 — Container** | Развёртываемые единицы: сервисы, БД, брокеры | Разработчики, DevOps |
| **C3 — Component** | Spring-компоненты внутри контейнера | Разработчики |

---

## Шаг 1 — Изучение проекта

**Технический стек:**
- Прочитай `build.gradle` / `pom.xml` — все зависимости
- Прочитай `application.yml` — datasource, kafka/rabbit, внешние URL, порты
- Найди Docker/docker-compose файлы — инфраструктурные компоненты
- Найди `@SpringBootApplication` — точки входа

**Контейнеры (C2):**
- Модули Gradle / Maven — отдельные развёртываемые JAR/сервисы?
- БД: тип (PostgreSQL, MySQL, H2), имя схемы
- Брокеры сообщений: Kafka topics, RabbitMQ exchanges
- Внешние HTTP-сервисы: URL из конфига, RestTemplate/WebClient/Feign клиенты
- Хранилища файлов: S3, локальная ФС
- Кэш: Redis, Caffeine

**Компоненты Spring (C3):**
- Все `@RestController` — список с их URL prefix
- Все `@Service` — список с их зонами ответственности
- Все `@Repository` / JPA-репозитории — список с агрегатами
- `@Scheduled` бины — фоновые задачи
- `@KafkaListener` / `@RabbitListener` — консьюмеры сообщений
- `@Configuration` — ключевые конфигурационные бины

**Пользователи системы:**
- Кто вызывает API: пользователи, другие сервисы, CI/CD
- Роли и типы доступа

---

## Шаг 2 — Построение диаграмм

### C1 — System Context

```mermaid
C4Context
  title System Context — {название системы}

  Person(user, "Пользователь", "Описание")
  Person_Ext(admin, "Администратор", "Описание")

  System(system, "{Название системы}", "Краткое описание что делает")

  System_Ext(extSystem1, "Внешняя система 1", "Описание")
  System_Ext(extSystem2, "Внешняя система 2", "Описание")

  Rel(user, system, "Использует", "HTTPS/REST")
  Rel(system, extSystem1, "Вызывает", "HTTPS/REST")
  Rel(extSystem2, system, "Отправляет события", "Kafka")
```

### C2 — Container

```mermaid
C4Container
  title Container Diagram — {название системы}

  Person(user, "Пользователь")

  System_Boundary(sys, "{Название системы}") {
    Container(app, "Backend API", "Spring Boot 3.x / Java 21", "Обрабатывает запросы")
    Container(frontend, "Frontend", "React / Nginx", "SPA-интерфейс")
    ContainerDb(db, "PostgreSQL", "PostgreSQL 15", "Основное хранилище данных")
    Container(broker, "Kafka", "Apache Kafka", "Асинхронные события")
  }

  System_Ext(extApi, "Внешний API", "Описание")

  Rel(user, frontend, "Использует", "HTTPS")
  Rel(frontend, app, "API вызовы", "HTTPS/REST")
  Rel(app, db, "Читает/пишет", "JDBC")
  Rel(app, broker, "Публикует/читает", "Kafka protocol")
  Rel(app, extApi, "Вызывает", "HTTPS/REST")
```

### C3 — Component (для Backend API)

```mermaid
C4Component
  title Component Diagram — Backend API

  Container_Ext(frontend, "Frontend")
  ContainerDb_Ext(db, "PostgreSQL")

  Container_Boundary(api, "Backend API") {
    Component(ctrl1, "{Domain}Controller", "@RestController", "REST API для {домена}")
    Component(ctrl2, "{Domain2}Controller", "@RestController", "REST API для {домена2}")

    Component(svc1, "{Domain}Service", "@Service", "Бизнес-логика {домена}")
    Component(svc2, "{Domain2}Service", "@Service", "Бизнес-логика {домена2}")

    Component(repo1, "{Domain}Repository", "@Repository", "Данные {домена}")
    Component(repo2, "{Domain2}Repository", "@Repository", "Данные {домена2}")

    Component(scheduler, "{Name}Scheduler", "@Scheduled", "Фоновая задача: {описание}")
  }

  Rel(frontend, ctrl1, "REST", "HTTPS/JSON")
  Rel(ctrl1, svc1, "Вызывает")
  Rel(svc1, repo1, "Читает/пишет")
  Rel(repo1, db, "SQL", "JDBC")
  Rel(svc1, svc2, "Зависит от")
```

---

## Формат вывода

Записать в файл `architecture.md`:

```markdown
# Архитектура системы {название}

## Обзор
{2–3 абзаца: назначение системы, основные технологии, масштаб}

## Технологический стек

| Компонент | Технология | Версия |
|-----------|-----------|--------|
| Backend   | Java / Spring Boot | ... |
| Database  | PostgreSQL | ... |
| ...       | ... | ... |

---

## C1 — System Context

{Mermaid C4Context диаграмма}

### Описание взаимодействий
- **{Актор/система}** → **{система}**: {описание}
- ...

---

## C2 — Container Diagram

{Mermaid C4Container диаграмма}

### Контейнеры

| Контейнер | Технология | Ответственность |
|-----------|-----------|-----------------|
| Backend API | Spring Boot | ... |
| ... | ... | ... |

---

## C3 — Component Diagram (Backend API)

{Mermaid C4Component диаграмма}

### Компоненты

| Компонент | Тип | Ответственность |
|-----------|-----|-----------------|
| {Name}Controller | @RestController | ... |
| {Name}Service | @Service | ... |
| ... | ... | ... |

---

## Ключевые потоки данных

### Поток 1: {название сценария}
1. {актор} → {компонент}: {действие}
2. {компонент} → {компонент}: {действие}
3. ...

---

## Известные ограничения и технический долг
- {ограничение/проблема}: {описание}
```

---

## Ограничения

- Использовать только то, что реально найдено в проекте — не выдумывать компоненты
- Если компонент непонятен — описать как "Unknown / TBD" и указать где он встречается
- Не писать код
- Mermaid-синтаксис должен быть валидным (можно проверить на mermaid.live)
