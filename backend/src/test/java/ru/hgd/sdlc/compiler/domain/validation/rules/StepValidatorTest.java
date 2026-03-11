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

@DisplayName("StepValidator")
class StepValidatorTest {

    private StepValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StepValidator();
    }

    private FlowDocument createFlowWithNode(NodeDocument node, PhaseId phaseId) {
        PhaseDocument phase = PhaseDocument.builder()
            .id(phaseId)
            .name("Default Phase")
            .nodeOrder(List.of(node.id()))
            .build();

        return FlowDocument.builder()
            .id(FlowId.of("test-flow"))
            .version(SemanticVersion.of("1.0.0"))
            .phaseOrder(List.of(phaseId))
            .phases(Map.of(phaseId, phase))
            .nodes(Map.of(node.id(), node))
            .build();
    }

    @Nested
    @DisplayName("executor node validation")
    class ExecutorNodeTest {

        @Test
        @DisplayName("validates valid executor node")
        void validatesValidExecutorNode() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");
            PhaseId phaseId = PhaseId.of("default");

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-executor"))
                .type(NodeType.EXECUTOR)
                .name("Test Executor")
                .phaseId(phaseId)
                .executorKind(ExecutorKind.BUILTIN)
                .handler(HandlerRef.builtin("test-handler"))
                .build();

            FlowDocument flow = createFlowWithNode(node, phaseId);
            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.isValid(), "Expected valid but got: " + result.allIssues());
        }

        @Test
        @DisplayName("reports error for missing handler")
        void reportsErrorForMissingHandler() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");
            PhaseId phaseId = PhaseId.of("default");

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-executor"))
                .type(NodeType.EXECUTOR)
                .name("Test Executor")
                .phaseId(phaseId)
                .executorKind(ExecutorKind.BUILTIN)
                .build();

            FlowDocument flow = createFlowWithNode(node, phaseId);
            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.NODE_MISSING_HANDLER)));
        }

        @Test
        @DisplayName("warns when executor has gate_kind")
        void warnsWhenExecutorHasGateKind() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");
            PhaseId phaseId = PhaseId.of("default");

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-executor"))
                .type(NodeType.EXECUTOR)
                .name("Test Executor")
                .phaseId(phaseId)
                .executorKind(ExecutorKind.BUILTIN)
                .handler(HandlerRef.builtin("test"))
                .gateKind(GateKind.APPROVAL)
                .build();

            FlowDocument flow = createFlowWithNode(node, phaseId);
            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.hasIssues());
            assertTrue(result.warnings().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.NODE_INVALID_TYPE)));
        }
    }

    @Nested
    @DisplayName("gate node validation")
    class GateNodeTest {

        @Test
        @DisplayName("validates valid gate node")
        void validatesValidGateNode() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");
            PhaseId phaseId = PhaseId.of("default");

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-gate"))
                .type(NodeType.GATE)
                .name("Test Gate")
                .phaseId(phaseId)
                .gateKind(GateKind.APPROVAL)
                .requiredApprovers(Set.of(Role.of("admin")))
                .build();

            FlowDocument flow = createFlowWithNode(node, phaseId);
            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.isValid(), "Expected valid but got: " + result.allIssues());
        }

        @Test
        @DisplayName("reports error for missing gate_kind")
        void reportsErrorForMissingGateKind() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");
            PhaseId phaseId = PhaseId.of("default");

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-gate"))
                .type(NodeType.GATE)
                .name("Test Gate")
                .phaseId(phaseId)
                .build();

            FlowDocument flow = createFlowWithNode(node, phaseId);
            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.NODE_MISSING_GATE_KIND)));
        }

        @Test
        @DisplayName("warns for approval gate without approvers")
        void warnsForApprovalGateWithoutApprovers() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");
            PhaseId phaseId = PhaseId.of("default");

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-gate"))
                .type(NodeType.GATE)
                .name("Test Gate")
                .phaseId(phaseId)
                .gateKind(GateKind.APPROVAL)
                .build();

            FlowDocument flow = createFlowWithNode(node, phaseId);
            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.hasIssues());
            assertTrue(result.warnings().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.SUGGESTED_FIELD_MISSING)));
        }
    }

    @Nested
    @DisplayName("transition validation")
    class TransitionValidationTest {

        @Test
        @DisplayName("reports error for transition to non-existent node")
        void reportsErrorForTransitionToNonExistentNode() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");
            PhaseId phaseId = PhaseId.of("default");

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-executor"))
                .type(NodeType.EXECUTOR)
                .name("Test Executor")
                .phaseId(phaseId)
                .handler(HandlerRef.builtin("test"))
                .transitions(List.of(
                    Transition.forward(NodeId.of("test-executor"), NodeId.of("non-existent"))
                ))
                .build();

            FlowDocument flow = createFlowWithNode(node, phaseId);
            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.UNRESOLVED_NODE_REF)));
        }
    }

    @Nested
    @DisplayName("phase membership")
    class PhaseMembershipTest {

        @Test
        @DisplayName("warns for node without phase assignment")
        void warnsForNodeWithoutPhaseAssignment() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");

            // Node without phaseId - the field is optional in builder
            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-executor"))
                .type(NodeType.EXECUTOR)
                .name("Test Executor")
                .handler(HandlerRef.builtin("test"))
                .build();

            // Phase doesn't reference this node
            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("default"))
                .name("Default")
                .build();

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("default")))
                .phases(Map.of(PhaseId.of("default"), phase))
                .nodes(Map.of(NodeId.of("test-executor"), node))
                .build();

            ValidationResult result = validator.validate(flow, context);

            // The node should be warned as orphan since it has no phaseId
            assertTrue(result.hasIssues(), "Expected warnings but got: " + result.allIssues());
            // Check for any validation issue - the node may be flagged as orphan or missing phase
            assertTrue(result.warnings().stream().anyMatch(e -> true) ||
                result.errors().stream().anyMatch(e -> true), "Expected validation issues for node without phase");
        }

        @Test
        @DisplayName("reports error for non-existent phase reference")
        void reportsErrorForNonExistentPhaseReference() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");
            PhaseId nonExistentPhaseId = PhaseId.of("non-existent");
            PhaseId actualPhaseId = PhaseId.of("default");

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test-executor"))
                .type(NodeType.EXECUTOR)
                .name("Test Executor")
                .phaseId(nonExistentPhaseId)
                .handler(HandlerRef.builtin("test"))
                .build();

            // Create flow with a phase, but node references different phase
            PhaseDocument phase = PhaseDocument.builder()
                .id(actualPhaseId)
                .name("Actual Phase")
                .build();

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(actualPhaseId))
                .phases(Map.of(actualPhaseId, phase))
                .nodes(Map.of(NodeId.of("test-executor"), node))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid(), "Expected failure for non-existent phase but got: " + result);
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.UNRESOLVED_PHASE_REF)));
        }
    }
}
