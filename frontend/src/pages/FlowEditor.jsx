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
  Checkbox,
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
import { useLocation, useParams } from 'react-router-dom';
import { apiRequest } from '../api/request.js';
import { toRussianError } from '../utils/errorMessages.js';
import { parse as parseYaml } from 'yaml';
import Editor from '@monaco-editor/react';
import { formatStatusLabel } from '../components/StatusTag.jsx';
import { useThemeMode } from '../theme/ThemeContext.jsx';
import { configureMonacoThemes, getMonacoThemeName } from '../utils/monacoTheme.js';

const { Title, Text } = Typography;

const NODE_KIND_META = {
  ai: {
    label: 'AI Executor',
    variant: 'executor ai',
  },
  command: {
    label: 'Command Executor',
    variant: 'executor command',
  },
  human_input: {
    label: 'Human Input Gate',
    variant: 'gate input',
  },
  human_approval: {
    label: 'Human Approval Gate',
    variant: 'gate approval',
  },
  terminal: {
    label: 'Terminal',
    variant: 'terminal',
  },
};

const DEFAULT_VERSION = '0.1';
const parseMajorMinor = (version) => {
  const normalized = (version || '').trim() || DEFAULT_VERSION;
  const match = normalized.match(/^(\d+)\.(\d+)(?:\.\d+)?$/);
  if (!match) {
    return { major: 0, minor: 0, valid: false };
  }
  return { major: Number(match[1]), minor: Number(match[2]), valid: true };
};
const compareVersions = (a, b) => {
  if (a.major !== b.major) return a.major - b.major;
  return a.minor - b.minor;
};
const nextMajorVersion = (version) => {
  const parsed = parseMajorMinor(version);
  const major = parsed.valid ? parsed.major : 0;
  return `${major + 1}.0`;
};
const nextMinorVersion = (version) => {
  const parsed = parseMajorMinor(version);
  if (!parsed.valid) {
    return DEFAULT_VERSION;
  }
  return `${parsed.major}.${parsed.minor + 1}`;
};
const getLatestVersion = (versions, status) => {
  const candidates = versions.filter((item) => !status || item.status === status);
  let best = null;
  candidates.forEach((item) => {
    const parsed = parseMajorMinor(item.value);
    if (!parsed.valid) {
      return;
    }
    if (!best || compareVersions(parsed, best.parsed) > 0) {
      best = { value: item.value, parsed };
    }
  });
  return best ? best.value : '';
};
const getMaxPublishedMajor = (versions) => {
  let maxMajor = null;
  versions.forEach((item) => {
    if (item.status !== 'published') return;
    const parsed = parseMajorMinor(item.value);
    if (!parsed.valid) return;
    if (maxMajor === null || parsed.major > maxMajor) {
      maxMajor = parsed.major;
    }
  });
  return maxMajor;
};
const getMaxPublishedMinorForMajor = (versions, major) => {
  let maxMinor = null;
  versions.forEach((item) => {
    if (item.status !== 'published') return;
    const parsed = parseMajorMinor(item.value);
    if (!parsed.valid || parsed.major !== major) return;
    if (maxMinor === null || parsed.minor > maxMinor) {
      maxMinor = parsed.minor;
    }
  });
  return maxMinor;
};
const getDraftForMajor = (versions, major) => (
  versions.find((item) => {
    if (item.status !== 'draft') return false;
    const parsed = parseMajorMinor(item.value);
    return parsed.valid && parsed.major === major;
  })
);

const EXECUTION_CONTEXT_TYPES = [
  { value: 'artifact_ref', label: 'Artifact' },
];

const SCOPE_OPTIONS = [
  { value: 'project', label: 'Scope:project' },
  { value: 'run', label: 'Scope:run' },
];
const TRANSFER_MODE_OPTIONS = [
  { value: 'by_ref', label: 'Transfer: by ref' },
  { value: 'by_value', label: 'Transfer: by value' },
];
const MODIFIABLE_OPTIONS = [
  { value: 'no', label: 'Modifiable: NO' },
  { value: 'yes', label: 'Modifiable: YES' },
];

const NODE_TYPE_OPTIONS = [
  { key: 'ai', label: 'AI Executor' },
  { key: 'command', label: 'Command Executor' },
  { key: 'human_input', label: 'Human Input Gate' },
  { key: 'human_approval', label: 'Human Approval Gate' },
  { key: 'terminal', label: 'Terminal' },
];

const DEFAULT_REWORK = { keepChanges: false, nextNode: '' };

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
      {data.isTerminal && <span className="flow-node-stop">Stop</span>}
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

const nodeTypes = { flowNode: FlowNode };
const platformOptions = [
  { value: 'FRONT', label: 'FRONT' },
  { value: 'BACK', label: 'BACK' },
  { value: 'DATA', label: 'DATA' },
];
const environmentOptions = [
  { value: 'dev', label: 'dev' },
  { value: 'prod', label: 'prod' },
];
const visibilityOptions = [
  { value: 'internal', label: 'internal' },
  { value: 'restricted', label: 'restricted' },
  { value: 'public', label: 'public' },
];
const lifecycleOptions = [
  { value: 'active', label: 'active' },
  { value: 'deprecated', label: 'deprecated' },
  { value: 'retired', label: 'retired' },
];
const publicationTargetOptions = [
  { value: 'db_only', label: 'DB' },
  { value: 'db_and_git', label: 'DB + Git' },
];
const publishModeOptions = [
  { value: 'local', label: 'local (direct push)' },
  { value: 'pr', label: 'pr (create Pull Request)' },
];
const flowKindOptions = [
  { value: 'orchestration', label: 'orchestration' },
  { value: 'governance', label: 'governance' },
  { value: 'analysis', label: 'analysis' },
  { value: 'delivery', label: 'delivery' },
];
const riskLevelOptions = [
  { value: 'low', label: 'low' },
  { value: 'medium', label: 'medium' },
  { value: 'high', label: 'high' },
  { value: 'critical', label: 'critical' },
];
const requiredLabel = (label) => `${label} *`;

const emptyFlow = {
  title: '',
  flowId: '',
  description: '',
  startNodeId: '',
  codingAgent: '',
  ruleRefs: [],
  teamCode: '',
  platformCode: 'FRONT',
  tags: [],
  flowKind: '',
  riskLevel: '',
  environment: 'dev',
  visibility: 'internal',
  lifecycleStatus: 'active',
  approvalStatus: '',
  contentSource: '',
  publicationStatus: 'draft',
  publicationTarget: 'db_and_git',
  publishMode: 'pr',
  failOnMissingDeclaredOutput: false,
  failOnMissingExpectedMutation: false,
  responseSchema: '',
};


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
    if (data.onRework && data.onRework.nextNode) {
      const keepChanges = !!data.onRework.keepChanges;
      addEdge(node.id, data.onRework.nextNode, `rework: keep_changes=${keepChanges}`, 'rework');
    }
  });

  return edges;
}

function toNodeData(node, isStart) {
  const kind = node.node_kind || node.nodeKind || node.type || '';
  const rawProducedArtifacts = node.produced_artifacts || node.producedArtifacts || [];
  const producedArtifacts = rawProducedArtifacts.map((artifact) => ({
    ...artifact,
    modifiable: artifact?.modifiable === true,
  }));
  const rawRework = node.on_rework || node.onRework || null;
  let onRework = null;
  if (rawRework) {
    onRework = {
      keepChanges: rawRework.keep_changes ?? rawRework.keepChanges ?? false,
      nextNode: rawRework.next_node || rawRework.nextNode || '',
    };
  }
  return {
    id: node.id || '',
    title: node.title || '',
    description: node.description || '',
    nodeKind: kind,
    type: kind,
    executionContext: (node.execution_context || node.executionContext || []).map((entry) => {
      if (!entry || typeof entry !== 'object') {
        return { type: 'artifact_ref', required: true, scope: 'run', path: '', transfer_mode: 'by_ref' };
      }
      return {
        ...entry,
        transfer_mode: entry.transfer_mode || 'by_ref',
      };
    }),
    instruction: node.instruction || '',
    checkpointBeforeRun: node.checkpoint_before_run ?? node.checkpointBeforeRun ?? false,
    skillRefs: node.skill_refs || node.skillRefs || [],
    responseSchema: node.response_schema ? JSON.stringify(node.response_schema, null, 2) : '',
    producedArtifacts,
    expectedMutations: node.expected_mutations || node.expectedMutations || [],
    onSuccess: node.on_success || node.onSuccess || '',
    onFailure: node.on_failure || node.onFailure || '',
    onSubmit: node.on_submit || node.onSubmit || '',
    onApprove: node.on_approve || node.onApprove || '',
    onRework: onRework || { ...DEFAULT_REWORK },
    isStart: !!isStart,
    isTerminal: kind === 'terminal',
  };
}

