# RunConstructor — детальный план реализации

## Суть

Новый `RunWorkspace` — режим **отладки workflow**: живой граф с подсвеченными нодами, структурированные логи, inline обработка human gates без перехода на отдельную страницу.

**Ключевое ограничение**: RunWorkspace активируется только когда ран запущен в `debug_mode = true`. Обычные production-прогоны работают через существующий `RunConsole` без изменений.

**RunWorkspace — это только execution view.** Конфигурация и запуск происходят до него: в `DebugRunDrawer` внутри FlowEditor или через toggle на `/run-launch`. RunWorkspace открывается сразу с уже запущенным раном.

---

## Что такое debug mode

Debug mode — режим, при котором разработчик отлаживает сам workflow: проверяет логику нод, шаги выполнения, поведение гейтов. Не связан жёстко с `publish_mode` — можно дебажить ран, который публикует в ветку.

| Откуда запущен | debug_mode | UI мониторинга |
|----------------|------------|----------------|
| FlowEditor → "Debug Run" (новая кнопка) | всегда `true` | RunWorkspace |
| `/run-launch` с включённым Debug toggle | `true` | RunWorkspace |
| `/run-launch` без Debug toggle (по умолчанию) | `false` | RunConsole (без изменений) |
| Все существующие раны | `false` | RunConsole (без изменений) |

---

## Точки входа для debug-запуска

### Основная — FlowEditor

Пользователь отлаживает workflow прямо из редактора. Кнопка "Test Run" переименовывается в "Debug Run" и открывает **правый Drawer** вместо модала — граф флоу остаётся виден:

```
┌─── FlowEditor ──────────────┬──── Debug Run ─────────────┐
│                             │                             │
│  [граф флоу]                │  Project                    │
│                             │  [my-project      ▾]        │
│  ┌──────┐   ┌──────┐        │                             │
│  │node1 │──▶│node2 │        │  Target branch              │
│  └──────┘   └──────┘        │  [main_____________]        │
│                             │                             │
│                             │  Feature request            │
│                             │  [_____________________]    │
│                             │  [_____________________]    │
│                             │                             │
│                             │  Publish mode               │
│                             │  ● Local  ○ Branch  ○ PR   │
│                             │                             │
│                             │  ─────────────────────────  │
│                             │  Flow: my-flow              │
│                             │         [▶ Run Debug]       │
└─────────────────────────────┴─────────────────────────────┘
```

Флоу известен из контекста редактора — не требует выбора. Три поля: проект, ветка, feature request.

### Вторичная — Run Launch

Страница `/run-launch` остаётся для production-запусков. Добавляется Switch "Debug mode" рядом с Publish mode. По умолчанию выключен.

---

## Что меняется, что остаётся

### Меняется

| Компонент | Изменение |
|-----------|-----------|
| `RunEntity.java` | Новое поле `debug_mode boolean` |
| `RunCreateRequest` | Новое поле `debug_mode` |
| `RunResponse` | Новое поле `debug_mode` |
| `FlowEditor.jsx` | Кнопка "Test Run" → "Debug Run", открывает DebugRunDrawer |
| `RunLaunch.jsx` | Switch "Debug mode" → при `true` navigate на `/run-workspace` |
| `RunConsole.jsx` | Кнопка "Debug View" если `run.debug_mode === true` |
| `AppShell.jsx` | Badge `[D]` у debug-ранов в Recent Runs + navigate на `/run-workspace` |

### Создаётся новое

```
frontend/src/components/flow/DebugRunDrawer.jsx  — launcher (замена TestRunModal)
frontend/src/pages/RunWorkspace.jsx              — execution view для debug-ранов
frontend/src/components/run/LiveGraph.jsx        — ReactFlow + статусы нод
frontend/src/components/run/ExecutionTimeline.jsx — таймлайн с таймером и rework chain
frontend/src/components/run/LiveLogViewer.jsx    — структурированный лог
frontend/src/components/run/GateInputPanel.jsx   — inline human_input форма
frontend/src/components/run/GateReviewPanel.jsx  — approval review (diff + actions)
frontend/src/components/run/StageTracker.jsx     — прогресс по approval gates
frontend/src/utils/flowLayout.js                 — buildFlowPreview (вынести из RunLaunch)
```

### Удаляется

```
frontend/src/components/flow/TestRunModal.jsx    — заменяется на DebugRunDrawer
```

### Не трогается

```
frontend/src/pages/RunConsole.jsx    — все production-раны идут сюда, без изменений
frontend/src/pages/HumanGate.jsx     — остаётся как fallback
frontend/src/pages/RunLaunch.jsx     — остаётся для production, + debug toggle
backend (кроме RunEntity и DTO)      — никакой бизнес-логики не меняется
```

