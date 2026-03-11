package ru.hgd.sdlc.compiler.domain.validation.rules;

import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.compiler.domain.validation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ru.hgd.sdlc.compiler.domain.validation.rules.ValidationCodes.*;

/**
 * Validates cross-references in a flow document.
 * Ensures all phase references, node dependencies, skill references, and
 * artifact bindings resolve to existing entities.
 */
public class CrossReferenceValidator implements Validator<FlowDocument> {

    // Registry of known skills (would be injected or configured in production)
    private final Set<String> knownSkills;

    public CrossReferenceValidator() {
        this(Set.of());
    }

    public CrossReferenceValidator(Set<String> knownSkills) {
        this.knownSkills = knownSkills != null ? knownSkills : Set.of();
    }

    @Override
    public ValidationResult validate(FlowDocument flow, ValidationContext context) {
        context.setFlowDocument(flow);

        // Register reference resolvers
        registerResolvers(flow, context);

        // Validate phase order references
        validatePhaseOrderReferences(flow, context);

        // Validate node references in phases
        validateNodeReferencesInPhases(flow, context);

        // Validate skill references
        validateSkillReferences(flow, context);

        // Validate artifact references
        validateArtifactReferences(flow, context);

        // Validate transition references
        validateTransitionReferences(flow, context);

        return context.toResult();
    }

    private void registerResolvers(FlowDocument flow, ValidationContext context) {
        // Register phase resolver
        context.registerResolver("phase", phaseId -> flow.phases().containsKey(PhaseId.of(phaseId)));

        // Register node resolver
        context.registerResolver("node", nodeId -> flow.nodes().containsKey(NodeId.of(nodeId)));

        // Register skill resolver
        context.registerResolver("skill", skillId -> knownSkills.contains(skillId));

        // Register artifact resolver
        context.registerResolver("artifact", artifactId -> flow.artifacts().containsKey(artifactId));
    }

    private void validatePhaseOrderReferences(FlowDocument flow, ValidationContext context) {
        List<PhaseId> phaseOrder = flow.phaseOrder();
        if (phaseOrder == null) {
            return;
        }

        Set<PhaseId> definedPhases = flow.phases().keySet();

        for (PhaseId phaseId : phaseOrder) {
            if (!definedPhases.contains(phaseId)) {
                context.addError(
                    UNRESOLVED_PHASE_ORDER_REF,
                    String.format("Phase '%s' in phase_order is not defined", phaseId.value()),
                    context.fileLocation()
                );
            }
        }
    }

    private void validateNodeReferencesInPhases(FlowDocument flow, ValidationContext context) {
        Set<NodeId> referencedNodes = new HashSet<>();

        // Collect all nodes referenced in phases
        for (PhaseDocument phase : flow.phases().values()) {
            if (phase.nodeOrder() != null) {
                referencedNodes.addAll(phase.nodeOrder());
            }
            if (phase.gateOrder() != null) {
                referencedNodes.addAll(phase.gateOrder());
            }
        }

        // Check each referenced node exists
        for (NodeId nodeId : referencedNodes) {
            if (!flow.nodes().containsKey(nodeId)) {
                context.addError(
                    UNRESOLVED_NODE_REF,
                    String.format("Node '%s' referenced in phase but not defined", nodeId.value()),
                    context.fileLocation()
                );
            }
        }
    }

    private void validateSkillReferences(FlowDocument flow, ValidationContext context) {
        for (NodeDocument node : flow.nodes().values()) {
            // Check handler reference
            if (node.handler() != null && node.handler().isPresent()) {
                HandlerRef handler = node.handler().get();

                if (handler.kind() == HandlerKind.SKILL) {
                    String skillId = handler.asSkillId() != null ? handler.asSkillId().value() : null;
                    if (skillId != null && !knownSkills.contains(skillId)) {
                        context.addWarning(
                            UNRESOLVED_SKILL_REF,
                            String.format("Node '%s' references unknown skill '%s'",
                                node.id().value(), skillId),
                            context.fileLocation()
                        );
                    }
                }
            }
        }
    }

    private void validateArtifactReferences(FlowDocument flow, ValidationContext context) {
        Set<String> definedArtifacts = flow.artifacts() != null ? flow.artifacts().keySet() : Set.of();

        for (NodeDocument node : flow.nodes().values()) {
            // Validate input artifact bindings
            if (node.inputs() != null) {
                for (ArtifactBinding binding : node.inputs()) {
                    if (binding.artifactId() != null) {
                        String templateId = binding.artifactId().value();
                        if (!definedArtifacts.contains(templateId)) {
                            context.addError(
                                UNRESOLVED_ARTIFACT_REF,
                                String.format("Node '%s' references undefined input artifact '%s'",
                                    node.id().value(), templateId),
                                context.fileLocation()
                            );
                        }
                    }
                }
            }

            // Validate output artifact bindings
            if (node.outputs() != null) {
                for (ArtifactBinding binding : node.outputs()) {
                    if (binding.artifactId() != null) {
                        String templateId = binding.artifactId().value();
                        if (!definedArtifacts.contains(templateId)) {
                            // Output artifacts might be defined by the node itself, so this is a warning
                            context.addWarning(
                                UNRESOLVED_ARTIFACT_REF,
                                String.format("Node '%s' outputs to undefined artifact '%s'",
                                    node.id().value(), templateId),
                                context.fileLocation()
                            );
                        }
                    }
                }
            }
        }
    }

    private void validateTransitionReferences(FlowDocument flow, ValidationContext context) {
        Set<NodeId> definedNodes = flow.nodes().keySet();

        for (NodeDocument node : flow.nodes().values()) {
            if (node.transitions() == null) {
                continue;
            }

            for (Transition transition : node.transitions()) {
                // Validate target node exists
                if (transition.to() != null && !definedNodes.contains(transition.to())) {
                    context.addError(
                        UNRESOLVED_NODE_REF,
                        String.format("Node '%s' has transition to undefined node '%s'",
                            node.id().value(), transition.to().value()),
                        context.fileLocation()
                    );
                }
            }
        }
    }

    /**
     * Creates a validator with a set of known skill IDs.
     *
     * @param skillIds the known skill IDs
     * @return a new validator instance
     */
    public static CrossReferenceValidator withSkills(Set<String> skillIds) {
        return new CrossReferenceValidator(skillIds);
    }
}
