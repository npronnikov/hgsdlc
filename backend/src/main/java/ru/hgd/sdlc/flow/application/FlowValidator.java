package ru.hgd.sdlc.flow.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Component;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;

@Component
public class FlowValidator {
    public List<String> validate(FlowModel flow) {
        List<String> errors = new ArrayList<>();
        if (flow == null) {
            errors.add("Flow model is required");
            return errors;
        }
        if (flow.getNodes() == null || flow.getNodes().isEmpty()) {
            errors.add("Flow has no nodes");
            return errors;
        }
        Map<String, NodeModel> nodesById = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (NodeModel node : flow.getNodes()) {
            if (node.getId() == null || node.getId().isBlank()) {
                errors.add("Node id is required");
                continue;
            }
            if (nodesById.containsKey(node.getId())) {
                duplicates.add(node.getId());
            } else {
                nodesById.put(node.getId(), node);
            }
        }
        for (String duplicate : duplicates) {
            errors.add("Duplicate node id: " + duplicate);
        }

        if (flow.getStartNodeId() == null || flow.getStartNodeId().isBlank()) {
            errors.add("start_node_id is required");
        } else if (!nodesById.containsKey(flow.getStartNodeId())) {
            errors.add("start_node_id does not exist: " + flow.getStartNodeId());
        }

        for (NodeModel node : nodesById.values()) {
            validateNode(node, nodesById, flow.getStartNodeId(), errors);
        }
        validateHumanInputPredecessorContracts(nodesById, errors);

        if (flow.getStartNodeId() != null && nodesById.containsKey(flow.getStartNodeId())) {
            Set<String> reachable = computeReachable(flow.getStartNodeId(), nodesById);
            for (String nodeId : nodesById.keySet()) {
                if (!reachable.contains(nodeId)) {
                    errors.add("Unreachable node: " + nodeId);
                }
            }
        }

        return errors;
    }

    private void validateNode(NodeModel node, Map<String, NodeModel> nodesById, String startNodeId, List<String> errors) {
        String nodeKind = normalize(node.getNodeKind());
        if (nodeKind == null) {
            nodeKind = normalize(node.getType());
        }
        if (nodeKind == null) {
            errors.add("Node type is required: " + node.getId());
            return;
        }
        if (!Set.of("ai", "command", "human_input", "human_approval", "terminal").contains(nodeKind)) {
            errors.add("Unsupported node type: " + node.getId());
            return;
        }
        if (Boolean.TRUE.equals(node.getCheckpointBeforeRun()) && !Set.of("ai", "command").contains(nodeKind)) {
            errors.add("checkpoint_before_run is only allowed for ai/command nodes: " + node.getId());
        }

        validateExecutionContext(node, nodeKind, nodesById, errors);
        validateDeclaredOutputs(node, nodeKind, errors);

        if (node.getSkillRefs() != null && !node.getSkillRefs().isEmpty() && !"ai".equals(nodeKind)) {
            errors.add("skill_refs only allowed for AI nodes: " + node.getId());
        }

        if ("terminal".equals(nodeKind)) {
            validateTerminal(node, errors);
            return;
        }
        if ("ai".equals(nodeKind) || "command".equals(nodeKind)) {
            validateExecutor(node, nodeKind, startNodeId, nodesById, errors);
            return;
        }
        validateGate(node, nodeKind, nodesById, errors);
    }

    private void validateTerminal(NodeModel node, List<String> errors) {
        boolean hasTransition = (node.getOnSuccess() != null && !node.getOnSuccess().isBlank())
                || (node.getOnFailure() != null && !node.getOnFailure().isBlank())
                || (node.getOnSubmit() != null && !node.getOnSubmit().isBlank())
                || (node.getOnApprove() != null && !node.getOnApprove().isBlank())
                || (node.getOnRework() != null
                && node.getOnRework().getNextNode() != null
                && !node.getOnRework().getNextNode().isBlank());
        if (hasTransition) {
            errors.add("Terminal node cannot have transitions: " + node.getId());
        }
        if (node.getSkillRefs() != null && !node.getSkillRefs().isEmpty()) {
            errors.add("skill_refs not allowed for terminal node: " + node.getId());
        }
    }

