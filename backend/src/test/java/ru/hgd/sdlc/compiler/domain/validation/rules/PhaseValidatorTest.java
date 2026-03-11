package ru.hgd.sdlc.compiler.domain.validation.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.compiler.domain.validation.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PhaseValidator")
class PhaseValidatorTest {

    private PhaseValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PhaseValidator();
    }

    @Nested
    @DisplayName("valid flow")
    class ValidFlowTest {

        @Test
        @DisplayName("validates flow with valid phases")
        void validatesFlowWithValidPhases() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");

            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup Phase")
                .build();

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(PhaseId.of("setup"), phase))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.isValid(), "Expected valid but got: " + result.allIssues());
        }

        @Test
        @DisplayName("validates empty flow")
        void validatesEmptyFlow() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.isValid());
        }
    }

    @Nested
    @DisplayName("phase order validation")
    class PhaseOrderValidationTest {

        @Test
        @DisplayName("reports error for duplicate phases in order")
        void reportsErrorForDuplicatePhasesInOrder() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");

            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .build();

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("setup"), PhaseId.of("setup")))
                .phases(Map.of(PhaseId.of("setup"), phase))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.PHASE_ID_NOT_UNIQUE)));
        }

        @Test
        @DisplayName("warns about phases not in order")
        void warnsAboutPhasesNotInOrder() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");

            PhaseDocument phase1 = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .build();
            PhaseDocument phase2 = PhaseDocument.builder()
                .id(PhaseId.of("develop"))
                .name("Develop")
                .build();

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(
                    PhaseId.of("setup"), phase1,
                    PhaseId.of("develop"), phase2
                ))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.hasIssues());
            assertTrue(result.warnings().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.PHASE_ORDER_MISMATCH)));
        }
    }

    @Nested
    @DisplayName("phase field validation")
    class PhaseFieldValidationTest {

        @Test
        @DisplayName("warns about empty phase")
        void warnsAboutEmptyPhase() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");

            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .build();

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(PhaseId.of("setup"), phase))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.warnings().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.PHASE_EMPTY)));
        }
    }

    @Nested
    @DisplayName("node reference validation")
    class NodeReferenceValidationTest {

        @Test
        @DisplayName("reports error for non-existent node in nodeOrder")
        void reportsErrorForNonExistentNodeInNodeOrder() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");

            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .nodeOrder(List.of(NodeId.of("non-existent")))
                .build();

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(PhaseId.of("setup"), phase))
                .nodes(Map.of())
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.UNRESOLVED_NODE_REF)));
        }

        @Test
        @DisplayName("reports error for non-gate in gateOrder")
        void reportsErrorForNonGateInGateOrder() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");

            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .gateOrder(List.of(NodeId.of("executor-node")))
                .build();

            NodeDocument executor = NodeDocument.builder()
                .id(NodeId.of("executor-node"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .build();

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(PhaseId.of("setup"), phase))
                .nodes(Map.of(NodeId.of("executor-node"), executor))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.NODE_INVALID_TYPE)));
        }
    }
}
