package ru.hgd.sdlc.flow.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
        validateDeclaredOutputs(node, errors);

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
                String scope = normalize(entry.getScope());
                if (!"run".equals(scope)) {
                    errors.add("human_input execution_context supports only scope=run: " + node.getId());
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
                if (!isModifiableProducedArtifact(sourceNode, entry.getPath(), "run")) {
                    errors.add("human_input execution_context must reference modifiable produced_artifacts: "
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
            }
        }
    }

    private void validateDeclaredOutputs(NodeModel node, List<String> errors) {
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

    private boolean isModifiableProducedArtifact(NodeModel sourceNode, String path, String scope) {
        if (sourceNode == null || sourceNode.getProducedArtifacts() == null || path == null || path.isBlank()) {
            return false;
        }
        String normalizedScope = normalize(scope);
        for (var artifact : sourceNode.getProducedArtifacts()) {
            if (artifact == null || artifact.getPath() == null || artifact.getPath().isBlank()) {
                continue;
            }
            String artifactScope = normalize(artifact.getScope());
            if (path.equals(artifact.getPath())
                    && normalizedScope != null
                    && normalizedScope.equals(artifactScope)
                    && Boolean.TRUE.equals(artifact.getModifiable())) {
                return true;
            }
        }
        return false;
    }

    private void validateHumanInputPredecessorContracts(Map<String, NodeModel> nodesById, List<String> errors) {
        for (NodeModel node : nodesById.values()) {
            String nodeKind = normalize(node.getNodeKind());
            if (nodeKind == null) {
                nodeKind = normalize(node.getType());
            }
            if (!"human_input".equals(nodeKind)) {
                continue;
            }
            List<NodeModel> predecessors = nodesById.values().stream()
                    .filter((candidate) -> collectTargets(candidate).contains(node.getId()))
                    .toList();
            if (predecessors.isEmpty()) {
                continue;
            }
            boolean hasModifiable = predecessors.stream()
                    .anyMatch((candidate) -> candidate.getProducedArtifacts() != null
                            && candidate.getProducedArtifacts().stream()
                            .anyMatch((artifact) -> artifact != null && Boolean.TRUE.equals(artifact.getModifiable())));
            if (!hasModifiable) {
                String predecessorIds = predecessors.stream().map(NodeModel::getId).sorted().reduce((a, b) -> a + ", " + b).orElse("unknown");
                errors.add("human_input requires at least one modifiable produced_artifact in predecessor nodes: "
                        + node.getId() + " <- [" + predecessorIds + "]");
            }
        }
    }

    private void validateHumanInputProducedArtifacts(
            NodeModel node,
            Map<String, NodeModel> nodesById,
            List<String> errors
    ) {
        Set<String> expectedArtifacts = new LinkedHashSet<>();
        if (node.getExecutionContext() != null) {
            for (var entry : node.getExecutionContext()) {
                if (entry == null) {
                    continue;
                }
                if (!"artifact_ref".equals(normalize(entry.getType()))) {
                    continue;
                }
                if (!"run".equals(normalize(entry.getScope()))) {
                    continue;
                }
                String sourceNodeId = entry.getNodeId();
                String path = entry.getPath();
                if (sourceNodeId == null || sourceNodeId.isBlank() || path == null || path.isBlank()) {
                    continue;
                }
                NodeModel sourceNode = nodesById.get(sourceNodeId);
                if (!isModifiableProducedArtifact(sourceNode, path, "run")) {
                    continue;
                }
                expectedArtifacts.add("run::" + path);
            }
        }
        if (expectedArtifacts.isEmpty()) {
            return;
        }

        Set<String> declaredArtifacts = new LinkedHashSet<>();
        if (node.getProducedArtifacts() != null) {
            for (var artifact : node.getProducedArtifacts()) {
                if (artifact == null || artifact.getPath() == null || artifact.getPath().isBlank()) {
                    continue;
                }
                declaredArtifacts.add(normalize(artifact.getScope()) + "::" + artifact.getPath());
            }
        }

        if (declaredArtifacts.isEmpty()) {
            errors.add("human_input produced_artifacts must mirror modifiable execution_context artifacts: " + node.getId());
            return;
        }
        for (String expected : expectedArtifacts) {
            if (!declaredArtifacts.contains(expected)) {
                errors.add("human_input produced_artifacts missing required artifact from execution_context: "
                        + node.getId() + " -> " + expected);
            }
        }
        for (String declared : declaredArtifacts) {
            if (!expectedArtifacts.contains(declared)) {
                errors.add("human_input produced_artifacts must match execution_context artifacts: "
                        + node.getId() + " -> " + declared);
            }
        }
    }
}
