package ru.hgd.sdlc.compiler.domain.validation.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.compiler.domain.validation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrossReferenceValidator")
class CrossReferenceValidatorTest {

    private CrossReferenceValidator validator;
    private ValidationContext context;

    @BeforeEach
    void setUp() {
        validator = new CrossReferenceValidator();
        context = ValidationContext.forFile("test-flow.md");
    }

    private FlowDocument.FlowDocumentBuilder baseFlowBuilder() {
        return FlowDocument.builder()
            .id(FlowId.of("test-flow"))
            .version(SemanticVersion.of("1.0.0"));
    }

    @Nested
    @DisplayName("phase order references")
    class PhaseOrderReferencesTest {

        @Test
        @DisplayName("reports error for undefined phase in phase_order")
        void reportsErrorForUndefinedPhaseInPhaseOrder() {
            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of(PhaseId.of("undefined-phase")))
                .phases(Map.of())
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.UNRESOLVED_PHASE_ORDER_REF)));
        }

        @Test
        @DisplayName("accepts defined phases in phase_order")
        void acceptsDefinedPhasesInPhaseOrder() {
            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .build();

            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(PhaseId.of("setup"), phase))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.isValid());
        }
    }

    @Nested
    @DisplayName("skill references")
    class SkillReferencesTest {

        @Test
        @DisplayName("warns for unknown skill reference")
        void warnsForUnknownSkillReference() {
            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-node"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.skill(SkillId.of("unknown-skill")))
                .phaseId(PhaseId.of("setup"))
                .build();

            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .nodeOrder(List.of(NodeId.of("test-node")))
                .build();

            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(PhaseId.of("setup"), phase))
                .nodes(Map.of(NodeId.of("test-node"), node))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.hasIssues());
            assertTrue(result.warnings().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.UNRESOLVED_SKILL_REF)));
        }

        @Test
        @DisplayName("accepts known skill reference")
        void acceptsKnownSkillReference() {
            validator = CrossReferenceValidator.withSkills(Set.of("known-skill"));

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-node"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.skill(SkillId.of("known-skill")))
                .phaseId(PhaseId.of("setup"))
                .build();

            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .nodeOrder(List.of(NodeId.of("test-node")))
                .build();

            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(PhaseId.of("setup"), phase))
                .nodes(Map.of(NodeId.of("test-node"), node))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.isValid());
            assertTrue(result.warnings().isEmpty());
        }
    }

    @Nested
    @DisplayName("artifact references")
    class ArtifactReferencesTest {

        @Test
        @DisplayName("reports error for undefined input artifact")
        void reportsErrorForUndefinedInputArtifact() {
            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-node"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .phaseId(PhaseId.of("setup"))
                .inputs(List.of(ArtifactBinding.required(ArtifactTemplateId.of("undefined-artifact"))))
                .build();

            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .nodeOrder(List.of(NodeId.of("test-node")))
                .build();

            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(PhaseId.of("setup"), phase))
                .nodes(Map.of(NodeId.of("test-node"), node))
                .artifacts(Map.of())
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.UNRESOLVED_ARTIFACT_REF)));
        }

        @Test
        @DisplayName("accepts defined artifact reference")
        void acceptsDefinedArtifactReference() {
            ArtifactTemplateDocument artifact = ArtifactTemplateDocument.builder()
                .id(ArtifactTemplateId.of("defined-artifact"))
                .name("Defined Artifact")
                .build();

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-node"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .phaseId(PhaseId.of("setup"))
                .inputs(List.of(ArtifactBinding.required(ArtifactTemplateId.of("defined-artifact"))))
                .build();

            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .nodeOrder(List.of(NodeId.of("test-node")))
                .build();

            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(PhaseId.of("setup"), phase))
                .nodes(Map.of(NodeId.of("test-node"), node))
                .artifacts(Map.of("defined-artifact", artifact))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.isValid());
        }
    }

    @Nested
    @DisplayName("transition references")
    class TransitionReferencesTest {

        @Test
        @DisplayName("reports error for transition to undefined node")
        void reportsErrorForTransitionToUndefinedNode() {
            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-node"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .phaseId(PhaseId.of("setup"))
                .transitions(List.of(
                    Transition.forward(NodeId.of("test-node"), NodeId.of("undefined-node"))
                ))
                .build();

            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .nodeOrder(List.of(NodeId.of("test-node")))
                .build();

            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(PhaseId.of("setup"), phase))
                .nodes(Map.of(NodeId.of("test-node"), node))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.UNRESOLVED_NODE_REF)));
        }
    }
}
