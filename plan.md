# План реализации

## Краткое резюме
Реализуем управляемые сессии для AI-нод и Ask Agent в human gate: для каждого AI-attempt фиксируем `session_id`, а при rework без отката даем явный выбор `resume previous session` или `start new session`. План состоит из 8 шагов (DB → backend domain/service/API → frontend → tests), основной риск — недетерминированность поведения при недоступной CLI-сессии, поэтому вводим жесткую session policy и явный fallback с аудитом.

## Архитектурные решения
- **Решение 1**: Сессия привязывается к `node_execution` (attempt), а не к run целиком.
  Причина: в текущей архитектуре каждый AI-запуск — отдельный subprocess; привязка к attempt дает предсказуемость и простой аудит.
- **Решение 2**: Для rework вводим явную политику `session_policy` (`resume_previous_session | new_session | auto`).
  Причина: убрать неявное поведение и дать управляемый выбор пользователю в UI.
- **Решение 3**: При `keep_changes=false` всегда принудительно `new_session`.
  Причина: после rollback к checkpoint продолжение старой памяти агента повышает риск конфликтов контекста.
- **Решение 4**: История чата хранится в отдельной таблице `gate_chat_message`, а не в `gate.payload_json`.
  Причина: избегаем раздувания payload и конфликтов optimistic locking.
- **Решение 5**: Ask Agent сначала пытается `resume` в исходную сессию AI-attempt, при недоступности использует контролируемый fallback (новая сессия/stateless) с аудит-событием.
  Причина: сохраняем UX «вкатиться в ту же сессию», но не блокируем ревью-процесс.

---

## Шаги реализации

### Шаг 1 — Расширить схему БД для session-aware runtime и chat (сложность: high)
**Цель**: Добавить persistence для ID сессии AI-attempt, политики rework-сессии и сообщений Ask Agent.

**Файлы**:
- `backend/src/main/resources/db/changelog/046-runtime-agent-session.sql` — добавить в `node_executions` поля `agent_session_id`, `agent_session_provider`.
- `backend/src/main/resources/db/changelog/047-runtime-rework-session-policy.sql` — добавить в `runs` поле `pending_rework_session_policy`.
- `backend/src/main/resources/db/changelog/048-gate-chat-message.sql` — создать таблицу `gate_chat_message` (+ индексы по `gate_id`, `created_at`).
- `backend/src/main/resources/db/changelog/db.changelog-master.yaml` — подключить changesets `046/047/048`.

**Детали реализации**:
```sql
ALTER TABLE node_executions
  ADD COLUMN agent_session_id VARCHAR(255),
  ADD COLUMN agent_session_provider VARCHAR(64);

ALTER TABLE runs
  ADD COLUMN pending_rework_session_policy VARCHAR(32);

CREATE TABLE gate_chat_message (
  id UUID PRIMARY KEY,
  gate_id UUID NOT NULL,
  role VARCHAR(8) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_gate_chat_message_gate_created
  ON gate_chat_message(gate_id, created_at);
```

**Зависит от**: нет зависимостей

**Проверка**:
- `cd backend && ./gradlew compileJava`
- Приложение стартует с Liquibase без ошибок на чистой БД.

---

### Шаг 2 — Обновить domain/repository слой runtime и chat (сложность: medium)
**Цель**: Сделать новые БД-поля доступными сервисам и API.

**Файлы**:
- `backend/src/main/java/ru/hgd/sdlc/runtime/domain/NodeExecutionEntity.java` — добавить `agentSessionId`, `agentSessionProvider`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/domain/RunEntity.java` — добавить `pendingReworkSessionPolicy`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/infrastructure/NodeExecutionRepository.java` — добавить запрос на поиск последнего `SUCCEEDED` AI execution до/для gate node.
- `backend/src/main/java/ru/hgd/sdlc/runtime/domain/GateChatMessageEntity.java` — новая сущность.
- `backend/src/main/java/ru/hgd/sdlc/runtime/infrastructure/GateChatMessageRepository.java` — новый репозиторий.

**Детали реализации**:
```java
@Column(name = "agent_session_id", length = 255)
private String agentSessionId;

@Column(name = "pending_rework_session_policy", length = 32)
private String pendingReworkSessionPolicy;
```

**Зависит от**: шаг 1

**Проверка**:
- `cd backend && ./gradlew compileJava`
- JPA schema validation проходит.

---

### Шаг 3 — Добавить session policy в rework-команду и сохранение в run state (сложность: high)
**Цель**: При `request-rework` фиксировать, как запускать следующую AI-ноду: через resume или с новой сессией.

