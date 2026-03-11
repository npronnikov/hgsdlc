package ru.hgd.sdlc.compiler.domain.validation.rules;

import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.compiler.domain.validation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ru.hgd.sdlc.compiler.domain.validation.rules.ValidationCodes.*;

/**
 * Validates phase definitions in a flow document.
 * Ensures phase IDs are unique, required fields are present, and gates are valid.
 */
public class PhaseValidator implements Validator<FlowDocument> {

    @Override
    public ValidationResult validate(FlowDocument flow, ValidationContext context) {
        context.setFlowDocument(flow);

        // Validate phase order consistency
        validatePhaseOrder(flow, context);

        // Validate each phase
        for (PhaseDocument phase : flow.phases().values()) {
            validatePhase(phase, flow, context);
        }

        // Check for phases not in phase_order
        checkOrphanPhases(flow, context);

        return context.toResult();
    }

    private void validatePhaseOrder(FlowDocument flow, ValidationContext context) {
        List<PhaseId> phaseOrder = flow.phaseOrder();

        if (phaseOrder == null || phaseOrder.isEmpty()) {
            // Empty phase order is valid for simple flows
            return;
        }

        // Check for duplicates in phase_order
        Set<PhaseId> seen = new HashSet<>();
        for (int i = 0; i < phaseOrder.size(); i++) {
            PhaseId phaseId = phaseOrder.get(i);
            if (!seen.add(phaseId)) {
                context.addError(
                    PHASE_ID_NOT_UNIQUE,
                    String.format("Phase '%s' appears multiple times in phase_order", phaseId.value()),
                    context.fileLocation()
                );
            }
        }
    }

    private void validatePhase(PhaseDocument phase, FlowDocument flow, ValidationContext context) {
        SourceLocation location = context.fileLocation();

        // Validate ID
        if (phase.id() == null) {
            context.addError(PHASE_MISSING_ID, "Phase is missing id", location);
        }

        // Validate name
        if (phase.name() == null || phase.name().isBlank()) {
            context.addError(
                PHASE_MISSING_NAME,
                String.format("Phase '%s' is missing name", phase.id() != null ? phase.id().value() : "unknown"),
                location
            );
        }

        // Validate nodes exist
        validateNodeReferences(phase, flow, context);

        // Validate gate nodes
        validateGateNodes(phase, flow, context);

        // Check for empty phase (warning)
        boolean hasNodes = phase.nodeOrder() != null && !phase.nodeOrder().isEmpty();
        boolean hasGates = phase.gateOrder() != null && !phase.gateOrder().isEmpty();
        if (!hasNodes && !hasGates) {
            context.addWarning(
                PHASE_EMPTY,
                String.format("Phase '%s' has no nodes or gates", phase.id().value()),
                location
            );
        }
    }

    private void validateNodeReferences(PhaseDocument phase, FlowDocument flow, ValidationContext context) {
        if (phase.nodeOrder() == null) {
            return;
        }

        Set<NodeId> seenNodes = new HashSet<>();
        for (NodeId nodeId : phase.nodeOrder()) {
            // Check for duplicates
            if (!seenNodes.add(nodeId)) {
                context.addError(
                    NODE_ID_NOT_UNIQUE,
                    String.format("Node '%s' appears multiple times in phase '%s'", nodeId.value(), phase.id().value()),
                    context.fileLocation()
                );
            }

            // Check node exists
            NodeDocument node = flow.nodes().get(nodeId);
            if (node == null) {
                context.addError(
                    UNRESOLVED_NODE_REF,
                    String.format("Node '%s' referenced in phase '%s' does not exist", nodeId.value(), phase.id().value()),
                    context.fileLocation()
                );
            }
        }
    }

    private void validateGateNodes(PhaseDocument phase, FlowDocument flow, ValidationContext context) {
        if (phase.gateOrder() == null) {
            return;
        }

        Set<NodeId> seenGates = new HashSet<>();
        for (NodeId gateId : phase.gateOrder()) {
            // Check for duplicates
            if (!seenGates.add(gateId)) {
                context.addError(
                    NODE_ID_NOT_UNIQUE,
                    String.format("Gate '%s' appears multiple times in phase '%s'", gateId.value(), phase.id().value()),
                    context.fileLocation()
                );
            }

            // Check gate node exists and is a gate
            NodeDocument node = flow.nodes().get(gateId);
            if (node == null) {
                context.addError(
                    UNRESOLVED_NODE_REF,
                    String.format("Gate '%s' referenced in phase '%s' does not exist", gateId.value(), phase.id().value()),
                    context.fileLocation()
                );
            } else if (node.type() != NodeType.GATE) {
                context.addError(
                    NODE_INVALID_TYPE,
                    String.format("Node '%s' in gate_order of phase '%s' is not a GATE node", gateId.value(), phase.id().value()),
                    context.fileLocation()
                );
            }
        }
    }

    private void checkOrphanPhases(FlowDocument flow, ValidationContext context) {
        List<PhaseId> phaseOrder = flow.phaseOrder();
        if (phaseOrder == null || phaseOrder.isEmpty()) {
            // If no phase_order defined, check that all phases are in the map
            return;
        }

        Set<PhaseId> orderedPhases = new HashSet<>(phaseOrder);
        for (PhaseId phaseId : flow.phases().keySet()) {
            if (!orderedPhases.contains(phaseId)) {
                context.addWarning(
                    PHASE_ORDER_MISMATCH,
                    String.format("Phase '%s' is defined but not in phase_order", phaseId.value()),
                    context.fileLocation()
                );
            }
        }
    }
}
