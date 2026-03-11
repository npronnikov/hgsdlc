package ru.hgd.sdlc.compiler.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TestFlowDocumentBuilder.
 */
class TestFlowDocumentBuilderTest {

    @Nested
    @DisplayName("Basic building")
    class BasicBuildingTests {

        @Test
        @DisplayName("should create flow with ID")
        void shouldCreateFlowWithId() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .build();

            assertEquals("test-flow", flow.id().value());
        }

        @Test
        @DisplayName("should set name and version")
        void shouldSetNameAndVersion() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .withName("Test Flow")
                .withVersion("2.1.0")
                .build();

            assertEquals("Test Flow", flow.name());
            assertEquals("2.1.0", flow.version().toString());
        }

        @Test
        @DisplayName("should set description")
        void shouldSetDescription() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .withDescription("Test description")
                .build();

            assertEquals("Test description", flow.description().content());
        }
    }

    @Nested
    @DisplayName("Phase building")
    class PhaseBuildingTests {

        @Test
        @DisplayName("should add single phase")
        void shouldAddSinglePhase() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .withPhase("main", "Main Phase")
                .build();

            assertEquals(1, flow.phaseOrder().size());
            assertTrue(flow.phases().containsKey(PhaseId.of("main")));
        }

        @Test
        @DisplayName("should add multiple phases in order")
        void shouldAddMultiplePhasesInOrder() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .withPhase("init", "Initialization")
                .withPhase("dev", "Development")
                .withPhase("deploy", "Deployment")
                .build();

            assertEquals(3, flow.phaseOrder().size());
            assertEquals(PhaseId.of("init"), flow.phaseOrder().get(0));
            assertEquals(PhaseId.of("dev"), flow.phaseOrder().get(1));
            assertEquals(PhaseId.of("deploy"), flow.phaseOrder().get(2));
        }
    }

    @Nested
    @DisplayName("Step building")
    class StepBuildingTests {

        @Test
        @DisplayName("should add executor step to phase")
        void shouldAddExecutorStepToPhase() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .withPhase("main", "Main Phase")
                .withExecutorStep("main", "execute", "Execute Task", HandlerRef.builtin("run"))
                .build();

            NodeDocument node = flow.getNode(NodeId.of("execute"));
            assertNotNull(node);
            assertEquals("Execute Task", node.name());
            assertTrue(node.isExecutor());
            assertEquals(ExecutorKind.SKILL, node.executorKind().orElse(null));
        }

        @Test
        @DisplayName("should throw for non-existent phase")
        void shouldThrowForNonExistentPhase() {
            assertThrows(IllegalArgumentException.class, () ->
                TestFlowDocumentBuilder.create("test-flow")
                    .withExecutorStep("nonexistent", "step", "Step", HandlerRef.builtin("run"))
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("Gate building")
    class GateBuildingTests {

        @Test
        @DisplayName("should add approval gate")
        void shouldAddApprovalGate() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .withPhase("main", "Main Phase")
                .withGate("main", "approval", GateKind.APPROVAL, Set.of(Role.of("tech_lead")))
                .build();

            NodeDocument gate = flow.getNode(NodeId.of("approval"));
            assertNotNull(gate);
            assertTrue(gate.isGate());
            assertEquals(GateKind.APPROVAL, gate.gateKind().orElse(null));
            assertTrue(gate.requiredApprovers().contains(Role.of("tech_lead")));
        }

        @Test
        @DisplayName("should add gate to phase gateOrder")
        void shouldAddGateToPhaseGateOrder() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .withPhase("main", "Main Phase")
                .withGate("main", "approval", GateKind.APPROVAL, Set.of(Role.of("tech_lead")))
                .build();

            PhaseDocument phase = flow.getPhase(PhaseId.of("main"));
            assertTrue(phase.gateOrder().contains(NodeId.of("approval")));
        }
    }

    @Nested
    @DisplayName("Transition building")
    class TransitionBuildingTests {

        @Test
        @DisplayName("should add forward transition")
        void shouldAddForwardTransition() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .withPhase("main", "Main Phase")
                .withExecutorStep("main", "step1", "Step 1", HandlerRef.builtin("run1"))
                .withExecutorStep("main", "step2", "Step 2", HandlerRef.builtin("run2"))
                .withTransition("step1", "step2")
                .build();

            NodeDocument step1 = flow.getNode(NodeId.of("step1"));
            assertEquals(1, step1.transitions().size());
            assertEquals(NodeId.of("step2"), step1.transitions().get(0).to());
        }

        @Test
        @DisplayName("should add conditional transition")
        void shouldAddConditionalTransition() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .withPhase("main", "Main Phase")
                .withExecutorStep("main", "step1", "Step 1", HandlerRef.builtin("run1"))
                .withExecutorStep("main", "step2", "Step 2", HandlerRef.builtin("run2"))
                .withTransition("step1", "step2", "approved == true")
                .build();

            NodeDocument step1 = flow.getNode(NodeId.of("step1"));
            assertEquals("approved == true", step1.transitions().get(0).condition().orElse(null));
        }

        @Test
        @DisplayName("should add rework transition")
        void shouldAddReworkTransition() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .withPhase("main", "Main Phase")
                .withExecutorStep("main", "step1", "Step 1", HandlerRef.builtin("run1"))
                .withExecutorStep("main", "step2", "Step 2", HandlerRef.builtin("run2"))
                .withReworkTransition("step2", "step1")
                .build();

            NodeDocument step2 = flow.getNode(NodeId.of("step2"));
            assertEquals(TransitionType.REWORK, step2.transitions().get(0).type());
        }

        @Test
        @DisplayName("should add skip transition")
        void shouldAddSkipTransition() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .withPhase("main", "Main Phase")
                .withExecutorStep("main", "step1", "Step 1", HandlerRef.builtin("run1"))
                .withExecutorStep("main", "step2", "Step 2", HandlerRef.builtin("run2"))
                .withSkipTransition("step1", "step2", "skip_step2 == true")
                .build();

            NodeDocument step1 = flow.getNode(NodeId.of("step1"));
            Transition skip = step1.transitions().get(0);
            assertEquals(TransitionType.SKIP, skip.type());
            assertEquals("skip_step2 == true", skip.condition().orElse(null));
        }
    }

    @Nested
    @DisplayName("Role building")
    class RoleBuildingTests {

        @Test
        @DisplayName("should add start roles")
        void shouldAddStartRoles() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .withStartRole("developer")
                .withStartRole("admin")
                .build();

            assertTrue(flow.startRoles().contains(Role.of("developer")));
            assertTrue(flow.startRoles().contains(Role.of("admin")));
        }

        @Test
        @DisplayName("should add default developer role if none specified")
        void shouldAddDefaultDeveloperRoleIfNoneSpecified() {
            FlowDocument flow = TestFlowDocumentBuilder.create("test-flow")
                .build();

            assertTrue(flow.startRoles().contains(Role.of("developer")));
        }
    }

    @Nested
    @DisplayName("Complex flow building")
    class ComplexFlowBuildingTests {

        @Test
        @DisplayName("should build complete flow")
        void shouldBuildCompleteFlow() {
            FlowDocument flow = TestFlowDocumentBuilder.create("complete-flow")
                .withName("Complete Flow")
                .withVersion("1.0.0")
                .withDescription("A complete test flow")
                .withPhase("dev", "Development")
                .withPhase("review", "Review")
                .withPhase("deploy", "Deployment")
                .withExecutorStep("dev", "code", "Write Code", HandlerRef.builtin("code-gen"))
                .withGate("review", "approval", GateKind.APPROVAL, Set.of(Role.of("tech_lead")))
                .withExecutorStep("deploy", "release", "Release", HandlerRef.builtin("deploy"))
                .withTransition("code", "approval")
                .withTransition("approval", "release")
                .withStartRoles(Set.of(Role.of("developer"), Role.of("tech_lead")))
                .build();

            assertNotNull(flow);
            assertEquals("complete-flow", flow.id().value());
            assertEquals("Complete Flow", flow.name());
            assertEquals(3, flow.phases().size());
            assertEquals(3, flow.nodes().size());
        }
    }
}
