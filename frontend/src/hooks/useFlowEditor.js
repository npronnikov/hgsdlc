import { useEffect, useMemo, useRef, useState } from 'react';
import { useNodesState } from 'reactflow';
import { Modal, message } from 'antd';
import { apiRequest } from '../api/request.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { toRussianError } from '../utils/errorMessages.js';
import { formatStatusLabel } from '../components/StatusTag.jsx';
import { useThemeMode } from '../theme/ThemeContext.jsx';
import { getMonacoThemeName } from '../utils/monacoTheme.js';
import {
  DEFAULT_VERSION,
  parseMajorMinor,
  getLatestVersion,
  getMaxPublishedMajor,
  getMaxPublishedMinorForMajor,
  getDraftForMajor,
} from '../utils/flowVersionUtils.js';
import { buildEdges, parseFlowYaml, parseFlowYamlWithMeta, DEFAULT_REWORK } from '../utils/flowSerializer.js';
import { validateFlow } from '../utils/flowValidator.js';
import {
  buildHumanInputExecutionContextSyncContracts,
  isExecutionContextListEqual,
  isManagedArtifactListEqual,
  normalizeNodeKind,
} from '../utils/humanInputArtifacts.js';
import { NODE_KIND_META } from '../components/flow/FlowNode.jsx';
import { v4 as uuidv4 } from 'uuid';

const LOCKED_PUBLICATION_STATUSES = new Set(['pending_approval', 'approved', 'publishing', 'published']);

function syncHumanInputManagedOutputs(nodes) {
  const contractsByNode = buildHumanInputExecutionContextSyncContracts(nodes);
  let hasChanges = false;
  const nextNodes = nodes.map((node) => {
    const data = node.data || {};
    if (normalizeNodeKind(data) !== 'human_input') {
      return node;
    }
    const contract = contractsByNode.get(node.id) || {
      syncedExecutionContext: [],
      expectedArtifacts: [],
    };
    const expectedExecutionContext = contract.syncedExecutionContext || [];
    const expectedArtifacts = contract.expectedArtifacts || [];
    const currentExecutionContext = data.executionContext || [];
    const currentArtifacts = data.producedArtifacts || [];
    const executionContextMatches = isExecutionContextListEqual(currentExecutionContext, expectedExecutionContext);
    const outputsMatch = isManagedArtifactListEqual(currentArtifacts, expectedArtifacts);
    if (executionContextMatches && outputsMatch) {
      return node;
    }
    hasChanges = true;
    return {
      ...node,
      data: {
        ...data,
        executionContext: expectedExecutionContext,
        producedArtifacts: expectedArtifacts,
      },
    };
  });
  return hasChanges ? nextNodes : nodes;
}

const emptyFlow = {
  title: '',
  flowId: '',
  description: '',
  startNodeId: '',
  codingAgent: '',
  canonicalName: '',
  ruleRefs: [],
  teamCode: '',
  platformCode: 'FRONT',
  tags: [],
  flowKind: '',
  riskLevel: '',
  scope: 'organization',
  forkedFrom: '',
  lifecycleStatus: 'active',
  publicationStatus: 'draft',
  failOnMissingDeclaredOutput: false,
  failOnMissingExpectedMutation: false,
  responseSchema: '',
};

