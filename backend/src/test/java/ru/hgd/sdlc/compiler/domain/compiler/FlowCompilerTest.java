package ru.hgd.sdlc.compiler.domain.compiler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.NodeIr;
import ru.hgd.sdlc.compiler.domain.model.ir.PhaseIr;
import ru.hgd.sdlc.compiler.domain.model.ir.TransitionIr;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FlowCompiler")
class FlowCompilerTest {

    private FlowCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new FlowCompiler();
    }

    @Nested
    @DisplayName("valid input")
    class ValidInputTest {

        @Test
        @DisplayName("compiles minimal valid flow")
        void compilesMinimalValidFlow() {
            FlowDocument document = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .build();

            CompilerResult<FlowIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            assertNotNull(result.getIr());
            assertEquals("test-flow", result.getIr().flowId().value());
            assertEquals("1.0.0", result.getIr().flowVersion().toString());
        }

        @Test
        @DisplayName("compiles flow with phases and nodes")
        void compilesFlowWithPhasesAndNodes() {
            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("phase-1"))
                .name("Phase 1")
                .order(0)
                .build();

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .executorKind(ExecutorKind.SKILL)
                .handler(HandlerRef.skill(SkillId.of("test-skill")))
                .build();

            FlowDocument document = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("phase-1")))
                .phases(Map.of(PhaseId.of("phase-1"), phase))
                .nodes(Map.of(NodeId.of("node-1"), node))
                .build();

            CompilerResult<FlowIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            FlowIr ir = result.getIr();
            assertEquals(1, ir.phases().size());
            assertEquals(1, ir.nodeIndex().size());

            PhaseIr phaseIr = ir.phases().get(0);
            assertEquals("phase-1", phaseIr.id().value());
            assertEquals("Phase 1", phaseIr.name());

            NodeIr nodeIr = ir.nodeIndex().get(NodeId.of("node-1"));
            assertNotNull(nodeIr);
            assertEquals("node-1", nodeIr.id().value());
            assertTrue(nodeIr.isExecutor());
        }

        @Test
        @DisplayName("compiles flow with transitions")
        void compilesFlowWithTransitions() {
            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("phase-1"))
                .name("Phase 1")
                .order(0)
                .build();

            NodeDocument node1 = NodeDocument.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .executorKind(ExecutorKind.SKILL)
                .handler(HandlerRef.skill(SkillId.of("skill-1")))
                .transitions(List.of(
                    Transition.forward(NodeId.of("node-1"), NodeId.of("node-2"))
                ))
                .build();

            NodeDocument node2 = NodeDocument.builder()
                .id(NodeId.of("node-2"))
                .type(NodeType.GATE)
                .phaseId(PhaseId.of("phase-1"))
                .gateKind(GateKind.APPROVAL)
                .requiredApprovers(Set.of(Role.of("developer")))
                .build();

            FlowDocument document = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("phase-1")))
                .phases(Map.of(PhaseId.of("phase-1"), phase))
                .nodes(Map.of(
                    NodeId.of("node-1"), node1,
                    NodeId.of("node-2"), node2
                ))
                .build();

            CompilerResult<FlowIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            List<TransitionIr> transitions = result.getIr().transitions();
            assertEquals(1, transitions.size());

            TransitionIr transition = transitions.get(0);
            assertEquals("node-1", transition.fromNode().value());
            assertEquals("node-2", transition.toNode().value());
            assertTrue(transition.isForward());
        }

        @Test
        @DisplayName("compiles flow with start roles")
        void compilesFlowWithStartRoles() {
            FlowDocument document = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .startRoles(Set.of(Role.of("developer"), Role.of("architect")))
                .build();

            CompilerResult<FlowIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            assertEquals(2, result.getIr().startRoles().size());
            assertTrue(result.getIr().startRoles().contains(Role.of("developer")));
            assertTrue(result.getIr().startRoles().contains(Role.of("architect")));
        }
    }

    @Nested
    @DisplayName("validation errors")
    class ValidationErrorTest {

        @Test
        @DisplayName("fails when phaseOrder references non-existent phase")
        void failsWhenPhaseOrderReferencesNonExistentPhase() {
            FlowDocument document = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("non-existent")))
                .build();

            CompilerResult<FlowIr> result = compiler.compile(document);

            assertTrue(result.isFailure());
            assertEquals(1, result.getErrors().size());
            assertEquals("E2002", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("phase"));
        }

        @Test
        @DisplayName("fails when node references non-existent phase")
        void failsWhenNodeReferencesNonExistentPhase() {
            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("non-existent"))
                .executorKind(ExecutorKind.SKILL)
                .handler(HandlerRef.skill(SkillId.of("skill-1")))
                .build();

            FlowDocument document = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .nodes(Map.of(NodeId.of("node-1"), node))
                .build();

            CompilerResult<FlowIr> result = compiler.compile(document);

            assertTrue(result.isFailure());
            assertEquals(1, result.getErrors().size());
            assertEquals("E2002", result.getFirstError().code());
        }

        @Test
        @DisplayName("fails when executor node missing handler")
        void failsWhenExecutorNodeMissingHandler() {
            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("phase-1"))
                .name("Phase 1")
                .order(0)
                .build();

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .build();

            FlowDocument document = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("phase-1")))
                .phases(Map.of(PhaseId.of("phase-1"), phase))
                .nodes(Map.of(NodeId.of("node-1"), node))
                .build();

            CompilerResult<FlowIr> result = compiler.compile(document);

            assertTrue(result.isFailure());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("fails when gate node missing gateKind")
        void failsWhenGateNodeMissingGateKind() {
            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("phase-1"))
                .name("Phase 1")
                .order(0)
                .build();

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.GATE)
                .phaseId(PhaseId.of("phase-1"))
                .build();

            FlowDocument document = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("phase-1")))
                .phases(Map.of(PhaseId.of("phase-1"), phase))
                .nodes(Map.of(NodeId.of("node-1"), node))
                .build();

            CompilerResult<FlowIr> result = compiler.compile(document);

            assertTrue(result.isFailure());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("fails when transition targets non-existent node")
        void failsWhenTransitionTargetsNonExistentNode() {
            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("phase-1"))
                .name("Phase 1")
                .order(0)
                .build();

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .executorKind(ExecutorKind.SKILL)
                .handler(HandlerRef.skill(SkillId.of("skill-1")))
                .transitions(List.of(
                    Transition.forward(NodeId.of("node-1"), NodeId.of("non-existent"))
                ))
                .build();

            FlowDocument document = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("phase-1")))
                .phases(Map.of(PhaseId.of("phase-1"), phase))
                .nodes(Map.of(NodeId.of("node-1"), node))
                .build();

            CompilerResult<FlowIr> result = compiler.compile(document);

            assertTrue(result.isFailure());
            assertEquals("E2007", result.getFirstError().code());
        }

        @Test
        @DisplayName("collects multiple errors")
        void collectsMultipleErrors() {
            PhaseDocument phase = PhaseDocument.builder()
                .id(PhaseId.of("phase-1"))
                .name("Phase 1")
                .order(0)
                .build();

            // Node missing both handler and executorKind
            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .transitions(List.of(
                    Transition.forward(NodeId.of("node-1"), NodeId.of("non-existent"))
                ))
                .build();

            FlowDocument document = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("phase-1")))
                .phases(Map.of(PhaseId.of("phase-1"), phase))
                .nodes(Map.of(NodeId.of("node-1"), node))
                .build();

            CompilerResult<FlowIr> result = compiler.compile(document);

            assertTrue(result.isFailure());
            assertTrue(result.getErrors().size() >= 2);
        }
    }

    @Nested
    @DisplayName("IR structure")
    class IrStructureTest {

        @Test
        @DisplayName("IR contains correct metadata")
        void irContainsCorrectMetadata() {
            FlowDocument document = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .build();

            CompilerResult<FlowIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            FlowIr ir = result.getIr();

            assertNotNull(ir.metadata());
            assertNotNull(ir.metadata().compiledAt());
            assertNotNull(ir.metadata().compilerVersion());
            assertNotNull(ir.metadata().irChecksum());
            assertNotNull(ir.metadata().packageChecksum());
        }

        @Test
        @DisplayName("IR implements CompiledIR interface")
        void irImplementsCompiledIRInterface() {
            FlowDocument document = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .build();

            CompilerResult<FlowIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            FlowIr ir = result.getIr();

            assertTrue(ir instanceof CompiledIR);
            assertEquals("test-flow", ir.irId());
            assertEquals("1.0.0", ir.sourceVersion());
            assertNotNull(ir.checksum());
        }
    }
}