    private void validateExecutor(
            NodeModel node,
            String nodeKind,
            String startNodeId,
            Map<String, NodeModel> nodesById,
            List<String> errors
    ) {
        boolean hasOnSuccess = node.getOnSuccess() != null && !node.getOnSuccess().isBlank();
        if (!hasOnSuccess) {
            errors.add("Executor node requires on_success: " + node.getId());
        }
        if ("ai".equals(nodeKind) && (node.getOnFailure() == null || node.getOnFailure().isBlank())) {
            errors.add("AI executor requires on_failure: " + node.getId());
        }
        if ("ai".equals(nodeKind)
                && node.getId() != null
                && node.getId().equals(startNodeId)
                && (node.getInstruction() == null || node.getInstruction().isBlank())) {
            errors.add("Start AI node requires instruction: " + node.getId());
        }
        if (hasOnSuccess) {
            assertTarget(node.getId(), "on_success", node.getOnSuccess(), nodesById, errors);
        }
        if (node.getOnFailure() != null && !node.getOnFailure().isBlank()) {
            if (!"ai".equals(nodeKind)) {
                errors.add("on_failure only supported for AI nodes: " + node.getId());
            } else {
                assertTarget(node.getId(), "on_failure", node.getOnFailure(), nodesById, errors);
            }
        }
    }

    private void validateGate(NodeModel node, String nodeKind, Map<String, NodeModel> nodesById, List<String> errors) {
        if ("human_input".equals(nodeKind)) {
            if (node.getOnSubmit() == null || node.getOnSubmit().isBlank()) {
                errors.add("human_input gate requires on_submit: " + node.getId());
            } else {
                assertTarget(node.getId(), "on_submit", node.getOnSubmit(), nodesById, errors);
            }
            if (node.getInstruction() == null || node.getInstruction().isBlank()) {
                errors.add("human_input gate requires instruction: " + node.getId());
            }
            if (node.getExecutionContext() == null || node.getExecutionContext().isEmpty()) {
                errors.add("human_input gate requires execution_context artifact_ref entries: " + node.getId());
            }
            validateHumanInputProducedArtifacts(node, nodesById, errors);
            return;
        }
        if ("human_approval".equals(nodeKind)) {
            if (node.getOnApprove() == null || node.getOnApprove().isBlank()) {
                errors.add("human_approval gate requires on_approve: " + node.getId());
            } else {
                assertTarget(node.getId(), "on_approve", node.getOnApprove(), nodesById, errors);
            }
            var onRework = node.getOnRework();
            if (onRework != null && onRework.getNextNode() != null && !onRework.getNextNode().isBlank()) {
                assertTarget(node.getId(), "on_rework", onRework.getNextNode(), nodesById, errors);
            } else {
                errors.add("human_approval gate requires on_rework: " + node.getId());
            }
            return;
        }
    }

    private void assertTarget(String nodeId, String route, String target, Map<String, NodeModel> nodesById, List<String> errors) {
        if (target == null || target.isBlank()) {
            errors.add("Invalid transition target for " + route + " on node: " + nodeId);
            return;
        }
        if (!nodesById.containsKey(target)) {
            errors.add("Transition target not found for " + route + " on node: " + nodeId + " -> " + target);
        }
    }