export function useFlowEditor({ flowId, isCreateMode }) {
  const { user } = useAuth();
  const canManageCatalog = user?.roles?.includes('ADMIN') || user?.roles?.includes('FLOW_CONFIGURATOR');
  const { isDark } = useThemeMode();
  const monacoTheme = getMonacoThemeName(isDark);

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
  const isReadOnly = !isEditing || !canManageCatalog;
  const canEditNodeId = currentStatus === 'draft' && isEditing;

  const filteredRules = rulesCatalog.filter(
    (rule) => (!flowMeta.codingAgent || rule.codingAgent === flowMeta.codingAgent)
      && (rule.lifecycleStatus == null || rule.lifecycleStatus === 'active')
  );
  const ruleOptions = filteredRules.map((rule) => ({
    value: rule.canonical,
    label: `${rule.name} · ${rule.version}${rule.scope === 'team' ? ' (team)' : ''}`,
    disabled: rule.status !== 'published',
  }));

  const filteredSkills = skillsCatalog.filter(
    (skill) => (!flowMeta.codingAgent || skill.codingAgent === flowMeta.codingAgent)
      && (skill.lifecycleStatus == null || skill.lifecycleStatus === 'active')
  );
  const skillOptions = filteredSkills.map((skill) => ({
    value: skill.canonical,
    label: `${skill.name} · ${skill.version}${skill.scope === 'team' ? ' (team)' : ''}`,
    disabled: skill.status !== 'published',
  }));

  const publicationStatusValue = (flowMeta.publicationStatus || '').toLowerCase();
  const hasPublicationRequest = LOCKED_PUBLICATION_STATUSES.has(publicationStatusValue);
  const canEditCurrentDraft = canManageCatalog && currentStatus === 'draft' && !hasPublicationRequest;
  const canDeleteDraft = canManageCatalog
    && !isCreateMode
    && !!flowMeta.flowId
    && !!flowVersion
    && currentStatus === 'draft'
    && !hasPublicationRequest;

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
      setFlowMeta((prev) => ({ ...prev, startNodeId: trimmed }));
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

  const handleFlowIdChange = (value) => {
    if (flowMeta.scope === 'team') {
      const trimmed = value.trim();
      if (trimmed && !trimmed.startsWith('team-')) {
        updateFlowMeta({ flowId: `team-${trimmed}` });
        return;
      }
    }
    updateFlowMeta({ flowId: value });
  };

  const handleScopeChange = (value) => {
    setFlowMeta((prev) => {
      const next = { ...prev, scope: value };
      if (isReadOnly) {
        return next;
      }
      if (value === 'team' && next.flowId && !next.flowId.startsWith('team-')) {
        next.flowId = `team-${next.flowId.trim()}`;
      }
      if (value === 'organization' && next.flowId && next.flowId.startsWith('team-')) {
        next.flowId = next.flowId.replace(/^team-/, '');
      }
      return next;
    });
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
      checkpointBeforeRun: kind === 'ai' || kind === 'command',
      skillRefs: [],
      allowedRoles: [],
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
        scope: rule.scope || 'organization',
        codingAgent: rule.coding_agent,
        status: rule.status,
        lifecycleStatus: rule.lifecycle_status,
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
        scope: skill.scope || 'organization',
        codingAgent: skill.coding_agent,
        status: skill.status,
        lifecycleStatus: skill.lifecycle_status,
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
        canonicalName: data.canonical_name || prev.canonicalName,
        ruleRefs: data.rule_refs || [],
        teamCode: data.team_code || '',
        platformCode: data.platform_code || 'FRONT',
        tags: data.tags || [],
        flowKind: data.flow_kind || '',
        riskLevel: data.risk_level || '',
        scope: data.scope || 'organization',
        forkedFrom: data.forked_from || '',
        lifecycleStatus: data.lifecycle_status || 'active',
        publicationStatus: data.publication_status || (data.status === 'published' ? 'published' : 'draft'),
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
        canonicalName: data.canonical_name || prev.canonicalName,
        ruleRefs: data.rule_refs || [],
        teamCode: data.team_code || '',
        platformCode: data.platform_code || 'FRONT',
        tags: data.tags || [],
        flowKind: data.flow_kind || '',
        riskLevel: data.risk_level || '',
        scope: data.scope || 'organization',
        forkedFrom: data.forked_from || '',
        lifecycleStatus: data.lifecycle_status || 'active',
        publicationStatus: data.publication_status || (data.status === 'published' ? 'published' : 'draft'),
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
    ];
    const desc = flowMeta.description || '';
    if (desc.includes('\n')) {
      lines.push('description: |');
      desc.split('\n').forEach((line) => lines.push(`  ${line}`));
    } else {
      lines.push(`description: ${desc}`);
    }
    lines.push(`start_node_id: ${flowMeta.startNodeId}`);
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
        if (data.description.includes('\n')) {
          lines.push('    description: |');
          data.description.split('\n').forEach((line) => lines.push(`      ${line}`));
        } else {
          lines.push(`    description: ${data.description}`);
        }
      }
      lines.push(`    type: ${data.nodeKind || data.type || ''}`);
      const nodeKind = data.nodeKind || data.type || '';
      if (nodeKind !== 'command') {
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
            if (nodeKind === 'human_input' && entry.type === 'artifact_ref') {
              lines.push(`        modifiable: ${entry.modifiable === true}`);
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
      const normalizedAllowedRoles = (data.allowedRoles || [])
        .filter((role) => typeof role === 'string' && role.trim())
        .map((role) => role.trim());
      if (normalizedAllowedRoles.length > 0) {
        lines.push('    allowed_roles:');
        normalizedAllowedRoles.forEach((role) => lines.push(`      - ${role}`));
      }
      if (nodeKind === 'ai' || nodeKind === 'command') {
        lines.push(`    checkpoint_before_run: ${!!data.checkpointBeforeRun}`);
      }
      if (nodeKind === 'command') {
        if (data.commandEngine) {
          lines.push(`    command_engine: ${data.commandEngine}`);
        }
        if (data.commandSpec && typeof data.commandSpec === 'object') {
          lines.push('    command_spec:');
          if (data.commandSpec.shell) {
            lines.push(`      shell: "${data.commandSpec.shell}"`);
          }
          if (Array.isArray(data.commandSpec.args)) {
            lines.push('      args:');
            data.commandSpec.args.forEach((arg) => {
              if (arg.includes('\n')) {
                lines.push('        - |');
                arg.split('\n').forEach((line) => lines.push(`          ${line}`));
              } else {
                lines.push(`        - "${arg}"`);
              }
            });
          }
        }
        if (data.successExitCodes && data.successExitCodes.length > 0) {
          lines.push(`    success_exit_codes: [${data.successExitCodes.join(', ')}]`);
        }
        if (data.maxFailureTransitions != null) {
          lines.push(`    max_failure_transitions: ${data.maxFailureTransitions}`);
        }
      }
      if (data.skillRefs && data.skillRefs.length > 0) {
        lines.push('    skill_refs:');
        data.skillRefs.forEach((ref) => lines.push(`      - ${ref}`));
      }
      if (nodeKind !== 'command' && data.responseSchema && data.responseSchema.trim()) {
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
        lines.push(`      next_node: ${data.onRework.nextNode}`);
      }
    });
    return lines.join('\n');
  };

  const saveFlow = async ({ publish, release = false }) => {
    if (!canManageCatalog) {
      message.error('Only ADMIN and FLOW_CONFIGURATOR can edit flows');
      return false;
    }
    const publication = (flowMeta.publicationStatus || '').toLowerCase();
    const isLockedAfterPublicationRequest = LOCKED_PUBLICATION_STATUSES.has(publication);
    if (!isCreateMode && isLockedAfterPublicationRequest) {
      message.error('Editing is locked after publication request');
      return false;
    }
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
    const normalizedFlowId = flowMeta.flowId.trim();
    const normalizedRouteFlowId = (flowId || '').trim();
    let effectiveVersion = normalizedFlowId === normalizedRouteFlowId ? (resourceVersion ?? 0) : 0;
    if (effectiveVersion === 0 && normalizedFlowId) {
      try {
        const versions = await apiRequest(`/flows/${normalizedFlowId}/versions`);
        const baseMajor = parseMajorMinor(baseVersion || flowVersion || DEFAULT_VERSION).major;
        const matchingDraft = versions.find((item) => {
          if (item.status !== 'draft') return false;
          const parsed = parseMajorMinor(item.version);
          return parsed.valid && parsed.major === baseMajor;
        });
        if (matchingDraft) {
          effectiveVersion = matchingDraft.resource_version ?? 0;
        }
      } catch (err) {
        // If flow does not exist yet, keep resource_version = 0 for first save.
      }
    }
    const flowYaml = buildFlowYaml();
    try {
      const response = await apiRequest(`/flows/${normalizedFlowId}/save`, {
        method: 'POST',
        headers: {
          'Idempotency-Key': uuidv4(),
        },
        body: JSON.stringify({
          flow_id: normalizedFlowId,
          coding_agent: flowMeta.codingAgent,
          team_code: flowMeta.teamCode?.trim(),
          platform_code: flowMeta.platformCode,
          tags: flowMeta.tags || [],
          flow_kind: flowMeta.flowKind,
          risk_level: flowMeta.riskLevel,
          scope: flowMeta.scope,
          lifecycle_status: flowMeta.lifecycleStatus,
          forked_from: flowMeta.forkedFrom || undefined,
          flow_yaml: flowYaml,
          publish,
          release,
          base_version: baseVersion || undefined,
          resource_version: effectiveVersion,
        }),
      });
      setFlowMeta((prev) => ({
        ...prev,
        flowId: response.flow_id || normalizedFlowId || prev.flowId,
        title: response.title || prev.title,
        description: response.description || prev.description,
        status: response.status || prev.status,
        startNodeId: response.start_node_id || prev.startNodeId,
        codingAgent: response.coding_agent || prev.codingAgent,
        canonicalName: response.canonical_name || prev.canonicalName,
        ruleRefs: response.rule_refs || prev.ruleRefs,
        teamCode: response.team_code || prev.teamCode,
        platformCode: response.platform_code || prev.platformCode,
        tags: response.tags || prev.tags,
        flowKind: response.flow_kind || prev.flowKind,
        riskLevel: response.risk_level || prev.riskLevel,
        scope: response.scope || prev.scope,
        forkedFrom: response.forked_from || prev.forkedFrom,
        lifecycleStatus: response.lifecycle_status || prev.lifecycleStatus,
        publicationStatus: response.publication_status || prev.publicationStatus,
        failOnMissingDeclaredOutput: response.fail_on_missing_declared_output ?? prev.failOnMissingDeclaredOutput,
        failOnMissingExpectedMutation: response.fail_on_missing_expected_mutation ?? prev.failOnMissingExpectedMutation,
        responseSchema: response.response_schema
          ? JSON.stringify(response.response_schema, null, 2)
          : prev.responseSchema,
      }));
      setFlowVersion(response.version || flowVersion);
      setCurrentStatus(response.status || currentStatus);
      setBaseVersion(response.version || baseVersion);
      setResourceVersion(response.resource_version ?? effectiveVersion);
      message.success(publish ? 'Publication requested' : 'Draft saved');
      if (response.flow_id || normalizedFlowId) {
        loadFlowVersions(response.flow_id || normalizedFlowId);
      }
      return true;
    } catch (err) {
      message.error(toRussianError(err?.message, err?.message || 'Failed to save Flow'));
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
      setIsEditing(canManageCatalog);
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
  }, [flowId, isCreateMode, canManageCatalog, setNodes]);

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

  useEffect(() => {
    setNodes((prev) => syncHumanInputManagedOutputs(prev));
  }, [nodes, setNodes]);

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
    if (!canManageCatalog) {
      return;
    }
    if (!latestPublishedVersion) {
      message.error('A published flow is required to create a new version');
      return;
    }
    const sourceVersion = latestPublishedVersion;
    const sourceId = flowMeta.flowId;
    if (sourceId && sourceVersion) {
      updateFlowMeta({ forkedFrom: `${sourceId}@${sourceVersion}` });
    }
    setBaseVersion(sourceVersion);
    setCurrentStatus('draft');
    updateFlowMeta({ publicationStatus: 'draft' });
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
    setPublishDialogOpen(true);
  };

  const deleteCurrentDraft = async () => {
    if (!canDeleteDraft) {
      return;
    }
    Modal.confirm({
      title: 'Delete draft flow?',
      content: `Flow ${flowMeta.flowId}@${flowVersion} will be removed.`,
      okText: 'Delete',
      okButtonProps: { danger: true },
      cancelText: 'Cancel',
      onOk: async () => {
        const currentFlowId = flowMeta.flowId;
        try {
          await apiRequest(`/flows/${currentFlowId}/versions/${flowVersion}/draft`, {
            method: 'DELETE',
          });
          message.success('Draft deleted');
          try {
            await loadFlow(currentFlowId);
            await loadFlowVersions(currentFlowId);
            setIsEditing(false);
          } catch (reloadErr) {
            setFlowMeta({
              ...emptyFlow,
              flowId: currentFlowId,
              scope: currentFlowId?.startsWith('team-') ? 'team' : 'organization',
            });
            setNodes([]);
            setSelectedNodeId(null);
            setFlowVersion(DEFAULT_VERSION);
            setBaseVersion(DEFAULT_VERSION);
            setCurrentStatus('draft');
            setResourceVersion(0);
            setVersionOptions([]);
            setIsEditing(true);
          }
        } catch (err) {
          message.error(toRussianError(err?.message, 'Failed to delete Flow draft'));
        }
      },
    });
  };

  const confirmPublish = async () => {
    const ok = await saveFlow({
      publish: true,
      release: publishVariant === 'major',
    });
    if (ok) {
      setPublishDialogOpen(false);
    }
  };

  const importFlowYaml = (yamlString) => {
    let result;
    try {
      result = parseFlowYamlWithMeta(yamlString);
    } catch (err) {
      message.error(`YAML parse error: ${err.message}`);
      return false;
    }
    if (!result) {
      message.error('Invalid YAML: expected a mapping at the root');
      return false;
    }
    const { meta: parsed, nodes: importedNodes } = result;
    if (!Array.isArray(parsed.nodes) || parsed.nodes.length === 0) {
      message.error('Invalid flow YAML: "nodes" array is missing or empty');
      return false;
    }
    if (importedNodes.length === 0) {
      message.error('Failed to parse nodes from YAML');
      return false;
    }

    setFlowMeta((prev) => ({
      ...prev,
      flowId: isCreateMode ? (parsed.id ?? prev.flowId) : prev.flowId,
      title: parsed.title ?? prev.title,
      description: parsed.description ?? prev.description,
      startNodeId: parsed.start_node_id ?? prev.startNodeId,
      codingAgent: parsed.coding_agent ?? prev.codingAgent,
      platformCode: parsed.platform_code ?? prev.platformCode,
      scope: parsed.scope ?? prev.scope,
      flowKind: parsed.flow_kind ?? prev.flowKind,
      riskLevel: parsed.risk_level ?? prev.riskLevel,
      ruleRefs: Array.isArray(parsed.rule_refs) ? parsed.rule_refs : prev.ruleRefs,
      failOnMissingDeclaredOutput: parsed.fail_on_missing_declared_output ?? prev.failOnMissingDeclaredOutput,
      failOnMissingExpectedMutation: parsed.fail_on_missing_expected_mutation ?? prev.failOnMissingExpectedMutation,
      responseSchema: parsed.response_schema
        ? JSON.stringify(parsed.response_schema, null, 2)
        : prev.responseSchema,
    }));
    setNodes(importedNodes);
    setSelectedNodeId(importedNodes[0]?.id ?? null);

    message.success(`Imported ${importedNodes.length} nodes from YAML`);
    return true;
  };

  return {
    // state
    flowMeta,
    nodes,
    setNodes,
    onNodesChange,
    flowVersion,
    resourceVersion,
    baseVersion,
    currentStatus,
    showYaml,
    setShowYaml,
    selectedNodeId,
    setSelectedNodeId,
    versionOptions,
    nodeIdDraft,
    setNodeIdDraft,
    contextMenu,
    setContextMenu,
    isEditing,
    setIsEditing,
    publishDialogOpen,
    setPublishDialogOpen,
    publishVariant,
    setPublishVariant,
    pendingConnection,
    setPendingConnection,
    routeOptions,
    setRouteOptions,
    routeChoice,
    setRouteChoice,
    // refs
    flowWrapperRef,
    flowInstance,
    setFlowInstance,
    // derived
    edges,
    nodeIdOptions,
    selectedNode,
    selectedNodeKind,
    showExecutionContextEditor,
    validationErrors,
    isReadOnly,
    canEditNodeId,
    ruleOptions,
    skillOptions,
    rulesCatalog,
    skillsCatalog,
    monacoTheme,
    isCreateMode,
    canManageCatalog,
    // version labels
    flowVersionLabel,
    publishLabel,
    releaseLabel,
    nextDraftVersion,
    latestPublishedVersion,
    draftForMajor,
    canEditCurrentDraft,
    canDeleteDraft,
    // handlers
    updateSelectedNode,
    renameSelectedNodeId,
    updateSelectedNodeList,
    addSelectedNodeListItem,
    removeSelectedNodeListItem,
    updateFlowMeta,
    handleFlowIdChange,
    handleScopeChange,
    removeNodeById,
    getRouteOptions,
    applyConnection,
    removeConnection,
    addNode,
    handleVersionSelect,
    buildFlowYaml,
    saveFlow,
    handleStartNodeChange,
    startDraftFromPublished,
    openPublishDialog,
    deleteCurrentDraft,
    confirmPublish,
    importFlowYaml,
  };
}
