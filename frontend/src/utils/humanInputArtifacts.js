const HUMAN_INPUT_KIND = 'human_input';
const UPSTREAM_SOURCE_KINDS = new Set(['ai', 'command']);

const normalizeText = (value) => String(value || '')
  .trim()
  .toLowerCase()
  .replaceAll('-', '_')
  .replaceAll(' ', '_');

export function normalizeNodeKind(data = {}) {
  return normalizeText(data.nodeKind || data.type);
}

export function normalizeArtifactScope(scope) {
  const normalized = normalizeText(scope);
  return normalized || null;
}

export function normalizeArtifactPath(path) {
  return String(path || '').trim();
}

export function artifactIdentityKey(scope, path) {
  return `${scope}::${path}`;
}

function executionContextIdentityKey(nodeId, scope, path) {
  return `${String(nodeId || '').trim()}::${artifactIdentityKey(scope, path)}`;
}

function collectNodeTargets(data = {}) {
  return [data.onSuccess, data.onFailure, data.onSubmit, data.onApprove, data.onRework?.nextNode]
    .filter(Boolean);
}

export function buildPredecessorMap(nodes) {
  const predecessorMap = new Map();
  (nodes || []).forEach((node) => {
    const targets = collectNodeTargets(node?.data || {});
    targets.forEach((target) => {
      if (!predecessorMap.has(target)) {
        predecessorMap.set(target, []);
      }
      predecessorMap.get(target).push(node);
    });
  });
  return predecessorMap;
}

function collectUpstreamExecutionContextCandidates(predecessors) {
  const candidatesByKey = new Map();
  (Array.isArray(predecessors) ? predecessors : []).forEach((predecessor) => {
    const sourceId = String(predecessor?.id || '').trim();
    if (!sourceId) {
      return;
    }
    const predecessorData = predecessor?.data || {};
    if (!UPSTREAM_SOURCE_KINDS.has(normalizeNodeKind(predecessorData))) {
      return;
    }
    (predecessorData.producedArtifacts || []).forEach((artifact) => {
      const path = normalizeArtifactPath(artifact?.path);
      if (!path) {
        return;
      }
      const scope = normalizeArtifactScope(artifact?.scope);
      if (scope !== 'run') {
        return;
      }
      const key = executionContextIdentityKey(sourceId, scope, path);
      if (candidatesByKey.has(key)) {
        return;
      }
      candidatesByKey.set(key, {
        type: 'artifact_ref',
        required: true,
        scope,
        path,
        node_id: sourceId,
        transfer_mode: 'by_ref',
      });
    });
  });
  return [...candidatesByKey.values()].sort((left, right) => {
    const leftKey = executionContextIdentityKey(left.node_id, left.scope, left.path);
    const rightKey = executionContextIdentityKey(right.node_id, right.scope, right.path);
    return leftKey.localeCompare(rightKey);
  });
}

function toExistingExecutionContextByKey(entries) {
  const byKey = new Map();
  (Array.isArray(entries) ? entries : []).forEach((entry) => {
    if (!entry || normalizeText(entry.type) !== 'artifact_ref') {
      return;
    }
    const path = normalizeArtifactPath(entry.path);
    const scope = normalizeArtifactScope(entry.scope);
    const sourceId = String(entry.node_id || '').trim();
    if (!path || scope !== 'run' || !sourceId) {
      return;
    }
    const key = executionContextIdentityKey(sourceId, scope, path);
    if (!byKey.has(key)) {
      byKey.set(key, entry);
    }
  });
  return byKey;
}

function buildSyncedExecutionContext(existingEntries, upstreamCandidates) {
  const existingByKey = toExistingExecutionContextByKey(existingEntries);
  return (Array.isArray(upstreamCandidates) ? upstreamCandidates : []).map((candidate) => {
    const key = executionContextIdentityKey(candidate.node_id, candidate.scope, candidate.path);
    const existing = existingByKey.get(key);
    return {
      ...candidate,
      required: existing?.required === false ? false : true,
      modifiable: typeof existing?.modifiable === 'boolean' ? existing.modifiable : true,
      transfer_mode: 'by_ref',
    };
  });
}

