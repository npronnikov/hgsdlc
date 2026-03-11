package ru.hgd.sdlc.compiler.domain.validation.rules;

import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.compiler.domain.validation.*;

import java.util.*;

import static ru.hgd.sdlc.compiler.domain.validation.rules.ValidationCodes.*;

/**
 * Validates semantic correctness of a flow document.
 * Checks for entry phase existence, cycles in phase order, terminal phases,
 * and reachability of all phases and nodes.
 */
public class SemanticValidator implements Validator<FlowDocument> {

    @Override
    public ValidationResult validate(FlowDocument flow, ValidationContext context) {
        context.setFlowDocument(flow);

        // Validate entry phase
        validateEntryPhase(flow, context);

        // Validate no cycles in phase order
        validateNoCycles(flow, context);

        // Validate terminal phases exist
        validateTerminalPhases(flow, context);

        // Validate reachability
        validateReachability(flow, context);

        return context.toResult();
    }

    private void validateEntryPhase(FlowDocument flow, ValidationContext context) {
        List<PhaseId> phaseOrder = flow.phaseOrder();

        if (phaseOrder == null || phaseOrder.isEmpty()) {
            // If there are phases defined but no order, that's a problem
            if (!flow.phases().isEmpty()) {
                context.addError(
                    NO_ENTRY_PHASE,
                    "Flow has phases but no phase_order defined",
                    context.fileLocation()
                );
            }
            return;
        }

        // First phase in order is the entry phase
        PhaseId entryPhaseId = phaseOrder.get(0);
        PhaseDocument entryPhase = flow.phases().get(entryPhaseId);

        if (entryPhase == null) {
            context.addError(
                NO_ENTRY_PHASE,
                String.format("Entry phase '%s' in phase_order is not defined", entryPhaseId.value()),
                context.fileLocation()
            );
        }
    }

    private void validateNoCycles(FlowDocument flow, ValidationContext context) {
        List<PhaseId> phaseOrder = flow.phaseOrder();
        if (phaseOrder == null || phaseOrder.size() < 2) {
            return; // No cycles possible with 0 or 1 phases
        }

        // Build a simple phase graph from phase order (sequential)
        // For more complex flows, transitions would need to be analyzed

        // Check for transitions that could create cycles
        Map<PhaseId, Set<PhaseId>> phaseTransitions = buildPhaseTransitionGraph(flow);

        // Detect cycles using DFS
        Set<PhaseId> visited = new HashSet<>();
        Set<PhaseId> recursionStack = new HashSet<>();

        for (PhaseId phaseId : flow.phases().keySet()) {
            if (detectCycle(phaseId, phaseTransitions, visited, recursionStack)) {
                context.addError(
                    CYCLE_IN_PHASE_ORDER,
                    String.format("Cycle detected in phase transitions involving phase '%s'", phaseId.value()),
                    context.fileLocation()
                );
                return; // Only report one cycle
            }
        }
    }

    private Map<PhaseId, Set<PhaseId>> buildPhaseTransitionGraph(FlowDocument flow) {
        Map<PhaseId, Set<PhaseId>> graph = new HashMap<>();

        // Initialize with empty sets
        for (PhaseId phaseId : flow.phases().keySet()) {
            graph.put(phaseId, new HashSet<>());
        }

        // Add sequential edges from phase_order
        List<PhaseId> phaseOrder = flow.phaseOrder();
        if (phaseOrder != null) {
            for (int i = 0; i < phaseOrder.size() - 1; i++) {
                PhaseId from = phaseOrder.get(i);
                PhaseId to = phaseOrder.get(i + 1);
                if (graph.containsKey(from)) {
                    graph.get(from).add(to);
                }
            }
        }

        // Add edges from node transitions
        for (NodeDocument node : flow.nodes().values()) {
            if (node.transitions() == null || node.phaseId().isEmpty()) {
                continue;
            }

            PhaseId fromPhase = node.phaseId().get();
            for (Transition transition : node.transitions()) {
                if (transition.to() != null) {
                    NodeDocument targetNode = flow.nodes().get(transition.to());
                    if (targetNode != null && targetNode.phaseId().isPresent()) {
                        PhaseId toPhase = targetNode.phaseId().get();
                        if (!fromPhase.equals(toPhase) && transition.type() == TransitionType.FORWARD) {
                            graph.computeIfAbsent(fromPhase, k -> new HashSet<>()).add(toPhase);
                        }
                    }
                }
            }
        }

        return graph;
    }

