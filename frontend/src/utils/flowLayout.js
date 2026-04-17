import { MarkerType, Position } from 'reactflow';
import { parse as parseYaml } from 'yaml';
import dagre from 'dagre';

export const NODE_KIND_META = {
  ai: { label: 'AI Executor', variant: 'executor ai' },
  command: { label: 'Shell Command', variant: 'executor command' },
  human_input: { label: 'Human Input Gate', variant: 'gate input' },
  human_approval: { label: 'Human Approval Gate', variant: 'gate approval' },
  terminal: { label: 'Stop Flow', variant: 'terminal' },
};

export function normalizeNodeKind(node) {
  const raw = node?.node_kind || node?.nodeKind || node?.type || node?.kind || '';
  const normalized = String(raw || '').trim().toLowerCase();
  return normalized === 'external_command' ? 'command' : normalized;
}

export function toNodeData(node, isStart) {
  const kind = normalizeNodeKind(node);
  const rawRework = node.on_rework || node.onRework || null;
  let onRework = null;
  if (rawRework) {
    onRework = { nextNode: rawRework.next_node || rawRework.nextNode || '' };
  }
  const onReworkRoutes = node.on_rework_routes || node.onReworkRoutes || null;
  return {
    id: node.id || '',
    title: node.title || node.id || '',
    nodeKind: kind,
    typeLabel: kind ? kind.replace(/_/g, ' ') : 'node',
    onSuccess: node.on_success || node.onSuccess || '',
    onFailure: node.on_failure || node.onFailure || '',
    onSubmit: node.on_submit || node.onSubmit || '',
    onApprove: node.on_approve || node.onApprove || '',
    onRework: onRework || null,
    onReworkRoutes,
    isStart: !!isStart,
    isTerminal: kind === 'terminal',
  };
}

export function buildEdges(nodes) {
  const edges = [];
  const nodeIds = new Set(nodes.map((node) => node.id));
  const addEdge = (source, target, label, routeType) => {
    if (!source || !target || !nodeIds.has(target)) return;
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
    if (data.onSuccess) addEdge(node.id, data.onSuccess, 'on_success', 'main');
    if (data.onFailure) addEdge(node.id, data.onFailure, 'on_failure', 'outcome');
    if (data.onSubmit) addEdge(node.id, data.onSubmit, 'on_submit', 'main');
    if (data.onApprove) addEdge(node.id, data.onApprove, 'on_approve', 'main');
    if (data.onRework && data.onRework.nextNode) addEdge(node.id, data.onRework.nextNode, 'on_rework', 'rework');
    if (data.onReworkRoutes && typeof data.onReworkRoutes === 'object') {
      Object.entries(data.onReworkRoutes).forEach(([mode, target]) => {
        addEdge(node.id, target, `rework: ${mode}`, 'rework');
      });
    }
  });

  return edges;
}

const NODE_WIDTH = 220;
const NODE_HEIGHT = 90;

function applyDagreLayout(nodes, edges, direction) {
  if (nodes.length === 0) return nodes;
  const graph = new dagre.graphlib.Graph();
  graph.setDefaultEdgeLabel(() => ({}));
  graph.setGraph({ rankdir: direction, nodesep: 80, ranksep: 140 });
  nodes.forEach((node) => graph.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT }));
  edges.forEach((edge) => graph.setEdge(edge.source, edge.target));
  dagre.layout(graph);
  const isHorizontal = direction === 'LR';
  return nodes.map((node) => {
    const layout = graph.node(node.id);
    return {
      ...node,
      targetPosition: isHorizontal ? Position.Left : Position.Top,
      sourcePosition: isHorizontal ? Position.Right : Position.Bottom,
      position: { x: layout.x - NODE_WIDTH / 2, y: layout.y - NODE_HEIGHT / 2 },
    };
  });
}

/**
 * Build ReactFlow-ready layout from a parsed flow model (JSON object).
 * Works with both FlowModel JSON (from flow_snapshot) and parsed YAML objects.
 */
export function buildFlowPreviewFromModel(model, direction = 'TB') {
  if (!model) return { nodes: [], edges: [], rawNodes: [] };
  try {
    const rawNodes = Array.isArray(model?.nodes) ? model.nodes : [];
    const startNodeId = model?.start_node_id || model?.startNodeId || '';
    const nodes = rawNodes.map((node, index) => ({
      id: node.id || `node-${index + 1}`,
      type: 'flowNode',
      position: { x: index * NODE_WIDTH, y: 0 },
      data: toNodeData(node, node.id === startNodeId),
    }));
    const edges = buildEdges(nodes);
    const layoutedNodes = applyDagreLayout(nodes, edges, direction);
    return { nodes: layoutedNodes, edges, rawNodes };
  } catch (_) {
    return { nodes: [], edges: [], rawNodes: [] };
  }
}

/**
 * Build ReactFlow-ready layout from YAML string.
 * Parses YAML then delegates to buildFlowPreviewFromModel.
 */
export function buildFlowPreview(flowYaml, direction = 'TB') {
  if (!flowYaml) return { nodes: [], edges: [], rawNodes: [] };
  try {
    const parsed = parseYaml(flowYaml);
    return buildFlowPreviewFromModel(parsed, direction);
  } catch (_) {
    return { nodes: [], edges: [], rawNodes: [] };
  }
}
