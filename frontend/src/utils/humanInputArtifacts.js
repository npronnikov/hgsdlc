const HUMAN_INPUT_KIND = 'human_input';
const UPSTREAM_MODIFIABLE_KINDS = new Set(['ai', 'command']);

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

export function buildHumanInputArtifactContracts(nodes) {
  const contracts = new Map();
  const predecessorMap = buildPredecessorMap(nodes);

  (nodes || []).forEach((node) => {
    const nodeData = node?.data || {};
    if (normalizeNodeKind(nodeData) !== HUMAN_INPUT_KIND) {
      return;
    }

    const predecessors = predecessorMap.get(node.id) || [];
    const predecessorIds = predecessors
      .map((candidate) => candidate?.id)
      .filter(Boolean)
      .sort();

    const artifactsByKey = new Map();
    const sourcesByKey = new Map();

    predecessors.forEach((predecessor) => {
      const predecessorData = predecessor?.data || {};
      if (!UPSTREAM_MODIFIABLE_KINDS.has(normalizeNodeKind(predecessorData))) {
        return;
      }
      const sourceId = predecessor?.id ? String(predecessor.id) : '';
      (predecessorData.producedArtifacts || []).forEach((artifact) => {
        if (!artifact || artifact.modifiable !== true) {
          return;
        }
        const path = normalizeArtifactPath(artifact.path);
        if (!path) {
          return;
        }
        const scope = normalizeArtifactScope(artifact.scope);
        if (!scope) {
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
        if (sourceId) {
          sourcesByKey.get(key).add(sourceId);
        }
      });
    });

    const sortedKeys = [...artifactsByKey.keys()].sort();
    const expectedArtifacts = sortedKeys.map((key) => artifactsByKey.get(key));
    const expectedKeys = new Set(sortedKeys);
    const collisions = new Map();
    sortedKeys.forEach((key) => {
      const sourceIds = [...(sourcesByKey.get(key) || [])].sort();
      if (sourceIds.length > 1) {
        collisions.set(key, sourceIds);
      }
    });

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
