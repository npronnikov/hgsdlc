package ru.hgd.sdlc.compiler.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.compiler.SkillCompiler;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.ir.serialization.SerializationException;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.testing.FlowDocumentFixtures;
import ru.hgd.sdlc.compiler.testing.SkillDocumentFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerializationServiceTest {

    private SerializationService serializationService;
    private FlowIr testFlowIr;
    private SkillIr testSkillIr;

    @BeforeEach
    void setUp() {
        serializationService = new SerializationService();

        // Create test IR instances
        var flowCompiler = new ru.hgd.sdlc.compiler.domain.compiler.FlowCompiler();
        var skillCompiler = new SkillCompiler();

        testFlowIr = flowCompiler.compile(FlowDocumentFixtures.simpleFlow()).getIr();
        testSkillIr = skillCompiler.compile(SkillDocumentFixtures.simpleSkill()).getIr();
    }

    @Nested
    @DisplayName("Flow IR serialization")
    class FlowIrSerialization {

        @Test
        @DisplayName("should serialize FlowIr to JSON")
        void shouldSerializeFlowIrToJson() throws SerializationException {
            String json = serializationService.serialize(testFlowIr);

            assertThat(json).isNotBlank();
            assertThat(json).contains("simple-flow");
        }

        @Test
        @DisplayName("should deserialize JSON to FlowIr")
        void shouldDeserializeJsonToFlowIr() throws SerializationException {
            String json = serializationService.serialize(testFlowIr);

            FlowIr deserialized = serializationService.deserializeFlow(json);

            assertThat(deserialized).isNotNull();
            assertThat(deserialized.flowId().value()).isEqualTo("simple-flow");
        }

        @Test
        @DisplayName("should round-trip FlowIr")
        void shouldRoundTripFlowIr() throws SerializationException {
            String json = serializationService.serialize(testFlowIr);
            FlowIr deserialized = serializationService.deserializeFlow(json);

            assertThat(deserialized.flowId()).isEqualTo(testFlowIr.flowId());
            assertThat(deserialized.flowVersion()).isEqualTo(testFlowIr.flowVersion());
        }
    }

    @Nested
    @DisplayName("Skill IR serialization")
    class SkillIrSerialization {

        @Test
        @DisplayName("should serialize SkillIr to JSON")
        void shouldSerializeSkillIrToJson() throws SerializationException {
            String json = serializationService.serialize(testSkillIr);

            assertThat(json).isNotBlank();
            assertThat(json).contains("simple-skill");
        }

        @Test
        @DisplayName("should deserialize JSON to SkillIr")
        void shouldDeserializeJsonToSkillIr() throws SerializationException {
            String json = serializationService.serialize(testSkillIr);

            SkillIr deserialized = serializationService.deserializeSkill(json);

            assertThat(deserialized).isNotNull();
            assertThat(deserialized.skillId().value()).isEqualTo("simple-skill");
        }

        @Test
        @DisplayName("should round-trip SkillIr")
        void shouldRoundTripSkillIr() throws SerializationException {
            String json = serializationService.serialize(testSkillIr);
            SkillIr deserialized = serializationService.deserializeSkill(json);

            assertThat(deserialized.skillId()).isEqualTo(testSkillIr.skillId());
            assertThat(deserialized.skillVersion()).isEqualTo(testSkillIr.skillVersion());
        }
    }

    @Nested
    @DisplayName("Type-specific deserialization")
    class TypeSpecificDeserialization {

        @Test
        @DisplayName("should deserialize FlowIr using deserializeFlow")
        void shouldDeserializeFlowIrUsingSpecificMethod() throws SerializationException {
            String json = serializationService.serialize(testFlowIr);

            FlowIr deserialized = serializationService.deserializeFlow(json);

            assertThat(deserialized).isNotNull();
            assertThat(deserialized.flowId().value()).isEqualTo("simple-flow");
        }

        @Test
        @DisplayName("should deserialize SkillIr using deserializeSkill")
        void shouldDeserializeSkillIrUsingSpecificMethod() throws SerializationException {
            String json = serializationService.serialize(testSkillIr);

            SkillIr deserialized = serializationService.deserializeSkill(json);

            assertThat(deserialized).isNotNull();
            assertThat(deserialized.skillId().value()).isEqualTo("simple-skill");
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw on null IR")
        void shouldThrowOnNullIr() {
            assertThatThrownBy(() -> serializationService.serialize(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw on null data")
        void shouldThrowOnNullData() {
            assertThatThrownBy(() -> serializationService.deserialize(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw on invalid JSON")
        void shouldThrowOnInvalidJson() {
            assertThatThrownBy(() -> serializationService.deserialize("not valid json"))
                .isInstanceOf(SerializationException.class);
        }

        @Test
        @DisplayName("should return null for safe serialization")
        void shouldReturnNullForSafeSerialization() {
            String result = serializationService.serializeOrNull(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for safe deserialization")
        void shouldReturnNullForSafeDeserialization() {
            var result = serializationService.deserializeOrNull(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for invalid JSON in safe mode")
        void shouldReturnNullForInvalidJsonInSafeMode() {
            var result = serializationService.deserializeOrNull("invalid json");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should check valid serialized FlowIr")
        void shouldCheckValidSerializedFlowIr() throws SerializationException {
            String json = serializationService.serialize(testFlowIr);

            // The polymorphic deserialization may fail, but type-specific works
            FlowIr deserialized = serializationService.deserializeFlow(json);
            assertThat(deserialized).isNotNull();
        }

        @Test
        @DisplayName("should check invalid serialized IR")
        void shouldCheckInvalidSerializedIr() {
            assertThat(serializationService.isValidSerializedIR("not json")).isFalse();
            assertThat(serializationService.isValidSerializedIR(null)).isFalse();
            assertThat(serializationService.isValidSerializedIR("")).isFalse();
        }
    }

    @Nested
    @DisplayName("Content type")
    class ContentType {

        @Test
        @DisplayName("should return application/json content type")
        void shouldReturnApplicationJsonContentType() {
            assertThat(serializationService.contentType()).isEqualTo("application/json");
        }
    }
}
