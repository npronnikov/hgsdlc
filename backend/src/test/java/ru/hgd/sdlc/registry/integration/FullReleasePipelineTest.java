package ru.hgd.sdlc.registry.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.PhaseIr;
import ru.hgd.sdlc.registry.application.builder.ReleaseBuilder;
import ru.hgd.sdlc.registry.application.builder.SourceInfo;
import ru.hgd.sdlc.registry.application.lockfile.DefaultLockfileGenerator;
import ru.hgd.sdlc.registry.application.lockfile.Lockfile;
import ru.hgd.sdlc.registry.application.lockfile.LockfileGenerator;
import ru.hgd.sdlc.registry.application.signing.Ed25519Signer;
import ru.hgd.sdlc.registry.application.signing.ProvenanceSigner;
import ru.hgd.sdlc.registry.application.verifier.DefaultProvenanceVerifier;
import ru.hgd.sdlc.registry.application.verifier.ProvenanceVerifier;
import ru.hgd.sdlc.registry.application.verifier.VerificationResult;
import ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the full release pipeline.
 * Tests the complete flow from IR to signed release package.
 */
@DisplayName("Full Release Pipeline Integration Test")
class FullReleasePipelineTest {

    private ReleaseBuilder releaseBuilder;
    private LockfileGenerator lockfileGenerator;
    private ProvenanceVerifier verifier;
    private KeyManagerTestFixture keyFixture;

    @BeforeEach
    void setUp() {
        keyFixture = KeyManagerTestFixture.create("test-key");
        ProvenanceSigner signer = keyFixture.createSigner();
        releaseBuilder = new ru.hgd.sdlc.registry.application.builder.DefaultReleaseBuilder("1.0.0", signer);
        lockfileGenerator = new DefaultLockfileGenerator();
        verifier = new DefaultProvenanceVerifier();
    }

    @Nested
    @DisplayName("Complete Release Build")
    class CompleteReleaseBuild {

        @Test
        @DisplayName("should build release with flow, phases, and skills")
        void shouldBuildReleaseWithFlowPhasesAndSkills() {
            // Given: A flow with phases and skills
            FlowIr flowIr = ReleaseTestFixtures.createFlowIrWithPhases(
                "complete-flow", "1.0.0",
                List.of("setup", "develop", "deploy")
            );
            Map<PhaseId, PhaseIr> phases = ReleaseTestFixtures.createPhasesMap("setup", "develop", "deploy");
            Map<SkillId, SkillIr> skills = ReleaseTestFixtures.createSkillsMap("code-gen", "test-runner");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When: Building the release
            ReleasePackage pkg = releaseBuilder.build(flowIr, phases, skills, sourceInfo);

            // Then: Package is created correctly
            assertNotNull(pkg);
            assertEquals("complete-flow@1.0.0", pkg.id().canonicalId());
            assertEquals(3, pkg.phaseCount());
            assertEquals(2, pkg.skillCount());
            assertTrue(pkg.provenance().isSigned());
        }

        @Test
        @DisplayName("should generate valid checksums in package")
        void shouldGenerateValidChecksumsInPackage() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("checksum-test", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Then
            assertNotNull(pkg.checksums());
            assertTrue(pkg.checksums().size() > 0);
            assertTrue(pkg.verifyIntegrity());
        }

        @Test
        @DisplayName("should include provenance with all required fields")
        void shouldIncludeProvenanceWithAllRequiredFields() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("provenance-test", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Then
            assertNotNull(pkg.provenance());
            assertEquals("provenance-test@1.0.0", pkg.provenance().getReleaseId().canonicalId());
            assertNotNull(pkg.provenance().getRepositoryUrl());
            assertNotNull(pkg.provenance().getCommitSha());
            assertNotNull(pkg.provenance().getBuildTimestamp());
            assertNotNull(pkg.provenance().getCommitAuthor());
            assertNotNull(pkg.provenance().getCommittedAt());
            assertNotNull(pkg.provenance().getBuilder());
            assertNotNull(pkg.provenance().getCompilerVersion());
            assertNotNull(pkg.provenance().getIrChecksum());
            assertNotNull(pkg.provenance().getPackageChecksum());
        }
    }

    @Nested
    @DisplayName("Release Verification")
    class ReleaseVerification {

