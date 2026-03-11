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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IRChecksum")
class IRChecksumTest {

    private IRChecksum checksum;
    private Sha256 hash(String value) {
        return Sha256.of(value);
    }

    @BeforeEach
    void setUp() {
        checksum = new IRChecksum();
    }

    @Nested
    @DisplayName("FlowIr checksum")
    class FlowIrChecksumTest {

        @Test
        @DisplayName("computes checksum for FlowIr")
        void computesChecksumForFlowIr() {
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

            Sha256 result = checksum.compute(ir);

            assertNotNull(result);
            assertNotNull(result.hexValue());
            assertEquals(64, result.hexValue().length()); // SHA-256 produces 64 hex chars
        }

        @Test
        @DisplayName("same IR produces same checksum")
        void sameIrProducesSameChecksum() {
            IrMetadata metadata = IrMetadata.builder()
                .packageChecksum(hash("package"))
                .irChecksum(hash("ir"))
                .compiledAt(Instant.parse("2024-01-01T00:00:00Z"))
                .compilerVersion("1.0.0")
                .build();

            FlowIr ir1 = FlowIr.builder()
                .flowId(FlowId.of("test-flow"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(metadata)
                .build();

            FlowIr ir2 = FlowIr.builder()
                .flowId(FlowId.of("test-flow"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(metadata)
                .build();

            Sha256 checksum1 = checksum.compute(ir1);
            Sha256 checksum2 = checksum.compute(ir2);

            assertEquals(checksum1, checksum2);
        }

        @Test
        @DisplayName("different IR produces different checksum")
        void differentIrProducesDifferentChecksum() {
            IrMetadata metadata = IrMetadata.builder()
                .packageChecksum(hash("package"))
                .irChecksum(hash("ir"))
                .compiledAt(Instant.parse("2024-01-01T00:00:00Z"))
                .compilerVersion("1.0.0")
                .build();

            FlowIr ir1 = FlowIr.builder()
                .flowId(FlowId.of("flow-1"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(metadata)
                .build();

            FlowIr ir2 = FlowIr.builder()
                .flowId(FlowId.of("flow-2"))
                .flowVersion(SemanticVersion.of("1.0.0"))
                .metadata(metadata)
                .build();

            Sha256 checksum1 = checksum.compute(ir1);
            Sha256 checksum2 = checksum.compute(ir2);

            assertNotEquals(checksum1, checksum2);
        }
    }

    @Nested
    @DisplayName("SkillIr checksum")
    class SkillIrChecksumTest {

        @Test
        @DisplayName("computes checksum for SkillIr")
        void computesChecksumForSkillIr() {
            SkillIr ir = SkillIr.builder()
                .skillId(SkillId.of("test-skill"))
                .skillVersion(SemanticVersion.of("1.0.0"))
                .name("Test Skill")
                .handler(HandlerRef.builtin("execute"))
                .irChecksum(hash("skill"))
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

            Sha256 result = checksum.compute(ir);

            assertNotNull(result);
            assertEquals(64, result.hexValue().length());
        }

        @Test
        @DisplayName("same SkillIr produces same checksum")
        void sameSkillIrProducesSameChecksum() {
            SkillIr ir1 = SkillIr.builder()
                .skillId(SkillId.of("test-skill"))
                .skillVersion(SemanticVersion.of("1.0.0"))
                .name("Test Skill")
                .handler(HandlerRef.builtin("execute"))
                .irChecksum(hash("skill"))
                .compiledAt(Instant.parse("2024-01-01T00:00:00Z"))
                .compilerVersion("1.0.0")
                .tags(List.of("tag1", "tag2"))
                .build();

            SkillIr ir2 = SkillIr.builder()
                .skillId(SkillId.of("test-skill"))
                .skillVersion(SemanticVersion.of("1.0.0"))
                .name("Test Skill")
                .handler(HandlerRef.builtin("execute"))
                .irChecksum(hash("skill"))
                .compiledAt(Instant.parse("2024-01-01T00:00:00Z"))
                .compilerVersion("1.0.0")
                .tags(List.of("tag1", "tag2"))
                .build();

            Sha256 checksum1 = checksum.compute(ir1);
            Sha256 checksum2 = checksum.compute(ir2);

            assertEquals(checksum1, checksum2);
        }
    }

    @Nested
    @DisplayName("verification")
    class VerificationTest {

        @Test
        @DisplayName("verifies matching checksum")
        void verifiesMatchingChecksum() {
            SkillIr ir = SkillIr.builder()
                .skillId(SkillId.of("test-skill"))
                .skillVersion(SemanticVersion.of("1.0.0"))
                .name("Test Skill")
                .handler(HandlerRef.builtin("execute"))
                .irChecksum(hash("skill"))
                .compiledAt(Instant.parse("2024-01-01T00:00:00Z"))
                .compilerVersion("1.0.0")
                .build();

            Sha256 expected = checksum.compute(ir);

            assertTrue(checksum.verify(ir, expected));
        }

        @Test
        @DisplayName("verifies matching checksum from hex string")
        void verifiesMatchingChecksumFromHexString() {
            SkillIr ir = SkillIr.builder()
                .skillId(SkillId.of("test-skill"))
                .skillVersion(SemanticVersion.of("1.0.0"))
                .name("Test Skill")
                .handler(HandlerRef.builtin("execute"))
                .irChecksum(hash("skill"))
                .compiledAt(Instant.parse("2024-01-01T00:00:00Z"))
                .compilerVersion("1.0.0")
                .build();

            Sha256 expected = checksum.compute(ir);

            assertTrue(checksum.verify(ir, expected.hexValue()));
        }

        @Test
        @DisplayName("fails verification for non-matching checksum")
        void failsVerificationForNonMatchingChecksum() {
            SkillIr ir = SkillIr.builder()
                .skillId(SkillId.of("test-skill"))
                .skillVersion(SemanticVersion.of("1.0.0"))
                .name("Test Skill")
                .handler(HandlerRef.builtin("execute"))
                .irChecksum(hash("skill"))
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

            Sha256 wrongChecksum = Sha256.of("wrong");

            assertFalse(checksum.verify(ir, wrongChecksum));
        }
    }

    @Nested
    @DisplayName("static methods")
    class StaticMethodsTest {

        @Test
        @DisplayName("ofSerialized computes checksum from string")
        void ofSerializedComputesChecksumFromString() {
            String data = "{\"test\":\"data\"}";

            Sha256 result = IRChecksum.ofSerialized(data);

            assertNotNull(result);
            assertEquals(64, result.hexValue().length());
        }

        @Test
        @DisplayName("ofSerialized produces consistent results")
        void ofSerializedProducesConsistentResults() {
            String data = "test data";

            Sha256 result1 = IRChecksum.ofSerialized(data);
            Sha256 result2 = IRChecksum.ofSerialized(data);

            assertEquals(result1, result2);
        }
    }
}
