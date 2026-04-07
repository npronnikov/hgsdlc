# План реализации

## Краткое резюме
Нужно перевести `/#/product-pipeline` с мокового сценария на реальный runtime-run: запускать flow через backend API, показывать текущую исполняемую ноду и live-аудит по polling. План состоит из 4 шагов: интеграция запуска, live polling, UI-отображение состояния и финальная проверка сборкой. Основной риск — неполная/нестабильная форма данных audit/nodes, поэтому нужны fallback-и в UI.

## Архитектурные решения
- **Решение 1**: Использовать те же API, что уже применяются в `run-console` (`/runs`, `/runs/{id}`, `/runs/{id}/nodes`, `/runs/{id}/audit`), чтобы не дорабатывать backend и не расходиться по контрактам.
- **Решение 2**: Сохранить текущую анимационную модель экрана (`headline -> dock -> center`) и встроить реальный запуск/опрос внутрь нее, чтобы не ломать уже согласованное UX-поведение.
- **Решение 3**: Маппинг `node_id -> title` строить из `flow_yaml` (`/flows/{flowId}`), с fallback на `node_id`, чтобы всегда показывать понятное имя ноды без зависимости от новых backend-полей.

---

## Шаги реализации

### Шаг 1 — Подключение реального запуска flow (сложность: medium)
**Цель**: убрать моковый запуск и создавать реальный run из `/#/product-pipeline`.

**Файлы**:
- `frontend/src/pages/ProductPipelineMvp.jsx` — заменить моковый `startRun` на вызов `POST /runs` с `project_id`, `target_branch`, `flow_canonical_name`, `feature_request`, `publish_mode`.

**Детали реализации**:
```javascript
const runResponse = await apiRequest('/runs', {
  method: 'POST',
  body: JSON.stringify({
    project_id,
    target_branch,
    flow_canonical_name,
    feature_request,
    publish_mode: 'branch',
    idempotency_key: crypto.randomUUID(),
  }),
});
setActiveRunId(runResponse.run_id);
```

**Зависит от**: нет зависимостей

**Проверка**:
- `npm run build` — фронтенд собирается без ошибок
- Ручная проверка: по нажатию Run создается реальный run (виден в `/#/run-console`)

---

### Шаг 2 — Реальный polling статуса, нод и аудита (сложность: medium)
**Цель**: получать данные выполнения из backend каждые 2 секунды.

**Файлы**:
- `frontend/src/pages/ProductPipelineMvp.jsx` — добавить polling для `/runs/{id}`, `/runs/{id}/nodes`, `/runs/{id}/audit`.

**Детали реализации**:
```javascript
useEffect(() => {
  const poll = () => loadRunRuntime(runId, { silent: true });
  poll();
  const id = window.setInterval(poll, 2000);
  return () => window.clearInterval(id);
}, [runId]);
```

**Зависит от**: шаг 1

**Проверка**:
- Ручная проверка: в процессе run статус/ноды/аудит обновляются без перезагрузки страницы

---

### Шаг 3 — UI для live-стадии и human gates (сложность: medium)
**Цель**: в центральном блоке показывать «Pipeline status», текущее имя ноды (title), и live-аудит; при human gate — кнопку перехода.

**Файлы**:
- `frontend/src/pages/ProductPipelineMvp.jsx` — рендер live-панели, вычисление текущей ноды, кнопка `Перейти` в `/gate-input` или `/gate-approval`.
- `frontend/src/styles.css` — стили live-аудит ленты и состояний панели.

**Детали реализации**:
```javascript
const gatePath = gate.gate_kind === 'human_input' ? '/gate-input' : '/gate-approval';
navigate(`${gatePath}?runId=${runId}&gateId=${gate.gate_id}&gateKind=${gate.gate_kind}`);
```

**Зависит от**: шаги 1, 2

**Проверка**:
- Ручная проверка: в центре отображается имя текущей ноды (не canonical), внизу прокручиваются audit events
- Ручная проверка: при `waiting_gate` появляется кнопка перехода в gate-экран

---

### Шаг 4 — Завершение и регрессионная проверка (сложность: low)
**Цель**: убедиться, что анимации start/cancel и текущие UX-правила не сломаны.

**Файлы**:
- `frontend/src/pages/ProductPipelineMvp.jsx`
- `frontend/src/styles.css`

**Детали реализации**:
```javascript
// Cancel: optional backend cancel + возврат UI в idle-анимацию
await apiRequest(`/runs/${runId}/cancel`, { method: 'POST' });
setState(APP_STATES.IDLE);
```

**Зависит от**: шаги 1–3

**Проверка**:
- `npm run build` — успешно
- Ручная проверка: Run/Canceled анимации соответствуют текущему UX

---

## Риски и способы их снижения

| Риск | Вероятность | Воздействие | Способ снижения |
|------|-------------|-------------|-----------------|
| `requirements.md` отсутствует в репозитории | med | low | Использовать подтвержденные требования из сообщения пользователя как источник требований |
| Разная форма payload у audit events | high | med | Добавить fallback-форматирование текста событий (event_type/summary/message) |
| У части run нет `current_node_id` в конкретный момент | med | med | Брать fallback из активного node execution или последней ноды |
| Отсутствует `title` ноды в runtime-данных | high | low | Строить map title из `flow_yaml` + fallback на `node_id` |

## Критические точки

Критическая точка — корректный `flow_canonical_name` при запуске run. Если flows API не возвращает canonical_name, запуск невозможен без fallback-запроса `GET /flows/{flowId}`.

## Итоговые критерии готовности

- [ ] `npm run build` — фронтенд собирается без ошибок
- [ ] Run в `/#/product-pipeline` реально создает run через backend
- [ ] В центре отображается текущее имя исполняемой ноды (title), а не canonical id
- [ ] Audit события в центре обновляются polling-ом каждые 2 секунды
- [ ] При достижении human gate доступна кнопка перехода в соответствующий gate-экран
- [ ] Cancel возвращает UI в стартовое состояние и не ломает существующие анимации