function parseFlowYaml(flowYaml, startNodeId) {
  if (!flowYaml) {
    return [];
  }
  try {
    const parsed = parseYaml(flowYaml);
    const nodes = Array.isArray(parsed?.nodes) ? parsed.nodes : [];
    return nodes.map((node, index) => ({
      id: node.id || `node-${index + 1}`,
      type: 'flowNode',
      position: { x: 80 + index * 320, y: 120 },
      data: toNodeData(node, node.id === startNodeId),
    }));
  } catch (err) {
    return [];
  }
}

function validateFlow(nodes, meta, rulesCatalog, skillsCatalog) {
  const errors = [];
  const nodeIds = nodes.map((node) => node.id);
  const uniqueIds = new Set(nodeIds);
  if (nodes.length === 0) {
    errors.push('Flow has no nodes.');
  }
  if (!meta.startNodeId) {
    errors.push('start_node_id is not set.');
  } else if (!uniqueIds.has(meta.startNodeId)) {
    errors.push(`start_node_id not found: ${meta.startNodeId}`);
  }
  if (!meta.codingAgent) {
    errors.push('coding_agent is not set.');
  }
  if (uniqueIds.size !== nodeIds.length) {
    const seen = new Set();
    nodeIds.forEach((id) => {
      if (seen.has(id)) {
        errors.push(`Duplicate node ID: ${id}`);
      }
      seen.add(id);
    });
  }

  const rulesByCanonical = new Map((rulesCatalog || []).map((rule) => [rule.canonical, rule]));
  const skillsByCanonical = new Map((skillsCatalog || []).map((skill) => [skill.canonical, skill]));
  if (rulesByCanonical.size > 0) {
    meta.ruleRefs.forEach((ref) => {
      const rule = rulesByCanonical.get(ref);
      if (!rule) {
        errors.push(`Rule ref not found: ${ref}`);
        return;
      }
      if (rule.status !== 'published') {
        errors.push(`Rule ref not published: ${ref}`);
      }
      if (meta.codingAgent && rule.codingAgent !== meta.codingAgent) {
        errors.push(`Rule ref does not match coding_agent: ${ref}`);
      }
    });
  }

  nodes.forEach((node) => {
    const data = node.data || {};
    const kind = data.nodeKind || data.type;
    if (!kind) {
      errors.push(`type is not set: ${node.id}`);
    }
    if (data.skillRefs && data.skillRefs.length > 0 && kind !== 'ai') {
      errors.push(`skill_refs are allowed only for AI nodes: ${node.id}`);
    }
    if (data.skillRefs && skillsByCanonical.size > 0) {
      data.skillRefs.forEach((ref) => {
        const skill = skillsByCanonical.get(ref);
        if (!skill) {
          errors.push(`Skill ref not found: ${ref}`);
          return;
        }
        if (skill.status !== 'published') {
          errors.push(`Skill ref not published: ${ref}`);
        }
        if (meta.codingAgent && skill.codingAgent !== meta.codingAgent) {
          errors.push(`Skill ref does not match coding_agent: ${ref}`);
        }
      });
    }

    if (kind !== 'command') {
      if (!Array.isArray(data.executionContext)) {
        errors.push(`execution_context is not set: ${node.id}`);
      } else {
        data.executionContext.forEach((entry) => {
          if (!entry?.type) {
            errors.push(`execution_context type is not set: ${node.id}`);
            return;
          }
          if (!EXECUTION_CONTEXT_TYPES.some((item) => item.value === entry.type)) {
            errors.push(`execution_context type is not supported: ${node.id}`);
          }
          if (entry.required === undefined || entry.required === null) {
            errors.push(`execution_context required is not set: ${node.id}`);
          }
          if (entry.type === 'artifact_ref') {
            if (!entry.path) {
              errors.push(`execution_context path is not set: ${node.id}`);
            }
            if (!entry.scope) {
              errors.push(`execution_context scope is not set: ${node.id}`);
            }
            if (entry.transfer_mode && !['by_ref', 'by_value'].includes(entry.transfer_mode)) {
              errors.push(`execution_context transfer_mode is not supported: ${node.id}`);
            }
            if ((entry.transfer_mode || 'by_ref') === 'by_value' && kind !== 'ai') {
              errors.push(`execution_context transfer_mode=by_value is supported only for ai nodes: ${node.id}`);
            }
            if (entry.scope === 'run' && !entry.node_id) {
              errors.push(`execution_context node_id is not set for run-scope artifact: ${node.id}`);
            }
          }
        });
      }
    }

    const checkPathList = (items, label) => {
      if (!Array.isArray(items)) return;
      items.forEach((item) => {
        if (!item) {
          errors.push(`${label} entry is not set: ${node.id}`);
          return;
        }
        if (item.required === undefined || item.required === null) {
          errors.push(`${label} required is not set: ${node.id}`);
        }
        if (!item.path) {
          errors.push(`${label} path is not set: ${node.id}`);
        }
        if (!item.scope) {
          errors.push(`${label} scope is not set: ${node.id}`);
        }
      });
    };
    checkPathList(data.producedArtifacts, 'produced_artifacts');
    checkPathList(data.expectedMutations, 'expected_mutations');

    if (kind === 'human_input') {
      if (!String(data.instruction || '').trim()) {
        errors.push(`human_input requires instruction: ${node.id}`);
      }
      if (!Array.isArray(data.executionContext) || data.executionContext.length === 0) {
        errors.push(`human_input requires execution_context artifact_ref entries: ${node.id}`);
      } else {
        data.executionContext.forEach((entry) => {
          if ((entry.type || '') !== 'artifact_ref') {
            errors.push(`human_input supports only artifact_ref execution_context: ${node.id}`);
          }
          if ((entry.transfer_mode || 'by_ref') !== 'by_ref') {
            errors.push(`human_input execution_context supports only transfer_mode=by_ref: ${node.id}`);
          }
          if ((entry.scope || 'run') !== 'run') {
            errors.push(`human_input execution_context supports only scope=run: ${node.id}`);
          }
          if (!entry.node_id) {
            errors.push(`human_input execution_context requires source node_id: ${node.id}`);
          }
        });
      }
    }

    const transitions = [];
    if (data.onSuccess) transitions.push(['on_success', data.onSuccess]);
    if (data.onFailure) transitions.push(['on_failure', data.onFailure]);
    if (data.onSubmit) transitions.push(['on_submit', data.onSubmit]);
    if (data.onApprove) transitions.push(['on_approve', data.onApprove]);
    if (data.onRework && data.onRework.nextNode) {
      transitions.push(['on_rework', data.onRework.nextNode]);
    }
    transitions.forEach(([label, target]) => {
      if (target && !uniqueIds.has(target)) {
        errors.push(`Invalid transition ${label} from ${node.id} -> ${target}`);
      }
    });

    if (kind === 'terminal') {
      if (transitions.length > 0) {
        errors.push(`Terminal node cannot have transitions: ${node.id}`);
      }
      return;
    }

    if (kind === 'ai' || kind === 'command') {
      if (!data.onSuccess) {
        errors.push(`Executor node requires on_success: ${node.id}`);
      }
      if (data.checkpointBeforeRun !== undefined && data.checkpointBeforeRun !== null && typeof data.checkpointBeforeRun !== 'boolean') {
        errors.push(`checkpoint_before_run must be boolean: ${node.id}`);
      }
    } else if (data.checkpointBeforeRun) {
      errors.push(`checkpoint_before_run is allowed only for ai/command: ${node.id}`);
    }
    if (kind === 'ai' && node.id === meta.startNodeId && !String(data.instruction || '').trim()) {
      errors.push(`Start AI node requires instruction: ${node.id}`);
    }

    if (kind === 'human_input') {
      if (!data.onSubmit) {
        errors.push(`human_input requires on_submit: ${node.id}`);
      }
    }

    if (kind === 'human_approval') {
      if (!data.onApprove) {
        errors.push(`human_approval requires on_approve: ${node.id}`);
      }
      if (!data.onRework || !data.onRework.nextNode) {
        errors.push(`human_approval requires on_rework: ${node.id}`);
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
        errors.push(`Unreachable node: ${id}`);
      }
    });
  }

  const nodesById = new Map(nodes.map((node) => [node.id, node]));
  const predecessorMap = new Map();
  nodes.forEach((node) => {
    const data = node.data || {};
    const targets = [data.onSuccess, data.onFailure, data.onSubmit, data.onApprove, data.onRework?.nextNode]
      .filter(Boolean);
    targets.forEach((target) => {
      if (!predecessorMap.has(target)) predecessorMap.set(target, []);
      predecessorMap.get(target).push(node);
    });
  });

  nodes.forEach((node) => {
    const data = node.data || {};
    const kind = data.nodeKind || data.type;
    if (kind !== 'human_input') return;
    const predecessors = predecessorMap.get(node.id) || [];
    const hasModifiable = predecessors.some((candidate) =>
      Array.isArray(candidate.data?.producedArtifacts)
      && candidate.data.producedArtifacts.some((artifact) => artifact?.modifiable === true));
    if (predecessors.length > 0 && !hasModifiable) {
      errors.push(`human_input requires at least one modifiable produced_artifact in predecessor nodes: ${node.id}`);
    }
    (data.executionContext || []).forEach((entry) => {
      const source = nodesById.get(entry.node_id);
      if (!source) return;
      const sourceHasMatch = (source.data?.producedArtifacts || []).some((artifact) =>
        artifact?.path === entry.path
        && (artifact.scope || 'run') === (entry.scope || 'run')
        && artifact.modifiable === true);
      if (!sourceHasMatch) {
        errors.push(`human_input execution_context must reference modifiable produced_artifacts: ${node.id} -> ${entry.node_id}:${entry.path || ''}`);
      }
    });
    const expectedOutputs = new Set(
      (data.executionContext || [])
        .filter((entry) => entry?.type === 'artifact_ref' && (entry.scope || 'run') === 'run' && entry?.path && entry?.node_id)
        .filter((entry) => {
          const source = nodesById.get(entry.node_id);
          return (source?.data?.producedArtifacts || []).some((artifact) =>
            artifact?.path === entry.path
            && (artifact.scope || 'run') === 'run'
            && artifact.modifiable === true);
        })
        .map((entry) => `run::${entry.path}`)
    );
    const declaredOutputs = new Set(
      (data.producedArtifacts || [])
        .filter((artifact) => artifact?.path)
        .map((artifact) => `${artifact.scope || 'run'}::${artifact.path}`)
    );
    if (expectedOutputs.size > 0 && declaredOutputs.size === 0) {
      errors.push(`human_input produced_artifacts must mirror modifiable execution_context artifacts: ${node.id}`);
    }
    expectedOutputs.forEach((expected) => {
      if (!declaredOutputs.has(expected)) {
        errors.push(`human_input produced_artifacts missing required artifact from execution_context: ${node.id} -> ${expected}`);
      }
    });
    declaredOutputs.forEach((declared) => {
      if (!expectedOutputs.has(declared)) {
        errors.push(`human_input produced_artifacts must match execution_context artifacts: ${node.id} -> ${declared}`);
      }
    });
  });

  return errors;
}