    private Set<String> computeReachable(String startNodeId, Map<String, NodeModel> nodesById) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);
        visited.add(startNodeId);
        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            NodeModel node = nodesById.get(currentId);
            if (node == null) {
                continue;
            }
            for (String next : collectTargets(node)) {
                if (next != null && nodesById.containsKey(next) && visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    private List<String> collectTargets(NodeModel node) {
        List<String> targets = new ArrayList<>();
        if (node.getOnSuccess() != null) {
            targets.add(node.getOnSuccess());
        }
        if (node.getOnFailure() != null) {
            targets.add(node.getOnFailure());
        }
        if (node.getOnSubmit() != null) {
            targets.add(node.getOnSubmit());
        }
        if (node.getOnApprove() != null) {
            targets.add(node.getOnApprove());
        }
        if (node.getOnRework() != null && node.getOnRework().getNextNode() != null) {
            targets.add(node.getOnRework().getNextNode());
        }
        return targets;
    }

    private void validateExecutionContext(
            NodeModel node,
            String nodeKind,
            Map<String, NodeModel> nodesById,
            List<String> errors
    ) {
        if ("command".equals(nodeKind)) {
            return;
        }
        if ("human_input".equals(nodeKind)) {
            if (node.getExecutionContext() == null || node.getExecutionContext().isEmpty()) {
                errors.add("execution_context is required for human_input: " + node.getId());
                return;
            }
            for (var entry : node.getExecutionContext()) {
                if (entry == null) {
                    errors.add("execution_context entry is required: " + node.getId());
                    continue;
                }
                String type = normalize(entry.getType());
                if (!"artifact_ref".equals(type)) {
                    errors.add("human_input supports only artifact_ref execution_context: " + node.getId());
                }
                String transferMode = normalizeTransferMode(entry.getTransferMode());
                if (transferMode != null && !"by_ref".equals(transferMode)) {
                    errors.add("human_input execution_context supports only transfer_mode=by_ref: " + node.getId());
                }
                String scope = normalize(entry.getScope());
                if (!"run".equals(scope)) {
                    errors.add("human_input execution_context supports only scope=run: " + node.getId());
                }
                if (entry.getModifiable() == null) {
                    errors.add("human_input execution_context modifiable flag is missing: " + node.getId());
                }
                if (entry.getNodeId() == null || entry.getNodeId().isBlank()) {
                    errors.add("human_input run-scoped artifact_ref requires node_id: " + node.getId());
                    continue;
                }
                NodeModel sourceNode = nodesById.get(entry.getNodeId());
                if (sourceNode == null) {
                    errors.add("human_input execution_context source node not found: " + node.getId() + " -> " + entry.getNodeId());
                    continue;
                }
                String sourceKind = normalizeNodeKind(sourceNode);
                if (!Set.of("ai", "command").contains(sourceKind)) {
                    errors.add("human_input execution_context source node must be ai/command: "
                            + node.getId() + " -> " + entry.getNodeId());
                }
                if (!isProducedArtifactDeclared(sourceNode, entry.getPath(), "run")) {
                    errors.add("human_input execution_context must reference produced_artifacts: "
                            + node.getId() + " -> " + entry.getNodeId() + ":" + entry.getPath());
                }
            }
            return;
        }
        if (node.getExecutionContext() == null) {
            errors.add("execution_context is required: " + node.getId());
            return;
        }
        for (int i = 0; i < node.getExecutionContext().size(); i++) {
            var entry = node.getExecutionContext().get(i);
            if (entry == null) {
                errors.add("execution_context entry is required: " + node.getId());
                continue;
            }
            String type = normalize(entry.getType());
            if (type == null) {
                errors.add("execution_context type is required: " + node.getId());
                continue;
            }
            if (!Set.of("directory_ref", "file_ref", "artifact_ref").contains(type)) {
                errors.add("Unsupported execution_context type: " + node.getId());
                continue;
            }
            if (entry.getRequired() == null) {
                errors.add("execution_context required flag is missing: " + node.getId());
            }
            if (entry.getPath() == null || entry.getPath().isBlank()) {
                errors.add("execution_context path is required: " + node.getId());
            } else {
                String scope = normalize(entry.getScope());
                if (scope == null) {
                    errors.add("execution_context scope is required: " + node.getId());
                } else if (!Set.of("project", "run").contains(scope)) {
                    errors.add("Unsupported execution_context scope: " + node.getId());
                }
                if ("artifact_ref".equals(type)) {
                    String transferMode = normalizeTransferMode(entry.getTransferMode());
                    if (transferMode != null && !Set.of("by_ref", "by_value").contains(transferMode)) {
                        errors.add("Unsupported execution_context transfer_mode: " + node.getId());
                    }
                    if ("by_value".equals(transferMode) && !"ai".equals(nodeKind)) {
                        errors.add("execution_context transfer_mode=by_value is supported only for ai nodes: " + node.getId());
                    }
                }
            }
        }
    }

    private void validateDeclaredOutputs(NodeModel node, String nodeKind, List<String> errors) {
        if (node.getProducedArtifacts() != null) {
            for (var entry : node.getProducedArtifacts()) {
                if (entry == null) {
                    errors.add("produced_artifacts entry is required: " + node.getId());
                    continue;
                }
                if (entry.getRequired() == null) {
                    errors.add("produced_artifacts required flag is missing: " + node.getId());
                }
                if (entry.getPath() == null || entry.getPath().isBlank()) {
                    errors.add("produced_artifacts path is required: " + node.getId());
                }
                String scope = normalize(entry.getScope());
                if (scope == null) {
                    errors.add("produced_artifacts scope is required: " + node.getId());
                } else if (!Set.of("project", "run").contains(scope)) {
                    errors.add("Unsupported produced_artifacts scope: " + node.getId());
                }
                if (Set.of("ai", "command").contains(nodeKind) && Boolean.TRUE.equals(entry.getModifiable())) {
                    errors.add("produced_artifacts modifiable=true is not supported for ai/command nodes: " + node.getId());
                }
            }
        }
        if (node.getExpectedMutations() != null) {
            for (var entry : node.getExpectedMutations()) {
                if (entry == null) {
                    errors.add("expected_mutations entry is required: " + node.getId());
                    continue;
                }
                if (entry.getRequired() == null) {
                    errors.add("expected_mutations required flag is missing: " + node.getId());
                }
                if (entry.getPath() == null || entry.getPath().isBlank()) {
                    errors.add("expected_mutations path is required: " + node.getId());
                }
                String scope = normalize(entry.getScope());
                if (scope == null) {
                    errors.add("expected_mutations scope is required: " + node.getId());
                } else if (!Set.of("project", "run").contains(scope)) {
                    errors.add("Unsupported expected_mutations scope: " + node.getId());
                }
            }
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase().replace(' ', '_').replace('-', '_');
    }

    private boolean isProducedArtifactDeclared(NodeModel sourceNode, String path, String scope) {
        String normalizedPath = normalizePath(path);
        if (sourceNode == null || sourceNode.getProducedArtifacts() == null || normalizedPath == null) {
            return false;
        }
        String normalizedScope = normalize(scope);
        for (var artifact : sourceNode.getProducedArtifacts()) {
            String artifactPath = artifact == null ? null : normalizePath(artifact.getPath());
            if (artifactPath == null) {
                continue;
            }
            String artifactScope = normalize(artifact.getScope());
            if (normalizedPath.equals(artifactPath)
                    && normalizedScope != null
                    && normalizedScope.equals(artifactScope)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeTransferMode(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if ("by_ref".equals(normalized) || "by_value".equals(normalized)) {
            return normalized;
        }
        return normalized;
    }

    private void validateHumanInputPredecessorContracts(Map<String, NodeModel> nodesById, List<String> errors) {
        for (NodeModel node : nodesById.values()) {
            String nodeKind = normalizeNodeKind(node);
            if (!"human_input".equals(nodeKind)) {
                continue;
            }
            List<NodeModel> predecessors = findImmediatePredecessors(node, nodesById);
            if (predecessors.isEmpty()) {
                continue;
            }
            Set<String> predecessorIds = predecessors.stream()
                    .map(NodeModel::getId)
                    .collect(java.util.stream.Collectors.toSet());
            boolean hasModifiable = node.getExecutionContext() != null
                    && node.getExecutionContext().stream()
                    .filter((entry) -> entry != null)
                    .filter((entry) -> "artifact_ref".equals(normalize(entry.getType())))
                    .filter((entry) -> "run".equals(normalize(entry.getScope())))
                    .filter((entry) -> Boolean.TRUE.equals(entry.getModifiable()))
                    .anyMatch((entry) -> {
                        String sourceNodeId = entry.getNodeId();
                        if (sourceNodeId == null || !predecessorIds.contains(sourceNodeId)) {
                            return false;
                        }
                        NodeModel sourceNode = nodesById.get(sourceNodeId);
                        return isProducedArtifactDeclared(sourceNode, entry.getPath(), entry.getScope());
                    });
            if (!hasModifiable) {
                String predecessorIdsText = predecessors.stream().map(NodeModel::getId).sorted().reduce((a, b) -> a + ", " + b).orElse("unknown");
                errors.add("human_input requires at least one execution_context artifact_ref with modifiable=true from predecessor nodes: "
                        + node.getId() + " <- [" + predecessorIdsText + "]");
            }
        }
    }

    private void validateHumanInputProducedArtifacts(
            NodeModel node,
            Map<String, NodeModel> nodesById,
            List<String> errors
    ) {
        List<NodeModel> predecessors = findImmediatePredecessors(node, nodesById);
        HumanInputArtifactExpectation expectation = buildHumanInputArtifactExpectation(node, predecessors, nodesById);

        for (Map.Entry<String, Set<String>> collision : expectation.collisions().entrySet()) {
            String sources = collision.getValue().stream().sorted().reduce((a, b) -> a + ", " + b).orElse("unknown");
            errors.add("human_input produced_artifacts collision in execution_context modifiable artifacts: "
                    + node.getId() + " -> " + collision.getKey() + " <- [" + sources + "]");
        }

        Map<String, Integer> declaredArtifactCounts = declaredArtifactCounts(node);
        Set<String> declaredArtifacts = declaredArtifactCounts.keySet();
        for (String expected : expectation.expectedKeys()) {
            if (!declaredArtifacts.contains(expected)) {
                errors.add("human_input produced_artifacts missing required artifact from execution_context modifiable set: "
                        + node.getId() + " -> " + expected);
            }
        }
        for (Map.Entry<String, Integer> declaredEntry : declaredArtifactCounts.entrySet()) {
            String declared = declaredEntry.getKey();
            int count = declaredEntry.getValue();
            if (!expectation.expectedKeys().contains(declared)) {
                errors.add("human_input produced_artifacts extra artifact not found in execution_context modifiable set: "
                        + node.getId() + " -> " + declared);
                continue;
            }
            if (count > 1) {
                errors.add("human_input produced_artifacts extra artifact not found in execution_context modifiable set: "
                        + node.getId() + " -> " + declared + " (duplicate x" + count + ")");
            }
        }
    }

    private String normalizeNodeKind(NodeModel node) {
        if (node == null) {
            return null;
        }
        String nodeKind = normalize(node.getNodeKind());
        if (nodeKind != null) {
            return nodeKind;
        }
        return normalize(node.getType());
    }

    private List<NodeModel> findImmediatePredecessors(NodeModel target, Map<String, NodeModel> nodesById) {
        if (target == null || target.getId() == null || target.getId().isBlank()) {
            return List.of();
        }
        return nodesById.values().stream()
                .sorted(Comparator.comparing(NodeModel::getId, Comparator.nullsLast(String::compareTo)))
                .filter((candidate) -> collectTargets(candidate).contains(target.getId()))
                .toList();
    }

    private HumanInputArtifactExpectation buildHumanInputArtifactExpectation(
            NodeModel humanInputNode,
            List<NodeModel> predecessors,
            Map<String, NodeModel> nodesById
    ) {
        Map<String, Set<String>> sourcesByArtifact = new HashMap<>();
        Set<String> expectedKeys = new LinkedHashSet<>();
        Set<String> predecessorIds = predecessors.stream()
                .map(NodeModel::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (humanInputNode != null && humanInputNode.getExecutionContext() != null) {
            for (var entry : humanInputNode.getExecutionContext()) {
                if (entry == null) {
                    continue;
                }
                if (!"artifact_ref".equals(normalize(entry.getType()))) {
                    continue;
                }
                if (!"run".equals(normalize(entry.getScope()))) {
                    continue;
                }
                if (!Boolean.TRUE.equals(entry.getModifiable())) {
                    continue;
                }
                String sourceNodeId = entry.getNodeId();
                if (sourceNodeId == null || sourceNodeId.isBlank() || !predecessorIds.contains(sourceNodeId)) {
                    continue;
                }
                NodeModel sourceNode = nodesById.get(sourceNodeId);
                if (!isProducedArtifactDeclared(sourceNode, entry.getPath(), entry.getScope())) {
                    continue;
                }
                String path = normalizePath(entry.getPath());
                String scope = normalize(entry.getScope());
                if (path == null || scope == null) {
                    continue;
                }
                String key = scope + "::" + path;
                expectedKeys.add(key);
                sourcesByArtifact.computeIfAbsent(key, (ignored) -> new LinkedHashSet<>()).add(sourceNodeId);
            }
        }

        Map<String, Set<String>> collisions = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : sourcesByArtifact.entrySet()) {
            if (entry.getValue().size() > 1) {
                collisions.put(entry.getKey(), entry.getValue());
            }
        }

        Set<String> sortedExpected = expectedKeys.stream()
                .sorted()
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        Map<String, Set<String>> sortedCollisions = new java.util.LinkedHashMap<>();
        collisions.keySet().stream().sorted().forEach((key) -> sortedCollisions.put(key, collisions.get(key)));

        return new HumanInputArtifactExpectation(sortedExpected, sortedCollisions);
    }

    private Map<String, Integer> declaredArtifactCounts(NodeModel node) {
        Map<String, Integer> declaredArtifacts = new java.util.LinkedHashMap<>();
        if (node.getProducedArtifacts() == null) {
            return declaredArtifacts;
        }
        for (var artifact : node.getProducedArtifacts()) {
            String path = artifact == null ? null : normalizePath(artifact.getPath());
            if (path == null) {
                continue;
            }
            String scope = normalize(artifact.getScope());
            if (scope == null) {
                continue;
            }
            String key = scope + "::" + path;
            declaredArtifacts.merge(key, 1, Integer::sum);
        }
        return declaredArtifacts;
    }

    private String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String trimmed = path.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private record HumanInputArtifactExpectation(
            Set<String> expectedKeys,
            Map<String, Set<String>> collisions
    ) {
    }
}
