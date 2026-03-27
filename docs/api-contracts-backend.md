# API Контракты — Backend

> Базовый URL: `http://localhost:8080`
> Аутентификация: `Authorization: Bearer <token>` (кроме `/api/auth/login`)
> Идемпотентные операции требуют заголовок `Idempotency-Key: <uuid>`

---

## Аутентификация `/api/auth`

| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/api/auth/login` | Вход. Тело: `{username, password}`. Ответ: `{token, user}` |
| `POST` | `/api/auth/logout` | Выход. Инвалидирует токен сессии |
| `GET` | `/api/auth/me` | Текущий пользователь: `{id, username, role, roles[]}` |

---

## Flows `/api/flows`

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/flows` | Список последних версий всех flows |
| `GET` | `/api/flows/query` | Каталог flows с фильтрацией и пагинацией (cursor-based) |
| `GET` | `/api/flows/{flowId}` | Последняя версия flow |
| `GET` | `/api/flows/{flowId}/versions` | Все версии flow |
| `GET` | `/api/flows/{flowId}/versions/{version}` | Конкретная версия |
| `POST` | `/api/flows/{flowId}/save` | Сохранить/обновить flow. Требует `Idempotency-Key` |

### Query-параметры `/api/flows/query`
`cursor`, `limit`, `search`, `codingAgent`, `teamCode`, `platformCode`, `flowKind`, `riskLevel`, `environment`, `approvalStatus`, `contentSource`, `visibility`, `lifecycleStatus`, `tag`, `status`, `version`, `hasDescription`

### Тело FlowSaveRequest
```json
{
  "flow_yaml": "<YAML-содержимое flow>"
}
```

---

## Rules `/api/rules`

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/rules` | Список последних версий всех rules |
| `GET` | `/api/rules/query` | Каталог rules с фильтрацией |
| `GET` | `/api/rules/{ruleId}` | Последняя версия rule |
| `GET` | `/api/rules/{ruleId}/versions` | Все версии |
| `GET` | `/api/rules/{ruleId}/versions/{version}` | Конкретная версия |
| `POST` | `/api/rules/{ruleId}/save` | Сохранить rule. Требует `Idempotency-Key` |
| `GET` | `/api/rule-templates` | Шаблоны rule по coding-агентам |

---

## Skills `/api/skills`

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/skills` | Список последних версий всех skills |
| `GET` | `/api/skills/query` | Каталог skills с фильтрацией |
| `GET` | `/api/skills/{skillId}` | Последняя версия skill |
| `GET` | `/api/skills/{skillId}/versions` | Все версии |
| `GET` | `/api/skills/{skillId}/versions/{version}` | Конкретная версия |
| `POST` | `/api/skills/{skillId}/save` | Сохранить skill. Требует `Idempotency-Key` |
| `GET` | `/api/skill-templates` | Шаблоны skill по coding-агентам |

---

## Projects `/api/projects`

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/projects` | Список проектов |
| `POST` | `/api/projects` | Создать проект |
| `GET` | `/api/projects/{projectId}` | Получить проект |
| `PATCH` | `/api/projects/{projectId}` | Обновить проект |
| `POST` | `/api/projects/{projectId}/archive` | Архивировать проект |
| `GET` | `/api/projects/{projectId}/runs` | История запусков проекта |

---

## Runs (Runtime) `/api/runs`

| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/api/runs` | Создать запуск flow. Запускается асинхронно после создания |
| `GET` | `/api/runs` | Список последних запусков |
| `GET` | `/api/runs/{runId}` | Состояние запуска с текущим gate |
| `POST` | `/api/runs/{runId}/resume` | Возобновить приостановленный запуск |
| `POST` | `/api/runs/{runId}/cancel` | Отменить запуск |
| `GET` | `/api/runs/{runId}/nodes` | Выполнения нод |
| `GET` | `/api/runs/{runId}/artifacts` | Артефакты запуска |
| `GET` | `/api/runs/{runId}/artifacts/{artifactVersionId}/content` | Содержимое артефакта |
| `GET` | `/api/runs/{runId}/gates/current` | Текущий активный gate |
| `GET` | `/api/runs/{runId}/audit` | Полный аудит-лог запуска |
| `GET` | `/api/runs/{runId}/audit/{eventId}` | Отдельное событие аудита |
| `GET` | `/api/runs/{runId}/audit/query` | Фильтрованный запрос аудита |
| `GET` | `/api/runs/{runId}/nodes/{nodeExecutionId}/log` | Лог выполнения ноды (streaming offset) |

