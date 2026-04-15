# Technology Stack

## Programming Languages

### Backend
- **Java 21** — Основной язык backend (LTS версия с virtual threads, pattern matching)
- **SQL** — Язык запросов к БД (Liquibase миграции)

### Frontend
- **JavaScript (ES2022+)** — Основной язык frontend (React JSX)
- **YAML** — Конфигурация flows
- **Markdown** — Документация для rules/skills

## Frameworks

### Backend Frameworks

| Фреймворк | Версия | Назначение |
|-----------|--------|------------|
| **Spring Boot** | 3.3.0 | Основной фреймворк приложения |
| **Spring Security** | 6.x | Аутентификация и авторизация |
| **Spring Data JPA** | 3.x | ORM и репозитории |
| **Liquibase** | (через Spring) | Миграции БД |
| **Spring Shell** | 3.2.0 | CLI (опционально) |
| **Hibernate** | (через Spring Data JPA) | JPA провайдер |

### Frontend Frameworks

| Фреймворк | Версия | Назначение |
|-----------|--------|------------|
| **React** | 18.2.0 | UI библиотека |
| **React Router DOM** | 6.22.0 | Routing в SPA |
| **Ant Design** | 5.15.0 | UI компоненты |
| **ReactFlow** | 11.11.3 | Визуальный редактор графов |
| **Vite** | 5.2.0 | Build tool и dev server |

## Infrastructure

### Database
- **PostgreSQL** — Production реляционная БД
- **H2** — In-memory БД для разработки

### Web Server
- **Spring Boot Embedded Tomcat** — Встроенный веб-сервер (по умолчанию в Spring Boot)

### Version Control
- **Git** — Версионирование кода, checkpoint-и, публикация

### Container
- **Docker** — Запуск PostgreSQL (compose.dev.yaml)

### CI/CD
- N/A (не реализовано в кодовой базе)

## Build Tools

### Backend
- **Gradle 8.x** — Система сборки (Kotlin DSL)
- **Gradle Wrapper** — Обёртка для согласования версий

### Frontend
- **npm** — Менеджер пакетов
- **Vite** — Build tool

## Testing Tools

### Backend
| Инструмент | Версия | Назначение |
|------------|--------|------------|
| **JUnit 5** | (через Spring Boot Test) | Юнит тесты |
| **Spring Boot Test** | (через spring-boot-starter-test) | Интеграционные тесты |
| **Testcontainers** | 1.19.8 | Docker контейнеры для тестов |
| **ArchUnit** | 1.3.0 | Архитектурные тесты |

### Frontend
| Инструмент | Версия | Назначение |
|------------|--------|------------|
| **Vite Test** | (через npm run test) | Тесты (но не реализованы) |
| N/A | N/A | Тесты не реализованы |

## Libraries

### Backend Libraries

| Библиотека | Версия | Назначение |
|------------|--------|------------|
| **Jackson Databind** | (через Spring Boot) | JSON сериализация |
| **Jackson Datatype JSR310** | (через Spring Boot) | Java 8 Date/Time API |
| **Jackson YAML** | 2.17.1 | YAML сериализация |
| **JSON Schema Validator** | 1.0.87 | Валидация JSON Schema |
| **Lombok** | 1.18.42 | Генерация кода |
| **BouncyCastle** | 1.78.1 | Криптография (Ed25519) |
| **PostgreSQL Driver** | (через runtimeOnly) | JDBC драйвер PostgreSQL |
| **H2 Database** | (через runtimeOnly) | In-memory БД |

### Frontend Libraries

| Библиотека | Версия | Назначение |
|------------|--------|------------|
| **@ant-design/icons** | 5.3.0 | Иконки для Ant Design |
| **@monaco-editor/react** | 4.6.0 | Monaco Editor компонент |
| **dagre** | 0.8.5 | Алгоритм компоновки графов |
| **mermaid** | 11.13.0 | Рендер диаграмм |
| **react-markdown** | 9.0.1 | Рендер Markdown |
| **remark-gfm** | 4.0.0 | GitHub Flavored Markdown |
| **yaml** | 2.4.5 | Парсинг YAML |
| **uuid** | 8.3.2 | Генерация UUID |

## Development Tools

### Backend
- **Java 21 SDK** — Компиляция и запуск
- **Gradle** — Сборка
- **Lombok Annotation Processor** — Генерация кода

### Frontend
- **Node.js 18+** — Runtime JavaScript
- **npm** — Менеджер пакетов
- **Vite HMR** — Hot Module Replacement

## Monitoring & Observability

| Инструмент | Назначение |
|------------|------------|
| **Spring Boot Actuator** | Health checks и метрики |
| **Application Logging** | Логирование через SLF4J |
| N/A | Нет распределённого трейсинга |
| N/A | нет APM |

## Security

| Инструмент | Назначение |
|------------|------------|
| **Spring Security** | Аутентификация и авторизация |
| **BCrypt** | Хеширование паролей (через Spring Security) |
| **Session-based Auth** | Сессионная аутентификация |
| N/A | Нет OAuth/OIDC |
| N/A | Нет JWT |

## Documentation

| Инструмент | Назначение |
|------------|------------|
| **Markdown** | Документация в README.md |
| **JavaDoc** | Документация Java кода (частично) |
| N/A | Нет Swagger/OpenAPI |
| N/A | Нет автоматической генерации API docs |
