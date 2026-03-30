---
stepsCompleted: [step-01-validate-prerequisites, step-02-design-epics]
inputDocuments:
  - _bmad-output/planning-artifacts/prd.md
  - _bmad-output/planning-artifacts/architecture.md
  - docs/index.md
  - docs/project-overview.md
  - docs/architecture.md
  - docs/source-tree-analysis.md
  - docs/integration-architecture.md
  - docs/development-guide.md
  - docs/api-contracts-backend.md
  - docs/data-models-backend.md
  - docs/ui-components-frontend.md
  - _bmad-output/project-context.md
---

# hgsdlc - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for hgsdlc, decomposing the requirements from the PRD and Architecture into implementable stories.

## Requirements Inventory

### Functional Requirements

**Управление воркфлоу**

FR1: ✅ Аналитик, Team Lead, Architect или Developer может запустить воркфлоу из каталога с указанием входных параметров
FR2: ✅ Аналитик, Team Lead, Architect или Developer может остановить выполняющийся воркфлоу
FR3: ✅ Пользователь может просматривать список запущенных воркфлоу и их текущий статус
FR4: ⚠️ Аналитик, Team Lead, Architect или Developer может повторно запустить воркфлоу с теми же зафиксированными версиями компонентов — _снимок flow_snapshot_json сохраняется, но endpoint "запустить снова по снимку" отсутствует_

**Движок выполнения воркфлоу**

FR5: ✅ Система выполняет воркфлоу строго в соответствии с зафиксированными версиями скиллов, промптов и rules на момент запуска
FR6: ✅ Аналитик, Team Lead, Architect или Developer может просматривать промежуточный артефакт на каждом шаге до подтверждения
FR7: ✅ Team Lead, Architect, Аналитик или Developer может подтвердить или отклонить результат шага воркфлоу
FR8: ✅ Система сохраняет снимок версий всех компонентов воркфлоу (скиллы, rules, промпты, модель LLM) на момент запуска
FR9: ❌ Система уведомляет участников о смене статуса шага или завершении воркфлоу — _SSE не реализован, замена — polling gates/inbox_
FR37: ✅ Аналитик, Team Lead, Architect или Developer может перезапустить отдельный шаг при ошибке выполнения без перезапуска всего воркфлоу

**Управление артефактами**

FR10: ✅ Аналитик, Team Lead, Architect или Developer может просматривать все артефакты, сгенерированные в рамках воркфлоу
FR11: ✅ Система связывает каждый артефакт с версией воркфлоу, скиллов и rules, которые его породили
FR12: ⚠️ Аналитик, Team Lead, Architect, Developer или Auditor может экспортировать артефакт в стандартном формате (Markdown, JSON) — _контент доступен через /content, явного export-endpoint с форматированием нет_
FR13: ❌ Система автоматически сохраняет финальные артефакты в подключённый Git-репозиторий — _publication публикует catalog-items (skills/rules/flows), но не артефакты запуска_

**Каталог воркфлоу**

FR15: ⚠️ Platform Admin может создавать, редактировать, публиковать и архивировать воркфлоу в каталоге организации — _CRUD и публикация реализованы, но без проверки роли Admin (RBAC отсутствует)_
FR16: ❌ Team Lead может создавать, редактировать, публиковать и архивировать воркфлоу в каталоге своего workspace — _workspace-уровень каталога не реализован_
FR17: ✅ Пользователь может просматривать доступные воркфлоу с описанием, параметрами и историей версий
FR18: ✅ Система сохраняет полную историю версий воркфлоу; опубликованные версии неизменяемы
FR39: ✅ Пользователь может искать воркфлоу в каталоге по названию и тегам

**Управление Workspace и пользователями**

FR19: ❌ Platform Admin может создавать и удалять workspace'ы — _модуль workspace отсутствует_
FR20: ❌ Platform Admin и Team Lead могут приглашать пользователей в workspace и назначать роли
FR21: ❌ Platform Admin и Team Lead могут изменять роли пользователей в workspace
FR22: ❌ Система разграничивает данные между workspace'ами — пользователи видят только данные своего workspace — _tenant_id отсутствует в схеме БД_
FR23: ✅ Пользователь может аутентифицироваться с помощью логина и пароля

