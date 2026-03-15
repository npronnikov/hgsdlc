import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Background,
  Controls,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
  useNodesState,
} from 'reactflow';
import 'reactflow/dist/style.css';
import {
  Button,
  Card,
  Dropdown,
  Divider,
  Input,
  List,
  Modal,
  Select,
  Space,
  Switch,
  Typography,
  message,
} from 'antd';
import { DeleteOutlined, MoreOutlined, PlusOutlined } from '@ant-design/icons';
import { rules, skills } from '../data/mock.js';
import { useLocation, useParams } from 'react-router-dom';
import { apiRequest } from '../api/request.js';
import { toRussianError } from '../utils/errorMessages.js';

const { Title, Text } = Typography;

const NODE_KIND_META = {
  ai: {
    label: 'AI executor',
    variant: 'executor ai',
    type: 'executor',
    executionMode: 'agent',
  },
  command: {
    label: 'Command executor',
    variant: 'executor command',
    type: 'executor',
    executionMode: 'command',
  },
  human_input: {
    label: 'Human input gate',
    variant: 'gate input',
    type: 'gate',
    executionMode: 'human_input',
  },
  human_approval: {
    label: 'Human approval gate',
    variant: 'gate approval',
    type: 'gate',
    executionMode: 'human_approval',
  },
  terminal: {
    label: 'Terminal',
    variant: 'terminal',
    type: 'terminal',
    executionMode: 'terminal',
  },
};

const EXECUTION_CONTEXT_TYPES = [
  { value: 'user_request', label: 'user_request' },
  { value: 'directory_ref', label: 'directory_ref' },
  { value: 'file_ref', label: 'file_ref' },
  { value: 'artifact_ref', label: 'artifact_ref' },
];

const NODE_TYPE_OPTIONS = [
  { key: 'ai', label: 'AI executor' },
  { key: 'command', label: 'Command executor' },
  { key: 'human_input', label: 'Human input gate' },
  { key: 'human_approval', label: 'Human approval gate' },
  { key: 'terminal', label: 'Terminal' },
];

