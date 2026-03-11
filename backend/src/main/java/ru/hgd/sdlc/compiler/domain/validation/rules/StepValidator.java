package ru.hgd.sdlc.compiler.domain.validation.rules;

import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.compiler.domain.validation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ru.hgd.sdlc.compiler.domain.validation.rules.ValidationCodes.*;

/**
 * Validates node (step) definitions in a flow document.
 * Ensures node IDs are unique, required fields are present per type,
 * and transitions are valid.
 */
public class StepValidator implements Validator<FlowDocument> {

    @Override
    public ValidationResult validate(FlowDocument flow, ValidationContext context) {
        context.setFlowDocument(flow);

        // Check for unique node IDs across the entire flow
        Set<NodeId> seenIds = new HashSet<>();

        for (NodeDocument node : flow.nodes().values()) {
            validateNode(node, flow, context);

            // Check global uniqueness
            if (!seenIds.add(node.id())) {
                context.addError(
                    NODE_ID_NOT_UNIQUE,
                    String.format("Node ID '%s' is not unique across the flow", node.id().value()),
                    context.fileLocation()
                );
            }
        }

        return context.toResult();
    }

    private void validateNode(NodeDocument node, FlowDocument flow, ValidationContext context) {
        SourceLocation location = context.fileLocation();

        // Validate required fields for all nodes
        if (node.id() == null) {
            context.addError(NODE_MISSING_ID, "Node is missing id", location);
            return; // Can't continue without ID
        }

        if (node.type() == null) {
            context.addError(
                NODE_INVALID_TYPE,
                String.format("Node '%s' is missing type", node.id().value()),
                location
            );
            return; // Can't continue without type
        }

        // Validate type-specific requirements
        if (node.type() == NodeType.EXECUTOR) {
            validateExecutorNode(node, context);
        } else if (node.type() == NodeType.GATE) {
            validateGateNode(node, context);
        }

        // Validate transitions
        validateTransitions(node, flow, context);

        // Validate phase membership
        validatePhaseMembership(node, flow, context);

        // Validate name (warning)
        if (node.name() == null || node.name().isBlank()) {
            context.addWarning(
                SUGGESTED_FIELD_MISSING,
                String.format("Node '%s' is missing name", node.id().value()),
                location
            );
        }
    }

    private void validateExecutorNode(NodeDocument node, ValidationContext context) {
        // Executor nodes require a handler
        if (node.handler() == null || node.handler().isEmpty()) {
            context.addError(
                NODE_MISSING_HANDLER,
                String.format("Executor node '%s' is missing handler", node.id().value()),
                context.fileLocation()
            );
        }

        // Executor nodes should have executorKind
        if (node.executorKind() == null || node.executorKind().isEmpty()) {
            context.addWarning(
                SUGGESTED_FIELD_MISSING,
                String.format("Executor node '%s' is missing executor_kind", node.id().value()),
                context.fileLocation()
            );
        }

        // Gate-specific fields should not be set
        if (node.gateKind() != null && node.gateKind().isPresent()) {
            context.addWarning(
                NODE_INVALID_TYPE,
                String.format("Executor node '%s' has gate_kind which should only be set for GATE nodes", node.id().value()),
                context.fileLocation()
            );
        }
    }

    private void validateGateNode(NodeDocument node, ValidationContext context) {
        // Gate nodes require a gate kind
        if (node.gateKind() == null || node.gateKind().isEmpty()) {
            context.addError(
                NODE_MISSING_GATE_KIND,
                String.format("Gate node '%s' is missing gate_kind", node.id().value()),
                context.fileLocation()
            );
        }

        // APPROVAL gates should have required approvers
        if (node.gateKind().isPresent() && node.gateKind().get() == GateKind.APPROVAL) {
            if (node.requiredApprovers() == null || node.requiredApprovers().isEmpty()) {
                context.addWarning(
                    SUGGESTED_FIELD_MISSING,
                    String.format("APPROVAL gate '%s' has no required_approvers", node.id().value()),
                    context.fileLocation()
                );
            }
        }

        // Executor-specific fields should not be set
        if (node.handler() != null && node.handler().isPresent()) {
            context.addWarning(
                NODE_INVALID_TYPE,
                String.format("Gate node '%s' has handler which should only be set for EXECUTOR nodes", node.id().value()),
                context.fileLocation()
            );
        }
    }

    private void validateTransitions(NodeDocument node, FlowDocument flow, ValidationContext context) {
        List<Transition> transitions = node.transitions();
        if (transitions == null || transitions.isEmpty()) {
            return;
        }

        Set<NodeId> seenTargets = new HashSet<>();

        for (Transition transition : transitions) {
            // Validate 'to' node exists
            if (transition.to() != null) {
                if (!flow.nodes().containsKey(transition.to())) {
                    context.addError(
                        UNRESOLVED_NODE_REF,
                        String.format("Node '%s' has transition to non-existent node '%s'",
                            node.id().value(), transition.to().value()),
                        context.fileLocation()
                    );
                }

                // Check for duplicate unconditional transitions to same target
                if (transition.condition().isEmpty() && !seenTargets.add(transition.to())) {
                    context.addWarning(
                        NODE_ID_NOT_UNIQUE,
                        String.format("Node '%s' has multiple unconditional transitions to '%s'",
                            node.id().value(), transition.to().value()),
                        context.fileLocation()
                    );
                }
            }

            // Validate 'from' matches current node (should always be true if constructed correctly)
            if (transition.from() != null && !transition.from().equals(node.id())) {
                context.addWarning(
                    NODE_INVALID_TYPE,
                    String.format("Transition 'from' node '%s' does not match current node '%s'",
                        transition.from().value(), node.id().value()),
                    context.fileLocation()
                );
            }

            // Validate condition syntax (basic check)
            transition.condition().ifPresent(condition -> {
                if (condition.isBlank()) {
                    context.addWarning(
                        NODE_INVALID_TYPE,
                        String.format("Node '%s' has transition with blank condition", node.id().value()),
                        context.fileLocation()
                    );
                }
            });
        }
    }

    private void validatePhaseMembership(NodeDocument node, FlowDocument flow, ValidationContext context) {
        // Node should belong to a phase
        if (node.phaseId() == null) {
            context.addWarning(
                ORPHAN_NODE,
                String.format("Node '%s' does not belong to any phase", node.id().value()),
                context.fileLocation()
            );
            return;
        }

        // Phase should exist
        if (node.phaseId().isPresent() && !flow.phases().containsKey(node.phaseId().get())) {
            context.addError(
                UNRESOLVED_PHASE_REF,
                String.format("Node '%s' references non-existent phase '%s'",
                    node.id().value(), node.phaseId().get().value()),
                context.fileLocation()
            );
        }
    }
}
