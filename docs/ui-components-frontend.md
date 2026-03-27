# UI-компоненты и страницы — Frontend

> Стек: React 18.2 + Vite 5.2 + Ant Design 5.15 + React Router 6.22
> Маршрутизация: HashRouter (без серверного рендеринга)
> Аутентификация: Context API (`AuthContext`) + Bearer-токен в localStorage

---

## Маршруты приложения

| Путь | Компонент | Описание |
|------|-----------|----------|
| `/login` | `Login` | Форма входа |
| `/overview` | `Overview` | Дашборд: счётчики, последние запуски |
| `/projects` | `Projects` | Список проектов |
| `/flows` | `Flows` | Каталог flows с фильтрами |
| `/flows/create` | `FlowEditor` | Создание нового flow |
| `/flows/:flowId` | `FlowEditor` | Редактирование flow |
| `/rules` | `Rules` | Каталог rules |
| `/rules/create` | `RuleEditor` | Создание rule |
| `/rules/:ruleId` | `RuleEditor` | Редактирование rule |
| `/skills` | `Skills` | Каталог skills |
| `/skills/create` | `SkillEditor` | Создание skill |
| `/skills/:skillId` | `SkillEditor` | Редактирование skill |
| `/requests` | `Requests` | Очередь публикаций (publication requests + jobs) |
| `/run-launch` | `RunLaunch` | Запуск нового flow run |
| `/run-console` | `RunConsole` | Консоль запуска: ноды, лог, артефакты, текущий gate |
| `/gates-inbox` | `GatesInbox` | Входящие gates для текущего пользователя |
| `/gate-input` | `GateInput` | Форма human_input gate |
| `/gate-approval` | `GateApproval` | Форма human_approval gate |
| `/audit-runtime` | `AuditRuntime` | Аудит-лог запуска |
| `/audit-agent` | `AuditAgent` | Лог выполнения агента (stdout/stderr) |
| `/audit-review` | `AuditReview` | Просмотр событий проверки |
| `/prompt-package` | `PromptPackage` | Просмотр сформированного промпта агента |
| `/artifacts` | `Artifacts` | Просмотр артефактов запуска |
| `/delta-summary` | `DeltaSummary` | Сводка изменений (diff summary) |
| `/versions` | `Versions` | История версий артефакта |
| `/settings` | `Settings` | Runtime и catalog настройки |

---

## Переиспользуемые компоненты

### `AppShell`
Основной layout: боковое меню навигации + `<Outlet>` для дочерних маршрутов. Включает переключатель темы.

### `ActionCenter`
Панель действий для gates: кнопки approve/rework, форма ввода, просмотр артефактов.

### `ArtifactViewer`
Просмотр содержимого артефактов: рендеринг Markdown (`react-markdown` + `remark-gfm`), текстовых файлов и бинарных.

### `StatusTag`
Ant Design `Tag` с цветовым кодированием для статусов (`running` → синий, `completed` → зелёный, `failed` → красный и т.д.).

---

## Ключевые компоненты-страницы

### `FlowEditor`
- Встроенный редактор YAML на базе Monaco Editor
- Визуализация графа нод через ReactFlow + dagre (автоматическая раскладка)
- Валидация YAML на клиенте
- Сохранение с идемпотентным ключом

### `RunConsole`
Главный рабочий экран оператора:
- Список нод выполнения с статусами
- Лог агента в реальном времени (polling с offset)
- Вкладки: ноды / артефакты / аудит / текущий gate
- Inline approve/rework прямо из консоли

### `GateApproval` / `GateInput`
- Форма просмотра артефактов gate с `ArtifactViewer`
- Кнопки: Approve / Request Rework с формой инструкции
- Оптимистичная блокировка через `expected_gate_version`

### `FlowEditor` (граф нод)
- Ноды отображаются как карточки: тип, ID, статус
- Рёбра (`on_success` / `on_failure`) визуализируют переходы
- Human gates выделены отдельным цветом

### `Requests` (Publication Queue)
- Двухпанельный вид: заявки (PublicationRequest) и задания (PublicationJob)
- Кнопки approve/reject/retry для каждого артефакта

---

## Управление состоянием

Состояние приложения не централизовано (нет Redux/MobX). Использован паттерн:
- `AuthContext` — глобальное: текущий пользователь + токен
- `ThemeContext` — глобальное: тема (light/dark)
- Локальный `useState` / `useEffect` на каждой странице
- API-вызовы через `src/api/request.js` (обёртка над `fetch` с Bearer-токеном)

---

## Модуль `src/api/request.js`

Централизованный HTTP-клиент:
- Добавляет `Authorization: Bearer <token>` из localStorage
- Для мутирующих запросов генерирует `Idempotency-Key`
- Нормализует ошибки через `src/utils/errorMessages.js`

---

## Утилиты

| Файл | Назначение |
|------|-----------|
| `utils/errorMessages.js` | Человекочитаемые сообщения об ошибках API |
| `utils/frontmatter.js` | Парсинг YAML frontmatter из Markdown-контента skills/rules |
| `utils/monacoTheme.js` | Кастомная тема Monaco Editor |
| `data/mock.js` | Мок-данные для разработки UI без backend |
