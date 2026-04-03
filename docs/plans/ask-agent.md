# Ask Agent — дизайн фичи

## Суть

В human gate пользователь может задать агенту вопрос о сделанных изменениях: почему изменена именно эта строка, что означает этот паттерн, почему выбрано такое решение. Агент отвечает в режиме **read-only консультанта** — не модифицирует файлы, не выполняет команды, только отвечает.

Чат является сессией: агент видит весь предыдущий диалог при каждом ответе.

---

## 1. Хранение диалога

### Вариант A: новая таблица `gate_chat_message` (рекомендуется)

```sql
gate_chat_message(
    id           UUID PRIMARY KEY,
    gate_id      UUID NOT NULL REFERENCES gate_instance(id),
    role         VARCHAR(8) NOT NULL,  -- 'user' | 'agent'
    content      TEXT NOT NULL,
    created_at   TIMESTAMP NOT NULL
)
```

**Плюсы:**
- Диалог сохраняется при обновлении страницы и виден с любого устройства
- История не раздувает `payloadJson` gate-а
- Легко подгружать только последние N сообщений при росте диалога

**API:**
- `GET /api/gates/{gateId}/chat` — получить историю чата
- `POST /api/gates/{gateId}/ask` → `{question, selectedDiff?}` → `{answer, messageId}`

### Вариант B: встроить в `payloadJson` gate-а

Добавить поле `chatMessages: [{role, content, createdAt}]` в существующий JSON.

**Плюсы:** нет новой таблицы, проще миграция.
**Минусы:** payload может вырасти до нескольких МБ при длинном диалоге; плохо работает с оптимистичной блокировкой при concurrent запросах.

**Вывод:** Вариант A чище. Вариант B допустим для MVP если хочется минимума изменений.

---

## 2. Как агент понимает, что за изменения сделаны

### Что передавать в промпт

Агент **не получает доступ к файловой системе** — это главная защита от непреднамеренных изменений. Весь контекст передаётся как текст в промпте.

В промпте ask-режима агент получает:

| Блок | Источник |
|------|----------|
| Инструкция ноды (что должно быть сделано) | `nodeExecution.nodeInstruction` |
| Git-diff всех изменений в рамках рана | `GET /gates/{gateId}/changes` + `GET /gates/{gateId}/diff?path=...` |
| Workflow progress (что делали предыдущие шаги) | существующий механизм `WorkflowProgressEntry` |
| Выделенный фрагмент diff-а (опционально) | выбор пользователя в UI |
| История диалога | `gate_chat_message` для этого gate |
| Текущий вопрос | ввод пользователя |

Git-diff для ask получаем теми же эндпоинтами, что и для отображения в UI — они уже есть. Если diff большой (>50KB) — передаём только summary + файлы, к которым есть вопрос.

---

## 3. Базовый промпт агента (ask-mode)

```
## ROLE
You are a software development assistant in READ-ONLY consultation mode.
You have just completed a coding task as part of an automated workflow.
A human reviewer is asking you questions about the changes you made.

STRICT CONSTRAINTS:
- You MUST NOT modify any files.
- You MUST NOT run any shell commands.
- You MUST NOT use any tools that write to disk or execute code.
- You can ONLY answer questions by producing plain text.

## TASK THAT WAS EXECUTED
{node_instruction}

## WORKFLOW CONTEXT (previous steps)
{workflow_progress}

## CHANGES MADE (git diff)
{git_diff}

{selected_diff_block}

## CONVERSATION SO FAR
{chat_history}

## CURRENT QUESTION
{user_question}

Answer concisely. If the question refers to a specific code fragment, quote it.
Do not suggest further changes unless explicitly asked.
```

Блок `{selected_diff_block}` добавляется только если пользователь выделил фрагмент:
```
## SELECTED FRAGMENT (user is asking about this specifically)
{selected_diff}
```

---

## 4. Защита от изменений файлов

Три слоя:

1. **Промпт**: явные инструкции NOT to use tools / NOT to write files (выше)
2. **Нет рабочей директории**: агент в ask-режиме запускается **без** `--add-dir` / `--workspace` флагов — ему физически не передаётся путь к проекту. Всё содержимое идёт через промпт-файл.
3. **Парсинг ответа**: ответ агента читается как plain text. Никакого `STEP_SUMMARY:` маркера не ожидается и не обрабатывается — сервер не ищет его в stdout.

Таким образом, даже если агент попытается вызвать file-write инструменты — ему некуда писать.

---

## 5. История для агента (контекст сессии)

В каждый запрос к агенту передаётся полная история диалога:

```
## CONVERSATION SO FAR

User: Почему ты изменил импорт в строке 12?
Agent: Старый импорт использовал устаревший API...

User: А почему не использовал named import?
Agent: ...
```

При росте диалога (>10 сообщений) — можно сжать старые сообщения до краткого резюме и передавать последние 6–8 полных сообщений. Сжатие опционально для первой версии.

Контекст запуска (feature request, node instruction, git diff) передаётся **каждый раз** — он не меняется в рамках сессии.

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
AskAnswerResult ask(UUID gateId, String question, String selectedDiff, User user) {
    GateInstance gate = ...;
    List<ChatMessage> history = chatRepo.findByGateIdOrderByCreatedAt(gateId);

    String diff = buildDiffContext(gate);          // собрать git diff из changes API
    String prompt = buildAskPrompt(gate, diff, selectedDiff, history, question);

    String answer = invokeAgentReadOnly(gate.getRunId(), prompt);  // без рабочей директории

    chatRepo.save(new GateChatMessage(gateId, "user", question));
    chatRepo.save(new GateChatMessage(gateId, "agent", answer));

    return new AskAnswerResult(answer);
}
```

### Новые эндпоинты

```
GET  /api/gates/{gateId}/chat         → List<ChatMessageDto>
POST /api/gates/{gateId}/ask          → {question, selectedDiff?} → {answer}
```

### Invocation без рабочей директории

В `CodingAgentStrategy` добавить метод `askAgent(promptText)` — запускает агент CLI только с промпт-файлом, без `--add-dir`, без `--workspace`. Или переиспользовать существующий `ProcessExecutionPort` с урезанными параметрами.

---

## 8. Открытые вопросы

| Вопрос | Варианты |
|--------|----------|
| Таймаут ask-запроса | 30–60 сек (ответ на вопрос быстрее полного запуска ноды) |
| Максимальный размер diff в промпте | 50KB — если больше, truncate + предупреждение |
| Сжатие истории диалога | Для v1 — нет (передаём всё); для v2 — резюмировать старые сообщения |
| Параллельные вопросы | Один вопрос за раз — UI блокирует поле пока агент отвечает |
| Аудит | Вопросы не аудируются (как указано в требованиях), только ответы сохраняются в БД |
