# Ask Agent — дизайн фичи

## Суть

В human gate пользователь может задать агенту вопрос о сделанных изменениях: почему изменена именно эта строка, что означает этот паттерн, почему выбрано такое решение. Агент продолжает **ту же Claude-сессию** в которой выполнял задачу — он помнит контекст и может читать файлы проекта.

Чат является сессией: каждый вопрос подключается к той же сессии через `--resume sessionId`.

---

## 1. Хранение диалога

Новая таблица `gate_chat_message`:

```sql
gate_chat_message(
    id           UUID PRIMARY KEY,
    gate_id      UUID NOT NULL REFERENCES gate_instances(id),
    role         VARCHAR(8) NOT NULL,  -- 'user' | 'agent'
    content      TEXT NOT NULL,
    created_at   TIMESTAMP NOT NULL
)
```

**API:**
- `GET /api/gates/{gateId}/chat` — получить историю чата
- `POST /api/gates/{gateId}/ask` → `{question, selectedDiff?}` → `{answer, messageId}`

---

## 2. Как агент понимает, что за изменения сделаны

Агент подключается к той же Claude-сессии (`--resume sessionId`), в которой выполнял задачу — он уже имеет полный контекст выполненной работы и может читать файлы проекта.

### Источник session ID

`GateInstance.nodeExecutionId` → `NodeExecutionEntity.agentSessionId`

- Если `agentSessionId != null` — подключаемся через `--resume sessionId`
- Если `agentSessionId == null` (нода не запускала агент) — запускаем новую сессию без `--resume`

### Что передавать в промпте ask-режима

| Блок | Источник |
|------|----------|
| Инструкция ноды (что должно быть сделано) | `nodeExecution` → `NodeModel.instruction` |
| Выделенный фрагмент diff-а (опционально) | выбор пользователя в UI |
| История диалога | `gate_chat_message` для этого gate |
| Текущий вопрос | ввод пользователя |

---

## 3. Базовый промпт агента (ask-mode)

Два файла шаблонов: `ask-prompt-template.en.md` и `ask-prompt-template.ru.md`.
Язык выбирается по настройке `runtime.prompt_language` (как для обычных промптов).

### Английская версия

```
## ASK MODE
You are answering questions about the coding task you just completed.
You have full access to the project files for reference.

## TASK THAT WAS EXECUTED
{NODE_INSTRUCTION}

## CONVERSATION SO FAR
{CHAT_HISTORY}

## CURRENT QUESTION
{QUESTION}

{SELECTED_DIFF_SECTION}

Answer concisely. Quote specific code fragments when relevant.
```

### Русская версия

```
## РЕЖИМ ВОПРОС
Ты отвечаешь на вопросы о задаче, которую только что выполнил.
У тебя есть полный доступ к файлам проекта для справки.

## ВЫПОЛНЕННАЯ ЗАДАЧА
{NODE_INSTRUCTION}

## ИСТОРИЯ ДИАЛОГА
{CHAT_HISTORY}

## ТЕКУЩИЙ ВОПРОС
{QUESTION}

{SELECTED_DIFF_SECTION}

Отвечай кратко. Цитируй конкретные фрагменты кода когда это уместно.
```

Блок `{SELECTED_DIFF_SECTION}` добавляется только если пользователь выделил фрагмент:

**EN:**
```
## SELECTED CODE FRAGMENT (user is asking about this specifically)
{SELECTED_DIFF}
```

**RU:**
```
## ВЫДЕЛЕННЫЙ ФРАГМЕНТ (пользователь спрашивает именно об этом)
{SELECTED_DIFF}
```

---

## 4. CLI-команда запуска

```
claude --dangerously-skip-permissions --output-format text -p {{PROMPT_FILE}} --resume 'SESSION_ID'
```

Отличия от обычного запуска:
- `--output-format text` вместо `stream-json` — ответ читается напрямую как plain text из stdout
- `--resume 'SESSION_ID'` — продолжение той же сессии
- Рабочая директория = `projectRoot` (агент может читать файлы)
- Никакого `STEP_SUMMARY:` маркера не ожидается