---

## Роутинг

```
/run-workspace?runId=X     — RunWorkspace (debug view, только для debug_mode=true)
/run-console?runId=X       — RunConsole (production view, без изменений)
/run-launch                — RunLaunch (без изменений, + debug toggle)
/human-gate?...            — HumanGate (fallback, не используется из RunWorkspace)
```

`RunWorkspace` при загрузке проверяет `run.debug_mode`. Если `false` — редиректит на `/run-console?runId=X`.

---

## Шаг 1 — Бэкенд: поле debug_mode

### 1.1 RunEntity.java

```java
@Column(name = "debug_mode", nullable = false)
@Builder.Default
private boolean debugMode = false;
```

### 1.2 Liquibase миграция

```sql
ALTER TABLE runs ADD COLUMN debug_mode BOOLEAN NOT NULL DEFAULT FALSE;
```

### 1.3 RunCreateRequest (RuntimeController.java)

```java
public record RunCreateRequest(
    // ... существующие поля ...
    @JsonProperty("debug_mode") Boolean debugMode   // nullable, default false
) {}
```

### 1.4 RunResponse (RuntimeController.java)

```java
public record RunResponse(
    // ... существующие поля ...
    @JsonProperty("debug_mode") boolean debugMode
) {}
```

В `RunResponse.from(RunEntity run, ...)` добавить: `run.isDebugMode()`.

### 1.5 RunLifecycleService

При создании рана: `entity.setDebugMode(request.debugMode() != null && request.debugMode())`.

---

## Шаг 2 — DebugRunDrawer (замена TestRunModal)

**Файл:** `frontend/src/components/flow/DebugRunDrawer.jsx`

Drawer вместо Modal — открывается справа поверх FlowEditor, не скрывая граф (ширина 360px).

```jsx
export function DebugRunDrawer({ open, onClose, canonicalName }) {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) return;
    apiRequest('/projects').then((data) => {
      setProjects(data || []);
      if (data?.length > 0) {
        const first = data[0];
        form.setFieldsValue({
          project_id: first.id,
          target_branch: first.default_branch || 'main',
          publish_mode: 'local',
        });
      }
    });
  }, [open, form]);

  const handleLaunch = async () => {
    const values = await form.validateFields();
    setLoading(true);
    try {
      const response = await apiRequest('/runs', {
        method: 'POST',
        body: JSON.stringify({
          project_id: values.project_id,
          target_branch: values.target_branch,
          flow_canonical_name: canonicalName,
          feature_request: values.feature_request,
          publish_mode: values.publish_mode,
          debug_mode: true,
          idempotency_key: crypto.randomUUID(),
        }),
      });
      onClose();
      navigate(`/run-workspace?runId=${response.run_id}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Drawer
      title="Debug Run"
      placement="right"
      width={360}
      open={open}
      onClose={onClose}
      footer={
        <Button type="primary" icon={<PlayCircleOutlined />} loading={loading} onClick={handleLaunch} block>
          Run Debug
        </Button>
      }
    >
      <Form layout="vertical" form={form}>
        <Form.Item label="Project" name="project_id" rules={[{ required: true }]}>
          <Select
            options={projects.map((p) => ({ value: p.id, label: p.name }))}
            onChange={(value) => {
              const project = projects.find((p) => p.id === value);
              if (project) form.setFieldValue('target_branch', project.default_branch || 'main');
            }}
          />
        </Form.Item>
        <Form.Item label="Target branch" name="target_branch" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item label="Feature request" name="feature_request" rules={[{ required: true }]}>
          <Input.TextArea rows={4} placeholder="Describe the change to debug" />
        </Form.Item>
        <Form.Item label="Publish mode" name="publish_mode" initialValue="local">
          <Radio.Group>
            <Radio value="local">Local</Radio>
            <Radio value="branch">Branch</Radio>
            <Radio value="pr">PR</Radio>
          </Radio.Group>
        </Form.Item>
      </Form>
      <div className="debug-drawer-footer-hint">
        Flow: <code>{canonicalName}</code>
      </div>
    </Drawer>
  );
}
```

### Обновить FlowEditor.jsx

```jsx
// Было:
import { TestRunModal } from '../components/flow/TestRunModal.jsx';
const [testRunModalOpen, setTestRunModalOpen] = useState(false);
// <Button onClick={() => setTestRunModalOpen(true)}>Test Run</Button>
// <TestRunModal open={testRunModalOpen} onClose={...} canonicalName={...} />

