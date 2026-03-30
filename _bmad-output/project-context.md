---
project_name: 'hgsdlc'
user_name: 'HGS Team'
date: '2026-03-30'
sections_completed: ['technology_stack', 'language_rules', 'framework_rules', 'testing_rules', 'code_quality', 'workflow', 'anti_patterns', 'runtime_patterns', 'catalog_versioning', 'publication_pipeline']
status: 'complete'
rule_count: 68
optimized_for_llm: true
---

# Контекст проекта для AI-агентов

_Этот файл содержит критические правила и паттерны, которым AI-агенты должны следовать при реализации кода. Акцент на неочевидных деталях, которые агенты могут пропустить._

---

## Технологический стек и версии

### Backend
- Java 21 (sourceCompatibility + targetCompatibility = VERSION_21)
- Spring Boot 3.3.0
- Spring Security, Spring Data JPA, Spring Validation, Spring Actuator
- Spring Shell 3.2.0 (CLI-интерфейс)
- Hibernate (ddl-auto: validate — схема управляется Liquibase)
- PostgreSQL (prod) / H2 in-memory MODE=PostgreSQL (dev/test)
- Liquibase (master changelog: db.changelog-master.yaml)
- Jackson Databind + datatype-jsr310 + dataformat-yaml 2.17.1
- json-schema-validator 1.0.87 (networknt)
- Lombok 1.18.42
- Bouncy Castle bcprov-jdk18on 1.78.1
- Testcontainers BOM 1.19.8 (PostgreSQL)
- Gradle с Kotlin DSL
- Группа артефактов: ru.hgd

### Frontend
- React 18.2.0 (без TypeScript — чистый JSX)
- Vite 5.2.0 (dev-сервер + build)
- Ant Design 5.15.0 + @ant-design/icons 5.3.0
- React Router DOM 6.22.0
- ReactFlow 11.11.3 + dagre 0.8.5
- Monaco Editor (@monaco-editor/react) 4.6.0
- React Markdown 9.0.1 + remark-gfm 4.0.0
- yaml 2.4.5

### Инфраструктура
- Docker Compose (infra/docker/compose.yml)
- Переменные окружения: DB_URL, DB_USERNAME, DB_PASSWORD
- Порт API: 8080, Vite proxies /api → localhost:8080

## Критические правила реализации

### Языковые правила

#### Java (Backend)
- **Только конструкторное внедрение зависимостей** — никаких `@Autowired` на полях или сеттерах.
  Все зависимости передаются через конструктор вручную (не `@RequiredArgsConstructor`).
