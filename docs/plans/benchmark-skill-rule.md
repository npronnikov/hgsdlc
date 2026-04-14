# Benchmark Skills & Rules — план реализации (Способ 3)

## Суть

Первоклассная сущность `BenchmarkCase` (многоразовый тест-кейс) + `BenchmarkRun` (конкретный прогон A vs B).
Позволяет тестировать влияние скила/рула на результат агента, накапливать историю по версиям и в будущем подключить LLM-судью для автоматической оценки.

---

## Последовательность работы

### Шаг 1 — Создание BenchmarkCase

Пользователь открывает скил или рул в каталоге → нажимает **"Benchmark"**.

Система показывает модал с формой:
- `project_repo_url` — ссылка на репозиторий (или выбор из существующих проектов)
- `instruction` — задание агенту (чистый текст, без служебных промптов)
- `name` (опционально) — название кейса для истории

При сохранении создаётся `BenchmarkCase`. Если кейс с такой инструкцией + репо уже существует — предлагается переиспользовать.

---

### Шаг 2 — Запуск BenchmarkRun

Система создаёт `BenchmarkRun` и инициирует **два параллельных** эфемерных прогона:

- **Run A** — агент + скил/рул (тестируемый артефакт)
- **Run B** — агент без скила/рула (контрольный)

Оба прогона имеют одинаковую структуру:
```
START → AI_NODE → HUMAN_APPROVAL → END
```

В AI_NODE:
- Промпт = инструкция из BenchmarkCase, без системных заголовков
- Run A: к промпту добавляется скил/рул (через обычный механизм materialization)
- Run B: никаких дополнительных артефактов

Флоу создаются эфемерно (флаг `ephemeral: true`), не попадают в каталог.

---

### Шаг 3 — Параллельное выполнение

Оба `startRun()` вызываются одновременно. Каждый прогон:
1. Клонирует репо в изолированный workspace (`runId_A/project/` и `runId_B/project/`)
2. Запускает агента с инструкцией
3. Агент делает изменения в рабочей копии
4. Прогон доходит до HUMAN_APPROVAL и встаёт на паузу

`BenchmarkRun` переходит в статус `WAITING_COMPARISON` когда **оба** прогона встали на паузу.

---

### Шаг 4 — Human Approval: экран сравнения

Пользователь открывает BenchmarkRun → видит специальный экран сравнения (отдельный от стандартного HumanGate):

```
┌─────────────────────────────────────────────────────────────────────┐
│  Benchmark: "Добавить обработку ошибок"  │  Skill: error-handling/v2 │
├──────────────────────────┬──────────────────────────────────────────┤
│  Run A (со скилом)       │  Run B (без скила)                       │
│  ─────────────────────   │  ─────────────────────                   │
│  + try { ... }           │  + if (result == null) {                 │
│  + catch (Exception e) { │  +   System.out.println("err");          │
│  +   log.error(...)      │  + }                                     │
│  + }                     │                                          │
├──────────────────────────┴──────────────────────────────────────────┤
│  Дельта (A vs B):                                                    │
│  A использует log.error + структурированную обработку               │
│  B — println без стека                                              │
├─────────────────────────────────────────────────────────────────────┤
│  [Скил полезен ✓]   [Скил не помог ✗]   [Пропустить]               │
└─────────────────────────────────────────────────────────────────────┘
```

Что показывается:
- **Git diff A** — все изменения, сделанные агентом в Run A
- **Git diff B** — все изменения, сделанные агентом в Run B
- **Diff of diffs** — что есть в A и чего нет в B (и наоборот), вычисляется на бэке

---

### Шаг 5 — Решение и завершение

Пользователь выбирает оценку:
- `SKILL_USEFUL` / `SKILL_NOT_HELPFUL` / `NEUTRAL`

Оба прогона завершаются (APPROVED → END). Workspace-ы можно сохранить или очистить.
`BenchmarkRun` сохраняет `human_verdict` + `completed_at`.

---

### Шаг 6 (опциональный) — LLM Judge

После human approval можно запустить **LLM Judge** — отдельный AI-вызов (без workspace, только текст):

Промпт судьи:
```
Instruction: <инструкция>
Result A (with skill): <git diff A>
Result B (without skill): <git diff B>

Evaluate on criteria:
1. Correctness (0-10)
2. Code quality (0-10)
3. Adherence to instruction (0-10)

Return JSON: { "a": {...scores}, "b": {...scores}, "winner": "A"|"B"|"tie", "reasoning": "..." }
```

Результат сохраняется в `BenchmarkRun.judge_result` (JSONB).
Judge запускается асинхронно, не блокирует human approval.