// Станет:
import { DebugRunDrawer } from '../components/flow/DebugRunDrawer.jsx';
const [debugDrawerOpen, setDebugDrawerOpen] = useState(false);
// <Button icon={<BugOutlined />} onClick={() => setDebugDrawerOpen(true)}>Debug Run</Button>
// <DebugRunDrawer open={debugDrawerOpen} onClose={...} canonicalName={...} />
```

---

## Шаг 3 — RunLaunch: добавить Debug mode toggle

**Файл:** `frontend/src/pages/RunLaunch.jsx`

Switch рядом с Publish mode:

```jsx
<Form.Item name="debug_mode" valuePropName="checked" initialValue={false}>
  <Switch checkedChildren="Debug" unCheckedChildren="Normal" />
</Form.Item>
```

В `handleLaunch`:

```js
const response = await apiRequest('/runs', {
  method: 'POST',
  body: JSON.stringify({ ...values, debug_mode: values.debug_mode || false }),
});

if (values.debug_mode) {
  navigate(`/run-workspace?runId=${response.run_id}`);
} else {
  navigate(`/run-console?runId=${response.run_id}`);
}
```

---

## Шаг 4 — RunConsole: кнопка "Debug View"

**Файл:** `frontend/src/pages/RunConsole.jsx`

В шапке `RunDetailView`, рядом с Refresh:

```jsx
{run.debug_mode && (
  <Button
    type="default"
    icon={<BugOutlined />}
    onClick={() => navigate(`/run-workspace?runId=${runId}`)}
  >
    Debug View
  </Button>
)}
```

---

## Шаг 5 — AppShell: Recent Runs для debug-ранов

**Файл:** `frontend/src/components/AppShell.jsx`

Два изменения:

**5.1 Badge у debug-ранов:**

```jsx
// В рендере pipeline-run-item:
<span className="pipeline-run-title" title={projectTitle}>{projectTitle}</span>
{run.debug_mode && <span className="pipeline-run-debug-badge">D</span>}
```

**5.2 Navigate на /run-workspace для debug-ранов:**

```jsx
onClick={() => navigate(
  run.debug_mode
    ? `/run-workspace?runId=${encodeURIComponent(runId)}`
    : `/run-console?runId=${encodeURIComponent(runId)}`
)}
```

**CSS:**

```css
.pipeline-run-debug-badge {
  font-size: 9px;
  font-weight: 700;
  background: #722ed1;
  color: #fff;
  border-radius: 3px;
  padding: 0 3px;
  line-height: 14px;
  flex-shrink: 0;
}
```

---

## Шаг 6 — Вынести buildFlowPreview

**Файл:** `frontend/src/utils/flowLayout.js`

Перенести из `RunLaunch.jsx`:
- `buildFlowPreview(flowYaml, direction)` — парсинг YAML + Dagre layout
- `buildEdges(nodes)` — построение рёбер
- `toNodeData(node, isStart)` — маппинг YAML-ноды в data
- `normalizeNodeKind(node)` — нормализация типа ноды
- `NODE_KIND_META` — метаданные типов нод

Используется в: `RunLaunch.jsx`, `LiveGraph.jsx`.

---

## Шаг 7 — FlowNode: runStatus prop

**Файл:** `frontend/src/components/flow/FlowNode.jsx`

Добавить поддержку `data.runStatus`:

```jsx
const RUN_STATUS_CLASS = {
  running:      'is-running',
  succeeded:    'is-succeeded',
  failed:       'is-failed',
  waiting_gate: 'is-waiting-gate',
};

