package ru.hgd.sdlc.compiler.domain.model.ir;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.shared.hashing.Sha256;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NodeIr")
class NodeIrTest {

    private Sha256 hash(String value) {
        return Sha256.of(value);
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("builds executor node IR")
        void buildsExecutorNodeIr() {
            ExecutorConfig executorConfig = ExecutorConfig.builder()
                .kind(ExecutorKind.SKILL)
                .handler(HandlerRef.skill(SkillId.of("generate-code")))
                .build();

            NodeIr node = NodeIr.builder()
                .id(NodeId.of("executor-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .name("Code Generator")
                .nodeHash(hash("executor-1"))
                .executorConfig(executorConfig)
                .build();

            assertEquals("executor-1", node.id().value());
            assertEquals(NodeType.EXECUTOR, node.type());
            assertTrue(node.isExecutor());
            assertFalse(node.isGate());
            assertTrue(node.executorConfig().isPresent());
            assertTrue(node.gateConfig().isEmpty());
        }

        @Test
        @DisplayName("builds gate node IR")
        void buildsGateNodeIr() {
            GateConfig gateConfig = GateConfig.builder()
                .kind(GateKind.APPROVAL)
                .build();

            NodeIr node = NodeIr.builder()
                .id(NodeId.of("approval-1"))
                .type(NodeType.GATE)
                .phaseId(PhaseId.of("phase-1"))
                .name("Approval Gate")
                .nodeHash(hash("approval-1"))
                .gateConfig(gateConfig)
                .build();

            assertEquals(NodeType.GATE, node.type());
            assertTrue(node.isGate());
            assertFalse(node.isExecutor());
            assertTrue(node.gateConfig().isPresent());
            assertTrue(node.executorConfig().isEmpty());
        }

        @Test
        @DisplayName("builds node with artifact bindings")
        void buildsNodeWithArtifactBindings() {
            ResolvedArtifactBinding input = ResolvedArtifactBinding.required(
                ArtifactTemplateId.of("input-doc"),
                hash("input-schema")
            );
            ResolvedArtifactBinding output = ResolvedArtifactBinding.required(
                ArtifactTemplateId.of("output-code"),
                hash("output-schema")
            );

            NodeIr node = NodeIr.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .nodeHash(hash("node-1"))
                .executorConfig(ExecutorConfig.builder()
                    .kind(ExecutorKind.BUILTIN)
                    .handler(HandlerRef.builtin("transform"))
                    .build())
                .inputs(java.util.List.of(input))
                .outputs(java.util.List.of(output))
                .build();

            assertEquals(1, node.inputs().size());
            assertEquals(1, node.outputs().size());
            assertEquals("input-doc", node.inputs().get(0).artifactId().value());
        }

        @Test
        @DisplayName("builds node with transition indices")
        void buildsNodeWithTransitionIndices() {
            NodeIr node = NodeIr.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .nodeHash(hash("node-1"))
                .executorConfig(ExecutorConfig.builder()
                    .kind(ExecutorKind.BUILTIN)
                    .handler(HandlerRef.builtin("test"))
                    .build())
                .transitionIndices(java.util.List.of(0, 1, 2))
                .build();

            assertEquals(3, node.transitionIndices().size());
            assertEquals(0, node.transitionIndices().get(0));
        }
    }

    @Nested
    @DisplayName("immutability")
    class ImmutabilityTest {

        @Test
        @DisplayName("inputs is immutable")
        void inputsIsImmutable() {
            NodeIr node = NodeIr.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .nodeHash(hash("node-1"))
                .executorConfig(ExecutorConfig.builder()
                    .kind(ExecutorKind.BUILTIN)
                    .handler(HandlerRef.builtin("test"))
                    .build())
                .inputs(java.util.List.of(
                    ResolvedArtifactBinding.required(ArtifactTemplateId.of("input"), hash("input-hash"))
                ))
                .build();

            assertThrows(UnsupportedOperationException.class, () ->
                node.inputs().add(ResolvedArtifactBinding.optional(ArtifactTemplateId.of("other"), hash("other-hash")))
            );
        }

        @Test
        @DisplayName("transitionIndices is immutable")
        void transitionIndicesIsImmutable() {
            NodeIr node = NodeIr.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .phaseId(PhaseId.of("phase-1"))
                .nodeHash(hash("node-1"))
                .executorConfig(ExecutorConfig.builder()
                    .kind(ExecutorKind.BUILTIN)
                    .handler(HandlerRef.builtin("test"))
                    .build())
                .transitionIndices(java.util.List.of(0))
                .build();

            assertThrows(UnsupportedOperationException.class, () ->
                node.transitionIndices().add(1)
            );
        }
    }
}