function buildExpectedProducedArtifactsFromExecutionContext(entries) {
  const artifactsByKey = new Map();
  const sourcesByKey = new Map();
  (Array.isArray(entries) ? entries : []).forEach((entry) => {
    if (!entry || normalizeText(entry.type) !== 'artifact_ref') {
      return;
    }
    if (entry.modifiable !== true) {
      return;
    }
    const path = normalizeArtifactPath(entry.path);
    const scope = normalizeArtifactScope(entry.scope);
    if (!path || !scope) {
      return;
    }
    const key = artifactIdentityKey(scope, path);
    if (!artifactsByKey.has(key)) {
      artifactsByKey.set(key, {
        path,
        scope,
        required: true,
        modifiable: false,
      });
    }
    if (!sourcesByKey.has(key)) {
      sourcesByKey.set(key, new Set());
    }
    const sourceNodeId = String(entry.node_id || '').trim();
    if (sourceNodeId) {
      sourcesByKey.get(key).add(sourceNodeId);
    }
  });

  const sortedKeys = [...artifactsByKey.keys()].sort();
  const collisions = new Map();
  sortedKeys.forEach((key) => {
    const sources = [...(sourcesByKey.get(key) || [])].sort();
    if (sources.length > 1) {
      collisions.set(key, sources);
    }
  });

  return {
    expectedArtifacts: sortedKeys.map((key) => artifactsByKey.get(key)),
    expectedKeys: new Set(sortedKeys),
    collisions,
  };
}

function isProducedArtifactDeclared(sourceNode, path, scope) {
  const sourceData = sourceNode?.data || {};
  const normalizedPath = normalizeArtifactPath(path);
  const normalizedScope = normalizeArtifactScope(scope);
  if (!normalizedPath || !normalizedScope) {
    return false;
  }
  return (sourceData.producedArtifacts || []).some((artifact) => {
    const artifactPath = normalizeArtifactPath(artifact?.path);
    const artifactScope = normalizeArtifactScope(artifact?.scope);
    return artifactPath === normalizedPath && artifactScope === normalizedScope;
  });
}

export function buildHumanInputExecutionContextSyncContracts(nodes) {
  const contracts = new Map();
  const predecessorMap = buildPredecessorMap(nodes);

  (nodes || []).forEach((node) => {
    const nodeData = node?.data || {};
    if (normalizeNodeKind(nodeData) !== HUMAN_INPUT_KIND) {
      return;
    }
    const predecessors = predecessorMap.get(node.id) || [];
    const predecessorIds = predecessors
      .map((candidate) => String(candidate?.id || '').trim())
      .filter(Boolean)
      .sort();
    const upstreamCandidates = collectUpstreamExecutionContextCandidates(predecessors);
    const syncedExecutionContext = buildSyncedExecutionContext(nodeData.executionContext || [], upstreamCandidates);
    const { expectedArtifacts, expectedKeys, collisions } = buildExpectedProducedArtifactsFromExecutionContext(syncedExecutionContext);
    contracts.set(node.id, {
      predecessorIds,
      syncedExecutionContext,
      expectedArtifacts,
      expectedKeys,
      collisions,
    });
  });

  return contracts;
}

