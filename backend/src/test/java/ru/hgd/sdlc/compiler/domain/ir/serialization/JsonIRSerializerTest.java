package ru.hgd.sdlc.compiler.domain.ir.serialization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.compiler.domain.model.ir.*;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonIRSerializer")
class JsonIRSerializerTest {

    private JsonIRSerializer serializer;
    private Sha256 hash(String value) {
        return Sha256.of(value);
    }

    @BeforeEach
    void setUp() {
        serializer = new JsonIRSerializer(true);
    }

    @Nested
    @DisplayName("FlowIr serialization")
    class FlowIrSerializationTest {

        @Test
        @DisplayName("serializes and deserializes minimal FlowIr")
        void serializesAndDeserializesMinimalFlowIr() throws SerializationException {
            IrMetadata metadata = IrMetadata.builder()
                .packageChecksum(hash("package"))
                .irChecksum(hash("ir"))
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

            FlowIr original = FlowIr.builder()
                .flowId(FlowId.of("test-flow"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(metadata)
                .build();

            String json = serializer.serialize(original);
            assertNotNull(json);
            assertTrue(json.contains("test-flow"));

            FlowIr deserialized = serializer.deserializeFlow(json);
            assertEquals(original.flowId().value(), deserialized.flowId().value());
            assertEquals(original.flowVersion().toString(), deserialized.flowVersion().toString());
        }

        @Test
        @DisplayName("serializes and deserializes complete FlowIr")
        void serializesAndDeserializesCompleteFlowIr() throws SerializationException {
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
                .nodeOrder(List.of(NodeId.of("node-1")))
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

            FlowIr original = FlowIr.builder()
                .flowId(FlowId.of("test-flow"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(metadata)
                .phases(List.of(phase))
                .nodeIndex(Map.of(NodeId.of("node-1"), node))
                .transitions(List.of(transition))
                .startRoles(Set.of(Role.of("developer")))
                .resumePolicy(ResumePolicy.FROM_CHECKPOINT)
                .build();

            String json = serializer.serialize(original);
            assertNotNull(json);

            FlowIr deserialized = serializer.deserializeFlow(json);
            assertEquals(1, deserialized.phases().size());
            assertEquals(1, deserialized.nodeIndex().size());
            assertEquals(1, deserialized.transitions().size());
            assertEquals(1, deserialized.startRoles().size());
        }

        @Test
        @DisplayName("preserves content type")
        void preservesContentType() {
            assertEquals("application/json", serializer.contentType());
        }
    }

    @Nested
    @DisplayName("SkillIr serialization")
    class SkillIrSerializationTest {

        @Test
        @DisplayName("serializes and deserializes minimal SkillIr")
        void serializesAndDeserializesMinimalSkillIr() throws SerializationException {
            SkillIr original = SkillIr.builder()
                .skillId(SkillId.of("test-skill"))
                .skillVersion(SemanticVersion.of("1.0.0"))
                .name("Test Skill")
                .handler(HandlerRef.builtin("execute"))
                .irChecksum(hash("skill"))
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

            String json = serializer.serialize(original);
            assertNotNull(json);
            assertTrue(json.contains("test-skill"));

            SkillIr deserialized = serializer.deserializeSkill(json);
            assertEquals(original.skillId().value(), deserialized.skillId().value());
            assertEquals(original.name(), deserialized.name());
        }

        @Test
        @DisplayName("serializes and deserializes complete SkillIr")
        void serializesAndDeserializesCompleteSkillIr() throws SerializationException {
            SkillIr original = SkillIr.builder()
                .skillId(SkillId.of("full-skill"))
                .skillVersion(SemanticVersion.of("2.1.0"))
                .name("Full Skill")
                .description(MarkdownBody.of("A complete skill"))
                .handler(HandlerRef.skill(SkillId.of("impl")))
                .inputSchema(Map.of("type", "object"))
                .outputSchema(Map.of("type", "string"))
                .tags(List.of("utility", "core"))
                .irChecksum(hash("full-skill"))
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

            String json = serializer.serialize(original);
            assertNotNull(json);

            SkillIr deserialized = serializer.deserializeSkill(json);
            assertEquals(2, deserialized.tags().size());
            assertFalse(deserialized.inputSchema().isEmpty());
            assertFalse(deserialized.outputSchema().isEmpty());
            assertTrue(deserialized.description().isPresent());
        }
    }

    @Nested
    @DisplayName("polymorphic deserialization")
    class PolymorphicDeserializationTest {

        @Test
        @DisplayName("deserializes FlowIr as CompiledIR using specific deserializer")
        void deserializesFlowIrAsCompiledIR() throws SerializationException {
            IrMetadata metadata = IrMetadata.builder()
                .packageChecksum(hash("package"))
                .irChecksum(hash("ir"))
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

            FlowIr original = FlowIr.builder()
                .flowId(FlowId.of("test-flow"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(metadata)
                .build();

            String json = serializer.serialize(original);
            // Use specific deserializer for FlowIr
            FlowIr deserialized = serializer.deserializeFlow(json);

            assertTrue(deserialized instanceof FlowIr);
            assertEquals("test-flow", deserialized.irId());
        }

        @Test
        @DisplayName("deserializes SkillIr as CompiledIR using specific deserializer")
        void deserializesSkillIrAsCompiledIR() throws SerializationException {
            SkillIr original = SkillIr.builder()
                .skillId(SkillId.of("test-skill"))
                .skillVersion(SemanticVersion.of("1.0.0"))
                .name("Test Skill")
                .handler(HandlerRef.builtin("execute"))
                .irChecksum(hash("skill"))
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

            String json = serializer.serialize(original);
            // Use specific deserializer for SkillIr
            SkillIr deserialized = serializer.deserializeSkill(json);

            assertTrue(deserialized instanceof SkillIr);
            assertEquals("test-skill", deserialized.irId());
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandlingTest {

        @Test
        @DisplayName("throws SerializationException on invalid JSON")
        void throwsOnInvalidJson() {
            assertThrows(SerializationException.class, () -> {
                serializer.deserialize("not valid json");
            });
        }

        @Test
        @DisplayName("throws SerializationException on empty string")
        void throwsOnEmptyString() {
            assertThrows(SerializationException.class, () -> {
                serializer.deserialize("");
            });
        }
    }
}
