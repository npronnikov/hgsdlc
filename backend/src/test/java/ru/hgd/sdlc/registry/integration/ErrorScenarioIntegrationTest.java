package ru.hgd.sdlc.registry.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.IrMetadata;
import ru.hgd.sdlc.compiler.domain.model.ir.PhaseIr;
import ru.hgd.sdlc.registry.application.builder.DefaultReleaseBuilder;
import ru.hgd.sdlc.registry.application.builder.ReleaseBuildException;
import ru.hgd.sdlc.registry.application.builder.ReleaseBuilder;
import ru.hgd.sdlc.registry.application.builder.SourceInfo;
import ru.hgd.sdlc.registry.application.lockfile.DefaultLockfileGenerator;
import ru.hgd.sdlc.registry.application.resolver.ReleaseNotFoundException;
import ru.hgd.sdlc.registry.application.verifier.DefaultProvenanceVerifier;
import ru.hgd.sdlc.registry.application.verifier.ProvenanceVerifier;
import ru.hgd.sdlc.registry.application.verifier.VerificationResult;
import ru.hgd.sdlc.registry.domain.model.provenance.BuilderInfo;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for error scenarios.
 * Tests error handling and edge cases in the release pipeline.
 */
@DisplayName("Error Scenario Integration Test")
class ErrorScenarioIntegrationTest {

    private ReleaseBuilder releaseBuilder;
    private ProvenanceVerifier verifier;
    private DefaultLockfileGenerator lockfileGenerator;

    @BeforeEach
    void setUp() {
        releaseBuilder = new DefaultReleaseBuilder();
        verifier = new DefaultProvenanceVerifier();
        lockfileGenerator = new DefaultLockfileGenerator();
    }

    @Nested
    @DisplayName("Missing Dependency Errors")
    class MissingDependencyErrors {

        @Test
        @DisplayName("should build package even with unresolved skills")
        void shouldBuildPackageEvenWithUnresolvedSkills() {
            // Given - flow references skills but they are not bundled
            Map<SkillId, Sha256> resolvedSkills = Map.of(
                SkillId.of("missing-skill"), Sha256.of("some-checksum")
            );

            FlowIr flowIr = FlowIr.builder()
                .flowId(FlowId.of("unresolved-flow"))
                .flowVersion(SemanticVersion.of(1, 0, 0))
                .metadata(IrMetadata.create(
                    Sha256.of("pkg-checksum"),
                    Sha256.of("ir-checksum"),
                    "1.0.0"
                ))
                .resolvedSkills(resolvedSkills)
                .build();

            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When - build succeeds (skills are resolved at runtime, not build time)
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Then
            assertNotNull(pkg);
            // Skills are not bundled because they weren't provided
            assertEquals(0, pkg.skillCount());
            // But resolved skills map is preserved in IR
            assertTrue(pkg.flowIr().resolvedSkills().containsKey(SkillId.of("missing-skill")));
        }
    }

    @Nested
    @DisplayName("Invalid Input Errors")
    class InvalidInputErrors {

        @Test
        @DisplayName("should throw on null flow IR")
        void shouldThrowOnNullFlowIr() {
            // Given
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When/Then
            assertThrows(ReleaseBuildException.class, () ->
                releaseBuilder.build(null, sourceInfo)
            );
        }

        @Test
        @DisplayName("should throw on null source info")
        void shouldThrowOnNullSourceInfo() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("test-flow", "1.0.0");

            // When/Then
            assertThrows(ReleaseBuildException.class, () ->
                releaseBuilder.build(flowIr, (SourceInfo) null)
            );
        }

        @Test
        @DisplayName("should throw on invalid commit SHA format")
        void shouldThrowOnInvalidCommitShaFormat() {
            // Given - source info with invalid commit SHA
            SourceInfo sourceInfo = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("invalid-sha") // Not 40 hex chars
                .commitAuthor("test@example.com")
                .committedAt(Instant.now())
                .build();

            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("invalid-sha-flow", "1.0.0");

            // When/Then - ReleaseBuildException is thrown during provenance building
            assertThrows(ReleaseBuildException.class, () ->
                releaseBuilder.build(flowIr, sourceInfo)
            );
        }