**Файлы**:
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/command/ReworkGateCommand.java` — добавить поле `sessionPolicy`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/api/RuntimeController.java` — расширить `GateReworkRequest` JSON-полем `session_policy`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/GateDecisionService.java` — нормализация политики:
  - `keep_changes=false` => всегда `new_session`;
  - `keep_changes=true` => `session_policy` из запроса или `auto`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/RuntimeStepTxService.java` — метод обновления `pendingReworkSessionPolicy` + audit (`rework_session_policy_staged`).

**Детали реализации**:
```java
String resolvedPolicy = !keepChanges ? "new_session" : normalizeSessionPolicy(command.sessionPolicy());
runtimeStepTxService.replacePendingReworkSessionPolicy(run.getId(), gate.getId(), resolvedPolicy);
```

**Зависит от**: шаг 2

**Проверка**:
- `cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeGateDecisionServiceTest`
- Контракт: `POST /gates/{id}/request-rework` сохраняет policy и возвращает корректный `resource_version`.

---

### Шаг 4 — Привязать AI-attempt к session_id и научить runtime делать resume/new (сложность: high)
**Цель**: Сделать запуск AI-ноды session-aware и управляемым через staged policy.

**Файлы**:
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/CodingAgentStrategy.java` — расширить `MaterializationRequest` данными `sessionMode`/`resumeSessionId`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/QwenCodingAgentStrategy.java` — поддержать placeholders `{{SESSION_ID}}`, `{{RESUME_SESSION_ID}}`, `{{SESSION_MODE}}`.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/ClaudeCodingAgentStrategy.java` — аналогично.
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunStepService.java` —
  - определить session mode для AI-attempt на основе `pendingReworkSessionPolicy`;
  - генерировать новый `session_id` для `new_session`;
  - выбирать `resume_session_id` для `resume_previous_session`;
  - сохранять фактический `agent_session_id` в `node_execution`;
  - очищать staged policy после consume.
- `backend/src/main/java/ru/hgd/sdlc/settings/application/SettingsService.java` — дефолтные launch-команды с session placeholders.

**Детали реализации**:
```java
String generatedSessionId = run.getId() + ":" + node.getId() + ":attempt-" + execution.getAttemptNo();
// resume_previous_session -> command template uses {{RESUME_SESSION_ID}}
// new_session -> command template uses {{SESSION_ID}}
```

**Зависит от**: шаги 2-3

**Проверка**:
- `cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeRegressionFlowTest`
- Проверить audit:
  - `agent_session_selected`
  - `agent_invocation_started` содержит эффективный режим.

---

### Шаг 5 — Привязать gate к исходной AI-сессии для Ask Agent (сложность: medium)
**Цель**: У каждого human gate иметь ссылку на AI-attempt, в чью сессию нужно «вкатываться» при ask.

**Файлы**:
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RunStepService.java` — в `buildGatePayload(...)` добавить:
  - `ask_source_node_execution_id`
  - `ask_source_node_id`
  - `ask_source_session_id`
  - `ask_source_session_provider`
- `backend/src/main/java/ru/hgd/sdlc/runtime/infrastructure/NodeExecutionRepository.java` — query для получения последнего успешного `ai` execution в рамках run (или до текущего gate execution).

**Детали реализации**:
```java
payload.put("ask_source_session_id", sourceExec.getAgentSessionId());
payload.put("ask_source_node_execution_id", sourceExec.getId().toString());
```

**Зависит от**: шаг 4

**Проверка**:
- `GET /runs/{runId}` -> `current_gate.payload` содержит `ask_source_session_id` для human gate.

---

### Шаг 6 — Реализовать backend Ask Agent API и сервис (сложность: high)
**Цель**: Добавить чат и ask-вызов, который сначала пытается работать через resume исходной сессии.

**Файлы**:
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/GateAskService.java` — новый сервис:
  - load gate + access checks;
  - load/save `gate_chat_message`;
  - собрать prompt (history + diff + selected fragment + workflow progress);
  - invoke agent in ask mode (resume first).
