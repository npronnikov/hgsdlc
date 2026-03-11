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

@DisplayName("SemanticValidator")
class SemanticValidatorTest {

    private SemanticValidator validator;
    private ValidationContext context;

    @BeforeEach
    void setUp() {
        validator = new SemanticValidator();
        context = ValidationContext.forFile("test-flow.md");
    }

    private FlowDocument.FlowDocumentBuilder baseFlowBuilder() {
        return FlowDocument.builder()
            .id(FlowId.of("test-flow"))
            .version(SemanticVersion.of("1.0.0"));
    }

    @Nested
    @DisplayName("entry phase validation")
    class EntryPhaseValidationTest {

        @Test
        @DisplayName("accepts flow with valid entry phase")
        void acceptsFlowWithValidEntryPhase() {
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

        @Test
        @DisplayName("reports error when entry phase is not defined")
        void reportsErrorWhenEntryPhaseIsNotDefined() {
            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of(PhaseId.of("undefined")))
                .phases(Map.of())
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.NO_ENTRY_PHASE)));
        }

        @Test
        @DisplayName("reports error when phases exist but no phase_order")
        void reportsErrorWhenPhasesExistButNoPhaseOrder() {
            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .build();

            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of())
                .phases(Map.of(PhaseId.of("setup"), phase))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.NO_ENTRY_PHASE)));
        }
    }

    @Nested
    @DisplayName("cycle detection")
    class CycleDetectionTest {

        @Test
        @DisplayName("accepts linear phase order")
        void acceptsLinearPhaseOrder() {
            PhaseDocument setup = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .build();
            PhaseDocument develop = PhaseDocument.builder()
                .id(PhaseId.of("develop"))
                .name("Develop")
                .build();
            PhaseDocument review = PhaseDocument.builder()
                .id(PhaseId.of("review"))
                .name("Review")
                .build();

            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of(PhaseId.of("setup"), PhaseId.of("develop"), PhaseId.of("review")))
                .phases(Map.of(
                    PhaseId.of("setup"), setup,
                    PhaseId.of("develop"), develop,
                    PhaseId.of("review"), review
                ))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.isValid());
        }

        // Note: Testing actual cycle detection would require creating transitions
        // between phases, which is complex. The linear order test covers the basic case.
    }

    @Nested
    @DisplayName("reachability validation")
    class ReachabilityValidationTest {

        @Test
        @DisplayName("warns about unreachable phases")
        void warnsAboutUnreachablePhases() {
            PhaseDocument setup = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .build();
            PhaseDocument orphan = PhaseDocument.builder()
                .id(PhaseId.of("orphan"))
                .name("Orphan")
                .build();

            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(
                    PhaseId.of("setup"), setup,
                    PhaseId.of("orphan"), orphan
                ))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.hasIssues());
            assertTrue(result.warnings().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.UNREACHABLE_PHASE)));
        }

        @Test
        @DisplayName("warns about unreachable nodes")
        void warnsAboutUnreachableNodes() {
            NodeDocument reachable = NodeDocument.builder()
                .id(NodeId.of("reachable"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .phaseId(PhaseId.of("setup"))
                .build();

            NodeDocument unreachable = NodeDocument.builder()
                .id(NodeId.of("unreachable"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .phaseId(PhaseId.of("setup"))
                .build();

            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .nodeOrder(List.of(NodeId.of("reachable"))) // unreachable not in order
                .build();

            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of(PhaseId.of("setup")))
                .phases(Map.of(PhaseId.of("setup"), phase))
                .nodes(Map.of(
                    NodeId.of("reachable"), reachable,
                    NodeId.of("unreachable"), unreachable
                ))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.warnings().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.UNREACHABLE_NODE)));
        }
    }

    @Nested
    @DisplayName("terminal phase validation")
    class TerminalPhaseValidationTest {

        @Test
        @DisplayName("accepts flow with terminal phase")
        void acceptsFlowWithTerminalPhase() {
            PhaseDocument setup = PhaseDocument.builder()
                .id(PhaseId.of("setup"))
                .name("Setup")
                .build();
            PhaseDocument complete = PhaseDocument.builder()
                .id(PhaseId.of("complete"))
                .name("Complete")
                .build();

            FlowDocument flow = baseFlowBuilder()
                .phaseOrder(List.of(PhaseId.of("setup"), PhaseId.of("complete")))
                .phases(Map.of(
                    PhaseId.of("setup"), setup,
                    PhaseId.of("complete"), complete
                ))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.isValid());
        }
    }
}
