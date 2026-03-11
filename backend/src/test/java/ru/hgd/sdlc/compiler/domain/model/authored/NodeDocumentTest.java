package ru.hgd.sdlc.compiler.domain.model.authored;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NodeDocument")
class NodeDocumentTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("builds executor node")
        void buildsExecutorNode() {
            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("executor-1"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.skill(SkillId.of("generate-code")))
                .build();

            assertEquals("executor-1", node.id().value());
            assertEquals(NodeType.EXECUTOR, node.type());
            assertTrue(node.isExecutor());
            assertFalse(node.isGate());
            assertTrue(node.handler().isPresent());
            assertEquals("skill://generate-code", node.handler().get().toString());
        }

        @Test
        @DisplayName("builds gate node")
        void buildsGateNode() {
            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("approval-1"))
                .type(NodeType.GATE)
                .gateKind(GateKind.APPROVAL)
                .requiredApprovers(Set.of(Role.of("architect"), Role.of("lead")))
                .build();

            assertEquals(NodeType.GATE, node.type());
            assertTrue(node.isGate());
            assertFalse(node.isExecutor());
            assertEquals(GateKind.APPROVAL, node.gateKind().get());
            assertEquals(2, node.requiredApprovers().size());
        }

        @Test
        @DisplayName("builds node with transitions")
        void buildsNodeWithTransitions() {
            NodeId next = NodeId.of("next-node");
            Transition transition = Transition.forward(NodeId.of("executor-1"), next);

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("executor-1"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .transitions(List.of(transition))
                .build();

            assertEquals(1, node.transitions().size());
            assertEquals(transition, node.transitions().get(0));
        }

        @Test
        @DisplayName("builds node with artifact bindings")
        void buildsNodeWithArtifactBindings() {
            ArtifactBinding input = ArtifactBinding.required(ArtifactTemplateId.of("input-doc"));
            ArtifactBinding output = ArtifactBinding.required(ArtifactTemplateId.of("output-code"));

            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("executor-1"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .inputs(List.of(input))
                .outputs(List.of(output))
                .build();

            assertEquals(1, node.inputs().size());
            assertEquals(1, node.outputs().size());
            assertEquals("input-doc", node.inputs().get(0).artifactId().value());
        }

        @Test
        @DisplayName("requires id - throws NPE when null")
        void requiresId() {
                assertThrows(NullPointerException.class, () ->
                    NodeDocument.builder()
                        .id(null)
                        .type(NodeType.EXECUTOR)
                        .build()
                );
            }

        @Test
        @DisplayName("requires type - throws NPE when null")
        void requiresType() {
                assertThrows(NullPointerException.class, () ->
                    NodeDocument.builder()
                        .id(NodeId.of("test"))
                        .type(null)
                        .build()
                );
            }
    }

    @Nested
    @DisplayName("immutability")
    class ImmutabilityTest {

        @Test
        @DisplayName("inputs is immutable")
        void inputsIsImmutable() {
            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .inputs(List.of(ArtifactBinding.required(ArtifactTemplateId.of("input"))))
                .build();

            assertThrows(UnsupportedOperationException.class, () ->
                node.inputs().add(ArtifactBinding.optional(ArtifactTemplateId.of("other")))
            );
        }

        @Test
        @DisplayName("transitions is immutable")
        void transitionsIsImmutable() {
            Transition t = Transition.forward(NodeId.of("test"), NodeId.of("next"));
            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .transitions(List.of(t))
                .build();

            assertThrows(UnsupportedOperationException.class, () ->
                node.transitions().add(Transition.skip(NodeId.of("test"), NodeId.of("skip"), "cond"))
            );
        }

        @Test
        @DisplayName("requiredApprovers is immutable")
        void requiredApproversIsImmutable() {
            NodeDocument node = NodeDocument.builder()
                .id(NodeId.of("test"))
                .type(NodeType.GATE)
                .gateKind(GateKind.APPROVAL)
                .requiredApprovers(Set.of(Role.of("admin")))
                .build();

            assertThrows(UnsupportedOperationException.class, () ->
                node.requiredApprovers().add(Role.of("user"))
            );
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("equal for same id")
        void equalForSameId() {
            NodeDocument node1 = NodeDocument.builder()
                .id(NodeId.of("test"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .build();

            NodeDocument node2 = NodeDocument.builder()
                .id(NodeId.of("test"))
                .type(NodeType.GATE)
                .gateKind(GateKind.APPROVAL)
                .build();

            assertEquals(node1, node2); // Same ID means equal
        }

        @Test
        @DisplayName("not equal for different id")
        void notEqualForDifferentId() {
            NodeDocument node1 = NodeDocument.builder()
                .id(NodeId.of("node-1"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .build();

            NodeDocument node2 = NodeDocument.builder()
                .id(NodeId.of("node-2"))
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.builtin("test"))
                .build();

            assertNotEquals(node1, node2);
        }
    }
}