export function buildHumanInputArtifactContracts(nodes) {
  const contracts = new Map();
  const predecessorMap = buildPredecessorMap(nodes);
  const nodesById = new Map((nodes || []).map((node) => [node?.id, node]));

  (nodes || []).forEach((node) => {
    const nodeData = node?.data || {};
    if (normalizeNodeKind(nodeData) !== HUMAN_INPUT_KIND) {
      return;
    }
    const predecessors = predecessorMap.get(node.id) || [];
    const predecessorIds = predecessors
      .map((candidate) => String(candidate?.id || '').trim())
      .filter(Boolean)
      .sort();
    const predecessorIdSet = new Set(predecessorIds);

    const filteredEntries = [];
    (nodeData.executionContext || []).forEach((entry) => {
      if (!entry || normalizeText(entry.type) !== 'artifact_ref') {
        return;
      }
      const path = normalizeArtifactPath(entry.path);
      const scope = normalizeArtifactScope(entry.scope);
      const sourceNodeId = String(entry.node_id || '').trim();
      if (!path || scope !== 'run' || !sourceNodeId) {
        return;
      }
      if (entry.modifiable !== true) {
        return;
      }
      if (!predecessorIdSet.has(sourceNodeId)) {
        return;
      }
      const sourceNode = nodesById.get(sourceNodeId);
      if (!sourceNode) {
        return;
      }
      if (!isProducedArtifactDeclared(sourceNode, path, scope)) {
        return;
      }
      filteredEntries.push({
        type: 'artifact_ref',
        path,
        scope,
        node_id: sourceNodeId,
        modifiable: true,
      });
    });

    const { expectedArtifacts, expectedKeys, collisions } = buildExpectedProducedArtifactsFromExecutionContext(filteredEntries);
    contracts.set(node.id, {
      predecessorIds,
      expectedArtifacts,
      expectedKeys,
      collisions,
    });
  });

  return contracts;
}

export function toDeclaredArtifactCountMap(artifacts) {
  const keys = new Map();
  (Array.isArray(artifacts) ? artifacts : []).forEach((artifact) => {
    const path = normalizeArtifactPath(artifact?.path);
    if (!path) {
      return;
    }
    const scope = normalizeArtifactScope(artifact?.scope);
    if (!scope) {
      return;
    }
    const key = artifactIdentityKey(scope, path);
    keys.set(key, (keys.get(key) || 0) + 1);
  });
  return keys;
}

export function toDeclaredArtifactKeySet(artifacts) {
  return new Set(toDeclaredArtifactCountMap(artifacts).keys());
}

function toManagedComparableList(artifacts) {
  return (Array.isArray(artifacts) ? artifacts : [])
    .map((artifact, index) => {
      const path = normalizeArtifactPath(artifact?.path);
      const scope = normalizeArtifactScope(artifact?.scope);
      const pathToken = path || `__missing_path__#${index}`;
      const scopeToken = scope || `__missing_scope__#${index}`;
      const required = artifact?.required !== false;
      const modifiable = artifact?.modifiable === true;
      return `${artifactIdentityKey(scopeToken, pathToken)}::${required ? '1' : '0'}::${modifiable ? '1' : '0'}`;
    })
    .sort();
}

export function isManagedArtifactListEqual(currentArtifacts, expectedArtifacts) {
  const currentComparable = toManagedComparableList(currentArtifacts);
  const expectedComparable = toManagedComparableList(expectedArtifacts);
  if (currentComparable.length !== expectedComparable.length) {
    return false;
  }
  return currentComparable.every((value, index) => value === expectedComparable[index]);
}

function toExecutionContextComparableList(entries) {
  return (Array.isArray(entries) ? entries : [])
    .map((entry, index) => {
      const type = normalizeText(entry?.type) || `__missing_type__#${index}`;
      const nodeId = String(entry?.node_id || '').trim() || `__missing_node_id__#${index}`;
      const scope = normalizeArtifactScope(entry?.scope) || `__missing_scope__#${index}`;
      const path = normalizeArtifactPath(entry?.path) || `__missing_path__#${index}`;
      const required = entry?.required === false ? '0' : '1';
      const modifiable = entry?.modifiable === true ? '1' : '0';
      return `${type}::${nodeId}::${artifactIdentityKey(scope, path)}::${required}::${modifiable}`;
    })
    .sort();
}

export function isExecutionContextListEqual(currentEntries, expectedEntries) {
  const currentComparable = toExecutionContextComparableList(currentEntries);
  const expectedComparable = toExecutionContextComparableList(expectedEntries);
  if (currentComparable.length !== expectedComparable.length) {
    return false;
  }
  return currentComparable.every((value, index) => value === expectedComparable[index]);
}
