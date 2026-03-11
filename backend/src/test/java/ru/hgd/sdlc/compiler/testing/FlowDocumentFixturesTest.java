package ru.hgd.sdlc.compiler.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FlowDocumentFixtures.
 */
class FlowDocumentFixturesTest {

    @Nested
    @DisplayName("simpleFlow()")
    class SimpleFlowTests {

        @Test
        @DisplayName("should create valid minimal flow")
        void shouldCreateValidMinimalFlow() {
            FlowDocument flow = FlowDocumentFixtures.simpleFlow();

            assertNotNull(flow);
            assertEquals("simple-flow", flow.id().value());
            assertEquals("Simple Flow", flow.name());
            assertEquals("1.0.0", flow.version().toString());
            assertNotNull(flow.description());
            assertFalse(flow.description().isEmpty());
        }

        @Test
        @DisplayName("should have single phase")
        void shouldHaveSinglePhase() {
            FlowDocument flow = FlowDocumentFixtures.simpleFlow();

            assertEquals(1, flow.phaseOrder().size());
            assertTrue(flow.phaseOrder().contains(PhaseId.of("main")));
        }

        @Test
        @DisplayName("should have developer start role")
        void shouldHaveDeveloperStartRole() {
            FlowDocument flow = FlowDocumentFixtures.simpleFlow();

            assertTrue(flow.startRoles().contains(Role.of("developer")));
        }
    }

    @Nested
    @DisplayName("multiPhaseFlow()")
    class MultiPhaseFlowTests {

        @Test
        @DisplayName("should create flow with three phases")
        void shouldCreateFlowWithThreePhases() {
            FlowDocument flow = FlowDocumentFixtures.multiPhaseFlow();

            assertEquals(3, flow.phaseOrder().size());
            assertEquals(PhaseId.of("development"), flow.phaseOrder().get(0));
            assertEquals(PhaseId.of("review"), flow.phaseOrder().get(1));
            assertEquals(PhaseId.of("deployment"), flow.phaseOrder().get(2));
        }

        @Test
        @DisplayName("should have transitions between nodes")
        void shouldHaveTransitionsBetweenNodes() {
            FlowDocument flow = FlowDocumentFixtures.multiPhaseFlow();

            NodeDocument devNode = flow.getNode(NodeId.of("develop"));
            assertNotNull(devNode);
            assertFalse(devNode.transitions().isEmpty());
        }
    }

    @Nested
    @DisplayName("flowWithGates()")
    class FlowWithGatesTests {

        @Test
        @DisplayName("should have approval gate")
        void shouldHaveApprovalGate() {
            FlowDocument flow = FlowDocumentFixtures.flowWithGates();

            NodeDocument gate = flow.getNode(NodeId.of("approval"));
            assertNotNull(gate);
            assertTrue(gate.isGate());
            assertEquals(GateKind.APPROVAL, gate.gateKind().orElse(null));
        }

        @Test
        @DisplayName("should require tech_lead and product_owner approvers")
        void shouldRequireApprovers() {
            FlowDocument flow = FlowDocumentFixtures.flowWithGates();

            NodeDocument gate = flow.getNode(NodeId.of("approval"));
            assertTrue(gate.requiredApprovers().contains(Role.of("tech_lead")));
            assertTrue(gate.requiredApprovers().contains(Role.of("product_owner")));
        }
    }

    @Nested
    @DisplayName("flowWithSteps()")
    class FlowWithStepsTests {

        @Test
        @DisplayName("should have conditional gate")
        void shouldHaveConditionalGate() {
            FlowDocument flow = FlowDocumentFixtures.flowWithSteps();

            NodeDocument gate = flow.getNode(NodeId.of("condition-check"));
            assertNotNull(gate);
            assertEquals(GateKind.CONDITIONAL, gate.gateKind().orElse(null));
        }

        @Test
        @DisplayName("should have skill executor")
        void shouldHaveSkillExecutor() {
            FlowDocument flow = FlowDocumentFixtures.flowWithSteps();

            NodeDocument skillNode = flow.getNode(NodeId.of("skill-execution"));
            assertNotNull(skillNode);
            assertEquals(ExecutorKind.SKILL, skillNode.executorKind().orElse(null));
        }

        @Test
        @DisplayName("should have script executor")
        void shouldHaveScriptExecutor() {
            FlowDocument flow = FlowDocumentFixtures.flowWithSteps();

            NodeDocument scriptNode = flow.getNode(NodeId.of("script-execution"));
            assertNotNull(scriptNode);
            assertEquals(ExecutorKind.SCRIPT, scriptNode.executorKind().orElse(null));
        }
    }

    @Nested
    @DisplayName("invalidFlow()")
    class InvalidFlowTests {

        @Test
        @DisplayName("should have empty phases map")
        void shouldHaveEmptyPhasesMap() {
            FlowDocument flow = FlowDocumentFixtures.invalidFlow();

            assertTrue(flow.phases().isEmpty());
        }

        @Test
        @DisplayName("should reference non-existent phase")
        void shouldReferenceNonExistentPhase() {
            FlowDocument flow = FlowDocumentFixtures.invalidFlow();

            NodeDocument orphanNode = flow.getNode(NodeId.of("orphan-node"));
            assertNotNull(orphanNode);
            assertTrue(orphanNode.phaseId().isPresent());
            assertEquals(PhaseId.of("missing-phase"), orphanNode.phaseId().get());
            assertFalse(flow.phases().containsKey(orphanNode.phaseId().get()));
        }
    }

    @Nested
    @DisplayName("builder()")
    class BuilderTests {

        @Test
        @DisplayName("should provide configurable builder")
        void shouldProvideConfigurableBuilder() {
            FlowDocument flow = FlowDocumentFixtures.builder()
                .name("Custom Name")
                .version(SemanticVersion.of("2.0.0"))
                .build();

            assertEquals("custom-flow", flow.id().value());
            assertEquals("Custom Name", flow.name());
            assertEquals("2.0.0", flow.version().toString());
        }
    }
}