**Dashboard и аналитика**

FR24: ✅ Team Lead и Platform Admin могут просматривать сводную статистику запусков по workspace
FR25: ✅ Аналитик, Team Lead, Architect, Developer или Auditor может просматривать детальный статус запуска с разбивкой по шагам
FR26: ❌ Team Lead может идентифицировать шаги воркфлоу с наибольшим количеством ручных исправлений — _данные есть (attemptNo, rejected gates), запрос не написан_
FR27: ❌ Platform Admin и Team Lead могут экспортировать данные метрик в стандартном формате

**Уведомления**

FR38: ❌ Аналитик, Team Lead, Architect или Developer получает in-app уведомление, когда требуется его действие (подтверждение шага воркфлоу) — _SseEmitter не реализован_

**Интеграции**

FR28: ⚠️ Система поддерживает интеграцию с self-hosted GitLab CE/EE для хранения и версионирования артефактов — _HTTP-вызовы в PublicationService, но без абстракции GitProvider_
FR29: ❓ Система поддерживает интеграцию с self-hosted Bitbucket Server для хранения и версионирования артефактов — _статус неизвестен, требует проверки PublicationService_

**Аудит и трассируемость**

FR32: ⚠️ Система автоматически записывает все действия пользователей в неизменяемый audit log — _реализован только runtime (runs/gates); действия с каталогом, ролями, workspace не логируются_
FR33: ⚠️ Platform Admin, Team Lead и Auditor могут просматривать audit log с фильтрацией по дате, пользователю, workspace и воркфлоу — _фильтрация реализована per-run; глобального audit log нет_
FR34: ❌ Platform Admin, Team Lead и Auditor могут экспортировать записи audit log
FR35: ❌ Auditor имеет read-only доступ к данным платформы без возможности запуска или изменения — _RBAC по ролям не реализован_
FR36: ⚠️ По любому артефакту система отображает полную цепочку трассируемости: воркфлоу → шаг → версии компонентов → пользователь → время — _run→node→gate→artifact работает; catalog-операции не в audit_

_Growth (вне MVP):_
- FR14: Сравнение артефактов двух запусков одного воркфлоу
- FR30: Интеграция с публичными Git-провайдерами (GitHub, GitLab.com)
- FR31: API для запуска воркфлоу из внешних систем

### NonFunctional Requirements

**Производительность**

NFR1: Интерактивные действия пользователя в UI (навигация, фильтрация, подтверждение шага) завершаются за < 1 с при нормальной нагрузке
NFR2: Запуск воркфлоу и выполнение шагов — асинхронные операции; система уведомляет по завершению без индикации в реальном времени
NFR3: API-запросы без LLM-вызова возвращают ответ за < 500 мс

**Безопасность**

NFR4: ❓ Все данные передаются по TLS 1.2+ — _конфигурационная, зависит от деплоя_
NFR5: ❌ Данные каждого workspace изолированы на уровне БД — tenant_id обязателен для всех записей — _workspace модуль отсутствует_
NFR6: ❌ Аккаунт блокируется после 5 последовательных неудачных попыток входа — _AuthService не содержит счётчика failed_attempts_
NFR7: ⚠️ Секреты и credentials хранятся в зашифрованном виде и не логируются — _пароли BCrypt, но audit payload_json может содержать sensitive data_
NFR8: ❌ RBAC-проверка выполняется на каждый API-запрос без исключений — _SecurityConfig: только аутентификация; ни одного @PreAuthorize в контроллерах_

**Масштабируемость**

NFR9: Система поддерживает до 50 активных workspace'ов без деградации производительности
NFR10: Система поддерживает до 1000 параллельных запусков воркфлоу одновременно
NFR11: Горизонтальное масштабирование компонентов возможно без изменения архитектуры

**Надёжность**

NFR12: Доступность платформы — не менее 99% в месяц (≤ 7.2 ч простоя)
NFR13: Данные audit log неизменяемы и не удаляются при ошибках системы
NFR14: При сбое шага воркфлоу состояние запуска сохраняется для перезапуска с прерванного шага (FR37)