    private boolean detectCycle(
            PhaseId current,
            Map<PhaseId, Set<PhaseId>> graph,
            Set<PhaseId> visited,
            Set<PhaseId> recursionStack) {

        if (recursionStack.contains(current)) {
            return true;
        }

        if (visited.contains(current)) {
            return false;
        }

        visited.add(current);
        recursionStack.add(current);

        Set<PhaseId> neighbors = graph.getOrDefault(current, Set.of());
        for (PhaseId neighbor : neighbors) {
            if (detectCycle(neighbor, graph, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(current);
        return false;
    }

    private void validateTerminalPhases(FlowDocument flow, ValidationContext context) {
        List<PhaseId> phaseOrder = flow.phaseOrder();
        if (phaseOrder == null || phaseOrder.isEmpty()) {
            return;
        }

        // The last phase in order should be terminal (no outgoing transitions to other phases)
        // But rework transitions are allowed

        PhaseId lastPhaseId = phaseOrder.get(phaseOrder.size() - 1);
        PhaseDocument lastPhase = flow.phases().get(lastPhaseId);

        if (lastPhase == null) {
            return; // Already reported as error
        }

        // Check if last phase has any forward transitions to other phases
        boolean hasForwardExit = false;
        for (NodeId nodeId : lastPhase.nodeOrder()) {
            NodeDocument node = flow.nodes().get(nodeId);
            if (node != null && node.transitions() != null) {
                for (Transition t : node.transitions()) {
                    if (t.type() == TransitionType.FORWARD) {
                        NodeDocument target = flow.nodes().get(t.to());
                        if (target != null && !target.phaseId().equals(lastPhaseId)) {
                            hasForwardExit = true;
                            break;
                        }
                    }
                }
            }
            if (hasForwardExit) break;
        }

        // A flow should have at least one terminal phase
        // If the last phase has forward exits, we should warn about missing terminal
        if (hasForwardExit) {
            context.addWarning(
                NO_TERMINAL_PHASE,
                String.format("Last phase '%s' has forward transitions but should be terminal", lastPhaseId.value()),
                context.fileLocation()
            );
        }
    }

    private void validateReachability(FlowDocument flow, ValidationContext context) {
        List<PhaseId> phaseOrder = flow.phaseOrder();
        if (phaseOrder == null || phaseOrder.isEmpty()) {
            return;
        }

        // Check phase reachability
        Set<PhaseId> reachablePhases = new HashSet<>();
        if (!phaseOrder.isEmpty()) {
            // All phases in order are reachable
            reachablePhases.addAll(phaseOrder);
        }

        // Find unreachable phases (defined but not in order)
        for (PhaseId phaseId : flow.phases().keySet()) {
            if (!reachablePhases.contains(phaseId)) {
                context.addWarning(
                    UNREACHABLE_PHASE,
                    String.format("Phase '%s' is defined but not reachable from phase_order", phaseId.value()),
                    context.fileLocation()
                );
            }
        }

        // Check node reachability
        validateNodeReachability(flow, context);
    }

    private void validateNodeReachability(FlowDocument flow, ValidationContext context) {
        Set<NodeId> reachableNodes = new HashSet<>();

        // Find entry nodes (first nodes in first phase)
        List<PhaseId> phaseOrder = flow.phaseOrder();
        if (phaseOrder != null && !phaseOrder.isEmpty()) {
            PhaseId entryPhaseId = phaseOrder.get(0);
            PhaseDocument entryPhase = flow.phases().get(entryPhaseId);

            if (entryPhase != null) {
                // First nodes in entry phase are entry points
                if (entryPhase.nodeOrder() != null && !entryPhase.nodeOrder().isEmpty()) {
                    NodeId entryNodeId = entryPhase.nodeOrder().get(0);
                    findReachableNodes(entryNodeId, flow, reachableNodes);
                }
                // Gates can also be entry points
                if (entryPhase.gateOrder() != null) {
                    for (NodeId gateId : entryPhase.gateOrder()) {
                        findReachableNodes(gateId, flow, reachableNodes);
                    }
                }
            }
        }

        // Report unreachable nodes
        for (NodeId nodeId : flow.nodes().keySet()) {
            if (!reachableNodes.contains(nodeId)) {
                context.addWarning(
                    UNREACHABLE_NODE,
                    String.format("Node '%s' is not reachable from any entry point", nodeId.value()),
                    context.fileLocation()
                );
            }
        }
    }

    private void findReachableNodes(NodeId start, FlowDocument flow, Set<NodeId> reachable) {
        if (reachable.contains(start)) {
            return;
        }

        NodeDocument node = flow.nodes().get(start);
        if (node == null) {
            return;
        }

        reachable.add(start);

        if (node.transitions() != null) {
            for (Transition t : node.transitions()) {
                if (t.to() != null) {
                    findReachableNodes(t.to(), flow, reachable);
                }
            }
        }
    }
}