- **Lombok на всех сущностях и моделях**: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`.
  На JPA-сущностях обязательно указывать `@Table(name = "...")` явно.
- **Jackson snake_case**: поля с составными именами аннотируются `@JsonProperty("snake_case_name")`.
  Пример: `@JsonProperty("canonical_name")`, `@JsonProperty("start_node_id")`.
- **`@JsonIgnoreProperties(ignoreUnknown = true)`** — обязательно на всех YAML/JSON-моделях
  (не на JPA-сущностях).
- **Временны́е типы**: использовать `java.time.Instant` для временны́х меток (не Date/LocalDateTime).
- **UUID как PK**: первичные ключи — `UUID`, генерируются в сервисном слое (`UUID.randomUUID()`),
  не через `@GeneratedValue`.
- **`@Enumerated(EnumType.STRING)`** — обязательно для всех enum-колонок в JPA-сущностях.
- **Text blocks** (`"""..."""`) для нативных SQL-запросов в `@Query`.

#### JavaScript (Frontend)
- **Чистый JSX без TypeScript** — расширение файлов `.jsx`, не `.tsx`/`.ts`.
  Никаких type annotations, интерфейсов, enum'ов.
- **ES-модули** (`"type": "module"` в package.json) — использовать `import/export`,
  не `require/module.exports`.
- **Все API-вызовы — только через `apiRequest(path, options)`** из `src/api/request.js`.
  Нельзя использовать `fetch` напрямую в компонентах.
- **Аутентификация**: токен хранится в `localStorage` под ключом `authToken`.
  Заголовок: `Authorization: Bearer <token>`.

### Правила Spring Boot (Backend)

#### Архитектура модулей
- Обязательная слоистая структура внутри каждого модуля:
  `api/` → `application/` → `domain/` → `infrastructure/`
- Новый функционал = новый модуль с той же структурой, или расширение существующего.
  Бизнес-логика только в `application/`, никогда в контроллере.
- `domain/` содержит ДВА типа объектов: JPA-сущности (`@Entity`) и YAML/JSON-модели
  (обычные POJO с `@JsonIgnoreProperties`). Не смешивать.

#### Транзакции
- `@Transactional(readOnly = true)` — на всех read-методах в сервисах.
- `@Transactional` (без readOnly) — только на write-методах в `application/`.
- Транзакции ЗАПРЕЩЕНЫ в контроллерах и репозиториях.
- Lazy-loaded ассоциации должны быть загружены внутри транзакции в сервисном слое
  (`open-in-view: false` выставлен глобально).

#### Контроллеры
- `@ExceptionHandler` для кастомных исключений — в каждом контроллере отдельно,
  не в `@ControllerAdvice`.
- Использовать: `NotFoundException`, `ValidationException`, `ConflictException`,
  `ForbiddenException` из `ru.hgd.sdlc.common`.
- Все POST-операции изменения состояния: принимать `@RequestHeader("Idempotency-Key")`
  и оборачивать в `idempotencyService.execute(...)`.
- Тип результата для `execute()` должен быть сериализуемым через Jackson.
- URL-маппинг: `/api/{resource}` — только множественное число, kebab-case.

#### Репозитории и пагинация
- Простые запросы — Spring Data именованные методы.
- Сложные запросы — `@Query(nativeQuery = true)` с text blocks.
- Cursor-based пагинация через `InstantUuidCursor` (пара `saved_at`, `id`). Не offset/page.

#### База данных и миграции
- Схема управляется ТОЛЬКО через Liquibase.
- Изменять существующие changelog SQL-файлы ЗАПРЕЩЕНО — только новые файлы
  со следующим порядковым номером.
- **Следующая миграция: `033-...`** — последний существующий файл `032-rules-flows-publication-pipeline.sql`.
- Нативный SQL должен работать на обоих диалектах (H2 MODE=PostgreSQL и реальный PG):
  использовать `LOWER() + LIKE` вместо `ILIKE`, не использовать `::cast`, избегать
  PostgreSQL-специфичных расширений.

#### Паттерн catalog-versioning (шаблон для новых каталожных сущностей)

Все каталожные сущности (`flow`, `skill`, `rule`) следуют единому шаблону полей:
- `lifecycle_status` VARCHAR(32) — `DRAFT | PUBLISHED` (`LifecycleStatus`)
- `approval_status` VARCHAR(32) — `DRAFT | PENDING_APPROVAL | APPROVED | REJECTED` (`ApprovalStatus`)
- `content_source` VARCHAR(32) — `DB | GIT` (`ContentSource`)
- `visibility` VARCHAR(32) — `PRIVATE | ORG` (`*Visibility`)
- `environment` VARCHAR(32) — `SANDBOX | PRODUCTION` (`*Environment`)
- Иммутабельность: опубликованная запись не изменяется — создаётся новая версия (DRAFT).
- **НЕ** использовать `@Version` (optimistic locking) — только статусы + новая запись.
- При создании новой каталожной сущности (например, workspace-шаблоны) — следовать этому шаблону.

#### Publication pipeline

- Поля `publication_status`, `publication_target`, `published_commit_sha`, `published_pr_url`,
  `last_publish_error` добавляются на таблицу сущности (см. migrations 031-032).
- `PublicationTarget`: `DB_ONLY | GIT_ONLY | GIT_AND_DB`.
- Git-публикация работает через PR: `PublicationPrPollService` создаёт PR и поллит статус слияния.
- **НЕ** реализовывать Git-публикацию в сервисном слое — только через модуль `publication`.

#### Runtime — конечный автомат выполнения

Статусы, которые агенты обязаны использовать точно (не придумывать новые):
- **RunStatus:** `CREATED → RUNNING → WAITING_GATE → RUNNING → COMPLETED | FAILED | CANCELLED`
- **NodeExecutionStatus:** `PENDING → RUNNING → COMPLETED | FAILED | SKIPPED`
- **GateStatus:** `AWAITING_INPUT → AWAITING_DECISION → APPROVED | REJECTED`
- **GateKind:** `HUMAN_INPUT | HUMAN_APPROVAL`
- Активные запуски: `CREATED, RUNNING, WAITING_GATE` — проверять через константу `ACTIVE_RUN_STATUSES`.
- Открытые гейты: `AWAITING_INPUT, AWAITING_DECISION` — через `OPEN_GATE_STATUSES`.
- Audit events — **append-only**: поля `sequence_no` (монотонно в рамках `run_id`),
  `actor_type` (`HUMAN | SYSTEM`), `payload_json`. Никогда не обновлять, не удалять.

#### Runtime — двухслойный транзакционный паттерн

LLM-вызовы занимают минуты — держать БД-соединение всё это время недопустимо.
Два класса с разными propagation:

**`RuntimeService`** — оркестратор (НЕ держит транзакцию):
- Все публичные методы: `@Transactional(propagation = Propagation.NOT_SUPPORTED)`
- Явно суспендит любую внешнюю транзакцию. Выполняет LLM-вызовы вне транзакции.

**`RuntimeStepTxService`** — атомарные записи состояния:
- Все методы: `@Transactional(propagation = Propagation.REQUIRES_NEW)`
- Только короткие write-операции: создание run, обновление статуса, append audit.

- **НЕ** добавлять `@Transactional` (без propagation) в `RuntimeService`.
- **НЕ** вызывать LLM внутри активной транзакции.
- Write-операции в рамках runtime → делегировать в `RuntimeStepTxService` с `REQUIRES_NEW`.

#### Scheduled jobs

- Паттерн: `@Scheduled(fixedDelayString = "${prop:default}", initialDelayString = "${prop:default}")`
- Интервалы выносятся в `application.yml` с дефолтами прямо в аннотации (мс).
- `@EnableScheduling` включён в `HgdSdlcApplication` — не добавлять повторно.
- Scheduled-методы живут в `application/` слое, не в контроллерах.
- Пример: `PublicationPrPollService.pollPrMergeStatus()` — поллинг PR merge status.

#### Spring Shell
- Shell-команды помечаются `@ShellComponent` и живут отдельно от REST-контроллеров.
- Shell-команды вызывают те же сервисы из `application/` — логика не дублируется.

### Правила React (Frontend)

#### Структура и роутинг
- Страницы — в `src/pages/` (`PascalCase.jsx`). Компоненты — в `src/components/`.
- Роутинг через `HashRouter`. Новые маршруты добавляются только в `App.jsx`.
- Защищённые маршруты оборачиваются в `<RequireAuth>`.

#### UI и стейт
- UI-компоненты только из Ant Design — никаких сторонних UI-библиотек.
- Стейт — локальный `useState`/`useEffect`. Нет Redux/Zustand/MobX.
- Данные загружаются в `useEffect` через `apiRequest()`. Ошибки — через `antd message`.
- Аутентификация — через `useAuth()` из `src/auth/AuthContext.jsx`.

#### Актуальные маршруты (App.jsx)

Все маршруты — относительно корня `#/`. Добавлять новые только в `App.jsx`:

