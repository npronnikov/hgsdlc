import { MarkerType } from 'reactflow';
import { parse as parseYaml } from 'yaml';
import dagre from 'dagre';

export const DEFAULT_REWORK = { nextNode: '' };

export function buildEdges(nodes) {
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
      addEdge(node.id, data.onRework.nextNode, 'on_rework', 'rework');
    }
  });

  return edges;
}

export function toNodeData(node, isStart) {
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
      nextNode: rawRework.next_node || rawRework.nextNode || '',
    };
  }
  const defaultCheckpointBeforeRun = kind === 'ai' || kind === 'command';
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
    checkpointBeforeRun: node.checkpoint_before_run ?? node.checkpointBeforeRun ?? defaultCheckpointBeforeRun,
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

export function parseFlowYaml(flowYaml, startNodeId) {
  if (!flowYaml) {
    return [];
  }
  try {
    const parsed = parseYaml(flowYaml);
    const rawNodes = Array.isArray(parsed?.nodes) ? parsed.nodes : [];
    const nodes = rawNodes.map((node, index) => ({
      id: node.id || `node-${index + 1}`,
      type: 'flowNode',
      position: { x: index * 220, y: 0 },
      data: toNodeData(node, node.id === startNodeId),
    }));
    const edges = buildEdges(nodes);
    if (nodes.length === 0) {
      return nodes;
    }

    const NODE_WIDTH = 220;
    const NODE_HEIGHT = 90;
    const graph = new dagre.graphlib.Graph();
    graph.setDefaultEdgeLabel(() => ({}));
    graph.setGraph({ rankdir: 'TB', nodesep: 80, ranksep: 140 });
    nodes.forEach((node) => {
      graph.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
    });
    edges.forEach((edge) => {
      graph.setEdge(edge.source, edge.target);
    });
    dagre.layout(graph);

    return nodes.map((node) => {
      const layout = graph.node(node.id);
      return {
        ...node,
        position: {
          x: layout.x - NODE_WIDTH / 2,
          y: layout.y - NODE_HEIGHT / 2,
        },
      };
    });
  } catch (err) {
    return [];
  }
}