---

## Метод сравнения результатов

### Уровень 1 — структурный (всегда)
- `git diff HEAD` в workspace после прогона → список изменённых файлов и строк
- Diff of diffs: построчное сравнение двух git diff-ов через унифицированный формат

### Уровень 2 — семантический (опциональный LLM Judge)
- Отправка обоих diff-ов в LLM с критериями оценки
- Структурированный JSON-ответ с оценками и обоснованием
- Хранение в JSONB → агрегация по версиям скила

### Уровень 3 — исторический (после накопления данных)
- График `score_a vs score_b` по версиям скила/рула
- Процент прогонов где `SKILL_USEFUL` — видна эффективность артефакта

---

## Предполагаемые изменения в системе

### Backend — новые сущности

**`BenchmarkCase`**
```
id, name, instruction, project_repo_url,
created_by, created_at
```

**`BenchmarkRun`**
```
id, case_id,
artifact_type (SKILL | RULE),
artifact_id, artifact_version_id,
coding_agent,
run_a_id (FK → RunEntity),  -- со скилом/рулом
run_b_id (FK → RunEntity),  -- без
status (RUNNING | WAITING_COMPARISON | COMPLETED | FAILED),
human_verdict (SKILL_USEFUL | SKILL_NOT_HELPFUL | NEUTRAL | null),
judge_result (JSONB, nullable),
diff_a (TEXT), diff_b (TEXT),   -- сохранённые git diff-ы
completed_at, created_by, created_at
```

### Backend — новые сервисы

**`BenchmarkService`**
- `createCase(instruction, repoUrl, name)` → BenchmarkCase
- `startRun(caseId, artifactType, artifactId, codingAgent)` → BenchmarkRun
  - создаёт два эфемерных флоу + два run-а
  - вызывает оба `startRun()` параллельно
- `onRunCompleted(runId)` — хук: когда оба run-а встали на HUMAN_APPROVAL → вычисляет diff_a, diff_b, diff-of-diffs → статус `WAITING_COMPARISON`
- `submitVerdict(benchmarkRunId, verdict)` → завершает оба run-а
- `triggerJudge(benchmarkRunId)` → асинхронный LLM-вызов

**`BenchmarkDiffService`**
- `computeGitDiff(workspacePath)` — git diff HEAD в workspace
- `computeDiffOfDiffs(diffA, diffB)` — сравнение двух diff-ов

### Backend — новые API

```
POST   /benchmark/cases                          — создать кейс
GET    /benchmark/cases                          — список кейсов
GET    /benchmark/cases/{id}/runs                — история прогонов по кейсу

POST   /benchmark/runs                           — запустить прогон
GET    /benchmark/runs/{id}                      — статус + дiffs
POST   /benchmark/runs/{id}/verdict              — сохранить оценку человека
POST   /benchmark/runs/{id}/judge                — запустить LLM Judge
```

### Frontend — новые страницы

**`/benchmark`** — список BenchmarkCase + последние результаты

**`/benchmark/:runId`** — экран сравнения:
- Side-by-side Monaco diff viewer (уже используется в проекте)
- Unified diff между двумя результатами
- Кнопки вердикта
- Блок с результатом LLM Judge (если есть)

**Изменения в существующих страницах:**
- `SkillDetail` / `RuleDetail` — кнопка "Benchmark" → открывает модал создания кейса
- Поддержка `ephemeral` флоу — не показывать в обычном каталоге

### Переиспользование существующего кода

| Что | Откуда |
|-----|--------|
| `CodingAgentStrategy.materializeWorkspace()` | Без изменений |
| `RunLifecycleService.startRun()` | Без изменений, передаётся `ephemeral` проект |
| `HumanGateHandler` | Без изменений, но BenchmarkService слушает события |
| Monaco diff viewer | Уже используется в HumanGate |
| `AgentPromptBuilder` | Минимальный режим: только инструкция, без служебных блоков |

---

## Что НЕ входит в MVP

- Хранение workspace после завершения (очищается сразу)
- LLM Judge (добавляется итеративно, таблица уже готова)
- Нотификации / webhooks о завершении прогона
- Запуск нескольких кейсов батчем

---

## Порядок реализации

1. `BenchmarkCase` + `BenchmarkRun` — миграции + JPA entities
2. `BenchmarkService.startRun()` — эфемерные флоу + параллельный запуск
3. `BenchmarkDiffService` — git diff + diff-of-diffs
4. API endpoints
5. Frontend: модал запуска из SkillDetail/RuleDetail
6. Frontend: страница `/benchmark/:runId` — side-by-side сравнение + вердикт
7. (итерация 2) LLM Judge
