import { buildEdges } from './flowSerializer.js';
import {
  buildHumanInputArtifactContracts,
  normalizeNodeKind,
  toDeclaredArtifactCountMap,
  toDeclaredArtifactKeySet,
} from './humanInputArtifacts.js';

export const EXECUTION_CONTEXT_TYPES = [
  { value: 'artifact_ref', label: 'Artifact' },
];

export function validateFlow(nodes, meta, rulesCatalog, skillsCatalog) {
  const errors = [];
  const nodeIds = nodes.map((node) => node.id);
  const uniqueIds = new Set(nodeIds);
  const nodesById = new Map(nodes.map((node) => [node.id, node]));
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
    const kind = normalizeNodeKind(data);
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

  const contractsByNode = buildHumanInputArtifactContracts(nodes);

  nodes.forEach((node) => {
    const data = node.data || {};
    const kind = normalizeNodeKind(data);
    if (kind !== 'human_input') return;
    const contract = contractsByNode.get(node.id) || {
      predecessorIds: [],
      expectedArtifacts: [],
      expectedKeys: new Set(),
      collisions: new Map(),
    };
    if (contract.predecessorIds.length > 0 && contract.expectedKeys.size === 0) {
      errors.push(`human_input requires at least one modifiable produced_artifact in predecessor nodes: ${node.id} <- [${contract.predecessorIds.join(', ') || 'unknown'}]`);
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
    contract.collisions.forEach((sources, key) => {
      errors.push(`human_input produced_artifacts collision in modifiable upstream outputs: ${node.id} -> ${key} <- [${sources.join(', ')}]`);
    });

    const declaredOutputCounts = toDeclaredArtifactCountMap(data.producedArtifacts || []);
    const declaredOutputs = toDeclaredArtifactKeySet(data.producedArtifacts || []);
    contract.expectedKeys.forEach((expected) => {
      if (!declaredOutputs.has(expected)) {
        errors.push(`human_input produced_artifacts missing modifiable upstream artifact: ${node.id} -> ${expected}`);
      }
    });
    declaredOutputCounts.forEach((count, declared) => {
      if (!contract.expectedKeys.has(declared)) {
        errors.push(`human_input produced_artifacts extra artifact not found in modifiable upstream: ${node.id} -> ${declared}`);
        return;
      }
      if (count > 1) {
        errors.push(`human_input produced_artifacts extra artifact not found in modifiable upstream: ${node.id} -> ${declared} (duplicate x${count})`);
      }
    });
  });

  return errors;
}
