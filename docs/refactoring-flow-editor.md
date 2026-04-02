# Рефакторинг FlowEditor.jsx

## Проблема

`FlowEditor.jsx` (~2587 LOC) — монолитный компонент. В нём живёт:
- утилиты для работы с версиями (строки 63-131): `parseMajorMinor`, `compareVersions`, `nextMajorVersion`, `getLatestVersion`, etc.
- `parseFlowYaml()`, `toNodeData()`, `buildEdges()` — сериализация/десериализация нодового графа
- `validateFlow()` (~300 строк) — полная клиентская валидация флоу
- `FlowNode` компонент — рендер одной ноды в ReactFlow
- `FlowEditor` компонент — 30+ useState, все обработчики событий, весь JSX (~1000 строк JSX)
- панель метаданных флоу (title, flowId, version, tags, etc.)
- панель редактирования ноды (instruction, skill refs, execution context, produced artifacts, transitions)
- YAML-просмотрщик (Monaco Editor)
- диалоги: создание ноды, выбор маршрута, публикация
- логика загрузки/сохранения через API

Каждое изменение в любой части требует понимания всего файла целиком.

---

## Что НЕ трогаем

- Логику валидации (`validateFlow`) — она правильная и хорошо написана, только переносим
- Визуальный вывод, CSS-классы — не меняем UI вообще
- API-вызовы — остаются в том же виде
- `FlowNode` компонент — можно оставить в файле или вынести — неприоритетно

---

## Целевая структура

```
frontend/src/pages/
  FlowEditor.jsx              ← сам компонент, только JSX + обработчики (~700 LOC)

frontend/src/utils/
  flowVersionUtils.js         ← версионные утилиты (уже частично дублируются в других компонентах?)
  flowSerializer.js           ← parseFlowYaml, toNodeData, buildEdges
  flowValidator.js            ← validateFlow

frontend/src/hooks/
  useFlowEditor.js            ← весь state + бизнес-логика FlowEditor

frontend/src/components/flow/
  FlowNode.jsx                ← компонент одной ноды
  NodeEditPanel.jsx           ← панель редактирования выбранной ноды
  FlowMetaPanel.jsx           ← панель метаданных флоу (title, version, tags, etc.)
```

---

## Этапы

### Этап 1 — Вынести `flowVersionUtils.js`

**Функции для переноса (строки 63-131):**
```js
parseMajorMinor(version)
compareVersions(a, b)
nextMajorVersion(version)
nextMinorVersion(version)
getLatestVersion(versions, status)
getMaxPublishedMajor(versions)
getMaxPublishedMinorForMajor(versions, major)
getDraftForMajor(versions, major)
```

**Создать:** `frontend/src/utils/flowVersionUtils.js`

```js
export const DEFAULT_VERSION = '0.1';
export const parseMajorMinor = (version) => { ... };
// ... остальные функции
```

**В FlowEditor.jsx:** заменить определения на импорт:
```js
import { parseMajorMinor, compareVersions, getLatestVersion, ... } from '../utils/flowVersionUtils.js';
```

**Риски:** нет — чистый перенос pure functions без side effects.

---

### Этап 2 — Вынести `flowSerializer.js`

**Функции для переноса (строки 226-329):**
```js
buildEdges(nodes)
toNodeData(node, isStart)
parseFlowYaml(flowYaml, startNodeId)
```

**Создать:** `frontend/src/utils/flowSerializer.js`

```js
export const buildEdges = (nodes) => { ... };
export const toNodeData = (node, isStart) => { ... };
export const parseFlowYaml = (flowYaml, startNodeId) => { ... };
```

**В FlowEditor.jsx:** импорт этих трёх функций.

**Риски:** `parseFlowYaml` использует `parseYaml` из библиотеки `yaml` — импорт переедет в утилиту. Нет side effects.

---

### Этап 3 — Вынести `flowValidator.js`

**Функция для переноса (строки 331-624):**
```js
validateFlow(nodes, meta, rulesCatalog, skillsCatalog)
```

Это самая большая (~300 строк), но хорошо написанная pure function — только принимает данные, возвращает `string[]`.

**Создать:** `frontend/src/utils/flowValidator.js`

```js
import { buildEdges } from './flowSerializer.js'; // уже переехал в этапе 2

export const validateFlow = (nodes, meta, rulesCatalog, skillsCatalog) => { ... };
```

**Примечание:** `validateFlow` использует `buildEdges` для reachability check. После этапа 2 оба уже в `utils/`, зависимость между ними — ок.

**Риски:** нет — pure function, никаких внешних зависимостей.

---

### Этап 4 — Вынести `FlowNode.jsx`

**Что переносим (строки 160-179):**
```jsx
function FlowNode({ data, selected }) { ... }
const nodeTypes = { flowNode: FlowNode };
```

**Создать:** `frontend/src/components/flow/FlowNode.jsx`

```jsx
import { Handle, Position } from 'reactflow';
// NODE_KIND_META тоже переезжает сюда
export function FlowNode({ data, selected }) { ... }
export const nodeTypes = { flowNode: FlowNode };
```

**В FlowEditor.jsx:** `import { FlowNode, nodeTypes } from '../components/flow/FlowNode.jsx';`

**Риски:** нет — самодостаточный компонент без state.