| Маршрут | Компонент | Назначение |
|---------|-----------|------------|
| `overview` | Overview | Дашборд |
| `projects` | Projects | Список проектов |
| `flows`, `flows/create`, `flows/:flowId` | Flows / FlowEditor | Каталог воркфлоу |
| `rules`, `rules/create`, `rules/:ruleId` | Rules / RuleEditor | Правила |
| `skills`, `skills/create`, `skills/:skillId` | Skills / SkillEditor | Навыки |
| `requests` | Requests | Очередь публикации |
| `run-launch`, `run-console` | RunLaunch / RunConsole | Запуск и консоль |
| `gates-inbox`, `gate-input`, `gate-approval` | Gates* | Гейты подтверждения |
| `audit-runtime`, `audit-agent`, `audit-review` | Audit* | Audit log |
| `artifacts`, `delta-summary`, `versions` | Artifacts / DeltaSummary / Versions | Артефакты |
| `settings`, `prompt-package` | Settings / PromptPackage | Настройки, промпты |

- `/publication-queue` → redirect на `/requests` (переименован, не восстанавливать).
- Новые workspace-маршруты (Epic 1): добавлять с префиксом `ws/:workspaceId/` согласно Architecture.

#### Тестирование фронтенда
- Тестового фреймворка нет и не добавлять (Jest, Vitest не установлены).
- Проверка корректности — только через `npm run build` (сборка без ошибок).