- `backend/src/main/java/ru/hgd/sdlc/runtime/api/RuntimeController.java` — endpoints:
  - `GET /api/gates/{gateId}/chat`
  - `POST /api/gates/{gateId}/ask`
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/service/RuntimeQueryService.java` — вспомогательный query для chat history (или отдельный query service).
- `backend/src/main/java/ru/hgd/sdlc/runtime/application/AgentPromptBuilder.java` (опционально) — helper для ask-prompt sections.

**Детали реализации**:
```java
try {
  answer = invokeAskWithResume(sourceSessionId, prompt);
} catch (SessionUnavailableException ex) {
  answer = invokeAskFallback(prompt); // new/stateless
  appendAudit(..., "ask_agent_resume_fallback", ...);
}
```

**Зависит от**: шаги 4-5

**Проверка**:
- `cd backend && ./gradlew test --tests ru.hgd.sdlc.runtime.RuntimeApiContractTest`
- API-контракт:
  - чат возвращается в хронологическом порядке;
  - ask создает 2 сообщения (`user`, `agent`).

---

### Шаг 7 — Добавить UI для Ask Agent и выбора session policy в rework (сложность: high)
**Цель**: Дать пользователю управляемые сценарии `resume/reset` и чат в gate UI.

**Файлы**:
- `frontend/src/pages/HumanGate.jsx` —
  - Drawer/панель Ask Agent;
  - загрузка `GET /gates/{id}/chat`;
  - отправка `POST /gates/{id}/ask`;
  - в блоке rework добавить выбор `Session policy`:
    - `Resume previous session`
    - `Start new session`
    - `Auto`.
  - если `keep_changes=false`, policy фиксируется в `new_session` и control disabled.
- `frontend/src/styles.css` — стили для ask drawer/chat bubbles/session policy block.

**Детали реализации**:
```jsx
const effectiveSessionPolicy = keepChanges ? sessionPolicy : 'new_session';
await apiRequest(`/gates/${gate.gate_id}/request-rework`, {
  method: 'POST',
  body: JSON.stringify({ ..., keep_changes: keepChanges, session_policy: effectiveSessionPolicy }),
});
```

**Зависит от**: шаг 6

**Проверка**:
- `cd frontend && npm run build`
- Ручные сценарии:
  - `keep_changes=true` -> можно выбрать resume/new/auto;
  - `keep_changes=false` -> UI принудительно `new_session`.

---

### Шаг 8 — Тесты, регрессии и документация (сложность: medium)
**Цель**: Закрепить поведение сессий и Ask Agent в тестах и документации.

**Файлы**:
- `backend/src/test/java/ru/hgd/sdlc/runtime/RuntimeRegressionFlowTest.java` — сценарии rework policy (`keep/new`, `keep/resume`, `discard/new`).
- `backend/src/test/java/ru/hgd/sdlc/runtime/RuntimeGateDecisionServiceTest.java` — валидация нормализации `session_policy`.
- `backend/src/test/java/ru/hgd/sdlc/runtime/RuntimeApiContractTest.java` — ask/chat endpoints.
- `docs/plans/ask-agent.md` — обновить разделы с учетом session-aware режима.
- `README.md` или `docs/developer-onboarding.md` — коротко описать runtime session policy.

**Детали реализации**:
```java
assertThat(nextAiExecution.getAgentSessionId()).isEqualTo(expectedSessionId);
assertThat(auditEvents).anyMatch(e -> e.getEventType().equals("rework_session_policy_staged"));
```

**Зависит от**: шаги 1-7

**Проверка**:
- `cd backend && ./gradlew test`
- `cd frontend && npm run build`
- Smoke: end-to-end run с human gate -> ask -> rework keep/resume -> следующий AI attempt.

---

## Риски и способы их снижения

| Риск | Вероятность | Воздействие | Способ снижения |
|------|-------------|-------------|-----------------|
| Resume CLI-сессии недоступен/протухает | med | high | Явный fallback + audit-событие + UI уведомление о degraded режиме |
| Команда запуска агента несовместима с placeholders сессии | med | high | Валидация launch command в Settings + дефолтные шаблоны + контрактные тесты |
| Неверная привязка gate к source AI execution | low | high | Query по `run_id + node_kind=ai + status=succeeded` + интеграционный тест на реальный граф |
| Рост контекста ask-подсказки (diff/history) | high | med | Ограничение размера, truncate policy, приоритет selected fragment |
| Конкурентные ask-запросы в один gate | med | med | Серверный lock по gate_id или reject второго запроса, UI блок input на время ответа |
| Регрессии в текущем rework потоке | med | high | Покрыть unit/integration тестами `GateDecisionService` и `RunStepService` |

## Критические точки
- Корректная нормализация `session_policy` в `request-rework` (иначе непредсказуемый запуск AI после gate).
- Стабильный алгоритм выбора `ask_source_session_id` для gate payload.
- Поддержка placeholders в `agent_launch_command` без ломки существующих custom-команд.

## Итоговые критерии готовности

- [ ] `cd backend && ./gradlew test` — все тесты проходят
- [ ] `cd frontend && npm run build` — фронтенд собирается без ошибок
- [ ] В `request-rework` при `keep_changes=false` policy всегда `new_session`
- [ ] В `request-rework` при `keep_changes=true` policy управляется явно (`resume/new/auto`)
- [ ] Следующий AI-attempt использует корректный session mode и сохраняет `agent_session_id`
- [ ] `Ask Agent` умеет продолжать исходную сессию gate (или фиксирует fallback)
- [ ] История Ask Agent сохраняется в БД и восстанавливается при перезагрузке страницы
- [ ] Новое поведение отражено в документации runtime/gate
