package ru.hgd.sdlc.compiler.domain.model.ir;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FlowIr")
class FlowIrTest {

    private Sha256 hash(String value) {
        return Sha256.of(value);
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("builds minimal flow IR")
        void buildsMinimalFlowIr() {
            IrMetadata metadata = IrMetadata.builder()
                .packageChecksum(hash("package"))
                .irChecksum(hash("ir"))
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

            FlowIr ir = FlowIr.builder()
                .flowId(FlowId.of("test-flow"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(metadata)
                .build();

            assertEquals("test-flow", ir.flowId().value());
            assertEquals("1.0.0", ir.flowVersion().toString());
            assertNotNull(ir.metadata());
            assertTrue(ir.phases().isEmpty());
            assertTrue(ir.nodeIndex().isEmpty());
            assertTrue(ir.transitions().isEmpty());
        }

        @Test
        @DisplayName("builds complete flow IR")
        void buildsCompleteFlowIr() {
            IrMetadata metadata = IrMetadata.builder()
                .packageChecksum(hash("package"))
                .irChecksum(hash("ir"))
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

            PhaseIr phase = PhaseIr.builder()
                .id(PhaseId.of("phase-1"))
                .name("Phase 1")
                .order(0)
                .build();

            NodeIr node = NodeIr.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .nodeHash(hash("node-1"))
                .executorConfig(ExecutorConfig.builder()
                    .kind(ExecutorKind.SKILL)
                    .handler(HandlerRef.skill(SkillId.of("test-skill")))
                    .build())
                .build();

            TransitionIr transition = TransitionIr.forward(
                NodeId.of("node-1"),
                NodeId.of("node-2"),
                0
            );

            FlowIr ir = FlowIr.builder()
                .flowId(FlowId.of("test-flow"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(metadata)
                .phases(List.of(phase))
                .nodeIndex(Map.of(NodeId.of("node-1"), node))
                .transitions(List.of(transition))
                .startRoles(Set.of(Role.of("developer")))
                .resumePolicy(ResumePolicy.FROM_CHECKPOINT)
                .build();

            assertEquals(1, ir.phases().size());
            assertEquals(1, ir.nodeIndex().size());
            assertEquals(1, ir.transitions().size());
            assertEquals(1, ir.startRoles().size());
        }
    }

    @Nested
    @DisplayName("accessors")
    class AccessorsTest {

        @Test
        @DisplayName("getNode returns node by ID")
        void getNodeReturnsNodeById() {
            NodeIr node = NodeIr.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .nodeHash(hash("node-1"))
                .build();

            FlowIr ir = FlowIr.builder()
                .flowId(FlowId.of("test"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(IrMetadata.builder()
                    .packageChecksum(hash("pkg"))
                    .irChecksum(hash("ir"))
                    .compiledAt(Instant.now())
                    .compilerVersion("1.0")
                    .build())
                .nodeIndex(Map.of(NodeId.of("node-1"), node))
                .build();

            assertTrue(ir.getNode(NodeId.of("node-1")).isPresent());
            assertTrue(ir.getNode(NodeId.of("nonexistent")).isEmpty());
        }

        @Test
        @DisplayName("getPhase returns phase by ID")
        void getPhaseReturnsPhaseById() {
            PhaseIr phase = PhaseIr.builder()
                .id(PhaseId.of("phase-1"))
                .name("Phase 1")
                .order(0)
                .build();

            FlowIr ir = FlowIr.builder()
                .flowId(FlowId.of("test"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(IrMetadata.builder()
                    .packageChecksum(hash("pkg"))
                    .irChecksum(hash("ir"))
                    .compiledAt(Instant.now())
                    .compilerVersion("1.0")
                    .build())
                .phases(List.of(phase))
                .build();

            assertTrue(ir.getPhase(PhaseId.of("phase-1")).isPresent());
            assertTrue(ir.getPhase(PhaseId.of("nonexistent")).isEmpty());
        }

        @Test
        @DisplayName("getTransition returns transition by index")
        void getTransitionReturnsTransitionByIndex() {
            TransitionIr t1 = TransitionIr.forward(NodeId.of("a"), NodeId.of("b"), 0);
            TransitionIr t2 = TransitionIr.forward(NodeId.of("b"), NodeId.of("c"), 1);

            FlowIr ir = FlowIr.builder()
                .flowId(FlowId.of("test"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(IrMetadata.builder()
                    .packageChecksum(hash("pkg"))
                    .irChecksum(hash("ir"))
                    .compiledAt(Instant.now())
                    .compilerVersion("1.0")
                    .build())
                .transitions(List.of(t1, t2))
                .build();

            assertTrue(ir.getTransition(0).isPresent());
            assertTrue(ir.getTransition(1).isPresent());
            assertTrue(ir.getTransition(-1).isEmpty());
            assertTrue(ir.getTransition(99).isEmpty());
        }
    }

    @Nested
    @DisplayName("counts")
    class CountsTest {

        @Test
        @DisplayName("totalNodes returns correct count")
        void totalNodesReturnsCorrectCount() {
            NodeIr node1 = NodeIr.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .nodeHash(hash("n1"))
                .build();

            NodeIr node2 = NodeIr.builder()
                .id(NodeId.of("node-2"))
                .type(NodeType.GATE)
                .phaseId(PhaseId.of("phase-1"))
                .nodeHash(hash("n2"))
                .build();

            FlowIr ir = FlowIr.builder()
                .flowId(FlowId.of("test"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(IrMetadata.builder()
                    .packageChecksum(hash("pkg"))
                    .irChecksum(hash("ir"))
                    .compiledAt(Instant.now())
                    .compilerVersion("1.0")
                    .build())
                .nodeIndex(Map.of(
                    NodeId.of("node-1"), node1,
                    NodeId.of("node-2"), node2
                ))
                .build();

            assertEquals(2, ir.totalNodes());
        }

        @Test
        @DisplayName("totalPhases returns correct count")
        void totalPhasesReturnsCorrectCount() {
            PhaseIr phase1 = PhaseIr.builder()
                .id(PhaseId.of("phase-1"))
                .name("Phase 1")
                .order(0)
                .build();

            PhaseIr phase2 = PhaseIr.builder()
                .id(PhaseId.of("phase-2"))
                .name("Phase 2")
                .order(1)
                .build();

            FlowIr ir = FlowIr.builder()
                .flowId(FlowId.of("test"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(IrMetadata.builder()
                    .packageChecksum(hash("pkg"))
                    .irChecksum(hash("ir"))
                    .compiledAt(Instant.now())
                    .compilerVersion("1.0")
                    .build())
                .phases(List.of(phase1, phase2))
                .build();

            assertEquals(2, ir.totalPhases());
        }
    }
}