function FlowNode({ data, selected }) {
  const visuals = NODE_KIND_META[data.nodeKind] || {};
  const statusClass = RUN_STATUS_CLASS[data.runStatus] || '';
  return (
    <div className={`flow-node ${visuals.variant || ''} ${statusClass} ${selected ? 'is-selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <div className="flow-node-header">
        <div>
          <div className="flow-node-title">{data.title}</div>
          <div className="flow-node-meta">{visuals.label || data.typeLabel}</div>
        </div>
        {data.isStart && <span className="flow-node-start">Start</span>}
        {data.runStatus === 'running' && (
          <span className="flow-node-run-badge is-running">running</span>
        )}
        {data.runStatus === 'waiting_gate' && (
          <span className="flow-node-run-badge is-waiting">waiting</span>
        )}
      </div>
      <div className="flow-node-id">{data.id}</div>
      {data.isTerminal && <span className="flow-node-stop">Stop</span>}
      <Handle type="source" position={Position.Right} />
    </div>
  );
}
```

**CSS в styles.css:**

```css
@keyframes node-pulse-blue {
  0%   { box-shadow: 0 0 0 0 rgba(22, 119, 255, 0.45); }
  70%  { box-shadow: 0 0 0 8px rgba(22, 119, 255, 0); }
  100% { box-shadow: 0 0 0 0 rgba(22, 119, 255, 0); }
}
@keyframes node-pulse-amber {
  0%   { box-shadow: 0 0 0 0 rgba(250, 173, 20, 0.5); }
  70%  { box-shadow: 0 0 0 8px rgba(250, 173, 20, 0); }
  100% { box-shadow: 0 0 0 0 rgba(250, 173, 20, 0); }
}
.flow-node.is-running      { border-color: #1677ff; animation: node-pulse-blue 1.6s infinite; }
.flow-node.is-waiting-gate { border-color: #faad14; animation: node-pulse-amber 2s infinite; }
.flow-node.is-succeeded    { border-color: #52c41a; }
.flow-node.is-failed       { border-color: #ff4d4f; }

.flow-node-run-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
  font-weight: 600;
  line-height: 16px;
}
.flow-node-run-badge.is-running { background: #1677ff; color: #fff; }
.flow-node-run-badge.is-waiting { background: #faad14; color: #000; }
```

---

## Шаг 8 — LiveGraph

**Файл:** `frontend/src/components/run/LiveGraph.jsx`

```jsx
export function LiveGraph({ flowYaml, nodeExecutions = [], onNodeClick }) {
  const [direction, setDirection] = useState('LR');
  const [layout, setLayout] = useState({ nodes: [], edges: [] });

  useEffect(() => {
    if (!flowYaml) return;
    const preview = buildFlowPreview(flowYaml, direction);
    setLayout({ nodes: preview.nodes, edges: preview.edges });
  }, [flowYaml, direction]);

  // Обогащение нод статусами выполнения
  const enrichedNodes = useMemo(() => layout.nodes.map((node) => {
    const latest = nodeExecutions.filter((e) => e.node_id === node.id).at(-1);
    return {
      ...node,
      data: {
        ...node.data,
        runStatus: latest?.status ?? null,
        nodeExecutionId: latest?.node_execution_id ?? null,
      },
    };
  }), [layout.nodes, nodeExecutions]);

  return (
    <div className="live-graph-canvas flow-canvas">
      <ReactFlow
        nodes={enrichedNodes}
        edges={layout.edges}
        nodeTypes={{ flowNode: FlowNode }}
        nodesDraggable={false}
        nodesConnectable={false}
        zoomOnScroll={false}
        panOnScroll
        fitView
        fitViewOptions={{ padding: 0.2 }}
        onNodeClick={(_, node) => node.data?.nodeExecutionId && onNodeClick?.(node.data.nodeExecutionId)}
        proOptions={{ hideAttribution: true }}
      >
        <Background gap={20} color="var(--color-border-subtle, #e2e8f0)" />
        <Controls showInteractive={false} />
        <Panel position="top-right">
          <Radio.Group value={direction} onChange={(e) => setDirection(e.target.value)}
            optionType="button" size="small">
            <Radio.Button value="TB">↕</Radio.Button>
            <Radio.Button value="LR">↔</Radio.Button>
          </Radio.Group>
        </Panel>
      </ReactFlow>
    </div>
  );
}
```

---

## Шаг 9 — ExecutionTimeline

**Файл:** `frontend/src/components/run/ExecutionTimeline.jsx`

### Живой таймер для running-нод

```jsx
function ElapsedTimer({ startedAt }) {
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    const start = new Date(startedAt).getTime();
    const id = setInterval(() => setElapsed(Math.floor((Date.now() - start) / 1000)), 1000);
    return () => clearInterval(id);
  }, [startedAt]);
  const m = Math.floor(elapsed / 60), s = elapsed % 60;
  return <span className="elapsed-timer">{m > 0 ? `${m}m ` : ''}{s}s</span>;
}
```

### Элемент таймлайна

```jsx
function TimelineItem({ item, isSelected, onSelect }) {
  const running = item.status === 'running';
  const done = ['succeeded', 'failed', 'cancelled'].includes(item.status);
  const duration = done && item.started_at && item.finished_at
    ? formatDuration(item.started_at, item.finished_at) : null;

  return (
    <div className={`etl-item status-${item.status} ${isSelected ? 'is-selected' : ''}`}
      onClick={() => onSelect(item.node_execution_id)}>
      <div className="etl-dot">{nodeTimelineIcon(item.status)}</div>
      <div className="etl-body">
        <div className="etl-name">{item.node_id}</div>
        <div className="etl-meta">
          <span className="etl-kind">{NODE_KIND_META[item.node_kind]?.label || item.node_kind}</span>
          {item.attempt_no > 1 && <span className="etl-attempt">↩ #{item.attempt_no}</span>}
          {item.gate_decision === 'approved' && <span className="etl-badge approved">Approved</span>}
          {item.gate_decision === 'rework_requested' && <span className="etl-badge rework">↩ Rework</span>}
        </div>
        <div className="etl-time">
          {running && <ElapsedTimer startedAt={item.started_at} />}
          {duration && <span className="etl-duration">{duration}</span>}
        </div>
      </div>
    </div>
  );
}
```

### Группировка rework-цепочек

Последовательные записи с одинаковым `node_id` схлопываются в группу. Отображается только последний attempt; остальные раскрываются по кнопке "Show N previous".

### Collapsed-режим (для approval review)

Когда `collapsed={true}` — показываются только иконки статуса без текста (ширина 48px).

```jsx
{collapsed ? (
  <div className="etl-dot-only" title={item.node_id}>{nodeTimelineIcon(item.status)}</div>
) : (
  <TimelineItem ... />
)}
```

---

## Шаг 10 — LiveLogViewer

**Файл:** `frontend/src/components/run/LiveLogViewer.jsx`

### Парсинг строк

```js
function parseLogLine(line) {
  const tsMatch = line.match(/^(\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?)\s+/);
  const ts = tsMatch?.[1] ?? null;
  const rest = tsMatch ? line.slice(tsMatch[0].length) : line;

  if (/^(ERROR|FATAL)/i.test(rest)) return { ts, kind: 'error',  text: rest };
  if (/^WARN/i.test(rest))          return { ts, kind: 'warn',   text: rest };
  if (/tool_use:/i.test(rest))      return { ts, kind: 'tool',   text: rest };
  if (/tool_result:/i.test(rest))   return { ts, kind: 'result', text: rest };
  return                             { ts, kind: 'plain',  text: rest };
}
```

| kind   | цвет        | иконка              |
|--------|-------------|---------------------|
| error  | `#ff4d4f`   | CloseCircleOutlined |
| warn   | `#faad14`   | WarningOutlined     |
| tool   | `#1677ff`   | ThunderboltOutlined |
| result | `#52c41a`   | CheckCircleOutlined |
| plain  | inherit     | —                   |

### Тулбар

```jsx
<div className="log-toolbar">
  <Select size="small" value={filter} onChange={setFilter}
    options={[{ value: 'all', label: 'All' }, { value: 'error', label: 'Errors' }, { value: 'tool', label: 'Tools' }]}
    style={{ width: 100 }}
  />
  <Input size="small" placeholder="Search..." value={search} onChange={(e) => setSearch(e.target.value)} allowClear style={{ width: 180 }} />
  <Switch size="small" checked={autoScroll} onChange={setAutoScroll} />
  <span>Follow</span>
</div>
```

### Polling — вынести в hook

```js
// frontend/src/hooks/useNodeLog.js
export function useNodeLog(runId, nodeExecutionId) {
  const [lines, setLines] = useState([]);
  const [running, setRunning] = useState(true);
  // Логика из RunConsole.NodeLogTab:
  // GET /runs/{runId}/nodes/{nodeExecutionId}/log?offset={offset}
  // interval: 1500ms если running, 5000ms после
  return { lines, running };
}
```

---

## Шаг 11 — GateInputPanel

**Файл:** `frontend/src/components/run/GateInputPanel.jsx`

Отображается в правой панели RunWorkspace вместо LiveLogViewer когда `gate.gate_kind === 'human_input'`.

```jsx
export function GateInputPanel({ runId, gate, onSubmitted }) {
  const editableArtifacts = gate.payload?.human_input_artifacts ?? [];
  const [editedByPath, setEditedByPath] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const submitInput = async () => {
    // Идентичная логика submitInput() из HumanGate.jsx
    // POST /gates/{gate.gate_id}/submit-input
    setSubmitting(true);
    try {
      await apiRequest(`/gates/${gate.gate_id}/submit-input`, {
        method: 'POST',
        body: JSON.stringify({
          expected_gate_version: gate.resource_version,
          artifacts: editableArtifacts
            .filter(/* только изменённые */)
            .map((artifact) => ({
              artifact_key: artifact.artifact_key,
              path: artifact.path,
              scope: artifact.scope || 'run',
              content_base64: encodeBase64(editedByPath[artifact.path] ?? artifact.content ?? ''),
            })),
        }),
      });
      onSubmitted();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="gate-input-panel">
      <div className="gate-input-header">
        <WarningOutlined className="gate-input-icon" />
        <span>Input Required</span>
        <Tag>{gate.node_id}</Tag>
      </div>
      {gate.payload?.user_instructions && (
        <div className="gate-input-instructions">{gate.payload.user_instructions}</div>
      )}
      {editableArtifacts.map((artifact) => {
        const content = editedByPath[artifact.path] ?? artifact.content ?? '';
        const formJson = isHumanForm(content);
        return (
          <div key={artifact.path}>
            {formJson
              ? <HumanFormViewer form={formJson} onChange={(v) => setEditedByPath((p) => ({ ...p, [artifact.path]: v }))} />
              : <Editor height={300} language="markdown" value={content}
                  onChange={(v) => setEditedByPath((p) => ({ ...p, [artifact.path]: v ?? '' }))} />
            }
          </div>
        );
      })}
      <div className="gate-input-footer">
        <Button type="primary" loading={submitting} onClick={submitInput}>Submit</Button>
      </div>
    </div>
  );
}
```

---

## Шаг 12 — StageTracker

**Файл:** `frontend/src/components/run/StageTracker.jsx`

Горизонтальный трекер approval gates над diff-панелью в GateReviewPanel.

```jsx
export function StageTracker({ nodeExecutions, currentGateId }) {
  // Берём только gate-ноды, группируем по node_id (берём последний attempt)
  const stages = useMemo(() => {
    const gateNodes = nodeExecutions.filter((e) => e.gate_kind === 'human_approval');
    const byNodeId = new Map();
    for (const e of gateNodes) {
      byNodeId.set(e.node_id, e); // последний перезаписывает
    }
    return Array.from(byNodeId.values());
  }, [nodeExecutions]);

  return (
    <div className="stage-tracker">
      {stages.map((stage, idx) => {
        const isCurrent = stage.gate_id === currentGateId || (!stage.gate_decision && !stage.gate_id);
        return (
          <React.Fragment key={stage.node_id}>
            <div className={`stage-item ${isCurrent ? 'is-current' : ''} ${stage.gate_decision ? 'is-done' : ''}`}>
              <div className="stage-num">{idx + 1}</div>
              <div className="stage-label">{stage.node_id}</div>
              <div className="stage-decision">
                {stage.gate_decision === 'approved' && <span className="sd-approved">Approved</span>}
                {stage.gate_decision === 'rework_requested' && <span className="sd-rework">↩ Rework</span>}
                {isCurrent && !stage.gate_decision && <span className="sd-current">This Review</span>}
                {!isCurrent && !stage.gate_decision && <span className="sd-pending">Pending</span>}
              </div>
            </div>
            {idx < stages.length - 1 && <div className="stage-connector" />}
          </React.Fragment>
        );
      })}
    </div>
  );
}
```

---

## Шаг 13 — GateReviewPanel

**Файл:** `frontend/src/components/run/GateReviewPanel.jsx`

Занимает центр + правую зону RunWorkspace (таймлайн сворачивается в 48px).

Переиспользует логику из `HumanGate.jsx` без копирования — всё state управление одинаковое.

```jsx
export function GateReviewPanel({ runId, gate, nodeExecutions, onDecision }) {
  // State: changes, selectedPath, diffMode, reworkRequests, approveComment,
  //        submitting, editedByPath, keepChanges, viewedFiles — идентично HumanGate.jsx

  // API calls: approve(), requestRework() — идентично HumanGate.jsx

  return (
    <div className="gate-review-panel">
      <StageTracker nodeExecutions={nodeExecutions} currentGateId={gate.gate_id} />

      <div className="gate-review-body">
        {/* Левая: file tree */}
        <div className="gate-review-tree">
          <div className="gate-review-summary">
            {summary.files_changed} files &nbsp;
            <span style={{ color: '#52c41a' }}>+{summary.added_lines}</span>&nbsp;
            <span style={{ color: '#ff4d4f' }}>-{summary.removed_lines}</span>
          </div>
          <Progress
            percent={totalFiles > 0 ? Math.round(viewedCount / totalFiles * 100) : 0}
            size="small" showInfo={false} format={() => `${viewedCount}/${totalFiles}`}
          />
          <Tree treeData={treeData} selectedKeys={[selectedPath]}
            onSelect={([key]) => key && setSelectedPath(key)} />
        </div>

        {/* Правая: diff */}
        <div className="gate-review-diff">
          <div className="gate-review-diff-toolbar">
            <Segmented options={['side-by-side', 'unified']} value={diffMode} onChange={setDiffMode} />
            <span className="mono" style={{ fontSize: 12 }}>{selectedPath}</span>
            <Button size="small" onClick={() => toggleViewed(selectedPath)}>
              {viewedFiles.has(selectedPath) ? 'Unmark' : 'Mark viewed'}
            </Button>
          </div>
          {/* DiffEditor или Editor (идентично HumanGate.jsx) */}
        </div>
      </div>

      {/* Action panel */}
      <div className="gate-review-actions">
        <div className="action-approve">
          <Input.TextArea placeholder="Approve comment (optional)" value={approveComment}
            onChange={(e) => setApproveComment(e.target.value)} rows={2} />
          <Button type="primary" loading={submitting === 'approve'} onClick={approve}>
            Approve
          </Button>
        </div>
        <div className="action-rework">
          {reworkRequests.length > 0 && <Collapse size="small" items={reworkItems} />}
          <Button size="small" onClick={openAddReworkRequest}>+ Add from selection</Button>
          <Input.TextArea placeholder="General rework instruction" value={reworkInstruction}
            onChange={(e) => setReworkInstruction(e.target.value)} rows={2} />
          <Button loading={submitting === 'rework'} disabled={!canSubmitRework} onClick={requestRework}>
            Request Rework{reworkRequests.length > 0 ? ` (${reworkRequests.length})` : ''}
          </Button>
        </div>
      </div>
    </div>
  );
}
```

---

## Шаг 14 — RunWorkspace

**Файл:** `frontend/src/pages/RunWorkspace.jsx`

RunWorkspace — **только execution view**. Открывается с уже запущенным раном.

### State

```js
const [run, setRun] = useState(null);
const [nodeExecutions, setNodeExecutions] = useState([]);
const [workspaceMode, setWorkspaceMode] = useState('execute');
// execute | gate:input | gate:approval
const [selectedNodeExecId, setSelectedNodeExecId] = useState(null);
const [timelineCollapsed, setTimelineCollapsed] = useState(false);
const [flowYaml, setFlowYaml] = useState(null);
```

### Guard: только debug_mode

```jsx
useEffect(() => {
  if (run && !run.debug_mode) {
    navigate(`/run-console?runId=${runId}`, { replace: true });
  }
}, [run?.debug_mode]);
```

### Загрузка flow_yaml

```js
// flow_yaml нет в RunResponse — запрашиваем отдельно после получения run
useEffect(() => {
  if (!run?.flow_canonical_name) return;
  apiRequest(`/flows?canonical_name=${encodeURIComponent(run.flow_canonical_name)}`)
    .then((data) => setFlowYaml(data?.[0]?.flow_yaml ?? null));
}, [run?.flow_canonical_name]);
```

### Автопереключение режима

```jsx
useEffect(() => {
  if (!run?.current_gate) { setWorkspaceMode('execute'); return; }
  setWorkspaceMode(run.current_gate.gate_kind === 'human_input' ? 'gate:input' : 'gate:approval');
  if (run.current_gate.gate_kind === 'human_approval') setTimelineCollapsed(true);
}, [run?.current_gate?.gate_id]);
```

### Layout

```jsx
<div className={`run-workspace ${timelineCollapsed ? 'tl-collapsed' : ''}`}>

  {/* Шапка */}
  <div className="rw-header">
    <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/run-console')} />
    <span className="mono rw-run-id">{shortId(runId, 12)}</span>
    <RunStatusDot status={run?.status} />
    <span className="rw-flow">{run?.flow_canonical_name}</span>
    <Tag className="rw-debug-tag">Debug</Tag>
    <Space className="rw-actions">
      <Button icon={<ReloadOutlined />} onClick={load} loading={loading} />
      <Button danger disabled={!isActive} onClick={cancelRun}>Stop</Button>
    </Space>
  </div>

  {/* Gate banner */}
  {workspaceMode !== 'execute' && (
    <div className="rw-gate-banner">
      <WarningOutlined />
      {workspaceMode === 'gate:input'
        ? `Input required — ${run.current_gate.node_id}`
        : `Review required — ${run.current_gate.node_id}`}
    </div>
  )}

  {/* Body */}
  <div className="rw-body">

    {/* Таймлайн */}
    <div className="rw-timeline">
      <ExecutionTimeline
        nodeExecutions={nodeExecutions}
        selectedId={selectedNodeExecId}
        onSelect={setSelectedNodeExecId}
        collapsed={timelineCollapsed}
      />
    </div>

    {/* Граф (только в execute-режиме) */}
    {workspaceMode !== 'gate:approval' && (
      <div className="rw-graph">
        <LiveGraph
          flowYaml={flowYaml}
          nodeExecutions={nodeExecutions}
          onNodeClick={setSelectedNodeExecId}
        />
      </div>
    )}

    {/* Правая панель */}
    <div className={`rw-detail ${workspaceMode === 'gate:approval' ? 'is-fullwidth' : ''}`}>
      {workspaceMode === 'execute' && (
        <LiveLogViewer runId={runId} nodeExecutionId={logNodeExecId} />
      )}
      {workspaceMode === 'gate:input' && (
        <GateInputPanel runId={runId} gate={run.current_gate}
          onSubmitted={() => { load(); setWorkspaceMode('execute'); }} />
      )}
      {workspaceMode === 'gate:approval' && (
        <GateReviewPanel runId={runId} gate={run.current_gate} nodeExecutions={nodeExecutions}
          onDecision={() => { load(); setTimelineCollapsed(false); setWorkspaceMode('execute'); }} />
      )}
    </div>

  </div>
</div>
```

### CSS

```css
.run-workspace {
  display: grid;
  grid-template-rows: 48px auto 1fr;
  height: 100vh;
  overflow: hidden;
}
.rw-body {
  display: grid;
  grid-template-columns: 220px 1fr 420px;
  overflow: hidden;
  transition: grid-template-columns 0.25s ease;
}
.run-workspace.tl-collapsed .rw-body {
  grid-template-columns: 48px 1fr 420px;
}
/* approval mode: граф убран, review занимает всё */
.rw-detail.is-fullwidth { grid-column: 2 / -1; }

.rw-timeline  { overflow-y: auto; border-right: 1px solid var(--color-border); }
.rw-graph     { overflow: hidden; }
.rw-detail    { overflow-y: auto; border-left: 1px solid var(--color-border); }

.rw-debug-tag { background: #722ed1; color: #fff; border: none; font-size: 10px; }
.rw-gate-banner {
  background: #fffbe6;
  border-bottom: 1px solid #ffe58f;
  padding: 6px 16px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}
```

---

## Шаг 15 — Новые поля в NodeExecutionDto

Проверить, есть ли в ответе `GET /runs/{runId}/nodes`:
- `node_kind` — нужно для таймлайна и LiveGraph
- `started_at`, `finished_at` — нужно для таймера и длительности
- `gate_kind`, `gate_decision` — нужно для StageTracker

Если нет — добавить в `NodeExecutionResponse`.

---

## Шаг 16 — Роутинг

**Файл:** `frontend/src/App.jsx`

```jsx
import RunWorkspace from './pages/RunWorkspace.jsx';
// ...
<Route path="run-workspace" element={<RunWorkspace />} />
```

**AppShell.jsx** — добавить в `routeMeta`:

```js
'/run-workspace': { title: 'Debug Run', menuKey: '/run-console' },
```

---

## Порядок реализации

| # | Задача | Зависит от | Сложность |
|---|--------|-----------|-----------|
| 1 | Бэкенд: `debug_mode` поле + миграция | — | Низкая |
| 2 | `DebugRunDrawer.jsx` + обновить FlowEditor | 1 | Средняя |
| 3 | `RunLaunch.jsx` — Debug toggle | 1 | Низкая |
| 4 | `RunConsole.jsx` — кнопка Debug View | 1 | Низкая |
| 5 | `AppShell.jsx` — badge + navigate | 1 | Низкая |
| 6 | `utils/flowLayout.js` — вынести buildFlowPreview | — | Низкая |
| 7 | `FlowNode.jsx` — runStatus + CSS | — | Низкая |
| 8 | `LiveGraph.jsx` | 6, 7 | Средняя |
| 9 | `useNodeLog.js` hook | — | Низкая |
| 10 | `ExecutionTimeline.jsx` | — | Средняя |
| 11 | `LiveLogViewer.jsx` | 9 | Средняя |
| 12 | `GateInputPanel.jsx` | — | Средняя |
| 13 | `StageTracker.jsx` | — | Низкая |
| 14 | `GateReviewPanel.jsx` | 13 | Высокая |
| 15 | `RunWorkspace.jsx` — сборка | 8, 10, 11, 12, 14 | Высокая |
| 16 | CSS + роутинг + NodeExecutionDto | 15 | Средняя |

**Итерации:**

- **Итерация 1** (шаги 1–5): backend + entry points. DebugRunDrawer уже работает, открывает RunWorkspace (пустую страницу).
- **Итерация 2** (шаги 6–11): display-компоненты (граф, таймлайн, логи) — независимы, можно параллельно.
- **Итерация 3** (шаги 12–14): gate-компоненты.
- **Итерация 4** (шаги 15–16): сборка RunWorkspace + финальные детали.

---

## Открытые вопросы

| Вопрос | Решение для v1 |
|--------|---------------|
| `flow_yaml` для LiveGraph | `GET /flows?canonical_name=X` после загрузки run |
| `node_kind`, `gate_kind`, `gate_decision` в NodeExecutionDto | Проверить наличие; добавить если нет |
| `gate_id` в node execution | Нужен для StageTracker; проверить наличие |
| Клавиатурная навигация по файлам в GateReviewPanel | Перенести `[` / `]` логику из HumanGate.jsx |
| Мобильная версия | v1 — вертикальный стек, граф скрыт |