### Правила тестирования

#### Интеграционные тесты (Backend)
- Интеграционные тесты запускаются против **реального PostgreSQL через Testcontainers**.
  Нельзя использовать `@DataJpaTest` с H2 для тестирования нативных SQL-запросов —
  результаты могут отличаться от production.
- Для тестов, использующих БД: `@SpringBootTest` + конфигурация Testcontainers PostgreSQL.
- JUnit 5 (`@Test` из `org.junit.jupiter.api`) — не JUnit 4.

#### Структура тестов
- Тестовые классы в `src/test/java/` с зеркальной структурой пакетов.
- Нейминг: `{ClassName}Test.java`.
- Lombok доступен в тестах (`testCompileOnly` + `testAnnotationProcessor`).

#### Что тестировать
- Сервисный слой (`application/`) — бизнес-логика и валидации.
- Репозитории с нативными запросами — против Testcontainers PostgreSQL.
- Idempotency-логика требует отдельного тест-кейса на повторный запрос.

#### Что НЕ делать
- Не мокировать БД для тестов нативных SQL-запросов.
- Не добавлять тестовые фреймворки во фронтенд.
- Не использовать H2 как замену PostgreSQL в интеграционных тестах.

### Качество кода и стиль

#### Именование (Backend)
- Пакеты: `ru.hgd.sdlc.{module}.{layer}` — строчные, без цифр.
- Классы: `PascalCase`. Контроллеры: `{Resource}Controller`. Сервисы: `{Resource}Service`.
  Репозитории: `{Entity}Repository`. Сущности: без суффикса (`FlowVersion`, не `FlowVersionEntity`).
- Константы: `UPPER_SNAKE_CASE`. Переменные и методы: `camelCase`.
- ID в коде: `flowId`, `ruleId` (camelCase), в JSON: `flow_id`, `rule_id` (snake_case через `@JsonProperty`).

#### Именование (Frontend)
- Файлы компонентов: `PascalCase.jsx`.
- Вспомогательные файлы: `camelCase.js` (например, `request.js`).
- Переменные и функции: `camelCase`. Константы: `UPPER_SNAKE_CASE`.

#### Стиль Java-кода
- Нет `var` — явные типы везде.
- Импорты: без wildcard (`import java.util.*` запрещено).
- Комментарии только для нетривиальной логики.

#### Конфигурация
- Настройки только через `application.yml`, не `application.properties`.
- Кастомные properties — в `@ConfigurationProperties`-классе (пример: `AuthProperties`).
- Секреты через переменные окружения (`${ENV_VAR:default}`).

