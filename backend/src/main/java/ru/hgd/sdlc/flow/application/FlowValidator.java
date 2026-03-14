package ru.hgd.sdlc.flow.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
            validateNode(node, nodesById, errors);
        }

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

    private void validateNode(NodeModel node, Map<String, NodeModel> nodesById, List<String> errors) {
        String type = normalize(node.getType());
        if (type == null) {
            errors.add("Node type is required: " + node.getId());
            return;
        }
        if ("executor".equals(type)) {
            validateExecutor(node, nodesById, errors);
            return;
        }
        if ("gate".equals(type)) {
            validateGate(node, nodesById, errors);
            return;
        }
        errors.add("Unsupported node type: " + node.getId());
    }

    private void validateExecutor(NodeModel node, Map<String, NodeModel> nodesById, List<String> errors) {
        String executorKind = normalize(node.getExecutorKind());
        if (executorKind == null) {
            errors.add("executor_kind is required: " + node.getId());
            return;
        }
        if (node.getSkillRefs() != null && !node.getSkillRefs().isEmpty() && !"ai".equals(executorKind)) {
            errors.add("skill_refs only allowed for AI nodes: " + node.getId());
        }
        if ("ai".equals(executorKind)) {
            boolean hasOnSuccess = node.getOnSuccess() != null && !node.getOnSuccess().isBlank();
            boolean hasOutcomes = node.getAllowedOutcomes() != null && !node.getAllowedOutcomes().isEmpty();
            if (hasOnSuccess && hasOutcomes) {
                errors.add("AI node cannot have both on_success and allowed_outcomes: " + node.getId());
            }
            if (!hasOnSuccess && !hasOutcomes) {
                errors.add("AI node requires on_success or allowed_outcomes: " + node.getId());
            }
            if (hasOnSuccess) {
                assertTarget(node.getId(), "on_success", node.getOnSuccess(), nodesById, errors);
            }
            if (hasOutcomes) {
                Map<String, String> routes = node.getOutcomeRoutes();
                if (routes == null || routes.isEmpty()) {
                    errors.add("AI node requires outcome_routes when allowed_outcomes present: " + node.getId());
                } else {
                    for (String outcome : node.getAllowedOutcomes()) {
                        String target = routes.get(outcome);
                        if (target == null || target.isBlank()) {
                            errors.add("Missing outcome route for " + outcome + " in node: " + node.getId());
                            continue;
                        }
                        assertTarget(node.getId(), "outcome:" + outcome, target, nodesById, errors);
                    }
                    for (String outcome : routes.keySet()) {
                        if (node.getAllowedOutcomes() == null || !node.getAllowedOutcomes().contains(outcome)) {
                            errors.add("Outcome route not declared in allowed_outcomes: " + node.getId() + " -> " + outcome);
                        }
                    }
                }
            }
            return;
        }
        if ("external_command".equals(executorKind)) {
            if (node.getOnSuccess() == null || node.getOnSuccess().isBlank()) {
                errors.add("External Command node requires on_success: " + node.getId());
            } else {
                assertTarget(node.getId(), "on_success", node.getOnSuccess(), nodesById, errors);
            }
            return;
        }
        errors.add("Unsupported executor_kind: " + node.getId());
    }

    private void validateGate(NodeModel node, Map<String, NodeModel> nodesById, List<String> errors) {
        String gateKind = normalize(node.getGateKind());
        if (gateKind == null) {
            errors.add("gate_kind is required: " + node.getId());
            return;
        }
        if ("human_input".equals(gateKind)) {
            if (node.getOnSubmit() == null || node.getOnSubmit().isBlank()) {
                errors.add("human_input gate requires on_submit: " + node.getId());
            } else {
                assertTarget(node.getId(), "on_submit", node.getOnSubmit(), nodesById, errors);
            }
            return;
        }
        if ("human_approval".equals(gateKind)) {
            if (node.getOnApprove() == null || node.getOnApprove().isBlank()) {
                errors.add("human_approval gate requires on_approve: " + node.getId());
            } else {
                assertTarget(node.getId(), "on_approve", node.getOnApprove(), nodesById, errors);
            }
            if (node.getOnReject() == null || node.getOnReject().isBlank()) {
                errors.add("human_approval gate requires on_reject: " + node.getId());
            } else {
                assertTarget(node.getId(), "on_reject", node.getOnReject(), nodesById, errors);
            }
            if (node.getOnReworkRoutes() == null || node.getOnReworkRoutes().isEmpty()) {
                errors.add("human_approval gate requires on_rework_routes: " + node.getId());
            } else {
                for (Map.Entry<String, String> entry : node.getOnReworkRoutes().entrySet()) {
                    assertTarget(node.getId(), "on_rework:" + entry.getKey(), entry.getValue(), nodesById, errors);
                }
            }
            return;
        }
        errors.add("Unsupported gate_kind: " + node.getId());
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
        if (node.getOutcomeRoutes() != null) {
            targets.addAll(node.getOutcomeRoutes().values());
        }
        if (node.getOnSubmit() != null) {
            targets.add(node.getOnSubmit());
        }
        if (node.getOnApprove() != null) {
            targets.add(node.getOnApprove());
        }
        if (node.getOnReject() != null) {
            targets.add(node.getOnReject());
        }
        if (node.getOnReworkRoutes() != null) {
            targets.addAll(node.getOnReworkRoutes().values());
        }
        return targets;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase().replace(' ', '_').replace('-', '_');
    }
}