Если `agentSessionId == null` — команда без `--resume`.

---

## 5. История для агента (контекст сессии)

Поскольку агент запускается через `--resume`, история всего предыдущего диалога уже находится в памяти сессии Claude. Дополнительная история из `gate_chat_message` передаётся в промпте в блоке `{CHAT_HISTORY}` как plain text, чтобы агент видел именно вопросы пользователя в этом UI-чате.

При первом вопросе (история пустая) блок `{CHAT_HISTORY}` опускается.

---

## 6. UI

### Вход в чат

В HumanGate два варианта входа:
- **Кнопка "Ask agent"** в шапке gate — открывает чат-панель без контекста конкретного фрагмента
- **Кнопка "Ask" рядом с блоком diff-а** — открывает чат с уже прикреплённым выделенным фрагментом

### Чат-панель

Drawer или нижняя панель (не новая страница, чтобы видеть diff и чат одновременно):

```
┌─────────────────────────────────────────┐
│ 💬 Ask agent                       [×]  │
├─────────────────────────────────────────┤
│  Agent: Готов ответить на вопросы       │
│         о сделанных изменениях.         │
│                                         │
│  You: Почему в строке 42 используется   │
│       async/await вместо Promise?       │
│                                         │
│  Agent: В данном контексте async/await  │
│         читаемее — функция уже была     │
│         async по другой причине...      │
├─────────────────────────────────────────┤
│ [Прикреплён фрагмент: utils.js:40-45 ×]│
│ ┌─────────────────────────────────┐     │
│ │ Введите вопрос...               │ [→] │
│ └─────────────────────────────────┘     │
└─────────────────────────────────────────┘
```

- Чат **не блокирует** approve/rework — они остаются доступными
- История чата сохраняется и видна при повторном открытии
- После закрытия gate (approve/rework) чат переходит в read-only

---

## 7. Backend: план реализации

### Новые сущности

```java
// GateChatMessageEntity
UUID id
UUID gateId
String role  // "user" | "agent"
String content
Instant createdAt
```

### Новый сервис `GateAskService`

```java
AskResult ask(UUID gateId, String question, @Nullable String selectedDiff) {
    GateInstanceEntity gate = ...;
    NodeExecutionEntity nodeExecution = ...;
    RunEntity run = ...;

    String sessionId = nodeExecution.getAgentSessionId();  // может быть null
    List<GateChatMessageEntity> history = chatRepo.findByGateIdOrderByCreatedAtAsc(gateId);

    String prompt = buildAskPrompt(nodeExecution, history, question, selectedDiff);

    // Записываем prompt-файл в .hgsdlc/ask/{gateId}/
    // Запускаем claude --output-format text -p promptFile [--resume sessionId]
    ProcessExecutionResult result = processPort.execute(...);

    String answer = result.stdout().trim();

    chatRepo.save(new GateChatMessageEntity(gateId, "user", question));
    chatRepo.save(new GateChatMessageEntity(gateId, "agent", answer));

    return new AskResult(answer);
}
```

### Новые эндпоинты

```
GET  /api/gates/{gateId}/chat         → List<ChatMessageResponse>
POST /api/gates/{gateId}/ask          → {question, selectedDiff?} → {answer}
```

### Расположение файлов ask-сессии

Prompt, stdout и stderr пишутся в:
```
{runWorkspaceRoot}/.hgsdlc/ask/{gateId}/{uuid}.prompt.md
{runWorkspaceRoot}/.hgsdlc/ask/{gateId}/{uuid}.stdout.log
{runWorkspaceRoot}/.hgsdlc/ask/{gateId}/{uuid}.stderr.log
```

---

## 8. Открытые вопросы

| Вопрос | Решение для v1 |
|--------|----------------|
| Таймаут ask-запроса | Тот же `ai_timeout_seconds` из настроек |
| Параллельные вопросы | Не блокируем на бэкенде; UI блокирует поле пока агент отвечает |
| Read-only после закрытия gate | Фронт проверяет `gate.status` и показывает чат как readonly |
| Защита от записи файлов | Не реализуем в v1 |