### Рабочий процесс разработки

#### Git и ветки
- Основная ветка: `main`. Фича-ветки: `{короткое-описание}` (kebab-case).
- Коммиты на английском, краткое описание в повелительном наклонении
  (пример: `Add cursor pagination to flows endpoint`).

#### Запуск локально
- Backend: `./gradlew bootRun` из `backend/` — профиль `local`, H2 in-memory, порт 8080.
- Frontend: `npm install && npm run dev` из `frontend/` — `/api` проксируется на `localhost:8080`.
- Production-like: Docker Compose из `infra/docker/compose.yml`.

#### Переменные окружения
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — для PostgreSQL.
- По умолчанию: H2 in-memory, пользователь `sa`, пустой пароль.

#### Сборка
- Backend: `./gradlew build`. Frontend: `npm run build`.
- Backend и frontend собираются независимо — нет единого корневого скрипта.

#### Coding agent
- Основной coding agent проекта — **Qwen Code**.
  Шаблоны правил и навыков ориентированы на его интеграцию.

### Критические антипаттерны — что агентам НЕЛЬЗЯ делать

#### Backend
- **НЕ** использовать `@GeneratedValue` для UUID — генерировать через `UUID.randomUUID()` в сервисе.
- **НЕ** добавлять `@ControllerAdvice` — обработчики исключений только в конкретном контроллере.
- **НЕ** менять `ddl-auto` с `validate` — изменения схемы только через Liquibase.
- **НЕ** редактировать существующие Liquibase SQL-файлы — только создавать новые.
- **НЕ** обращаться к lazy-ассоциациям за пределами транзакции.
- **НЕ** использовать `@Autowired` на полях — только конструкторное внедрение.
- **НЕ** использовать PostgreSQL-специфичный синтаксис в нативных запросах
  (`ILIKE`, `::cast`) — использовать `LOWER() + LIKE`.
- **НЕ** использовать `@Version` (optimistic locking) — версионирование через
  иммутабельные записи со статусами DRAFT/PUBLISHED.
- **НЕ** удалять старые версии сущностей — модель append-only.
- **НЕ** добавлять `@Transactional` (без propagation) в `RuntimeService` — только `NOT_SUPPORTED`.
- **НЕ** вызывать LLM внутри активной транзакции — держит соединение на минуты.
- **НЕ** писать напрямую в `audit_events` из бизнес-логики — только через `RuntimeStepTxService.appendAudit()`.
- **НЕ** обновлять и не удалять записи `audit_events` — таблица append-only.
- **НЕ** реализовывать Git-публикацию в сервисном слое — только через модуль `publication`.
- **НЕ** нумеровать новые Liquibase-файлы ниже 033 — нумерация строго возрастающая.

#### Frontend
- **НЕ** использовать `BrowserRouter` — только `HashRouter`.
- **НЕ** вызывать `fetch()` напрямую — только через `apiRequest()`.
- **НЕ** добавлять новые npm-зависимости для UI — только Ant Design.
- **НЕ** добавлять TypeScript в проект.
- **НЕ** создавать глобальный стейт-менеджер.
- **НЕ** добавлять роуты минуя `App.jsx`.

#### Безопасность
- Токен авторизации в `localStorage` — не передавать в URL, не логировать.
- Seed-пользователь (`admin/admin`) только для dev — не использовать в production.

---

## Руководство по использованию

**Для AI-агентов:**
- Читать этот файл перед реализацией любого кода в проекте.
- Соблюдать ВСЕ правила точно как задокументировано.
- При сомнениях — выбирать более строгий вариант.
- Сообщать команде если обнаружен новый паттерн, не покрытый этим файлом.

**Для команды:**
- Обновлять файл при изменении технологического стека или паттернов.
- Удалять правила, которые стали очевидными со временем.
- Проверять актуальность ежеквартально.

_Последнее обновление: 2026-03-30_