### Additional Requirements

_Технические требования из Architecture, влияющие на реализацию:_

- Brownfield: стек зафиксирован — Java 21 + Spring Boot 3.3 / React 18 + Ant Design 5 / PostgreSQL 16 — не заменять
- Liquibase append-only: миграции 1–32 неизменяемы; новые файлы нумеруются от 33+
- SQL-совместимость: запросы должны работать на H2 MODE=PostgreSQL и реальном PostgreSQL (запрещено `ILIKE`, `::cast`)
- UUID генерируется в сервисном слое (`UUID.randomUUID()`), не через `@GeneratedValue`
- Frontend: без TypeScript; UI только из Ant Design; без Redux/Zustand; все вызовы API через `apiRequest()`
- **Первая история реализации:** новый Spring-модуль `workspace` + Liquibase-миграция 33 (таблица `workspaces` + `workspace_id` на всех существующих сущностях)
- Новые Spring-модули для создания: `workspace`, `notification`, `git-provider`
- Async-очередь выполнения: DB-polling + `SELECT ... FOR UPDATE SKIP LOCKED` (без внешних брокеров)
- SSE-уведомления: Spring `SseEmitter` в модуле `notification`; frontend подписывается на `/api/notifications/stream`
- Git-абстракция: интерфейс `GitProvider` + реализации `GitLabProvider` / `BitbucketServerProvider` через Spring `RestClient`
- Audit-иммутабельность: PostgreSQL GRANT (без DELETE/UPDATE на `audit_events`) + Liquibase-триггер
- Workspace-контекст в URL: `#/ws/:workspaceId/` prefix для всех workspace-aware маршрутов; Admin: `#/admin/...`
- Новые frontend-страницы: WorkspaceSelector, WorkspaceAdmin, NotificationBell (Ant Design Badge)
- Production: Kubernetes, stateless backend (сессии в БД), Spring Actuator probes, K8s Secrets → env vars
- Архитектурный порядок реализации: Workspace → RBAC → Version snapshot → Catalog (2-уровневый) → Notifications → Git-provider

### UX Design Requirements

_UX-спецификация не создавалась. Существующие UI-компоненты задокументированы в `docs/ui-components-frontend.md`._

_Технические UI-требования, выведенные из Architecture:_