        @Test
        @DisplayName("should verify signed package successfully")
        void shouldVerifySignedPackageSuccessfully() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("verify-test", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // When
            VerificationResult result = verifier.verify(pkg);

            // Then
            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("should verify signature validity")
        void shouldVerifySignatureValidity() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("sig-test", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // When
            VerificationResult sigResult = verifier.verifySignature(pkg.provenance());

            // Then
            assertTrue(sigResult.isSuccess());
        }

        @Test
        @DisplayName("should contain valid signature information")
        void shouldContainValidSignatureInformation() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("sig-info-test", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Then
            assertTrue(pkg.provenance().isSigned());
            assertTrue(pkg.provenance().getSignature().isPresent());

            var signature = pkg.provenance().getSignature().get();
            assertEquals("Ed25519", signature.algorithm());
            assertNotNull(signature.keyId());
            assertNotNull(signature.value());
            assertNotNull(signature.publicKey());
            assertNotNull(signature.signedAt());
        }
    }

    @Nested
    @DisplayName("Lockfile Generation")
    class LockfileGeneration {

        @Test
        @DisplayName("should generate lockfile for single package")
        void shouldGenerateLockfileForSinglePackage() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("lockfile-test", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // When
            Lockfile lockfile = lockfileGenerator.generate(List.of(pkg));

            // Then
            assertNotNull(lockfile);
            assertNotNull(lockfile.checksum());
            assertEquals("lockfile-test", lockfile.flowId());
            assertEquals("1.0.0", lockfile.flowVersion());
            assertEquals(1, lockfile.entries().size());
        }

        @Test
        @DisplayName("should include correct entry in lockfile")
        void shouldIncludeCorrectEntryInLockfile() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("entry-test", "2.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // When
            Lockfile lockfile = lockfileGenerator.generate(List.of(pkg));

            // Then
            var entry = lockfile.entries().get(0);
            assertEquals("entry-test@2.0.0", entry.releaseId());
            assertNotNull(entry.irChecksum());
            assertNotNull(entry.packageChecksum());
        }

        @Test
        @DisplayName("should have valid lockfile checksum")
        void shouldHaveValidLockfileChecksum() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("checksum-lockfile", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // When
            Lockfile lockfile = lockfileGenerator.generate(List.of(pkg));

            // Then
            assertNotNull(lockfile.checksum());
            assertTrue(lockfile.checksum().length() > 0);
            // Checksum should be hex string
            assertTrue(lockfile.checksum().matches("^[0-9a-f]+$"));
        }
    }

    @Nested
    @DisplayName("Package Round Trip")
    class PackageRoundTrip {

        @Test
        @DisplayName("should serialize and deserialize package")
        void shouldSerializeAndDeserializePackage() throws Exception {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("roundtrip-test", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage original = releaseBuilder.build(flowIr, sourceInfo);

            // When - serialize to JSON and back
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module());

            String json = mapper.writeValueAsString(original);
            ReleasePackage deserialized = mapper.readValue(json, ReleasePackage.class);

            // Then
            assertEquals(original.id().canonicalId(), deserialized.id().canonicalId());
            assertEquals(original.flowId().value(), deserialized.flowId().value());
            assertEquals(original.phaseCount(), deserialized.phaseCount());
            assertEquals(original.skillCount(), deserialized.skillCount());
        }

        @Test
        @DisplayName("should preserve provenance through serialization")
        void shouldPreserveProvenanceThroughSerialization() throws Exception {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("provenance-rt", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage original = releaseBuilder.build(flowIr, sourceInfo);

            // When
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module());

            String json = mapper.writeValueAsString(original);
            ReleasePackage deserialized = mapper.readValue(json, ReleasePackage.class);

            // Then
            assertEquals(original.provenance().getReleaseId().canonicalId(),
                        deserialized.provenance().getReleaseId().canonicalId());
            assertEquals(original.provenance().getRepositoryUrl(),
                        deserialized.provenance().getRepositoryUrl());
            assertEquals(original.provenance().getCommitSha(),
                        deserialized.provenance().getCommitSha());
        }
    }

    @Nested
    @DisplayName("Package Access")
    class PackageAccessTests {

        @Test
        @DisplayName("should access package components directly")
        void shouldAccessPackageComponentsDirectly() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createFlowIrWithPhases(
                "access-test", "1.0.0",
                List.of("phase1", "phase2")
            );
            Map<PhaseId, PhaseIr> phases = ReleaseTestFixtures.createPhasesMap("phase1", "phase2");
            Map<SkillId, SkillIr> skills = ReleaseTestFixtures.createSkillsMap("skill1");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, phases, skills, sourceInfo);

            // Then - access directly through package
            assertEquals("access-test@1.0.0", pkg.id().canonicalId());
            assertEquals(2, pkg.phaseCount());
            assertEquals(1, pkg.skillCount());
            assertTrue(pkg.getPhase(PhaseId.of("phase1")).isPresent());
            assertTrue(pkg.getSkill(SkillId.of("skill1")).isPresent());
        }
    }
}