function FlowNode({ data, selected }) {
  const visuals = NODE_KIND_META[data.nodeKind] || {};
  return (
    <div className={`flow-node ${visuals.variant || data.variant || ''} ${selected ? 'is-selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <div className="flow-node-header">
        <div>
          <div className="flow-node-title">{data.title}</div>
          <div className="flow-node-meta">{visuals.label || data.typeLabel}</div>
        </div>
        {data.isStart && <span className="flow-node-start">Start</span>}
      </div>
      <div className="flow-node-id">{data.id}</div>
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

const nodeTypes = { flowNode: FlowNode };

const ARTIFACT_PATH_PREFIX = '.hgwork/{runId}/{nodeId}/artifacts/';

const initialFlow = {
  title: 'Flow изменений',
  flowId: 'feature-change-flow',
  description: 'Flow реализации изменений с этапом согласования.',
  status: 'draft',
  startNodeId: 'intake-analysis',
  codingAgent: 'qwen',
  ruleRefs: ['project-rule@1.0.2'],
  failOnMissingDeclaredOutput: true,
  failOnMissingExpectedMutation: true,
  responseSchema: '',
};

const emptyFlow = {
  title: '',
  flowId: '',
  description: '',
  status: 'draft',
  startNodeId: '',
  codingAgent: '',
  ruleRefs: [],
  failOnMissingDeclaredOutput: false,
  failOnMissingExpectedMutation: false,
  responseSchema: '',
};

const initialNodes = [
  {
    id: 'intake-analysis',
    type: 'flowNode',
    position: { x: 40, y: 80 },
    data: {
      id: 'intake-analysis',
      title: 'Анализ запроса',
      description: 'Анализирует запрос и формирует вопросы.',
      nodeKind: 'ai',
      type: 'executor',
      executionMode: 'agent',
      executionContext: [
        { type: 'user_request', required: true },
        { type: 'directory_ref', path: 'docs/requirements', required: true },
      ],
      instruction: 'Проанализировать запрос и подготовить уточняющие вопросы.',
      skillRefs: ['update-requirements@1.2.0'],
      responseSchema: '',
      producedArtifacts: [
        { path: '.hgwork/{runId}/{nodeId}/artifacts/questions.md', required: true },
      ],
      expectedMutations: [],
      onSuccess: 'collect-answers',
      isStart: true,
    },
  },
  {
    id: 'collect-answers',
    type: 'flowNode',
    position: { x: 360, y: 40 },
    data: {
      id: 'collect-answers',
      title: 'Сбор ответов',
      description: 'Сбор ответов владельца продукта.',
      nodeKind: 'human_input',
      type: 'gate',
      executionMode: 'human_input',
      executionContext: [
        { type: 'artifact_ref', path: '.hgwork/{runId}/intake-analysis/artifacts/questions.md', required: true },
      ],
      instruction: '',
      skillRefs: [],
      responseSchema: '',
      producedArtifacts: [
        { path: '.hgwork/{runId}/{nodeId}/artifacts/answers.md', required: true },
      ],
      expectedMutations: [],
      onSubmit: 'process-answers',
    },
  },
  {
    id: 'process-answers',
    type: 'flowNode',
    position: { x: 660, y: 80 },
    data: {
      id: 'process-answers',
      title: 'Обработать ответы',
      description: 'Обновить требования на основе ответов.',
      nodeKind: 'ai',
      type: 'executor',
      executionMode: 'agent',
      executionContext: [
        { type: 'user_request', required: true },
        { type: 'artifact_ref', path: '.hgwork/{runId}/collect-answers/artifacts/answers.md', required: true },
      ],
      instruction: 'Обновить требования на основе ответов.',
      skillRefs: ['update-requirements@1.2.0'],
      responseSchema: '',
      producedArtifacts: [],
      expectedMutations: [
        { path: 'docs/requirements/**', required: true },
      ],
      onSuccess: 'approve-requirements',
    },
  },
  {
    id: 'approve-requirements',
    type: 'flowNode',
    position: { x: 980, y: 80 },
    data: {
      id: 'approve-requirements',
      title: 'Согласовать требования',
      description: 'Проверить обновлённые требования.',
      nodeKind: 'human_approval',
      type: 'gate',
      executionMode: 'human_approval',
      executionContext: [
        { type: 'directory_ref', path: 'docs/requirements', required: true },
      ],
      instruction: '',
      skillRefs: [],
      responseSchema: '',
      producedArtifacts: [
        { path: '.hgwork/{runId}/{nodeId}/artifacts/approval-comment.md', required: false },
      ],
      expectedMutations: [],
      onApprove: 'close-run',
      onReject: 'close-run',
      onReworkRoutes: {
        keep_workspace: 'process-answers',
      },
    },
  },
  {
    id: 'close-run',
    type: 'flowNode',
    position: { x: 1280, y: 80 },
    data: {
      id: 'close-run',
      title: 'Завершить запуск',
      description: 'Завершение flow.',
      nodeKind: 'command',
      type: 'executor',
      executionMode: 'command',
      executionContext: [],
      instruction: '',
      skillRefs: [],
      responseSchema: '',
      producedArtifacts: [],
      expectedMutations: [],
      onSuccess: null,
    },
  },
];

function extractArtifactName(path) {
  if (!path) {
    return '';
  }
  const lastSlashIndex = path.lastIndexOf('/');
  return lastSlashIndex >= 0 ? path.slice(lastSlashIndex + 1) : path;
}

function buildArtifactPath(name) {
  const trimmed = (name || '').trim();
  if (!trimmed) {
    return '';
  }
  return `${ARTIFACT_PATH_PREFIX}${trimmed}`;
}

function buildEdges(nodes) {
  const edges = [];
  const nodeIds = new Set(nodes.map((node) => node.id));
  const addEdge = (source, target, label, routeType) => {
    if (!source || !target || !nodeIds.has(target)) {
      return;
    }
    edges.push({
      id: `${source}-${target}-${label}`,
      source,
      target,
      label,
      type: 'smoothstep',
      markerEnd: { type: MarkerType.ArrowClosed },
      className: `edge-${routeType}`,
      data: { routeType },
    });
  };

  nodes.forEach((node) => {
    const data = node.data || {};
    if (data.onSuccess) {
      addEdge(node.id, data.onSuccess, 'on_success', 'main');
    }
    if (data.onFailure) {
      addEdge(node.id, data.onFailure, 'on_failure', 'outcome');
    }
    if (data.onSubmit) {
      addEdge(node.id, data.onSubmit, 'on_submit', 'main');
    }
    if (data.onApprove) {
      addEdge(node.id, data.onApprove, 'on_approve', 'main');
    }
    if (data.onReject) {
      addEdge(node.id, data.onReject, 'on_reject', 'outcome');
    }
    if (data.onReworkRoutes) {
      Object.entries(data.onReworkRoutes).forEach(([mode, target]) => {
        addEdge(node.id, target, `rework: ${mode}`, 'rework');
      });
    }
  });

  return edges;
}

function validateFlow(nodes, meta) {
  const errors = [];
  const nodeIds = nodes.map((node) => node.id);
  const uniqueIds = new Set(nodeIds);
  if (nodes.length === 0) {
    errors.push('Flow не содержит нод.');
  }
  if (!meta.startNodeId) {
    errors.push('start_node_id не задан.');
  } else if (!uniqueIds.has(meta.startNodeId)) {
    errors.push(`start_node_id не найден: ${meta.startNodeId}`);
  }
  if (!meta.codingAgent) {
    errors.push('coding_agent не задан.');
  }
  if (uniqueIds.size !== nodeIds.length) {
    const seen = new Set();
    nodeIds.forEach((id) => {
      if (seen.has(id)) {
        errors.push(`Дублирующийся ID ноды: ${id}`);
      }
      seen.add(id);
    });
  }

  const rulesByCanonical = new Map(rules.map((rule) => [rule.canonical, rule]));
  const skillsByCanonical = new Map(skills.map((skill) => [skill.canonical, skill]));
  meta.ruleRefs.forEach((ref) => {
    const rule = rulesByCanonical.get(ref);
    if (!rule) {
      errors.push(`Rule ref не найден: ${ref}`);
      return;
    }
    if (rule.status !== 'published') {
      errors.push(`Rule ref не опубликован: ${ref}`);
    }
    if (meta.codingAgent && rule.codingAgent !== meta.codingAgent) {
      errors.push(`Rule ref не соответствует coding_agent: ${ref}`);
    }
  });

  nodes.forEach((node) => {
    const data = node.data || {};
    if (!data.nodeKind) {
      errors.push(`node_kind не задан: ${node.id}`);
    }
    if (!data.executionMode) {
      errors.push(`execution_mode не задан: ${node.id}`);
    }
    if (data.skillRefs && data.skillRefs.length > 0 && data.nodeKind !== 'ai') {
      errors.push(`skill_refs разрешены только для AI нод: ${node.id}`);
    }
    if (data.skillRefs) {
      data.skillRefs.forEach((ref) => {
        const skill = skillsByCanonical.get(ref);
        if (!skill) {
          errors.push(`Skill ref не найден: ${ref}`);
          return;
        }
        if (skill.status !== 'published') {
          errors.push(`Skill ref не опубликован: ${ref}`);
        }
        if (meta.codingAgent && skill.codingAgent !== meta.codingAgent) {
          errors.push(`Skill ref не соответствует coding_agent: ${ref}`);
        }
      });
    }

    if (!Array.isArray(data.executionContext)) {
      errors.push(`execution_context не задан: ${node.id}`);
    } else {
      data.executionContext.forEach((entry) => {
        if (!entry?.type) {
          errors.push(`execution_context type не задан: ${node.id}`);
          return;
        }
        if (!EXECUTION_CONTEXT_TYPES.some((item) => item.value === entry.type)) {
          errors.push(`execution_context type не поддерживается: ${node.id}`);
        }
        if (entry.required === undefined || entry.required === null) {
          errors.push(`execution_context required не задан: ${node.id}`);
        }
        if (entry.type !== 'user_request' && !entry.path) {
          errors.push(`execution_context path не задан: ${node.id}`);
        }
        if (entry.type === 'user_request' && entry.path) {
          errors.push(`user_request не должен иметь path: ${node.id}`);
        }
      });
    }

    const checkPathList = (items, label) => {
      if (!Array.isArray(items)) return;
      items.forEach((item) => {
        if (!item) {
          errors.push(`${label} entry не задан: ${node.id}`);
          return;
        }
        if (item.required === undefined || item.required === null) {
          errors.push(`${label} required не задан: ${node.id}`);
        }
        if (!item.path) {
          errors.push(`${label} path не задан: ${node.id}`);
        }
      });
    };
    checkPathList(data.producedArtifacts, 'produced_artifacts');
    checkPathList(data.expectedMutations, 'expected_mutations');

    const transitions = [];
    if (data.onSuccess) transitions.push(['on_success', data.onSuccess]);
    if (data.onFailure) transitions.push(['on_failure', data.onFailure]);
    if (data.onSubmit) transitions.push(['on_submit', data.onSubmit]);
    if (data.onApprove) transitions.push(['on_approve', data.onApprove]);
    if (data.onReject) transitions.push(['on_reject', data.onReject]);
    if (data.onReworkRoutes) {
      Object.entries(data.onReworkRoutes).forEach(([key, value]) => transitions.push([`rework:${key}`, value]));
    }
    transitions.forEach(([label, target]) => {
      if (target && !uniqueIds.has(target)) {
        errors.push(`Невалидный переход ${label} из ${node.id} -> ${target}`);
      }
    });

    if (data.nodeKind === 'terminal' || data.type === 'terminal') {
      if (transitions.length > 0) {
        errors.push(`Terminal нода не может иметь переходов: ${node.id}`);
      }
      return;
    }

    if (data.type === 'executor') {
      if (!data.onSuccess) {
        errors.push(`Executor нода требует on_success: ${node.id}`);
      }
    }

    if (data.nodeKind === 'human_input') {
      if (!data.onSubmit) {
        errors.push(`human_input требует on_submit: ${node.id}`);
      }
    }

    if (data.nodeKind === 'human_approval') {
      if (!data.onApprove) {
        errors.push(`human_approval требует on_approve: ${node.id}`);
      }
      if (!data.onReject) {
        errors.push(`human_approval требует on_reject: ${node.id}`);
      }
      if (!data.onReworkRoutes || Object.keys(data.onReworkRoutes).length === 0) {
        errors.push(`human_approval требует on_rework_routes: ${node.id}`);
      } else {
        Object.entries(data.onReworkRoutes).forEach(([mode, target]) => {
          if (!target) {
            errors.push(`Невалидный rework route ${mode}: ${node.id}`);
          }
        });
      }
    }
  });

  if (meta.startNodeId && uniqueIds.has(meta.startNodeId)) {
    const edges = buildEdges(nodes);
    const adjacency = new Map();
    edges.forEach((edge) => {
      if (!adjacency.has(edge.source)) adjacency.set(edge.source, []);
      adjacency.get(edge.source).push(edge.target);
    });
    const visited = new Set([meta.startNodeId]);
    const queue = [meta.startNodeId];
    while (queue.length) {
      const current = queue.shift();
      const nextNodes = adjacency.get(current) || [];
      nextNodes.forEach((next) => {
        if (!visited.has(next)) {
          visited.add(next);
          queue.push(next);
        }
      });
    }
    nodeIds.forEach((id) => {
      if (!visited.has(id)) {
        errors.push(`Недостижимая нода: ${id}`);
      }
    });
  }

  return errors;
}

export default function FlowEditor() {
  const { flowId } = useParams();
  const location = useLocation();
  const isCreateMode = flowId === 'create' || location.pathname.endsWith('/flows/create');
  const [flowMeta, setFlowMeta] = useState(isCreateMode ? emptyFlow : initialFlow);
  const [resourceVersion, setResourceVersion] = useState(0);
  const [flowVersion, setFlowVersion] = useState(isCreateMode ? '0.1' : '0.1');
  const [currentStatus, setCurrentStatus] = useState(isCreateMode ? 'draft' : '');
  const [showYaml, setShowYaml] = useState(false);
  const [nodes, setNodes, onNodesChange] = useNodesState(isCreateMode ? [] : initialNodes);
  const [selectedNodeId, setSelectedNodeId] = useState(
    isCreateMode ? null : (initialNodes[0]?.id || null),
  );
  const [flowInstance, setFlowInstance] = useState(null);
  const flowWrapperRef = useRef(null);
  const [pendingConnection, setPendingConnection] = useState(null);
  const [routeOptions, setRouteOptions] = useState([]);
  const [routeChoice, setRouteChoice] = useState(null);
  const [reworkMode, setReworkMode] = useState('');
  const [reworkTarget, setReworkTarget] = useState('');
  const [nodeIdDraft, setNodeIdDraft] = useState('');
  const [contextMenu, setContextMenu] = useState(null);
  const [isEditing, setIsEditing] = useState(false);
  const flowVersionLabel = currentStatus === 'draft' || isCreateMode
    ? 'черновик'
    : (flowVersion || '0.0.0');
  const edges = useMemo(() => buildEdges(nodes), [nodes]);
  const nodeIdOptions = useMemo(
    () => nodes.map((node) => ({ value: node.id, label: node.id })),
    [nodes]
  );
  const selectedNode = nodes.find((node) => node.id === selectedNodeId);
  const validationErrors = validateFlow(nodes, flowMeta);
  const isReadOnly = !isEditing;
  const canEditNodeId = flowMeta.status === 'draft' && isEditing;

  const filteredRules = rules.filter(
    (rule) => !flowMeta.codingAgent || rule.codingAgent === flowMeta.codingAgent
  );
  const ruleOptions = filteredRules.map((rule) => ({
    value: rule.canonical,
    label: `${rule.name} · ${rule.version}`,
    disabled: rule.status !== 'published',
  }));

  const filteredSkills = skills.filter(
    (skill) => !flowMeta.codingAgent || skill.codingAgent === flowMeta.codingAgent
  );
  const skillOptions = filteredSkills.map((skill) => ({
    value: skill.canonical,
    label: `${skill.name} · ${skill.version}`,
    disabled: skill.status !== 'published',
  }));

  const updateSelectedNode = (updates) => {
    if (!selectedNodeId) {
      return;
    }
    setNodes((prev) =>
      prev.map((node) =>
        node.id === selectedNodeId
          ? { ...node, data: { ...node.data, ...updates } }
          : node
      )
    );
  };

  const renameSelectedNodeId = (nextId) => {
    if (!selectedNodeId) {
      return;
    }
    const trimmed = nextId.trim();
    if (!trimmed) {
      return;
    }
    if (trimmed === selectedNodeId) {
      return;
    }
    const exists = nodes.some((node) => node.id === trimmed);
    if (exists) {
      message.error(`ID ноды уже используется: ${trimmed}`);
      return;
    }
    setNodes((prev) =>
      prev.map((node) => {
        if (node.id === selectedNodeId) {
          return { ...node, id: trimmed, data: { ...node.data, id: trimmed } };
        }
        const data = node.data || {};
        const nextData = { ...data };
        if (data.onSuccess === selectedNodeId) nextData.onSuccess = trimmed;
        if (data.onFailure === selectedNodeId) nextData.onFailure = trimmed;
        if (data.onSubmit === selectedNodeId) nextData.onSubmit = trimmed;
        if (data.onApprove === selectedNodeId) nextData.onApprove = trimmed;
        if (data.onReject === selectedNodeId) nextData.onReject = trimmed;
        if (data.onReworkRoutes) {
          nextData.onReworkRoutes = Object.fromEntries(
            Object.entries(data.onReworkRoutes).map(([key, value]) => [
              key,
              value === selectedNodeId ? trimmed : value,
            ])
          );
        }
        return { ...node, data: nextData };
      })
    );
    if (flowMeta.startNodeId === selectedNodeId) {
      updateFlowMeta({ startNodeId: trimmed });
    }
    setSelectedNodeId(trimmed);
  };

  const updateSelectedNodeList = (key, index, updates) => {
    if (!selectedNode) {
      return;
    }
    const current = selectedNode.data[key] || [];
    const next = current.map((item, idx) => (idx === index ? { ...item, ...updates } : item));
    updateSelectedNode({ [key]: next });
  };

  const addSelectedNodeListItem = (key, item) => {
    if (!selectedNode) {
      return;
    }
    const current = selectedNode.data[key] || [];
    updateSelectedNode({ [key]: [...current, item] });
  };

  const removeSelectedNodeListItem = (key, index) => {
    if (!selectedNode) {
      return;
    }
    const current = selectedNode.data[key] || [];
    updateSelectedNode({ [key]: current.filter((_, idx) => idx !== index) });
  };

  const updateFlowMeta = (updates) => {
    setFlowMeta((prev) => ({ ...prev, ...updates }));
  };

  const removeNodeById = (nodeId) => {
    if (isReadOnly) {
      return;
    }
    setNodes((prev) =>
      prev
        .filter((node) => node.id !== nodeId)
        .map((node) => {
          const data = node.data || {};
          const nextData = { ...data };
          if (data.onSuccess === nodeId) nextData.onSuccess = '';
          if (data.onFailure === nodeId) nextData.onFailure = '';
          if (data.onSubmit === nodeId) nextData.onSubmit = '';
          if (data.onApprove === nodeId) nextData.onApprove = '';
          if (data.onReject === nodeId) nextData.onReject = '';
          if (data.onReworkRoutes) {
            nextData.onReworkRoutes = Object.fromEntries(
              Object.entries(data.onReworkRoutes).filter(([, value]) => value !== nodeId)
            );
          }
          return { ...node, data: nextData };
        })
    );
    if (flowMeta.startNodeId === nodeId) {
      updateFlowMeta({ startNodeId: '' });
    }
    if (selectedNodeId === nodeId) {
      setSelectedNodeId(null);
    }
  };

  const getRouteOptions = (node) => {
    const data = node?.data || {};
    if (data.nodeKind === 'ai') {
      return [
        { value: 'on_success', label: 'on_success' },
        { value: 'on_failure', label: 'on_failure' },
      ];
    }
    if (data.nodeKind === 'command') {
      return [
        { value: 'on_success', label: 'on_success' },
        { value: 'on_failure', label: 'on_failure' },
      ];
    }
    if (data.nodeKind === 'human_input') {
      return [{ value: 'on_submit', label: 'on_submit' }];
    }
    if (data.nodeKind === 'human_approval') {
      return [
        { value: 'on_approve', label: 'on_approve' },
        { value: 'on_reject', label: 'on_reject' },
        { value: 'rework:keep_workspace', label: 'rework: keep_workspace' },
        { value: 'rework:discard_uncommitted', label: 'rework: discard_uncommitted' },
      ];
    }
    if (data.nodeKind === 'terminal') {
      return [];
    }
    return [
      { value: 'on_success', label: 'on_success' },
      { value: 'on_failure', label: 'on_failure' },
    ];
  };

  const applyConnection = (sourceId, targetId, routeKey) => {
    if (!sourceId || !targetId || !routeKey) {
      return;
    }
    setNodes((prev) =>
      prev.map((node) => {
        if (node.id !== sourceId) {
          return node;
        }
        const data = { ...node.data };
        if (routeKey === 'on_success') {
          data.onSuccess = targetId;
        } else if (routeKey === 'on_failure') {
          data.onFailure = targetId;
        } else if (routeKey === 'on_submit') {
          data.onSubmit = targetId;
        } else if (routeKey === 'on_approve') {
          data.onApprove = targetId;
        } else if (routeKey === 'on_reject') {
          data.onReject = targetId;
        } else if (routeKey.startsWith('rework:')) {
          const mode = routeKey.split(':')[1];
          data.onReworkRoutes = { ...(data.onReworkRoutes || {}) };
          data.onReworkRoutes[mode] = targetId;
        }
        return { ...node, data };
      })
    );
  };

  const removeConnection = (edge) => {
    if (!edge?.source || !edge?.label) {
      return;
    }
    const label = edge.label;
    setNodes((prev) =>
      prev.map((node) => {
        if (node.id !== edge.source) {
          return node;
        }
        const data = { ...node.data };
        if (label === 'on_success') {
          data.onSuccess = null;
        } else if (label === 'on_failure') {
          data.onFailure = null;
        } else if (label === 'on_submit') {
          data.onSubmit = null;
        } else if (label === 'on_approve') {
          data.onApprove = null;
        } else if (label === 'on_reject') {
          data.onReject = null;
        } else if (label.startsWith('rework:')) {
          const mode = label.split(':')[1].trim();
          if (data.onReworkRoutes) {
            const nextRoutes = { ...data.onReworkRoutes };
            delete nextRoutes[mode];
            data.onReworkRoutes = nextRoutes;
          }
        }
        return { ...node, data };
      })
    );
  };

  const buildNodeData = (kind, id) => {
    const meta = NODE_KIND_META[kind] || NODE_KIND_META.ai;
    return {
      id,
      title: `Новая ${meta.label}`,
      description: '',
      nodeKind: kind,
      type: meta.type,
      executionMode: meta.executionMode,
      executionContext: [],
      instruction: '',
      skillRefs: [],
      responseSchema: '',
      producedArtifacts: [],
      expectedMutations: [],
      onSuccess: '',
      onFailure: '',
      onSubmit: '',
      onApprove: '',
      onReject: '',
      onReworkRoutes: {},
    };
  };

  const addNode = (kind, positionOverride) => {
    if (isReadOnly) {
      return;
    }
    setNodes((prev) => {
      const base = kind === 'ai'
        ? 'ai'
        : kind === 'command'
          ? 'command'
          : kind === 'human_input'
            ? 'input'
            : 'approval';
      let index = prev.length + 1;
      let id = `${base}-${index}`;
      const existing = new Set(prev.map((node) => node.id));
      while (existing.has(id)) {
        index += 1;
        id = `${base}-${index}`;
      }
      let position = positionOverride || { x: 120, y: 120 };
      if (!positionOverride && flowInstance && flowWrapperRef.current) {
        const bounds = flowWrapperRef.current.getBoundingClientRect();
        const center = {
          x: bounds.left + bounds.width / 2,
          y: bounds.top + bounds.height / 2,
        };
        position = flowInstance.project(center);
      }
      const nextNode = {
        id,
        type: 'flowNode',
        position,
        data: buildNodeData(kind, id),
      };
      setSelectedNodeId(id);
      return [...prev, nextNode];
    });
  };

  const loadFlow = async (id) => {
    try {
      const data = await apiRequest(`/flows/${id}`);
      setFlowMeta((prev) => ({
        ...prev,
        flowId: data.flow_id || id,
        title: data.title || prev.title,
        description: data.description || prev.description,
        status: data.status || prev.status,
        startNodeId: data.start_node_id || prev.startNodeId,
        codingAgent: data.coding_agent || prev.codingAgent,
        ruleRefs: data.rule_refs || [],
        failOnMissingDeclaredOutput: data.fail_on_missing_declared_output ?? prev.failOnMissingDeclaredOutput,
        failOnMissingExpectedMutation: data.fail_on_missing_expected_mutation ?? prev.failOnMissingExpectedMutation,
        responseSchema: data.response_schema ? JSON.stringify(data.response_schema, null, 2) : prev.responseSchema,
      }));
      setFlowVersion(data.version || flowVersion);
      setCurrentStatus(data.status || '');
      setResourceVersion(data.resource_version ?? 0);
    } catch (err) {
      message.error(err.message || 'Не удалось загрузить Flow');
    }
  };

  const buildFlowYaml = () => {
    const version = flowVersion || '0.1';
    const canonicalName = `${flowMeta.flowId}@${version}`;
    const lines = [
      `id: ${flowMeta.flowId}`,
      `version: "${version}"`,
      `canonical_name: ${canonicalName}`,
      `title: ${flowMeta.title}`,
      `description: ${flowMeta.description || ''}`,
      `status: ${flowMeta.status || 'draft'}`,
      `start_node_id: ${flowMeta.startNodeId}`,
      `coding_agent: ${flowMeta.codingAgent || ''}`,
    ];
    lines.push(`fail_on_missing_declared_output: ${!!flowMeta.failOnMissingDeclaredOutput}`);
    lines.push(`fail_on_missing_expected_mutation: ${!!flowMeta.failOnMissingExpectedMutation}`);
    if (flowMeta.ruleRefs.length === 0) {
      lines.push('rule_refs: []');
    } else {
      lines.push('rule_refs:');
      flowMeta.ruleRefs.forEach((ref) => lines.push(`  - ${ref}`));
    }
    if (flowMeta.responseSchema && flowMeta.responseSchema.trim()) {
      lines.push('response_schema:');
      flowMeta.responseSchema.trim().split('\n').forEach((line) => lines.push(`  ${line}`));
    }
    lines.push('');
    lines.push('nodes:');
    nodes.forEach((node) => {
      const data = node.data || {};
      lines.push(`  - id: ${node.id}`);
      if (data.title) {
        lines.push(`    title: ${data.title}`);
      }
      if (data.description) {
        lines.push(`    description: ${data.description}`);
      }
      lines.push(`    type: ${data.type || 'executor'}`);
      lines.push(`    node_kind: ${data.nodeKind || ''}`);
      lines.push(`    execution_mode: ${data.executionMode || ''}`);
      if (data.executionContext && data.executionContext.length > 0) {
        lines.push('    execution_context:');
        data.executionContext.forEach((entry) => {
          lines.push(`      - type: ${entry.type}`);
          lines.push(`        required: ${!!entry.required}`);
          if (entry.path) {
            lines.push(`        path: ${entry.path}`);
          }
        });
      } else {
        lines.push('    execution_context: []');
      }
      if (data.instruction) {
        lines.push('    instruction: |');
        data.instruction.split('\n').forEach((line) => lines.push(`      ${line}`));
      }
      if (data.skillRefs && data.skillRefs.length > 0) {
        lines.push('    skill_refs:');
        data.skillRefs.forEach((ref) => lines.push(`      - ${ref}`));
      }
      if (data.responseSchema && data.responseSchema.trim()) {
        lines.push('    response_schema:');
        data.responseSchema.trim().split('\n').forEach((line) => lines.push(`      ${line}`));
      }
      if (data.producedArtifacts && data.producedArtifacts.length > 0) {
        lines.push('    produced_artifacts:');
        data.producedArtifacts.forEach((artifact) => {
          lines.push(`      - path: ${artifact.path}`);
          lines.push(`        required: ${!!artifact.required}`);
        });
      } else {
        lines.push('    produced_artifacts: []');
      }
      if (data.expectedMutations && data.expectedMutations.length > 0) {
        lines.push('    expected_mutations:');
        data.expectedMutations.forEach((mutation) => {
          lines.push(`      - path: ${mutation.path}`);
          lines.push(`        required: ${!!mutation.required}`);
        });
      } else {
        lines.push('    expected_mutations: []');
      }
      if (data.onSuccess) {
        lines.push(`    on_success: ${data.onSuccess}`);
      }
      if (data.onFailure) {
        lines.push(`    on_failure: ${data.onFailure}`);
      }
      if (data.onSubmit) {
        lines.push(`    on_submit: ${data.onSubmit}`);
      }
      if (data.onApprove) {
        lines.push(`    on_approve: ${data.onApprove}`);
      }
      if (data.onReject) {
        lines.push(`    on_reject: ${data.onReject}`);
      }
      if (data.onReworkRoutes && Object.keys(data.onReworkRoutes).length > 0) {
        lines.push('    on_rework_routes:');
        Object.entries(data.onReworkRoutes).forEach(([key, value]) => {
          lines.push(`      ${key}: ${value}`);
        });
      }
    });
    return lines.join('\n');
  };

  const saveFlow = async ({ publish, release = false }) => {
    if (!flowMeta.flowId) {
      message.error('Нужен ID Flow');
      return false;
    }
    if (!flowMeta.title.trim()) {
      message.error('Нужно название');
      return false;
    }
    if (!flowMeta.startNodeId.trim()) {
      message.error('Нужна стартовая нода');
      return false;
    }
    if (!flowMeta.codingAgent.trim()) {
      message.error('Нужен coding_agent');
      return false;
    }
    if (publish && flowMeta.status !== 'published') {
      message.error('Для публикации нужен status=published');
      return false;
    }
    if (!publish && flowMeta.status !== 'draft') {
      message.error('Для сохранения нужен status=draft');
      return false;
    }
    const flowYaml = buildFlowYaml();
    try {
      const response = await apiRequest(`/flows/${flowMeta.flowId}/save`, {
        method: 'POST',
        headers: {
          'Idempotency-Key': crypto.randomUUID(),
        },
        body: JSON.stringify({
          flow_id: flowMeta.flowId,
          flow_yaml: flowYaml,
          publish,
          release,
          resource_version: resourceVersion,
        }),
      });
      setFlowMeta((prev) => ({
        ...prev,
        flowId: response.flow_id || prev.flowId,
        title: response.title || prev.title,
        description: response.description || prev.description,
        status: response.status || prev.status,
        startNodeId: response.start_node_id || prev.startNodeId,
        codingAgent: response.coding_agent || prev.codingAgent,
        ruleRefs: response.rule_refs || prev.ruleRefs,
        failOnMissingDeclaredOutput: response.fail_on_missing_declared_output ?? prev.failOnMissingDeclaredOutput,
        failOnMissingExpectedMutation: response.fail_on_missing_expected_mutation ?? prev.failOnMissingExpectedMutation,
        responseSchema: response.response_schema
          ? JSON.stringify(response.response_schema, null, 2)
          : prev.responseSchema,
      }));
      setFlowVersion(response.version || flowVersion);
      setCurrentStatus(response.status || currentStatus);
      setResourceVersion(response.resource_version ?? resourceVersion);
      message.success(publish ? 'Flow опубликован' : 'Черновик сохранён');
      return true;
    } catch (err) {
      message.error(toRussianError(err?.message, 'Не удалось сохранить Flow'));
      return false;
    }
  };

  useEffect(() => {
    if (!flowId) {
      return;
    }
    if (isCreateMode) {
      setFlowMeta(emptyFlow);
      setNodes([]);
      setSelectedNodeId(null);
      setFlowVersion('0.1');
      setCurrentStatus('draft');
      setResourceVersion(0);
      return;
    }
    setNodes(initialNodes);
    setSelectedNodeId(initialNodes[0]?.id || null);
    loadFlow(flowId);
  }, [flowId, isCreateMode, setNodes]);

  useEffect(() => {
    setReworkMode('');
    setReworkTarget('');
  }, [selectedNodeId]);

  useEffect(() => {
    if (!contextMenu) {
      return;
    }
    const handleMouseDown = () => setContextMenu(null);
    window.addEventListener('mousedown', handleMouseDown);
    return () => window.removeEventListener('mousedown', handleMouseDown);
  }, [contextMenu]);

  useEffect(() => {
    if (isReadOnly) {
      setContextMenu(null);
    }
  }, [isReadOnly]);

  useEffect(() => {
    if (!selectedNode) {
      setNodeIdDraft('');
      return;
    }
    setNodeIdDraft(selectedNode.id);
  }, [selectedNode]);

  useEffect(() => {
    if (!flowMeta.startNodeId) {
      return;
    }
    setNodes((prev) =>
      prev.map((node) => ({
        ...node,
        data: { ...node.data, isStart: node.id === flowMeta.startNodeId },
      }))
    );
  }, [flowMeta.startNodeId, setNodes]);

  const handleStartNodeChange = (value) => {
    updateFlowMeta({ startNodeId: value });
    setNodes((prev) =>
      prev.map((node) => ({
        ...node,
        data: { ...node.data, isStart: node.id === value },
      }))
    );
  };

  return (
    <div className="flow-editor-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Редактор Flow</Title>
        <Space>
          {!isEditing ? (
            <Button type="default" onClick={() => setIsEditing(true)}>
              Редактировать
            </Button>
          ) : (
            <Button
              type="default"
              onClick={async () => {
                const ok = await saveFlow({ publish: false });
                if (ok) {
                  setIsEditing(false);
                }
              }}
            >
              Сохранить
            </Button>
          )}
          <Dropdown
            menu={{
              items: [
                { key: 'publish', label: 'Опубликовать версию' },
                { key: 'release', label: 'Выпустить релиз' },
              ],
              onClick: ({ key }) => {
                saveFlow({ publish: true, release: key === 'release' });
              },
            }}
          >
            <Button type="default" icon={<MoreOutlined />}>Опубликовать</Button>
          </Dropdown>
        </Space>
      </div>

      <div className="split-layout flow-editor-layout">
        <Card className="flow-canvas-card">
          <div className="flow-canvas-header">
            <div>
              <Text className="muted">Канвас</Text>
              <div className="mono">{flowMeta.flowId || 'new-flow'}@{flowVersionLabel}</div>
            </div>
            <Space>
              <Button
                type="default"
                onClick={() => setShowYaml((prev) => !prev)}
              >
                {showYaml ? 'Показать дизайнер' : 'Просмотр YAML'}
              </Button>
            </Space>
          </div>
          <div className="flow-canvas" ref={flowWrapperRef}>
            {showYaml ? (
              <pre className="code-block" style={{ margin: 0, height: '100%' }}>
                {buildFlowYaml()}
              </pre>
            ) : (
              <ReactFlow
                nodes={nodes}
                edges={edges}
                onNodesChange={onNodesChange}
                onEdgesChange={(changes) => {
                  if (isReadOnly) {
                    return;
                  }
                  changes
                    .filter((change) => change.type === 'remove')
                    .forEach((change) => {
                      const removedEdge = edges.find((edge) => edge.id === change.id);
                      if (removedEdge) {
                        removeConnection(removedEdge);
                      }
                    });
                }}
                onConnect={(connection) => {
                  if (isReadOnly) {
                    return;
                  }
                  if (!connection.source || !connection.target) {
                    return;
                  }
                  const sourceNode = nodes.find((node) => node.id === connection.source);
                  const options = getRouteOptions(sourceNode);
                  if (options.length === 0) {
                    return;
                  }
                  if (options.length === 1) {
                    applyConnection(connection.source, connection.target, options[0].value);
                    return;
                  }
                  setPendingConnection({
                    source: connection.source,
                    target: connection.target,
                  });
                  setRouteOptions(options);
                  setRouteChoice(options[0]?.value || null);
                }}
                nodeTypes={nodeTypes}
                nodesDraggable={!isReadOnly}
                nodesConnectable={!isReadOnly}
                fitView
                onInit={setFlowInstance}
                onNodeClick={(_, node) => {
                  setSelectedNodeId(node.id);
                  setContextMenu(null);
                }}
                onPaneClick={() => {
                  setSelectedNodeId(null);
                  setContextMenu(null);
                }}
                onPaneContextMenu={(event) => {
                  if (isReadOnly) {
                    return;
                  }
                  event.preventDefault();
                  setContextMenu({
                    x: event.clientX,
                    y: event.clientY,
                    type: 'pane',
                  });
                }}
                onNodeContextMenu={(event, node) => {
                  if (isReadOnly) {
                    return;
                  }
                  event.preventDefault();
                  setSelectedNodeId(node.id);
                  setContextMenu({
                    x: event.clientX,
                    y: event.clientY,
                    type: 'node',
                    nodeId: node.id,
                  });
                }}
                proOptions={{ hideAttribution: true }}
              >
                <Background gap={20} color="#e2e8f0" />
                <Controls />
              </ReactFlow>
            )}
            {contextMenu && (
              <div
                className="flow-context-menu"
                style={{ top: contextMenu.y, left: contextMenu.x }}
                onMouseDown={(event) => event.stopPropagation()}
              >
                <div className="flow-context-title">Добавить ноду</div>
                <div className="flow-context-section">
                  {NODE_TYPE_OPTIONS.map((option) => (
                    <Button
                      key={option.key}
                      type="text"
                      onClick={() => {
                        if (flowInstance && flowWrapperRef.current) {
                          const bounds = flowWrapperRef.current.getBoundingClientRect();
                          const position = flowInstance.project({
                            x: contextMenu.x - bounds.left,
                            y: contextMenu.y - bounds.top,
                          });
                          addNode(option.key, position);
                        } else {
                          addNode(option.key);
                        }
                        setContextMenu(null);
                      }}
                    >
                      {option.label}
                    </Button>
                  ))}
                </div>
                {selectedNodeId && (
                  <>
                    <div className="flow-context-divider" />
                    <Button
                      danger
                      type="text"
                      onClick={() => {
                        const targetId = contextMenu.nodeId || selectedNodeId;
                        setContextMenu(null);
                        Modal.confirm({
                          title: 'Удалить ноду?',
                          content: `Нода ${targetId} будет удалена вместе с её связями.`,
                          okText: 'Удалить',
                          cancelText: 'Отмена',
                          okButtonProps: { danger: true },
                          onOk: () => removeNodeById(targetId),
                        });
                      }}
                    >
                      Удалить ноду
                    </Button>
                  </>
                )}
              </div>
            )}
          </div>
        </Card>

        <div className="flow-right-panel">
          {!selectedNode ? (
            <Card className="flow-panel-card">
              <Title level={5}>Данные Flow</Title>
              <div className="form-stack">
              <div>
                <Text className="muted">Название</Text>
                <div className="field-control">
                  <Input
                    value={flowMeta.title}
                    disabled={isReadOnly}
                    onChange={(event) => updateFlowMeta({ title: event.target.value })}
                  />
                </div>
              </div>
              <div>
                <Text className="muted">ID Flow</Text>
                <div className="field-control">
                  <Input
                    value={flowMeta.flowId}
                    disabled={isReadOnly}
                    onChange={(event) => updateFlowMeta({ flowId: event.target.value })}
                  />
                </div>
              </div>
              <div>
                <Text className="muted">Описание</Text>
                <div className="field-control">
                  <Input.TextArea
                    rows={3}
                    value={flowMeta.description}
                    disabled={isReadOnly}
                    onChange={(event) => updateFlowMeta({ description: event.target.value })}
                  />
                </div>
              </div>
              <div>
                <Text className="muted">Статус</Text>
                <div className="field-control">
                  <Select
                    value={flowMeta.status}
                    disabled={isReadOnly}
                    onChange={(value) => updateFlowMeta({ status: value })}
                    options={[
                      { value: 'draft', label: 'draft' },
                      { value: 'published', label: 'published' },
                    ]}
                  />
                </div>
              </div>
              <div>
                <Text className="muted">Стартовая нода</Text>
                <div className="field-control">
                  <Select
                    value={flowMeta.startNodeId}
                    disabled={isReadOnly}
                    onChange={handleStartNodeChange}
                    options={nodes.map((node) => ({ value: node.id, label: node.id }))}
                  />
                </div>
              </div>
              <div>
                <Text className="muted">Кодинг агент</Text>
                <div className="field-control">
                  <Select
                    value={flowMeta.codingAgent}
                    disabled={isReadOnly}
                    onChange={(value) => updateFlowMeta({ codingAgent: value })}
                    options={[
                      { value: 'qwen', label: 'qwen' },
                      { value: 'claude', label: 'claude' },
                      { value: 'cursor', label: 'cursor' },
                    ]}
                    placeholder="Выберите coding agent"
                  />
                </div>
              </div>
            </div>

            <Divider />

            <div className="linked-block">
              <div className="linked-header">
                <Title level={5}>Привязанные правила</Title>
              </div>
              <Select
                mode="multiple"
                options={ruleOptions}
                value={flowMeta.ruleRefs}
                disabled={isReadOnly}
                onChange={(value) => updateFlowMeta({ ruleRefs: value })}
                placeholder="Выберите правила"
              />
              <List
                dataSource={flowMeta.ruleRefs}
                locale={{ emptyText: 'Нет связанных правил' }}
                renderItem={(ref, index) => {
                  return (
                    <List.Item
                      className="linked-item"
                    >
                      <div className="linked-rule-row">
                        <span className="mono linked-rule-name">{ref}</span>
                        <div className="linked-rule-actions">
                          <Button
                            key="delete"
                            size="small"
                            type="text"
                            danger
                            icon={<DeleteOutlined />}
                            disabled={isReadOnly}
                            onClick={() => updateFlowMeta({ ruleRefs: flowMeta.ruleRefs.filter((item) => item !== ref) })}
                          />
                        </div>
                      </div>
                    </List.Item>
                  );
                }}
              />
            </div>

            <Divider />

            <div className="form-stack">
              <div className="switch-row">
                <Text className="muted">Провалить при отсутствии объявленного выхода</Text>
                <Switch
                  checked={flowMeta.failOnMissingDeclaredOutput}
                  disabled={isReadOnly}
                  onChange={(checked) => updateFlowMeta({ failOnMissingDeclaredOutput: checked })}
                />
              </div>
              <div className="switch-row">
                <Text className="muted">Провалить при отсутствии ожидаемой мутации</Text>
                <Switch
                  checked={flowMeta.failOnMissingExpectedMutation}
                  disabled={isReadOnly}
                  onChange={(checked) => updateFlowMeta({ failOnMissingExpectedMutation: checked })}
                />
              </div>
              <div>
                <Text className="muted">Схема ответа (опционально)</Text>
                <div className="field-control">
                  <Input.TextArea
                    rows={6}
                    value={flowMeta.responseSchema}
                    placeholder="YAML/JSON schema"
                    disabled={isReadOnly}
                    onChange={(event) => updateFlowMeta({ responseSchema: event.target.value })}
                  />
                </div>
              </div>
            </div>
            </Card>
          ) : (
            <Card className="flow-panel-card">
              <Title level={5}>Выбранная нода</Title>
              <div className="form-stack">
                <div>
                  <Text className="muted">ID ноды</Text>
                  <div className="field-control">
                    <Input
                      value={nodeIdDraft}
                      disabled={!canEditNodeId}
                      onChange={(event) => {
                        if (!canEditNodeId) {
                          return;
                        }
                        setNodeIdDraft(event.target.value);
                      }}
                      onBlur={(event) => {
                        if (!canEditNodeId) {
                          return;
                        }
                        renameSelectedNodeId(event.target.value);
                      }}
                      onPressEnter={(event) => {
                        if (!canEditNodeId) {
                          return;
                        }
                        renameSelectedNodeId(event.currentTarget.value);
                      }}
                    />
                  </div>
                </div>
                <div>
                  <Text className="muted">Название</Text>
                  <div className="field-control">
                    <Input
                      value={selectedNode.data.title}
                      disabled={isReadOnly}
                      onChange={(event) => updateSelectedNode({ title: event.target.value })}
                    />
                  </div>
                </div>
                <div>
                  <Text className="muted">Описание</Text>
                  <div className="field-control">
                    <Input.TextArea
                      rows={2}
                      value={selectedNode.data.description}
                      disabled={isReadOnly}
                      onChange={(event) => updateSelectedNode({ description: event.target.value })}
                    />
                  </div>
                </div>
                <div>
                  <Text className="muted">Тип</Text>
                  <div className="field-control">
                    <Input value={selectedNode.data.type} disabled />
                  </div>
                </div>
                <div>
                  <Text className="muted">Тип Node</Text>
                  <div className="field-control">
                    <Select
                      value={selectedNode.data.nodeKind}
                      disabled={isReadOnly}
                      onChange={(value) => {
                        const meta = NODE_KIND_META[value] || {};
                      updateSelectedNode({
                        nodeKind: value,
                        type: meta.type,
                        executionMode: meta.executionMode,
                        skillRefs: value === 'ai' ? selectedNode.data.skillRefs || [] : [],
                        onSubmit: value === 'human_input' ? selectedNode.data.onSubmit || '' : '',
                        onApprove: value === 'human_approval' ? selectedNode.data.onApprove || '' : '',
                        onReject: value === 'human_approval' ? selectedNode.data.onReject || '' : '',
                        onReworkRoutes: value === 'human_approval' ? selectedNode.data.onReworkRoutes || {} : {},
                        onSuccess: value === 'terminal' ? '' : selectedNode.data.onSuccess || '',
                        onFailure: value === 'ai' || value === 'command' ? (selectedNode.data.onFailure || '') : '',
                      });
                      }}
                      options={[
                        { value: 'ai', label: 'ai' },
                        { value: 'command', label: 'command' },
                        { value: 'human_input', label: 'human_input' },
                        { value: 'human_approval', label: 'human_approval' },
                        { value: 'terminal', label: 'terminal' },
                      ]}
                    />
                  </div>
                </div>
                <div>
                  <Text className="muted">Режим исполнения</Text>
                  <div className="field-control">
                    <Input value={selectedNode.data.executionMode} disabled />
                  </div>
                </div>

                <Divider />

                <div>
                  <Text className="muted">Контекст исполнения</Text>
                  <div className="field-control">
                    <div className="context-list">
                    {(selectedNode.data.executionContext || []).map((entry, index) => (
                      <div key={`${entry.type}-${index}`} className="context-row">
                        <Select
                          value={entry.type}
                          options={EXECUTION_CONTEXT_TYPES}
                          disabled={isReadOnly}
                          onChange={(value) => updateSelectedNodeList(
                            'executionContext',
                            index,
                            value === 'user_request' ? { type: value, path: '' } : { type: value }
                          )}
                          style={{ minWidth: 140 }}
                        />
                        <Input
                          value={entry.path || ''}
                          placeholder="путь"
                          disabled={isReadOnly || entry.type === 'user_request'}
                          onChange={(event) => updateSelectedNodeList('executionContext', index, { path: event.target.value })}
                        />
                        <Switch
                          checked={!!entry.required}
                          disabled={isReadOnly}
                          onChange={(checked) => updateSelectedNodeList('executionContext', index, { required: checked })}
                        />
                        <Button
                          size="small"
                          type="text"
                          danger
                          icon={<DeleteOutlined />}
                          disabled={isReadOnly}
                          onClick={() => removeSelectedNodeListItem('executionContext', index)}
                        />
                      </div>
                    ))}
                    <Button
                      size="small"
                      type="dashed"
                      icon={<PlusOutlined />}
                      disabled={isReadOnly}
                      onClick={() =>
                        addSelectedNodeListItem('executionContext', { type: 'user_request', required: true })
                      }
                    >
                      Добавить контекст
                    </Button>
                    </div>
                  </div>
                </div>

                <Divider />

                <div>
                  <Text className="muted">Инструкция</Text>
                  <div className="field-control">
                    <Input.TextArea
                      rows={3}
                      value={selectedNode.data.instruction}
                      disabled={isReadOnly}
                      onChange={(event) => updateSelectedNode({ instruction: event.target.value })}
                    />
                  </div>
                </div>
                <div>
                  <Text className="muted">Схема ответа (опционально)</Text>
                  <div className="field-control">
                    <Input.TextArea
                      rows={4}
                      value={selectedNode.data.responseSchema}
                      placeholder="YAML/JSON schema"
                      disabled={isReadOnly}
                      onChange={(event) => updateSelectedNode({ responseSchema: event.target.value })}
                    />
                  </div>
                </div>

                <Divider />

                <div className="linked-block">
                  <div className="linked-header">
                    <Title level={5}>Привязанные навыки</Title>
                    <Text className="muted">Только для AI</Text>
                  </div>
                  {selectedNode.data.nodeKind !== 'ai' ? (
                    <div className="card-muted">Привязанные навыки доступны только для AI-исполнителей.</div>
                  ) : (
                    <>
                      <Select
                        mode="multiple"
                        options={skillOptions}
                        value={selectedNode.data.skillRefs || []}
                        disabled={isReadOnly}
                        onChange={(value) => updateSelectedNode({ skillRefs: value })}
                        placeholder="Выберите навыки"
                      />
                      <List
                        dataSource={selectedNode.data.skillRefs || []}
                        locale={{ emptyText: 'Нет связанных навыков' }}
                        renderItem={(ref, index) => {
                          const skillRefs = selectedNode.data.skillRefs || [];
                          return (
                            <List.Item className="linked-item">
                              <div className="linked-rule-row">
                                <span className="mono linked-rule-name">{ref}</span>
                                <div className="linked-rule-actions">
                                  <Button
                                    key="delete"
                                    size="small"
                                    type="text"
                                    danger
                                    icon={<DeleteOutlined />}
                                    disabled={isReadOnly}
                                    onClick={() => updateSelectedNode({ skillRefs: skillRefs.filter((item) => item !== ref) })}
                                  />
                                </div>
                              </div>
                            </List.Item>
                          );
                        }}
                      />
                    </>
                  )}
                </div>

                <Divider />

                <div>
                  <Title level={5}>Ожидаемые выходы</Title>
                  <Text className="muted">Создаваемые артефакты</Text>
                  <div className="context-list">
                    {(selectedNode.data.producedArtifacts || []).map((entry, index) => (
                      <div key={`${entry.path}-${index}`} className="artifact-row">
                        <Input
                          value={extractArtifactName(entry.path)}
                          placeholder="имя артефакта"
                          disabled={isReadOnly}
                          onChange={(event) =>
                            updateSelectedNodeList('producedArtifacts', index, {
                              path: buildArtifactPath(event.target.value),
                            })
                          }
                        />
                        <div className="artifact-row-controls">
                          <Button
                            size="small"
                            type="text"
                            danger
                            icon={<DeleteOutlined />}
                            disabled={isReadOnly}
                            onClick={() => removeSelectedNodeListItem('producedArtifacts', index)}
                          />
                        </div>
                      </div>
                    ))}
                    <Button
                      type="dashed"
                      icon={<PlusOutlined />}
                      disabled={isReadOnly}
                      onClick={() => addSelectedNodeListItem('producedArtifacts', { path: '', required: true })}
                    >
                      Добавить артефакт
                    </Button>
                  </div>

                  <Text className="muted" style={{ marginTop: 12 }}>Ожидаемые изменения</Text>
                  <div className="context-list">
                    {(selectedNode.data.expectedMutations || []).map((entry, index) => (
                      <div key={`${entry.path}-${index}`} className="context-row">
                        <Input
                          value={entry.path || ''}
                          placeholder="путь"
                          disabled={isReadOnly}
                          onChange={(event) => updateSelectedNodeList('expectedMutations', index, { path: event.target.value })}
                        />
                        <Switch
                          checked={!!entry.required}
                          disabled={isReadOnly}
                          onChange={(checked) => updateSelectedNodeList('expectedMutations', index, { required: checked })}
                        />
                        <Button
                          size="small"
                          type="text"
                          danger
                          icon={<DeleteOutlined />}
                          disabled={isReadOnly}
                          onClick={() => removeSelectedNodeListItem('expectedMutations', index)}
                        />
                      </div>
                    ))}
                    <Button
                      type="dashed"
                      icon={<PlusOutlined />}
                      disabled={isReadOnly}
                      onClick={() => addSelectedNodeListItem('expectedMutations', { path: '', required: true })}
                    >
                      Добавить изменение
                    </Button>
                  </div>
                </div>

                <Divider />

                <div>
                  <Title level={5}>Переходы</Title>
                  {selectedNode.data.type === 'terminal' && (
                    <div className="card-muted">Terminal нода завершает flow и не имеет переходов.</div>
                  )}
                  {selectedNode.data.type === 'executor' && (
                    <>
                      <div className="transition-block">
                        <Text className="muted mono">on_success</Text>
                        <div className="field-control">
                          <Select
                            value={selectedNode.data.onSuccess || undefined}
                            disabled={isReadOnly}
                            allowClear
                            options={nodeIdOptions}
                            placeholder="Выберите ноду"
                            onChange={(value) => updateSelectedNode({ onSuccess: value || '' })}
                          />
                        </div>
                      </div>
                      <div className="transition-block">
                        <Text className="muted mono">on_failure</Text>
                        <div className="field-control">
                          <Select
                            value={selectedNode.data.onFailure || undefined}
                            disabled={isReadOnly}
                            allowClear
                            options={nodeIdOptions}
                            placeholder="Выберите ноду"
                            onChange={(value) => updateSelectedNode({ onFailure: value || '' })}
                          />
                        </div>
                      </div>
                    </>
                  )}
                  {selectedNode.data.nodeKind === 'human_input' && (
                    <div className="transition-block">
                      <Text className="muted mono">on_submit</Text>
                      <div className="field-control">
                        <Select
                          value={selectedNode.data.onSubmit || undefined}
                          disabled={isReadOnly}
                          allowClear
                          options={nodeIdOptions}
                          placeholder="Выберите ноду"
                          onChange={(value) => updateSelectedNode({ onSubmit: value || '' })}
                        />
                      </div>
                    </div>
                  )}
                  {selectedNode.data.nodeKind === 'human_approval' && (
                    <>
                      <div className="transition-block">
                        <Text className="muted mono">on_approve</Text>
                        <div className="field-control">
                          <Select
                            value={selectedNode.data.onApprove || undefined}
                            disabled={isReadOnly}
                            allowClear
                            options={nodeIdOptions}
                            placeholder="Выберите ноду"
                            onChange={(value) => updateSelectedNode({ onApprove: value || '' })}
                          />
                        </div>
                      </div>
                      <div className="transition-block">
                        <Text className="muted mono">on_reject</Text>
                        <div className="field-control">
                          <Select
                            value={selectedNode.data.onReject || undefined}
                            disabled={isReadOnly}
                            allowClear
                            options={nodeIdOptions}
                            placeholder="Выберите ноду"
                            onChange={(value) => updateSelectedNode({ onReject: value || '' })}
                          />
                        </div>
                      </div>
                      <div className="transition-list">
                        {Object.entries(selectedNode.data.onReworkRoutes || {}).map(([mode, target]) => (
                          <div key={mode} className="transition-block">
                            <div className="transition-header">
                              <Text className="muted mono">rework:{mode}</Text>
                              <Button
                                size="small"
                                type="text"
                                danger
                                icon={<DeleteOutlined />}
                                disabled={isReadOnly}
                                onClick={() => {
                                  const nextRoutes = { ...(selectedNode.data.onReworkRoutes || {}) };
                                  delete nextRoutes[mode];
                                  updateSelectedNode({ onReworkRoutes: nextRoutes });
                                }}
                              />
                            </div>
                            <div className="field-control">
                              <Select
                                value={target || undefined}
                                disabled={isReadOnly}
                                allowClear
                                options={nodeIdOptions}
                                placeholder="Выберите ноду"
                                onChange={(value) => {
                                  const nextRoutes = { ...(selectedNode.data.onReworkRoutes || {}) };
                                  nextRoutes[mode] = value || '';
                                  updateSelectedNode({ onReworkRoutes: nextRoutes });
                                }}
                              />
                            </div>
                          </div>
                        ))}
                        <div className="transition-block">
                          <Text className="muted">Режим rework</Text>
                          <div className="field-control">
                            <Input
                              value={reworkMode}
                              placeholder="например, keep_workspace"
                              disabled={isReadOnly}
                              onChange={(event) => setReworkMode(event.target.value)}
                            />
                          </div>
                          <Text className="muted">Целевая нода</Text>
                          <div className="field-control">
                            <Select
                              value={reworkTarget || undefined}
                              disabled={isReadOnly}
                              allowClear
                              options={nodeIdOptions}
                              placeholder="Выберите ноду"
                              onChange={(value) => setReworkTarget(value || '')}
                            />
                          </div>
                          <Button
                            size="small"
                            type="default"
                            icon={<PlusOutlined />}
                            disabled={isReadOnly}
                            onClick={() => {
                              if (!reworkMode || !reworkTarget) {
                                return;
                              }
                              const nextRoutes = { ...(selectedNode.data.onReworkRoutes || {}) };
                              nextRoutes[reworkMode] = reworkTarget;
                              updateSelectedNode({ onReworkRoutes: nextRoutes });
                              setReworkMode('');
                              setReworkTarget('');
                            }}
                          >
                            Добавить
                          </Button>
                        </div>
                      </div>
                    </>
                  )}
                </div>
              </div>
            </Card>
          )}

          <Card className="flow-panel-card" style={{ marginTop: 16 }}>
            <Title level={5}>Валидация</Title>
            {validationErrors.length === 0 ? (
              <div className="card-muted">Ошибок валидации не найдено.</div>
            ) : (
              <List
                dataSource={validationErrors}
                renderItem={(item) => <List.Item className="card-muted">{item}</List.Item>}
              />
            )}
          </Card>
        </div>
      </div>
      <Modal
        open={!!pendingConnection}
        title="Выберите тип связи"
        okText="Применить"
        cancelText="Отмена"
        onCancel={() => setPendingConnection(null)}
        onOk={() => {
          if (pendingConnection?.source && pendingConnection?.target && routeChoice) {
            applyConnection(pendingConnection.source, pendingConnection.target, routeChoice);
          }
          setPendingConnection(null);
        }}
      >
        <Select
          value={routeChoice}
          onChange={(value) => setRouteChoice(value)}
          options={routeOptions}
          style={{ width: '100%' }}
        />
      </Modal>
    </div>
  );
}