- UX-DR1: Добавить страницу WorkspaceSelector для выбора/переключения workspace при входе
- UX-DR2: Добавить раздел WorkspaceAdmin (управление пользователями и ролями в workspace) доступный Team Lead
- UX-DR3: Добавить компонент NotificationBell на AppShell (Ant Design Badge с количеством) с подпиской на SSE-поток
- UX-DR4: Переработать навигацию AppShell: добавить workspace-контекст (#/ws/:workspaceId/) и Admin-раздел (#/admin/)
- UX-DR5: Добавить страницу Admin: управление workspace'ами и пользователями для Platform Admin

### FR Coverage Map

_Легенда: ✅ реализован | ⚠️ частично | ❌ не реализован | ❓ неизвестно_

FR1: ✅ Epic 3 — Запуск воркфлоу из каталога с параметрами
FR2: ✅ Epic 3 — Остановка выполняющегося воркфлоу
FR3: ✅ Epic 3 — Просмотр списка запущенных воркфлоу и статусов
FR4: ⚠️ Epic 3 — Повторный запуск с зафиксированными версиями
FR5: ✅ Epic 3 — Выполнение строго по зафиксированным версиям компонентов
FR6: ✅ Epic 3 — Просмотр промежуточного артефакта на шаге
FR7: ✅ Epic 3 — Подтверждение/отклонение результата шага
FR8: ✅ Epic 3 — Снимок версий компонентов на момент запуска
FR9: ❌ Epic 5 — Уведомление о смене статуса шага / завершении воркфлоу
FR10: ✅ Epic 4 — Просмотр артефактов воркфлоу
FR11: ✅ Epic 4 — Связь артефакта с версиями компонентов
FR12: ⚠️ Epic 4 — Экспорт артефакта (Markdown, JSON)
FR13: ❌ Epic 4 — Автосохранение финальных артефактов в Git
FR15: ⚠️ Epic 2 — Управление воркфлоу Platform Admin (каталог организации)
FR16: ❌ Epic 2 — Управление воркфлоу Team Lead (каталог workspace)
FR17: ✅ Epic 2 — Просмотр доступных воркфлоу с описанием и версиями
FR18: ✅ Epic 2 — История версий воркфлоу; опубликованные неизменяемы
FR19: ❌ Epic 1 — Создание и удаление workspace'ов
FR20: ❌ Epic 1 — Приглашение пользователей и назначение ролей
FR21: ❌ Epic 1 — Изменение ролей пользователей в workspace
FR22: ❌ Epic 1 — Изоляция данных между workspace'ами
FR23: ✅ Epic 1 — Аутентификация логин/пароль
FR24: ✅ Epic 6 — Сводная статистика запусков по workspace
FR25: ✅ Epic 6 — Детальный статус запуска с разбивкой по шагам
FR26: ❌ Epic 6 — Шаги с наибольшим количеством ручных исправлений
FR27: ❌ Epic 6 — Экспорт метрик
FR28: ⚠️ Epic 4 — Интеграция с self-hosted GitLab CE/EE
FR29: ❓ Epic 4 — Интеграция с self-hosted Bitbucket Server
FR32: ⚠️ Epic 6 — Неизменяемый audit log всех действий пользователей
FR33: ⚠️ Epic 6 — Просмотр audit log с фильтрацией
FR34: ❌ Epic 6 — Экспорт audit log
FR35: ❌ Epic 6 — Read-only доступ Auditor
FR36: ⚠️ Epic 6 — Полная цепочка трассируемости по артефакту
FR37: ✅ Epic 3 — Перезапуск отдельного шага без перезапуска воркфлоу
FR38: ❌ Epic 5 — In-app уведомление о требуемом действии
FR39: ✅ Epic 2 — Поиск воркфлоу по названию и тегам
FR14, FR30, FR31: Growth — вне MVP

## Epic List

### Epic 1: Workspace & Управление доступом
Platform Admin и Team Lead могут создавать workspace'ы, управлять пользователями с ролевым доступом (RBAC) и аутентифицироваться. Данные изолированы между workspace'ами.
**FRs:** FR19, FR20, FR21, FR22, FR23
**UX-DRs:** UX-DR1, UX-DR2, UX-DR4, UX-DR5

### Epic 2: Каталог воркфлоу
Пользователи могут просматривать, искать и управлять версионированными шаблонами воркфлоу в каталоге организации и своего workspace.
**FRs:** FR15, FR16, FR17, FR18, FR39

### Epic 3: Запуск и управление выполнением воркфлоу
Пользователи могут запускать воркфлоу, отслеживать прогресс, просматривать и подтверждать/отклонять результат каждого шага, перезапускать упавшие шаги и повторять запуски с зафиксированными версиями компонентов.
**FRs:** FR1, FR2, FR3, FR4, FR5, FR6, FR7, FR8, FR37

### Epic 4: Управление артефактами и Git-интеграция
Пользователи могут просматривать, трассировать и экспортировать артефакты. Система автоматически синхронизирует финальные артефакты с self-hosted Git-провайдером (GitLab CE/EE или Bitbucket Server).
**FRs:** FR10, FR11, FR12, FR13, FR28, FR29

### Epic 5: Уведомления
Пользователи своевременно получают in-app уведомления о требуемых действиях (подтверждение шага) и о смене статуса воркфлоу.
**FRs:** FR9, FR38
**UX-DRs:** UX-DR3

### Epic 6: Аналитика, Аудит и Трассируемость
Admins, Team Leads и Auditors могут просматривать метрики выполнения, экспортировать данные и трассировать полную цепочку происхождения артефактов через неизменяемый audit log.
**FRs:** FR24, FR25, FR26, FR27, FR32, FR33, FR34, FR35, FR36