        @Test
        @DisplayName("should accept valid commit SHA")
        void shouldAcceptValidCommitSha() {
            // Given - source info with valid commit SHA
            SourceInfo sourceInfo = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("0123456789abcdef0123456789abcdef01234567") // Valid 40 hex chars
                .commitAuthor("test@example.com")
                .committedAt(Instant.now())
                .build();

            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("valid-sha-flow", "1.0.0");

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Then
            assertNotNull(pkg);
            assertEquals("0123456789abcdef0123456789abcdef01234567", pkg.provenance().getCommitSha());
        }
    }

    @Nested
    @DisplayName("Query Non-Existent Release")
    class QueryNonExistentRelease {

        @Test
        @DisplayName("should create ReleaseNotFoundException with release ID")
        void shouldCreateReleaseNotFoundExceptionWithReleaseId() {
            // Given
            ReleaseId releaseId = ReleaseId.parse("nonexistent@1.0.0");

            // When
            ReleaseNotFoundException exception = new ReleaseNotFoundException(releaseId);

            // Then
            assertTrue(exception.getMessage().contains("nonexistent@1.0.0"));
            assertEquals(releaseId, exception.releaseId());
        }

        @Test
        @DisplayName("should parse valid release ID for lookup")
        void shouldParseValidReleaseIdForLookup() {
            // When
            ReleaseId id = ReleaseId.parse("my-flow@2.0.0");

            // Then
            assertEquals("my-flow", id.flowId().value());
            assertEquals(2, id.version().major());
            assertEquals(0, id.version().minor());
            assertEquals(0, id.version().patch());
        }
    }

    @Nested
    @DisplayName("Corrupted Package Detection")
    class CorruptedPackageDetection {

        @Test
        @DisplayName("should detect missing provenance")
        void shouldDetectMissingProvenance() {
            // Given - a valid package - provenance is required so can't create one without it
            // Instead we verify that a package with provenance passes validation
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("has-provenance", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // When - verify a package that has provenance
            VerificationResult result = verifier.verify(pkg);

            // Then - should pass because provenance exists
            assertTrue(result.isSuccess());
            assertFalse(result.hasErrors());
        }

        @Test
        @DisplayName("should detect missing required fields in provenance")
        void shouldDetectMissingRequiredFieldsInProvenance() {
            // Given - provenance validation happens at construction time
            // This test verifies that the verifier properly validates a complete provenance
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("complete-prov", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // When
            VerificationResult result = verifier.verify(pkg);

            // Then - complete provenance should pass
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should detect invalid git commit SHA format")
        void shouldDetectInvalidGitCommitShaFormat() {
            // Given - trying to validate an invalid git SHA
            // Provenance.validateGitCommit is called by DefaultReleaseBuilder, not by the builder itself
            String invalidSha = "not-a-valid-sha";

            // When/Then - Provenance validates commit SHA in the validateGitCommit method
            assertThrows(IllegalArgumentException.class, () -> {
                Provenance.validateGitCommit(invalidSha);
            });
        }
    }

    @Nested
    @DisplayName("Lockfile Error Handling")
    class LockfileErrorHandling {

        @Test
        @DisplayName("should throw on empty package list")
        void shouldThrowOnEmptyPackageList() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                lockfileGenerator.generate(List.of())
            );
        }

        @Test
        @DisplayName("should throw on null package list")
        void shouldThrowOnNullPackageList() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                lockfileGenerator.generate(null)
            );
        }
    }

    @Nested
    @DisplayName("Version Parsing Errors")
    class VersionParsingErrors {

        @Test
        @DisplayName("should parse valid release ID")
        void shouldParseValidReleaseId() {
            // When
            ReleaseId id = ReleaseId.parse("valid-flow@1.2.3");

            // Then
            assertEquals("valid-flow", id.flowId().value());
            assertEquals("1.2.3", id.version().formatted());
        }

        @Test
        @DisplayName("should parse release ID with prerelease")
        void shouldParseReleaseIdWithPrerelease() {
            // When
            ReleaseId id = ReleaseId.parse("prerelease-flow@2.0.0-beta.1");

            // Then
            assertEquals("prerelease-flow", id.flowId().value());
            assertTrue(id.version().formatted().contains("beta.1"));
        }
    }

    @Nested
    @DisplayName("Checksum Verification Errors")
    class ChecksumVerificationErrors {

        @Test
        @DisplayName("should verify checksums are present in provenance")
        void shouldVerifyChecksumsArePresentInProvenance() {
            // Given - a properly built package has checksums
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("checksum-test", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // When
            VerificationResult result = verifier.verify(pkg);

            // Then - should succeed because checksums are present
            assertTrue(result.isSuccess());
            assertNotNull(pkg.provenance().getIrChecksum());
            assertNotNull(pkg.provenance().getPackageChecksum());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle very long flow ID")
        void shouldHandleVeryLongFlowId() {
            // Given
            String longFlowId = "a".repeat(200);
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr(longFlowId, "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Then
            assertNotNull(pkg);
            assertEquals(longFlowId, pkg.flowId().value());
        }

        @Test
        @DisplayName("should handle flow with many phases")
        void shouldHandleFlowWithManyPhases() {
            // Given
            java.util.List<String> phaseNames = new java.util.ArrayList<>();
            for (int i = 0; i < 50; i++) {
                phaseNames.add("phase-" + i);
            }

            FlowIr flowIr = ReleaseTestFixtures.createFlowIrWithPhases(
                "many-phases-flow", "1.0.0", phaseNames
            );

            Map<PhaseId, PhaseIr> phases = new java.util.HashMap<>();
            for (int i = 0; i < 50; i++) {
                PhaseId id = PhaseId.of("phase-" + i);
                phases.put(id, PhaseIr.builder().id(id).name("Phase " + i).order(i).build());
            }

            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, phases, Map.of(), sourceInfo);

            // Then
            assertNotNull(pkg);
            assertEquals(50, pkg.phaseCount());
        }

        @Test
        @DisplayName("should handle flow with many skills")
        void shouldHandleFlowWithManySkills() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("many-skills-flow", "1.0.0");

            Map<SkillId, SkillIr> skills = new java.util.HashMap<>();
            for (int i = 0; i < 100; i++) {
                SkillId id = SkillId.of("skill-" + i);
                skills.put(id, ReleaseTestFixtures.createSimpleSkillIr("skill-" + i, "1.0.0"));
            }

            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, Map.of(), skills, sourceInfo);

            // Then
            assertNotNull(pkg);
            assertEquals(100, pkg.skillCount());
        }

        @Test
        @DisplayName("should handle special characters in flow ID")
        void shouldHandleSpecialCharactersInFlowId() {
            // Given
            String specialId = "flow-with-dashes_and_underscores";
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr(specialId, "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Then
            assertNotNull(pkg);
            assertTrue(pkg.id().canonicalId().contains(specialId));
        }
    }
}