---

### Этап 5 — Выделить `useFlowEditor` хук

Это наибольший по объёму шаг. Переносим весь state и обработчики из тела `FlowEditor()` в хук.

**Создать:** `frontend/src/hooks/useFlowEditor.js`

```js
export function useFlowEditor({ flowId, isCreateMode }) {
  // все useState (30+ штук)
  const [flowMeta, setFlowMeta] = useState(emptyFlow);
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  // ... остальные

  // все derived values
  const edges = useMemo(() => buildEdges(nodes), [nodes]);
  const validationErrors = validateFlow(nodes, flowMeta, rulesCatalog, skillsCatalog);
  // ...

  // все обработчики
  const updateSelectedNode = (updates) => { ... };
  const renameSelectedNodeId = (nextId) => { ... };
  const handleSave = async () => { ... };
  const handlePublish = async () => { ... };
  // ...

  return {
    // state
    flowMeta, nodes, selectedNode, selectedNodeId, edges,
    // derived
    validationErrors, isReadOnly, ruleOptions, skillOptions,
    // handlers
    updateSelectedNode, renameSelectedNodeId, handleSave, handlePublish,
    setSelectedNodeId, onNodesChange,
    // refs
    flowWrapperRef, flowInstance, setFlowInstance,
    // dialog state
    publishDialogOpen, setPublishDialogOpen, publishVariant, setPublishVariant,
    // ... всё что нужно JSX
  };
}
```

**FlowEditor.jsx после:**
```jsx
export default function FlowEditor() {
  const { flowId } = useParams();
  const isCreateMode = flowId === 'create' || ...;
  const editor = useFlowEditor({ flowId, isCreateMode });

  return (
    <div>
      <FlowMetaPanel editor={editor} />
      <ReactFlow nodes={editor.nodes} edges={editor.edges} ... />
      {editor.selectedNode && <NodeEditPanel editor={editor} />}
    </div>
  );
}
```

**Риски:**
- Большой перенос — делать в один коммит после Этапов 1-4
- `useNodesState` из reactflow должен остаться в хуке (не в компоненте)
- Нужно внимательно передать все `ref`-значения через возвращаемый объект
- После переноса проверить что `useCallback`/`useMemo` работают корректно в новом контексте

---

### Этап 6 — Выделить `NodeEditPanel.jsx` (опционально)

Панель редактирования выбранной ноды — правая часть интерфейса с instruction, skillRefs, executionContext, producedArtifacts, expectedMutations, transitions.

Это самый большой кусок JSX (~600-800 строк условной разметки). После Этапа 5 выделяется просто:

```jsx
// components/flow/NodeEditPanel.jsx
export function NodeEditPanel({ editor }) {
  const { selectedNode, updateSelectedNode, ... } = editor;
  // весь JSX правой панели
}
```

**Делать только после Этапа 5** — когда всё state уже в хуке и пробрасывается через `editor`.

---

### Этап 7 — Выделить `FlowMetaPanel.jsx` (опционально)

Панель метаданных флоу сверху/слева: title, flowId, version selector, coding agent, tags, platform, flow kind, risk level, scope, rule refs, publish button.

Аналогично Этапу 6 — только после Этапа 5.

---

## Порядок выполнения

```
Этап 1: flowVersionUtils.js   — pure utils, нет рисков
Этап 2: flowSerializer.js     — pure utils, нет рисков
Этап 3: flowValidator.js      — pure utils, нет рисков (зависит от Этапа 2)
Этап 4: FlowNode.jsx          — standalone компонент
Этап 5: useFlowEditor.js      ← главный шаг, после 1-4
Этап 6: NodeEditPanel.jsx     — после Этапа 5 (по желанию)
Этап 7: FlowMetaPanel.jsx     — после Этапа 5 (по желанию)
```

Этапы 1-4 независимы, их можно делать в любом порядке. Каждый — отдельный коммит.

---

## Итог по размерам

| Файл | До | После |
|------|----|-------|
| FlowEditor.jsx | 2587 LOC | ~400-500 LOC (JSX + точка входа) |
| flowVersionUtils.js | — | ~80 LOC |
| flowSerializer.js | — | ~110 LOC |
| flowValidator.js | — | ~300 LOC |
| useFlowEditor.js | — | ~500-600 LOC |
| FlowNode.jsx | — | ~30 LOC |
| NodeEditPanel.jsx | — | ~600 LOC (опц.) |
| FlowMetaPanel.jsx | — | ~200 LOC (опц.) |

Этапы 1-5 обязательные, 6-7 — по желанию. После Этапа 5 FlowEditor уже значительно легче читать и модифицировать.

---

## Риски

| Риск | Митигация |
|------|-----------|
| `useMemo`/`useCallback` в хуке — изменение контекста вызова | Проверить зависимости у каждого хука при переносе |
| `flowWrapperRef` и `flowInstance` нужны в ReactFlow — передача через return объекта | Передавать ref-значения напрямую, не оборачивать |
| Дублирование `DEFAULT_VERSION` между файлами | После Этапа 1 вынести в `flowVersionUtils.js` и импортировать везде |
| Тесты отсутствуют для FlowEditor | Этапы 1-3 создают тестируемые pure functions — добавить тесты для них |