### Тело RunCreateRequest
```json
{
  "project_id": "<uuid>",
  "target_branch": "main",
  "flow_canonical_name": "my-flow@1.0",
  "feature_request": "Описание задачи",
  "idempotency_key": "<uuid>"
}
```

---

## Gates `/api/gates`

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/gates/inbox` | Список gates, ожидающих действия текущего пользователя |
| `POST` | `/api/gates/{gateId}/submit-input` | Отправить человеческий ввод (human_input gate) |
| `POST` | `/api/gates/{gateId}/approve` | Одобрить (human_approval gate) |
| `POST` | `/api/gates/{gateId}/request-rework` | Запросить переработку |

### Тело GateApproveRequest
```json
{
  "expected_gate_version": 1,
  "comment": "LGTM",
  "reviewed_artifact_version_ids": ["<uuid>"]
}
```

### Тело GateReworkRequest
```json
{
  "expected_gate_version": 1,
  "mode": "keep_changes",
  "comment": "Нужно исправить X",
  "instruction": "Подробная инструкция для агента",
  "reviewed_artifact_version_ids": ["<uuid>"]
}
```

---

## Publications `/api/publications`

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/publications/requests` | Список заявок на публикацию |
| `GET` | `/api/publications/jobs` | Список заданий публикации |
| `GET` | `/api/publications/requests/{requestId}/jobs` | Задания по заявке |
| `POST` | `/api/publications/flows/{flowId}/versions/{version}/approve` | Утвердить публикацию flow |
| `POST` | `/api/publications/flows/{flowId}/versions/{version}/reject` | Отклонить публикацию flow |
| `POST` | `/api/publications/flows/{flowId}/versions/{version}/retry` | Повторить публикацию flow |
| `POST` | `/api/publications/rules/{ruleId}/versions/{version}/approve` | Утвердить публикацию rule |
| `POST` | `/api/publications/rules/{ruleId}/versions/{version}/reject` | Отклонить публикацию rule |
| `POST` | `/api/publications/rules/{ruleId}/versions/{version}/retry` | Повторить публикацию rule |
| `POST` | `/api/publications/skills/{skillId}/versions/{version}/approve` | Утвердить публикацию skill |
| `POST` | `/api/publications/skills/{skillId}/versions/{version}/reject` | Отклонить публикацию skill |
| `POST` | `/api/publications/skills/{skillId}/versions/{version}/retry` | Повторить публикацию skill |

---

## Settings `/api/settings`

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/settings/runtime` | Текущие runtime-настройки |
| `PUT` | `/api/settings/runtime` | Обновить: `workspace_root`, `coding_agent`, `ai_timeout_seconds` |
| `PUT` | `/api/settings/catalog` | Обновить настройки каталога (git repo URL, ветка, SSH/HTTPS ключи) |
| `PUT` | `/api/settings/catalog/repair` | Синхронизировать каталог из git-репозитория в БД |

---

## Dashboard `/api/dashboard`

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/dashboard/overview` | Сводные метрики: счётчики runs, flows, rules, skills |

---

## Коды ошибок

| HTTP-код | Условие |
|----------|---------|
| `400 Bad Request` | Ошибка валидации |
| `401 Unauthorized` | Не аутентифицирован |
| `403 Forbidden` | Нет прав |
| `404 Not Found` | Объект не найден |
| `409 Conflict` | Конфликт версий (оптимистичная блокировка) |
| `422 Unprocessable Entity` | Логическая ошибка выполнения |
