package ru.hgd.sdlc.compiler.domain.model.authored;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FlowDocument")
class FlowDocumentTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTest {

        @Test
        @DisplayName("builds minimal flow document")
        void buildsMinimalFlowDocument() {
            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .name("test-flow") // Lombok doesn't auto-default name
                .version(SemanticVersion.of("1.0.0"))
                .build();

            assertEquals("test-flow", flow.id().value());
            assertEquals("1.0.0", flow.version().toString());
            assertEquals("test-flow", flow.name());
            assertTrue(flow.description().isEmpty());
            assertTrue(flow.phaseOrder().isEmpty());
            assertTrue(flow.phases().isEmpty());
            assertTrue(flow.nodes().isEmpty());
        }

        @Test
        @DisplayName("builds complete flow document")
        void buildsCompleteFlowDocument() {
            PhaseId phaseId = PhaseId.of("phase-1");
            PhaseDocument phase = PhaseDocument.builder()
                .id(phaseId)
                .name("Phase 1")
                .order(0)
                .build();

            NodeId nodeId = NodeId.of("node-1");
            NodeDocument node = NodeDocument.builder()
                .id(nodeId)
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.skill(SkillId.of("test-skill")))
                .build();

            Instant now = Instant.now();

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .name("Test Flow")
                .version(SemanticVersion.of("1.0.0"))
                .description(MarkdownBody.of("A test flow"))
                .phaseOrder(List.of(phaseId))
                .phases(Map.of(phaseId, phase))
                .nodes(Map.of(nodeId, node))
                .startRoles(Set.of(Role.of("developer")))
                .resumePolicy(ResumePolicy.FROM_CHECKPOINT)
                .authoredAt(now)
                .author("test-author")
                .build();

            assertEquals("Test Flow", flow.name());
            assertEquals("A test flow", flow.description().content());
            assertEquals(1, flow.phaseOrder().size());
            assertEquals(1, flow.phases().size());
            assertEquals(1, flow.nodes().size());
            assertEquals(1, flow.startRoles().size());
            assertTrue(flow.startRoles().contains(Role.of("developer")));
            assertEquals(ResumePolicy.FROM_CHECKPOINT, flow.resumePolicy());
            assertEquals(now, flow.authoredAt());
            assertEquals("test-author", flow.author());
        }

        @Test
        @DisplayName("requires id - throws NPE when null")
        void requiresId() {
            // Lombok @NonNull throws NPE when building with null id
            assertThrows(NullPointerException.class, () ->
                FlowDocument.builder()
                    .id(null)
                    .version(SemanticVersion.of("1.0.0"))
                    .build()
            );
        }

        @Test
        @DisplayName("requires version - throws NPE when null")
        void requiresVersion() {
            // Lombok @NonNull throws NPE when building with null version
            assertThrows(NullPointerException.class, () ->
                FlowDocument.builder()
                    .id(FlowId.of("test"))
                    .version(null)
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("accessors")
    class AccessorsTest {

        @Test
        @DisplayName("getPhase returns phase by ID")
        void getPhaseReturnsPhaseById() {
            PhaseId phaseId = PhaseId.of("phase-1");
            PhaseDocument phase = PhaseDocument.builder()
                .id(phaseId)
                .name("Phase 1")
                .order(0)
                .build();

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test"))
                .name("test")
                .version(SemanticVersion.of("1.0.0"))
                .phases(Map.of(phaseId, phase))
                .build();

            assertEquals(phase, flow.getPhase(phaseId));
            assertNull(flow.getPhase(PhaseId.of("nonexistent")));
        }

        @Test
        @DisplayName("getNode returns node by ID")
        void getNodeReturnsNodeById() {
            NodeId nodeId = NodeId.of("node-1");
            NodeDocument node = NodeDocument.builder()
                .id(nodeId)
                .type(NodeType.EXECUTOR)
                .handler(HandlerRef.skill(SkillId.of("test")))
                .build();

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test"))
                .name("test")
                .version(SemanticVersion.of("1.0.0"))
                .nodes(Map.of(nodeId, node))
                .build();

            assertEquals(node, flow.getNode(nodeId));
            assertNull(flow.getNode(NodeId.of("nonexistent")));
        }
    }

    @Nested
    @DisplayName("immutability")
    class ImmutabilityTest {

        @Test
        @DisplayName("phaseOrder is immutable")
        void phaseOrderIsImmutable() {
            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test"))
                .name("test")
                .version(SemanticVersion.of("1.0.0"))
                .phaseOrder(List.of(PhaseId.of("phase-1")))
                .build();

            assertThrows(UnsupportedOperationException.class, () ->
                flow.phaseOrder().add(PhaseId.of("phase-2"))
            );
        }

        @Test
        @DisplayName("phases is immutable")
        void phasesIsImmutable() {
            PhaseId phaseId = PhaseId.of("phase-1");
            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test"))
                .name("test")
                .version(SemanticVersion.of("1.0.0"))
                .phases(Map.of(phaseId, PhaseDocument.builder().id(phaseId).name("p1").build()))
                .build();

            assertThrows(UnsupportedOperationException.class, () ->
                flow.phases().put(PhaseId.of("phase-2"), PhaseDocument.builder().id(PhaseId.of("phase-2")).name("p2").build())
            );
        }

        @Test
        @DisplayName("startRoles is immutable")
        void startRolesIsImmutable() {
            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test"))
                .name("test")
                .version(SemanticVersion.of("1.0.0"))
                .startRoles(Set.of(Role.of("developer")))
                .build();

            assertThrows(UnsupportedOperationException.class, () ->
                flow.startRoles().add(Role.of("admin"))
            );
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("equal for same id and version")
        void equalForSameIdAndVersion() {
            FlowDocument flow1 = FlowDocument.builder()
                .id(FlowId.of("test"))
                .name("test1")
                .version(SemanticVersion.of("1.0.0"))
                .build();

            FlowDocument flow2 = FlowDocument.builder()
                .id(FlowId.of("test"))
                .name("Different Name")
                .version(SemanticVersion.of("1.0.0"))
                .build();

            assertEquals(flow1, flow2);
            assertEquals(flow1.hashCode(), flow2.hashCode());
        }

        @Test
        @DisplayName("not equal for different id")
        void notEqualForDifferentId() {
            FlowDocument flow1 = FlowDocument.builder()
                .id(FlowId.of("test-1"))
                .name("test")
                .version(SemanticVersion.of("1.0.0"))
                .build();

            FlowDocument flow2 = FlowDocument.builder()
                .id(FlowId.of("test-2"))
                .name("test")
                .version(SemanticVersion.of("1.0.0"))
                .build();

            assertNotEquals(flow1, flow2);
        }

        @Test
        @DisplayName("not equal for different version")
        void notEqualForDifferentVersion() {
            FlowDocument flow1 = FlowDocument.builder()
                .id(FlowId.of("test"))
                .name("test")
                .version(SemanticVersion.of("1.0.0"))
                .build();

            FlowDocument flow2 = FlowDocument.builder()
                .id(FlowId.of("test"))
                .name("test")
                .version(SemanticVersion.of("2.0.0"))
                .build();

            assertNotEquals(flow1, flow2);
        }
    }
}