export default function FlowEditor() {
  const { isDark } = useThemeMode();
  const monacoTheme = getMonacoThemeName(isDark);
  const { flowId } = useParams();
  const location = useLocation();
  const isCreateMode = flowId === 'create' || location.pathname.endsWith('/flows/create');
  const [flowMeta, setFlowMeta] = useState(emptyFlow);
  const [resourceVersion, setResourceVersion] = useState(0);
  const [flowVersion, setFlowVersion] = useState(isCreateMode ? '0.1' : '0.1');
  const [baseVersion, setBaseVersion] = useState('');
  const [currentStatus, setCurrentStatus] = useState(isCreateMode ? 'draft' : '');
  const [showYaml, setShowYaml] = useState(false);
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [selectedNodeId, setSelectedNodeId] = useState(null);
  const [versionOptions, setVersionOptions] = useState([]);
  const [rulesCatalog, setRulesCatalog] = useState([]);
  const [skillsCatalog, setSkillsCatalog] = useState([]);
  const [flowInstance, setFlowInstance] = useState(null);
  const flowWrapperRef = useRef(null);
  const [pendingConnection, setPendingConnection] = useState(null);
  const [routeOptions, setRouteOptions] = useState([]);
  const [routeChoice, setRouteChoice] = useState(null);
  const [nodeIdDraft, setNodeIdDraft] = useState('');
  const [contextMenu, setContextMenu] = useState(null);
  const [isEditing, setIsEditing] = useState(false);
  const [publishDialogOpen, setPublishDialogOpen] = useState(false);
  const [publishVariant, setPublishVariant] = useState('minor');
  const [publishDialogTarget, setPublishDialogTarget] = useState('db_and_git');
  const [publishDialogMode, setPublishDialogMode] = useState('pr');
  const latestPublishedVersion = getLatestVersion(versionOptions, 'published');
  const currentParsed = parseMajorMinor(flowVersion || baseVersion || latestPublishedVersion || DEFAULT_VERSION);
  const currentMajor = currentParsed.valid ? currentParsed.major : parseMajorMinor(DEFAULT_VERSION).major;
  const maxMinorForMajor = getMaxPublishedMinorForMajor(versionOptions, currentMajor);
  const nextDraftVersion = maxMinorForMajor !== null
    ? `${currentMajor}.${maxMinorForMajor + 1}`
    : (currentMajor === parseMajorMinor(DEFAULT_VERSION).major && !latestPublishedVersion
      ? DEFAULT_VERSION
      : `${currentMajor}.0`);
  const draftForMajor = getDraftForMajor(versionOptions, currentMajor);
  const publishVersion = draftForMajor ? draftForMajor.value : nextDraftVersion;
  const publishLabel = `Publish version -> ${publishVersion}`;
  const maxPublishedMajor = getMaxPublishedMajor(versionOptions);
  const releaseMajor = maxPublishedMajor === null ? 1 : maxPublishedMajor + 1;
  const releaseVersion = `${releaseMajor}.0`;
  const releaseLabel = `Breaking update (major) -> ${releaseVersion}`;
  const flowVersionLabel = currentStatus === 'draft' || isCreateMode
    ? 'draft'
    : (flowVersion || '0.0.0');
  const edges = useMemo(() => buildEdges(nodes), [nodes]);
  const nodeIdOptions = useMemo(
    () => nodes.map((node) => ({ value: node.id, label: node.id })),
    [nodes]
  );
  const selectedNode = nodes.find((node) => node.id === selectedNodeId);
  const selectedNodeKind = selectedNode?.data?.nodeKind || selectedNode?.data?.type || '';
  const showExecutionContextEditor = ['ai', 'human_approval', 'human_input'].includes(selectedNodeKind);
  const validationErrors = validateFlow(nodes, flowMeta, rulesCatalog, skillsCatalog);
  const isReadOnly = !isEditing;
  const canEditNodeId = currentStatus === 'draft' && isEditing;

  const renderVersionSelector = () => {
    if (flowMeta.flowId) {
      return (
        <Select
          value={flowVersion || undefined}
          options={versionOptions}
          onChange={(value) => handleVersionSelect(value)}
          className="rule-version-select"
          placeholder="Version"
          disabled={isEditing}
        />
      );
    }
    return <span className="rule-version-pill">new</span>;
  };

  const filteredRules = rulesCatalog.filter(
    (rule) => !flowMeta.codingAgent || rule.codingAgent === flowMeta.codingAgent
  );
  const ruleOptions = filteredRules.map((rule) => ({
    value: rule.canonical,
    label: `${rule.name} · ${rule.version}`,
    disabled: rule.status !== 'published',
  }));

  const filteredSkills = skillsCatalog.filter(
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
      message.error(`Node ID is already used: ${trimmed}`);
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
        if (data.onRework?.nextNode === selectedNodeId) {
          nextData.onRework = { ...data.onRework, nextNode: trimmed };
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
          if (data.onRework?.nextNode === nodeId) {
            nextData.onRework = { ...data.onRework, nextNode: '' };
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
    const kind = data.nodeKind || data.type;
    if (kind === 'ai') {
      return [
        { value: 'on_success', label: 'on_success' },
        { value: 'on_failure', label: 'on_failure' },
      ];
    }
    if (kind === 'command') {
      return [
        { value: 'on_success', label: 'on_success' },
        { value: 'on_failure', label: 'on_failure' },
      ];
    }
    if (kind === 'human_input') {
      return [{ value: 'on_submit', label: 'on_submit' }];
    }
    if (kind === 'human_approval') {
      return [
        { value: 'on_approve', label: 'on_approve' },
        { value: 'on_rework', label: 'on_rework' },
      ];
    }
    if (kind === 'terminal') {
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
        } else if (routeKey === 'on_rework') {
          data.onRework = { ...(data.onRework || DEFAULT_REWORK), nextNode: targetId };
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
        } else if (label === 'on_rework' || label.startsWith('rework')) {
          if (data.onRework) {
            data.onRework = { ...data.onRework, nextNode: '' };
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
      title: `New ${meta.label}`,
      description: '',
      nodeKind: kind,
      type: kind,
      executionContext: [],
      instruction: '',
      checkpointBeforeRun: false,
      skillRefs: [],
      responseSchema: '',
      producedArtifacts: [],
      expectedMutations: [],
      onSuccess: '',
      onFailure: '',
      onSubmit: '',
      onApprove: '',
      onRework: { ...DEFAULT_REWORK },
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

  const loadRulesCatalog = async () => {
    try {
      const data = await apiRequest('/rules');
      const mapped = data.map((rule) => ({
        key: rule.rule_id,
        name: rule.title || rule.rule_id,
        description: rule.description || '',
        ruleId: rule.rule_id,
        codingAgent: rule.coding_agent,
        status: rule.status,
        version: rule.version,
        canonical: rule.canonical_name,
      }));
      setRulesCatalog(mapped);
    } catch (err) {
      message.error(err.message || 'Failed to load Rules');
    }
  };

  const loadSkillsCatalog = async () => {
    try {
      const data = await apiRequest('/skills');
      const mapped = data.map((skill) => ({
        key: skill.skill_id,
        name: skill.name || skill.skill_id,
        description: skill.description || '',
        skillId: skill.skill_id,
        codingAgent: skill.coding_agent,
        status: skill.status,
        version: skill.version,
        canonical: skill.canonical_name,
      }));
      setSkillsCatalog(mapped);
    } catch (err) {
      message.error(err.message || 'Failed to load Skills');
    }
  };

  const loadFlowVersions = async (id) => {
    try {
      const versions = await apiRequest(`/flows/${id}/versions`);
      const mapped = versions.map((item) => ({
        label: `v${item.version} · ${formatStatusLabel(item.status)}`,
        value: item.version,
        status: item.status,
        flowId: item.flow_id,
        resourceVersion: item.resource_version,
      }));
      setVersionOptions(mapped);
    } catch (err) {
      message.error(err.message || 'Failed to load Flow versions');
    }
  };

  const loadFlow = async (id) => {
    try {
      const data = await apiRequest(`/flows/${id}`);
      const parsedNodes = parseFlowYaml(data.flow_yaml, data.start_node_id || '');
      setFlowMeta((prev) => ({
        ...prev,
        flowId: data.flow_id || id,
        title: data.title || prev.title,
        description: data.description || prev.description,
        status: data.status || prev.status,
        startNodeId: data.start_node_id || prev.startNodeId,
        codingAgent: data.coding_agent || prev.codingAgent,
        ruleRefs: data.rule_refs || [],
        teamCode: data.team_code || '',
        platformCode: data.platform_code || 'FRONT',
        tags: data.tags || [],
        flowKind: data.flow_kind || '',
        riskLevel: data.risk_level || '',
        environment: data.environment || 'dev',
        visibility: data.visibility || 'internal',
        lifecycleStatus: data.lifecycle_status || 'active',
        approvalStatus: data.approval_status || '',
        contentSource: data.content_source || '',
        publicationStatus: data.publication_status || 'draft',
        publicationTarget: data.publication_target || 'db_and_git',
        failOnMissingDeclaredOutput: data.fail_on_missing_declared_output ?? prev.failOnMissingDeclaredOutput,
        failOnMissingExpectedMutation: data.fail_on_missing_expected_mutation ?? prev.failOnMissingExpectedMutation,
        responseSchema: data.response_schema ? JSON.stringify(data.response_schema, null, 2) : prev.responseSchema,
      }));
      setFlowVersion(data.version || flowVersion);
      setBaseVersion(data.version || flowVersion);
      setCurrentStatus(data.status || '');
      setResourceVersion(data.resource_version ?? 0);
      setNodes(parsedNodes);
      setSelectedNodeId(parsedNodes[0]?.id || null);
    } catch (err) {
      message.error(err.message || 'Failed to load Flow');
    }
  };

  const handleVersionSelect = async (value, { keepEditing = false } = {}) => {
    const selected = versionOptions.find((option) => option.value === value);
    const targetFlowId = selected?.flowId || flowMeta.flowId;
    if (!targetFlowId) {
      return;
    }
    try {
      const data = await apiRequest(`/flows/${targetFlowId}/versions/${value}`);
      const parsedNodes = parseFlowYaml(data.flow_yaml, data.start_node_id || '');
      setFlowMeta((prev) => ({
        ...prev,
        flowId: data.flow_id || targetFlowId,
        title: data.title || prev.title,
        description: data.description || prev.description,
        status: data.status || prev.status,
        startNodeId: data.start_node_id || prev.startNodeId,
        codingAgent: data.coding_agent || prev.codingAgent,
        ruleRefs: data.rule_refs || [],
        teamCode: data.team_code || '',
        platformCode: data.platform_code || 'FRONT',
        tags: data.tags || [],
        flowKind: data.flow_kind || '',
        riskLevel: data.risk_level || '',
        environment: data.environment || 'dev',
        visibility: data.visibility || 'internal',
        lifecycleStatus: data.lifecycle_status || 'active',
        approvalStatus: data.approval_status || '',
        contentSource: data.content_source || '',
        publicationStatus: data.publication_status || 'draft',
        publicationTarget: data.publication_target || 'db_and_git',
        failOnMissingDeclaredOutput: data.fail_on_missing_declared_output ?? prev.failOnMissingDeclaredOutput,
        failOnMissingExpectedMutation: data.fail_on_missing_expected_mutation ?? prev.failOnMissingExpectedMutation,
        responseSchema: data.response_schema ? JSON.stringify(data.response_schema, null, 2) : prev.responseSchema,
      }));
      setFlowVersion(data.version || value);
      setBaseVersion(data.version || value);
      setCurrentStatus(data.status || '');
      setResourceVersion(data.resource_version ?? 0);
      setNodes(parsedNodes);
      setSelectedNodeId(parsedNodes[0]?.id || null);
      setIsEditing(keepEditing);
    } catch (err) {
      message.error(err.message || 'Failed to load selected version');
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
      `start_node_id: ${flowMeta.startNodeId}`,
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
      lines.push(`    type: ${data.nodeKind || data.type || ''}`);
      if ((data.nodeKind || data.type) !== 'command') {
        if (data.executionContext && data.executionContext.length > 0) {
          lines.push('    execution_context:');
          data.executionContext.forEach((entry) => {
            lines.push(`      - type: ${entry.type}`);
            lines.push(`        required: ${!!entry.required}`);
            if (entry.scope) {
              lines.push(`        scope: ${entry.scope}`);
            }
            if (entry.transfer_mode) {
              lines.push(`        transfer_mode: ${entry.transfer_mode}`);
            }
            if (entry.node_id) {
              lines.push(`        node_id: ${entry.node_id}`);
            }
            if (entry.path) {
              lines.push(`        path: ${entry.path}`);
            }
          });
        } else {
          lines.push('    execution_context: []');
        }
      }
      if (data.instruction) {
        lines.push('    instruction: |');
        data.instruction.split('\n').forEach((line) => lines.push(`      ${line}`));
      }
      if ((data.nodeKind || data.type) === 'ai' || (data.nodeKind || data.type) === 'command') {
        lines.push(`    checkpoint_before_run: ${!!data.checkpointBeforeRun}`);
      }
      if (data.skillRefs && data.skillRefs.length > 0) {
        lines.push('    skill_refs:');
        data.skillRefs.forEach((ref) => lines.push(`      - ${ref}`));
      }
      if ((data.nodeKind || data.type) !== 'command' && data.responseSchema && data.responseSchema.trim()) {
        lines.push('    response_schema:');
        data.responseSchema.trim().split('\n').forEach((line) => lines.push(`      ${line}`));
      }
      if (data.producedArtifacts && data.producedArtifacts.length > 0) {
        lines.push('    produced_artifacts:');
        data.producedArtifacts.forEach((artifact) => {
          lines.push('      -');
          const artifactScope = artifact.scope;
          if (artifactScope) {
            lines.push(`        scope: ${artifactScope}`);
          }
          lines.push(`        path: ${artifact.path}`);
          lines.push(`        required: ${!!artifact.required}`);
          if (artifact.modifiable) {
            lines.push('        modifiable: true');
          }
        });
      } else {
        lines.push('    produced_artifacts: []');
      }
      if (data.expectedMutations && data.expectedMutations.length > 0) {
        lines.push('    expected_mutations:');
        data.expectedMutations.forEach((mutation) => {
          lines.push('      -');
          if (mutation.scope) {
            lines.push(`        scope: ${mutation.scope}`);
          }
          lines.push(`        path: ${mutation.path}`);
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
      if (data.onRework && data.onRework.nextNode) {
        lines.push('    on_rework:');
        lines.push(`      keep_changes: ${!!data.onRework.keepChanges}`);
        lines.push(`      next_node: ${data.onRework.nextNode}`);
      }
    });
    return lines.join('\n');
  };

  const saveFlow = async ({ publish, release = false, publicationTargetOverride = null, publishModeOverride = null }) => {
    if (!flowMeta.flowId) {
      message.error('Flow ID is required');
      return false;
    }
    if (!flowMeta.title.trim()) {
      message.error('Name is required');
      return false;
    }
    if (!flowMeta.startNodeId.trim()) {
      message.error('Start node is required');
      return false;
    }
    if (!flowMeta.codingAgent.trim()) {
      message.error('coding_agent is required');
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
          coding_agent: flowMeta.codingAgent,
          team_code: flowMeta.teamCode?.trim(),
          platform_code: flowMeta.platformCode,
          tags: flowMeta.tags || [],
          flow_kind: flowMeta.flowKind,
          risk_level: flowMeta.riskLevel,
          environment: flowMeta.environment,
          visibility: flowMeta.visibility,
          lifecycle_status: flowMeta.lifecycleStatus,
          flow_yaml: flowYaml,
          publish,
          publication_target: publicationTargetOverride || flowMeta.publicationTarget,
          publish_mode: publishModeOverride || flowMeta.publishMode,
          release,
          base_version: baseVersion || undefined,
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
        teamCode: response.team_code || prev.teamCode,
        platformCode: response.platform_code || prev.platformCode,
        tags: response.tags || prev.tags,
        flowKind: response.flow_kind || prev.flowKind,
        riskLevel: response.risk_level || prev.riskLevel,
        environment: response.environment || prev.environment,
        visibility: response.visibility || prev.visibility,
        lifecycleStatus: response.lifecycle_status || prev.lifecycleStatus,
        approvalStatus: response.approval_status || prev.approvalStatus,
        contentSource: response.content_source || prev.contentSource,
        publicationStatus: response.publication_status || prev.publicationStatus,
        publicationTarget: response.publication_target || prev.publicationTarget,
        publishMode: publishModeOverride || prev.publishMode,
        failOnMissingDeclaredOutput: response.fail_on_missing_declared_output ?? prev.failOnMissingDeclaredOutput,
        failOnMissingExpectedMutation: response.fail_on_missing_expected_mutation ?? prev.failOnMissingExpectedMutation,
        responseSchema: response.response_schema
          ? JSON.stringify(response.response_schema, null, 2)
          : prev.responseSchema,
      }));
      setFlowVersion(response.version || flowVersion);
      setCurrentStatus(response.status || currentStatus);
      setBaseVersion(response.version || baseVersion);
      setResourceVersion(response.resource_version ?? resourceVersion);
      message.success(publish ? 'Publication requested' : 'Draft saved');
      if (response.flow_id || flowMeta.flowId) {
        loadFlowVersions(response.flow_id || flowMeta.flowId);
      }
      return true;
    } catch (err) {
      message.error(toRussianError(err?.message, 'Failed to save Flow'));
      return false;
    }
  };

  useEffect(() => {
    if (isCreateMode) {
      setFlowMeta(emptyFlow);
      setNodes([]);
      setSelectedNodeId(null);
      setFlowVersion('0.1');
      setBaseVersion('0.1');
      setCurrentStatus('draft');
      setResourceVersion(0);
      setVersionOptions([]);
      setIsEditing(true);
      return;
    }
    if (!flowId) {
      return;
    }
    setNodes([]);
    setSelectedNodeId(null);
    setIsEditing(false);
    loadFlow(flowId);
    loadFlowVersions(flowId);
  }, [flowId, isCreateMode, setNodes]);

  useEffect(() => {
    loadRulesCatalog();
    loadSkillsCatalog();
  }, []);

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

  const startDraftFromPublished = () => {
    const sourceVersion = flowVersion || baseVersion || latestPublishedVersion || DEFAULT_VERSION;
    setBaseVersion(sourceVersion);
    setCurrentStatus('draft');
    setIsEditing(true);
    if (draftForMajor) {
      setResourceVersion(draftForMajor.resourceVersion ?? 0);
      setFlowVersion(draftForMajor.value || nextDraftVersion);
      return;
    }
    setResourceVersion(0);
    setFlowVersion(nextDraftVersion);
  };

  const openPublishDialog = () => {
    setPublishVariant('minor');
    setPublishDialogTarget(flowMeta.publicationTarget || 'db_and_git');
    setPublishDialogMode(flowMeta.publishMode || 'pr');
    setPublishDialogOpen(true);
  };

  const confirmPublish = async () => {
    const ok = await saveFlow({
      publish: true,
      release: publishVariant === 'major',
      publicationTargetOverride: publishDialogTarget,
      publishModeOverride: publishDialogMode,
    });
    if (ok) {
      setPublishDialogOpen(false);
    }
  };

  return (
    <div className="flow-editor-page">
      <div className="page-header">
        <Title level={3} style={{ margin: 0 }}>Flow editor</Title>
        <Space>
          {!isEditing ? (
            currentStatus === 'draft' ? (
              <Button type="default" onClick={() => setIsEditing(true)}>
                Edit
              </Button>
            ) : (
              <Button type="default" onClick={startDraftFromPublished}>
                {`Create new version ${nextDraftVersion}`}
              </Button>
            )
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
              Save
            </Button>
          )}
          <Button type="default" onClick={openPublishDialog}>Request publication</Button>
        </Space>
      </div>

      <div className="split-layout flow-editor-layout">
        <Card className="flow-canvas-card">
          <div className="flow-canvas-header">
            <div>
              <Text className="muted">Canvas</Text>
              <div className="mono">{flowMeta.flowId || 'new-flow'}@{flowVersionLabel}</div>
            </div>
            <Space>
              <Button
                type="default"
                onClick={() => setShowYaml((prev) => !prev)}
              >
                {showYaml ? 'Show designer' : 'YAML view'}
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
                <div className="flow-context-title">Add node</div>
                <div className="flow-context-section">
                  {NODE_TYPE_OPTIONS.map((option) => (
                    <Button
                      key={option.key}
                      type="default"
                      className="flow-context-add-btn"
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
                      type="default"
                      onClick={() => {
                        const targetId = contextMenu.nodeId || selectedNodeId;
                        setContextMenu(null);
                        Modal.confirm({
                          title: 'Delete node?',
                          content: `Node ${targetId} will be removed with its links.`,
                          okText: 'Delete',
                          cancelText: 'Cancel',
                          okButtonProps: { danger: true },
                          onOk: () => removeNodeById(targetId),
                        });
                      }}
                    >
                      Delete node
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
              <div className="rule-fields-header">
                <Title level={5} style={{ margin: 0 }}>Flow data</Title>
                {renderVersionSelector()}
              </div>
              <div className="form-stack">
              <div>
                <Text className="muted">{requiredLabel('Coding agent')}</Text>
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
                    placeholder="Select coding agent"
                    title="Для какого coding-agent выполняется flow."
                  />
                </div>
              </div>
              <div>
                <Text className="muted">{requiredLabel('Name')}</Text>
                <div className="field-control">
                  <Input
                    value={flowMeta.title}
                    disabled={isReadOnly}
                    onChange={(event) => updateFlowMeta({ title: event.target.value })}
                    title="Короткое отображаемое имя flow."
                  />
                </div>
              </div>
              <div>
                <Text className="muted">{requiredLabel('ID Flow')}</Text>
                <div className="field-control">
                  <Input
                    value={flowMeta.flowId}
                    disabled={isReadOnly}
                    onChange={(event) => updateFlowMeta({ flowId: event.target.value })}
                    title="Стабильный идентификатор flow для canonical_name и ссылок."
                  />
                </div>
              </div>
              <div>
                <Text className="muted">Description</Text>
                <div className="field-control">
                  <Input.TextArea
                    rows={3}
                    value={flowMeta.description}
                    disabled={isReadOnly}
                    onChange={(event) => updateFlowMeta({ description: event.target.value })}
                    title="Краткое описание сценария flow и его назначения."
                  />
                </div>
              </div>
              <div>
                <Text className="muted">{requiredLabel('Team code')}</Text>
                <div className="field-control">
                  <Input
                    value={flowMeta.teamCode}
                    disabled={isReadOnly}
                    onChange={(event) => updateFlowMeta({ teamCode: event.target.value })}
                    title="Код команды-владельца flow."
                  />
                </div>
              </div>
              <div>
                <Text className="muted">{requiredLabel('Platform')}</Text>
                <div className="field-control">
                  <Select
                    value={flowMeta.platformCode || undefined}
                    disabled={isReadOnly}
                    onChange={(value) => updateFlowMeta({ platformCode: value })}
                    options={platformOptions}
                    placeholder="Select platform"
                    title="Платформа применения flow: FRONT, BACK или DATA."
                  />
                </div>
              </div>
              <div>
                <Text className="muted">Tags</Text>
                <div className="field-control">
                  <Select
                    mode="tags"
                    value={flowMeta.tags || []}
                    disabled={isReadOnly}
                    onChange={(value) => updateFlowMeta({ tags: value })}
                    placeholder="Add tags"
                    title="Теги для фильтрации и поиска flow."
                  />
                </div>
              </div>
              <div>
                <Text className="muted">Flow kind</Text>
                <div className="field-control">
                  <Select
                    value={flowMeta.flowKind || undefined}
                    disabled={isReadOnly}
                    onChange={(value) => updateFlowMeta({ flowKind: value })}
                    options={flowKindOptions}
                    placeholder="Select flow kind"
                    title="Тип flow (orchestration/governance/analysis/delivery)."
                  />
                </div>
              </div>
              <div>
                <Text className="muted">Risk level</Text>
                <div className="field-control">
                  <Select
                    value={flowMeta.riskLevel || undefined}
                    disabled={isReadOnly}
                    onChange={(value) => updateFlowMeta({ riskLevel: value })}
                    options={riskLevelOptions}
                    placeholder="Select risk level"
                    title="Уровень риска исполнения flow."
                  />
                </div>
              </div>
              <div>
                <Text className="muted">Environment</Text>
                <div className="field-control">
                  <Select
                    value={flowMeta.environment || undefined}
                    disabled={isReadOnly}
                    onChange={(value) => updateFlowMeta({ environment: value })}
                    options={environmentOptions}
                    placeholder="Select environment"
                    title="Среда использования версии: dev или prod."
                  />
                </div>
              </div>
              <div>
                <Text className="muted">Visibility</Text>
                <div className="field-control">
                  <Select
                    value={flowMeta.visibility || undefined}
                    disabled={isReadOnly}
                    onChange={(value) => updateFlowMeta({ visibility: value })}
                    options={visibilityOptions}
                    placeholder="Select visibility"
                    title="Видимость версии flow внутри платформы."
                  />
                </div>
              </div>
              <div>
                <Text className="muted">Lifecycle status</Text>
                <div className="field-control">
                  <Select
                    value={flowMeta.lifecycleStatus || undefined}
                    disabled={isReadOnly}
                    onChange={(value) => updateFlowMeta({ lifecycleStatus: value })}
                    options={lifecycleOptions}
                    placeholder="Select lifecycle status"
                    title="Состояние жизненного цикла версии."
                  />
                </div>
              </div>
              {!isCreateMode && (
                <div>
                  <Text className="muted">Approval status</Text>
                  <div className="mono">{flowMeta.approvalStatus || 'draft'}</div>
                </div>
              )}
              {!isCreateMode && (
                <div>
                  <Text className="muted">Content source</Text>
                  <div className="mono">{flowMeta.contentSource || 'db'}</div>
                </div>
              )}
              {!isCreateMode && (
                <div>
                  <Text className="muted">Publication status</Text>
                  <div className="mono">{flowMeta.publicationStatus || 'draft'}</div>
                </div>
              )}
              <div>
                <Text className="muted">{requiredLabel('Start node')}</Text>
                <div className="field-control">
                  <Select
                    value={flowMeta.startNodeId}
                    disabled={isReadOnly}
                    onChange={handleStartNodeChange}
                    options={nodes.map((node) => ({ value: node.id, label: node.id }))}
                    title="Узел, с которого начинается исполнение flow."
                  />
                </div>
              </div>
            </div>

            <Divider />

            <div className="linked-block">
              <div className="linked-header">
                <Title level={5}>Linked rules</Title>
              </div>
              <Select
                mode="multiple"
                options={ruleOptions}
                value={flowMeta.ruleRefs}
                disabled={isReadOnly}
                onChange={(value) => updateFlowMeta({ ruleRefs: value })}
                placeholder="Select rules"
              />
              <List
                dataSource={flowMeta.ruleRefs}
                locale={{ emptyText: 'No linked rules' }}
                renderItem={(ref, index) => {
                  const rule = rulesCatalog.find((item) => item.canonical === ref);
                  const description = rule?.description || '';
                  const snippet = description.length > 80
                    ? `${description.slice(0, 80)}...`
                    : description;
                  return (
                    <List.Item
                      className="linked-item"
                    >
                      <div className="linked-rule-row">
                        <span className="mono linked-rule-name">{ref}</span>
                      </div>
                      {snippet && (
                        <Text type="secondary" className="linked-rule-description">
                          {snippet}
                        </Text>
                      )}
                    </List.Item>
                  );
                }}
              />
            </div>

            <Divider />

            <div className="form-stack">
              <div className="switch-row">
                <Text className="muted">Stop Flow if expected output is missing</Text>
                <Switch
                  checked={flowMeta.failOnMissingDeclaredOutput}
                  disabled={isReadOnly}
                  onChange={(checked) => updateFlowMeta({ failOnMissingDeclaredOutput: checked })}
                />
              </div>
              <div className="switch-row">
                <Text className="muted">Stop Flow if expected mutation is missing</Text>
                <Switch
                  checked={flowMeta.failOnMissingExpectedMutation}
                  disabled={isReadOnly}
                  onChange={(checked) => updateFlowMeta({ failOnMissingExpectedMutation: checked })}
                />
              </div>
              <div>
                <Text className="muted">Response schema (optional)</Text>
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
              <div className="rule-fields-header">
                <Title level={5} style={{ margin: 0 }}>Selected node</Title>
                {renderVersionSelector()}
              </div>
              <div className="form-stack">
                <div>
                  <Text className="muted">{requiredLabel('Node ID')}</Text>
                  <div className="field-control">
                    <Input
                      value={nodeIdDraft}
                      disabled={!canEditNodeId}
                      title="Уникальный идентификатор узла внутри flow."
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
                  <Text className="muted">{requiredLabel('Name')}</Text>
                  <div className="field-control">
                    <Input
                      value={selectedNode.data.title}
                      disabled={isReadOnly}
                      title="Короткое отображаемое имя узла."
                      onChange={(event) => updateSelectedNode({ title: event.target.value })}
                    />
                  </div>
                </div>
                <div>
                  <Text className="muted">Description</Text>
                  <div className="field-control">
                    <Input.TextArea
                      rows={2}
                      value={selectedNode.data.description}
                      disabled={isReadOnly}
                      title="Описание назначения узла."
                      onChange={(event) => updateSelectedNode({ description: event.target.value })}
                    />
                  </div>
                </div>
                <div>
                  <Text className="muted">{requiredLabel('Node type')}</Text>
                  <div className="field-control">
                    <Select
                      value={selectedNode.data.nodeKind || selectedNode.data.type}
                      disabled={isReadOnly}
                      title="Тип узла определяет его поведение в runtime."
                      onChange={(value) => {
                        updateSelectedNode({
                          nodeKind: value,
                          type: value,
                          skillRefs: value === 'ai' ? selectedNode.data.skillRefs || [] : [],
                          onSubmit: value === 'human_input' ? selectedNode.data.onSubmit || '' : '',
                          onApprove: value === 'human_approval' ? selectedNode.data.onApprove || '' : '',
                          onRework: value === 'human_approval'
                            ? selectedNode.data.onRework || { ...DEFAULT_REWORK }
                            : { ...DEFAULT_REWORK },
                          executionContext: value === 'command' ? [] : (selectedNode.data.executionContext || []),
                          responseSchema: value === 'command' ? '' : (selectedNode.data.responseSchema || ''),
                          onSuccess: value === 'terminal' ? '' : selectedNode.data.onSuccess || '',
                          onFailure: value === 'ai' || value === 'command' ? (selectedNode.data.onFailure || '') : '',
                          checkpointBeforeRun: value === 'ai' || value === 'command'
                            ? !!selectedNode.data.checkpointBeforeRun
                            : false,
                        });
                      }}
                      options={[
                        { value: 'ai', label: 'AI Executor' },
                        { value: 'command', label: 'Command Executor' },
                        { value: 'human_input', label: 'Human Input Gate' },
                        { value: 'human_approval', label: 'Human Approval Gate' },
                        { value: 'terminal', label: 'Terminal' },
                      ]}
                    />
                  </div>
                </div>
                {showExecutionContextEditor && (
                  <>
                    <Divider />
                    <div>
                      <Title level={5}>Execution context</Title>
                      <Text className="muted">Node input data</Text>
                      <div className="context-list">
                        {(selectedNode.data.executionContext || []).map((entry, index) => (
                          <div key={`${entry.type}-${index}`} className="context-row">
                            <div className="context-row-header">
                              <Select
                                value={entry.type}
                                options={EXECUTION_CONTEXT_TYPES}
                                disabled={isReadOnly}
                                title="Тип входного контекста узла."
                                onChange={(value) => updateSelectedNodeList(
                                  'executionContext',
                                  index,
                                  { type: value, scope: entry.scope || 'run', transfer_mode: entry.transfer_mode || 'by_ref' }
                                )}
                              />
                              <Button
                                size="small"
                                type="default"
                                danger
                                className="artifact-delete-btn"
                                icon={<DeleteOutlined />}
                                disabled={isReadOnly}
                                onClick={() => removeSelectedNodeListItem('executionContext', index)}
                              />
                            </div>
                            {entry.type === 'artifact_ref' && (
                              <div className="context-row-fields">
                                <Select
                                  value={entry.scope || 'run'}
                                  options={SCOPE_OPTIONS}
                                  disabled={isReadOnly}
                                  title="Область поиска артефакта: run или project."
                                  onChange={(value) => updateSelectedNodeList('executionContext', index, {
                                    scope: value,
                                    node_id: value === 'project' ? undefined : entry.node_id,
                                  })}
                                />
                                {selectedNodeKind === 'ai' && (
                                  <Select
                                    value={entry.transfer_mode || 'by_ref'}
                                    options={TRANSFER_MODE_OPTIONS}
                                    disabled={isReadOnly}
                                    title="Режим передачи артефакта в AI-контекст."
                                    onChange={(value) => updateSelectedNodeList('executionContext', index, {
                                      transfer_mode: value,
                                    })}
                                  />
                                )}
                                {(entry.scope || 'run') === 'run' && (
                                  <Select
                                    value={entry.node_id || undefined}
                                    options={nodeIdOptions.filter((opt) => opt.value !== selectedNode.id)}
                                    placeholder="source-node"
                                    disabled={isReadOnly}
                                    title="Узел-источник артефакта в текущем run."
                                    allowClear
                                    popupClassName="node-source-select-dropdown"
                                    popupMatchSelectWidth
                                    getPopupContainer={(trigger) => trigger.parentElement || document.body}
                                    onChange={(value) => updateSelectedNodeList('executionContext', index, { node_id: value || '' })}
                                  />
                                )}
                                <Input
                                  className="context-field-full"
                                  value={entry.path || ''}
                                  placeholder="file name"
                                  disabled={isReadOnly}
                                  title="Путь к входному артефакту."
                                  onChange={(event) => updateSelectedNodeList('executionContext', index, { path: event.target.value })}
                                />
                              </div>
                            )}
                          </div>
                        ))}
                        <Button
                          type="default"
                          icon={<PlusOutlined />}
                          disabled={isReadOnly}
                          onClick={() =>
                            addSelectedNodeListItem('executionContext', {
                              type: 'artifact_ref',
                              required: true,
                              scope: 'run',
                              path: '',
                              transfer_mode: 'by_ref',
                            })
                          }
                        >
                          Add context
                        </Button>
                        </div>
                    </div>
                  </>
                )}

                {selectedNodeKind === 'ai' && (
                  <>
                    <div>
                      <Text className="muted">Instruction</Text>
                      <div className="field-control">
                        <Input.TextArea
                          rows={3}
                          value={selectedNode.data.instruction}
                          disabled={isReadOnly}
                          title="Инструкция для AI-узла."
                          onChange={(event) => updateSelectedNode({ instruction: event.target.value })}
                        />
                      </div>
                    </div>
                    <div>
                      <Text className="muted">Response schema (optional, JSON)</Text>
                      <div className="field-control schema-editor-wrap">
                        <Editor
                          height="120px"
                          defaultLanguage="json"
                          beforeMount={configureMonacoThemes}
                          theme={monacoTheme}
                          value={selectedNode.data.responseSchema || ''}
                          onChange={(value) => updateSelectedNode({ responseSchema: value ?? '' })}
                          options={{
                            readOnly: isReadOnly,
                            minimap: { enabled: false },
                            lineNumbers: 'off',
                            scrollBeyondLastLine: false,
                            folding: false,
                            fontSize: 12,
                            tabSize: 2,
                            automaticLayout: true,
                            wordWrap: 'on',
                            overviewRulerLanes: 0,
                            hideCursorInOverviewRuler: true,
                            scrollbar: { vertical: 'auto', horizontal: 'hidden' },
                          }}
                        />
                      </div>
                    </div>
                  </>
                )}

                {selectedNodeKind === 'human_input' && (
                  <div>
                    <Text className="muted">Instruction for user</Text>
                    <div className="field-control">
                      <Input.TextArea
                        rows={3}
                        value={selectedNode.data.instruction}
                        disabled={isReadOnly}
                        title="Текст инструкции, которую увидит пользователь на шаге ввода."
                        onChange={(event) => updateSelectedNode({ instruction: event.target.value })}
                      />
                    </div>
                  </div>
                )}

                {selectedNodeKind === 'command' && (
                  <>
                    <Divider />
                    <div>
                      <Text className="muted">Bash commands</Text>
                      <div className="field-control">
                        <Input.TextArea
                          rows={4}
                          value={selectedNode.data.instruction}
                          placeholder={'#!/usr/bin/env bash\nset -euo pipefail\necho "Hello"'}
                          disabled={isReadOnly}
                          title="Команды, выполняемые command-узлом."
                          onChange={(event) => updateSelectedNode({ instruction: event.target.value })}
                        />
                      </div>
                    </div>
                  </>
                )}

                {(selectedNodeKind === 'ai' || selectedNodeKind === 'command') && (
                  <div>
                    <Text className="muted">Save node state before launch</Text>
                    <div className="field-control">
                      <Checkbox
                        checked={!!selectedNode.data.checkpointBeforeRun}
                        disabled={isReadOnly}
                        onChange={(event) => updateSelectedNode({ checkpointBeforeRun: event.target.checked })}
                      >
                        Yes/No
                      </Checkbox>
                    </div>
                  </div>
                )}

                {selectedNodeKind === 'ai' && (
                  <>
                    <Divider />
                    <div className="linked-block">
                      <div className="linked-header">
                        <Title level={5}>Linked skills</Title>
                        <Text className="muted">AI only</Text>
                      </div>
                      <Select
                        mode="multiple"
                        options={skillOptions}
                        value={selectedNode.data.skillRefs || []}
                        disabled={isReadOnly}
                        onChange={(value) => updateSelectedNode({ skillRefs: value })}
                        placeholder="Select skills"
                      />
                      <List
                        dataSource={selectedNode.data.skillRefs || []}
                        locale={{ emptyText: 'No linked skills' }}
                        renderItem={(ref) => {
                          const skill = skillsCatalog.find((item) => item.canonical === ref);
                          const description = skill?.description || '';
                          const snippet = description.length > 80
                            ? `${description.slice(0, 80)}...`
                            : description;
                          return (
                            <List.Item className="linked-item">
                              <div className="linked-rule-row">
                                <span className="mono linked-rule-name">{ref}</span>
                              </div>
                              {snippet && (
                                <Text type="secondary" className="linked-rule-description">
                                  {snippet}
                                </Text>
                              )}
                            </List.Item>
                          );
                        }}
                      />
                    </div>
                  </>
                )}

                {selectedNodeKind !== 'terminal' && (
                  <>
                    <Divider />

                    <div>
                      <Title level={5}>Expected outputs</Title>
                      <Text className="muted">Generated artifacts</Text>
                      <div className="context-list">
                        {(selectedNode.data.producedArtifacts || []).map((entry, index) => (
                          <div key={`artifact-${index}`} className="context-row artifact-context-row">
                            <div className="context-row-header">
                              <Text className="muted">Artifact #{index + 1}</Text>
                              <Button
                                size="small"
                                type="default"
                                danger
                                className="artifact-delete-btn"
                                icon={<DeleteOutlined />}
                                disabled={isReadOnly}
                                onClick={() => removeSelectedNodeListItem('producedArtifacts', index)}
                              />
                            </div>
                            <div className="context-row-fields artifact-row-fields">
                              <Select
                                value={entry.scope || 'run'}
                                options={SCOPE_OPTIONS}
                                disabled={isReadOnly}
                                title="Область, где будет создан артефакт."
                                onChange={(value) => updateSelectedNodeList('producedArtifacts', index, { scope: value })}
                              />
                              <Select
                                value={entry.modifiable === true ? 'yes' : 'no'}
                                options={MODIFIABLE_OPTIONS}
                                disabled={isReadOnly}
                                title="Можно ли редактировать артефакт на human_input шаге."
                                onChange={(value) => updateSelectedNodeList('producedArtifacts', index, { modifiable: value === 'yes' })}
                              />
                              <Input
                                className="context-field-full artifact-path-input"
                                value={entry.path || ''}
                                placeholder="path"
                                disabled={isReadOnly}
                                title="Путь создаваемого артефакта."
                                onChange={(event) =>
                                  updateSelectedNodeList('producedArtifacts', index, { path: event.target.value })
                                }
                              />
                            </div>
                          </div>
                        ))}
                        <Button
                          type="default"
                          icon={<PlusOutlined />}
                          disabled={isReadOnly}
                          onClick={() => addSelectedNodeListItem('producedArtifacts', {
                            path: '',
                            required: true,
                            scope: 'run',
                            modifiable: false,
                          })}
                        >
                          Add artifact
                        </Button>
                      </div>

                      <Text className="muted" style={{ marginTop: 12 }}>Expected changes</Text>
                      <div className="context-list">
                        {(selectedNode.data.expectedMutations || []).map((entry, index) => (
                          <div key={`mutation-${index}`} className="mutation-row">
                            <Select
                              value={entry.scope || 'project'}
                              options={SCOPE_OPTIONS}
                              disabled={isReadOnly}
                              title="Область применения ожидаемого изменения."
                              onChange={(value) => updateSelectedNodeList('expectedMutations', index, { scope: value })}
                            />
                            <Input
                              value={entry.path || ''}
                              placeholder="path"
                              disabled={isReadOnly}
                              title="Путь файла, который должен быть изменён."
                              onChange={(event) => updateSelectedNodeList('expectedMutations', index, { path: event.target.value })}
                            />
                            <Button
                              size="small"
                              type="default"
                              danger
                              icon={<DeleteOutlined />}
                              disabled={isReadOnly}
                              onClick={() => removeSelectedNodeListItem('expectedMutations', index)}
                            />
                          </div>
                        ))}
                        <Button
                          type="default"
                          icon={<PlusOutlined />}
                          disabled={isReadOnly}
                          onClick={() => addSelectedNodeListItem('expectedMutations', { path: '', required: true, scope: 'project' })}
                        >
                          Add change
                        </Button>
                      </div>
                    </div>

                    <Divider />

                    <div>
                      <Title level={5}>Transitions</Title>
                      {(selectedNodeKind === 'ai' || selectedNodeKind === 'command') && (
                        <>
                          <div className="transition-block">
                            <Text className="muted mono">on_success</Text>
                            <div className="field-control">
                              <Select
                                value={selectedNode.data.onSuccess || undefined}
                                disabled={isReadOnly}
                                allowClear
                                options={nodeIdOptions}
                                placeholder="Select node"
                                title="Узел перехода при успешном завершении."
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
                                placeholder="Select node"
                                title="Узел перехода при ошибке."
                                onChange={(value) => updateSelectedNode({ onFailure: value || '' })}
                              />
                            </div>
                          </div>
                        </>
                      )}
                      {selectedNodeKind === 'human_input' && (
                        <div className="transition-block">
                          <Text className="muted mono">on_submit</Text>
                          <div className="field-control">
                            <Select
                              value={selectedNode.data.onSubmit || undefined}
                              disabled={isReadOnly}
                              allowClear
                              options={nodeIdOptions}
                              placeholder="Select node"
                              title="Узел перехода после пользовательского ввода."
                              onChange={(value) => updateSelectedNode({ onSubmit: value || '' })}
                            />
                          </div>
                        </div>
                      )}
                      {selectedNodeKind === 'human_approval' && (
                        <>
                          <div className="transition-block">
                            <Text className="muted mono">on_approve</Text>
                            <div className="field-control">
                              <Select
                                value={selectedNode.data.onApprove || undefined}
                                disabled={isReadOnly}
                                allowClear
                                options={nodeIdOptions}
                                placeholder="Select node"
                                title="Узел перехода после approve."
                                onChange={(value) => updateSelectedNode({ onApprove: value || '' })}
                              />
                            </div>
                          </div>
                          <div className="transition-block">
                            <Space size="middle" align="center">
                              <Text className="muted mono">on_rework</Text>
                              <Checkbox
                                checked={!!(selectedNode.data.onRework || DEFAULT_REWORK).keepChanges}
                                disabled={isReadOnly}
                                onChange={(event) => {
                                  const current = selectedNode.data.onRework || DEFAULT_REWORK;
                                  updateSelectedNode({
                                    onRework: {
                                      ...current,
                                      keepChanges: event.target.checked,
                                    },
                                  });
                                }}
                              >
                                keep_changes
                              </Checkbox>
                            </Space>
                            <div className="field-control">
                              <Select
                                value={(selectedNode.data.onRework || DEFAULT_REWORK).nextNode || undefined}
                                disabled={isReadOnly}
                                allowClear
                                options={nodeIdOptions}
                                placeholder="Select node"
                                title="Узел перехода при rework."
                                onChange={(value) => {
                                  const current = selectedNode.data.onRework || DEFAULT_REWORK;
                                  updateSelectedNode({
                                    onRework: {
                                      ...current,
                                      nextNode: value || '',
                                    },
                                  });
                                }}
                              />
                            </div>
                          </div>
                        </>
                      )}
                    </div>
                  </>
                )}
              </div>
            </Card>
          )}

          <Card className="flow-panel-card" style={{ marginTop: 16 }}>
            <Title level={5}>Validation</Title>
            {validationErrors.length === 0 ? (
              <div className="card-muted">No validation errors found.</div>
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
        title="Request publication"
        open={publishDialogOpen}
        onCancel={() => setPublishDialogOpen(false)}
        onOk={confirmPublish}
        okText="Request"
        cancelText="Cancel"
      >
        <div style={{ display: 'grid', gap: 12 }}>
          <div>
            <Text className="muted">Version strategy</Text>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              value={publishVariant}
              onChange={setPublishVariant}
              options={[
                { value: 'minor', label: publishLabel },
                { value: 'major', label: releaseLabel },
              ]}
            />
          </div>
          <div>
            <Text className="muted">Publication target</Text>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              value={publishDialogTarget}
              onChange={setPublishDialogTarget}
              options={publicationTargetOptions}
            />
          </div>
          <div>
            <Text className="muted">Publish mode</Text>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              value={publishDialogMode}
              onChange={setPublishDialogMode}
              options={publishModeOptions}
            />
          </div>
        </div>
      </Modal>
      <Modal
        open={!!pendingConnection}
        title="Select link type"
        okText="Apply"
        cancelText="Cancel"
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
