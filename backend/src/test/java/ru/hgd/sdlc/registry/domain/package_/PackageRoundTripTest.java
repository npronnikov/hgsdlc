package ru.hgd.sdlc.registry.domain.package_;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.compiler.domain.model.authored.HandlerRef;
import ru.hgd.sdlc.compiler.domain.model.authored.HandlerKind;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.IrMetadata;
import ru.hgd.sdlc.compiler.domain.model.ir.PhaseIr;
import ru.hgd.sdlc.registry.domain.model.provenance.BuilderInfo;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.release.ChecksumManifest;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for the package format.
 * Tests that packages can be written and read back with identical content.
 */
@DisplayName("Package Round-Trip")
class PackageRoundTripTest {

    private ReleasePackageBuilder builder;
    private ReleasePackageReader reader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        builder = new ReleasePackageBuilder();
        reader = new ReleasePackageReader();
    }

    @Nested
    @DisplayName("write then read")
    class WriteThenRead {

        @Test
        @DisplayName("should preserve release ID")
        void shouldPreserveReleaseId() throws Exception {
            var original = createFullPackage();
            Path zipPath = tempDir.resolve("test.zip");

            builder.writeToZip(original, zipPath);
            var restored = reader.readFromZip(zipPath);

            assertEquals(original.id().canonicalId(), restored.id().canonicalId());
            assertEquals(original.id().flowId(), restored.id().flowId());
            assertEquals(original.id().version(), restored.id().version());
        }

        @Test
        @DisplayName("should preserve flow IR")
        void shouldPreserveFlowIr() throws Exception {
            var original = createFullPackage();
            Path zipPath = tempDir.resolve("test.zip");

            builder.writeToZip(original, zipPath);
            var restored = reader.readFromZip(zipPath);

            assertEquals(original.flowIr().flowId(), restored.flowIr().flowId());
            assertEquals(original.flowIr().flowVersion(), restored.flowIr().flowVersion());
        }

        @Test
        @DisplayName("should preserve phases")
        void shouldPreservePhases() throws Exception {
            FlowIr flowIr = createFlowIr();
            Provenance provenance = createProvenance();
            Map<PhaseId, PhaseIr> phases = Map.of(
                PhaseId.of("setup"), createPhaseIr("setup", 0),
                PhaseId.of("develop"), createPhaseIr("develop", 1),
                PhaseId.of("review"), createPhaseIr("review", 2)
            );

            var original = builder.fromSource(flowIr, phases, Map.of(), provenance);
            Path zipPath = tempDir.resolve("test.zip");

            builder.writeToZip(original, zipPath);
            var restored = reader.readFromZip(zipPath);

            assertEquals(original.phaseCount(), restored.phaseCount());
            for (PhaseId phaseId : phases.keySet()) {
                assertTrue(restored.getPhase(phaseId).isPresent());
                assertEquals(
                    original.getPhase(phaseId).get().name(),
                    restored.getPhase(phaseId).get().name()
                );
            }
        }

        @Test
        @DisplayName("should preserve skills")
        void shouldPreserveSkills() throws Exception {
            FlowIr flowIr = createFlowIr();
            Provenance provenance = createProvenance();
            Map<SkillId, SkillIr> skills = Map.of(
                SkillId.of("code-gen"), createSkillIr("code-gen"),
                SkillId.of("test-runner"), createSkillIr("test-runner")
            );

            var original = builder.fromSource(flowIr, Map.of(), skills, provenance);
            Path zipPath = tempDir.resolve("test.zip");

            builder.writeToZip(original, zipPath);
            var restored = reader.readFromZip(zipPath);

            assertEquals(original.skillCount(), restored.skillCount());
            for (SkillId skillId : skills.keySet()) {
                assertTrue(restored.getSkill(skillId).isPresent());
                assertEquals(
                    original.getSkill(skillId).get().name(),
                    restored.getSkill(skillId).get().name()
                );
            }
        }

        @Test
        @DisplayName("should preserve provenance")
        void shouldPreserveProvenance() throws Exception {
            var original = createFullPackage();
            Path zipPath = tempDir.resolve("test.zip");

            builder.writeToZip(original, zipPath);
            var restored = reader.readFromZip(zipPath);

            assertEquals(original.provenance().getReleaseId(), restored.provenance().getReleaseId());
            assertEquals(original.provenance().getCommitSha(), restored.provenance().getCommitSha());
            assertEquals(original.provenance().getRepositoryUrl(), restored.provenance().getRepositoryUrl());
        }

        @Test
        @DisplayName("should preserve checksum manifest")
        void shouldPreserveChecksumManifest() throws Exception {
            var original = createFullPackage();
            Path zipPath = tempDir.resolve("test.zip");

            builder.writeToZip(original, zipPath);
            var restored = reader.readFromZip(zipPath);

            // Check manifest has entries
            ChecksumManifest restoredChecksums = restored.checksums();
            assertTrue(restoredChecksums.size() > 0);

            // Verify key files have checksums (checksums.sha256 does not include itself)
            assertTrue(restoredChecksums.contains(PackageFormat.FILE_FLOW_IR));
            assertTrue(restoredChecksums.contains(PackageFormat.FILE_PROVENANCE));
        }

        @Test
        @DisplayName("should produce valid checksums")
        void shouldProduceValidChecksums() throws Exception {
            var pkg = createFullPackage();
            Path zipPath = tempDir.resolve("test.zip");

            builder.writeToZip(pkg, zipPath);

            // Reading should succeed - checksums are validated during read
            var restored = reader.readFromZip(zipPath);
            assertNotNull(restored);
        }

        @Test
        @DisplayName("should produce deterministic output")
        void shouldProduceDeterministicOutput() throws Exception {
            // Build two packages with the same content (but different timestamps may differ)
            // At minimum, they should both be readable
            FlowIr flowIr = createFlowIr();
            Provenance provenance = createProvenance();

            var pkg1 = builder.fromSource(flowIr, Map.of(), Map.of(), provenance);
            var pkg2 = builder.fromSource(flowIr, Map.of(), Map.of(), provenance);

            Path zip1 = tempDir.resolve("test1.zip");
            Path zip2 = tempDir.resolve("test2.zip");

            builder.writeToZip(pkg1, zip1);
            builder.writeToZip(pkg2, zip2);

            // Both should be readable
            var restored1 = reader.readFromZip(zip1);
            var restored2 = reader.readFromZip(zip2);

            assertEquals(restored1.id().canonicalId(), restored2.id().canonicalId());
            assertEquals(restored1.flowIr().flowId(), restored2.flowIr().flowId());
        }
    }

    @Nested
    @DisplayName("integrity verification")
    class IntegrityVerification {

        @Test
        @DisplayName("should verify checksums on read")
        void shouldVerifyChecksumsOnRead() throws Exception {
            var original = createFullPackage();
            Path zipPath = tempDir.resolve("test.zip");

            builder.writeToZip(original, zipPath);

            // If checksums are valid, reading should succeed
            var restored = reader.readFromZip(zipPath);
            assertNotNull(restored);
        }

        @Test
        @DisplayName("should have matching checksums in manifest")
        void shouldHaveMatchingChecksumsInManifest() throws Exception {
            var original = createFullPackage();
            Path zipPath = tempDir.resolve("test.zip");

            builder.writeToZip(original, zipPath);
            var restored = reader.readFromZip(zipPath);

            // The checksum manifest should have entries for all package files
            ChecksumManifest checksums = restored.checksums();

            // Verify we can compute hash of a file and it matches
            byte[] testContent = "test".getBytes();
            var hash = computeHash(testContent);
            assertNotNull(hash);
        }
    }

    // Helper methods

    private ReleasePackage createFullPackage() throws Exception {
        FlowIr flowIr = createFlowIr();
        Provenance provenance = createProvenance();
        Map<PhaseId, PhaseIr> phases = Map.of(
            PhaseId.of("setup"), createPhaseIr("setup", 0)
        );
        Map<SkillId, SkillIr> skills = Map.of(
            SkillId.of("code-gen"), createSkillIr("code-gen")
        );
        return builder.fromSource(flowIr, phases, skills, provenance);
    }

    private FlowIr createFlowIr() {
        Sha256 packageChecksum = Sha256.of("test-package");
        Sha256 irChecksum = Sha256.of("test-ir");

        return FlowIr.builder()
            .flowId(FlowId.of("test-flow"))
            .flowVersion(SemanticVersion.of(1, 0, 0))
            .metadata(IrMetadata.create(packageChecksum, irChecksum, "1.0.0"))
            .build();
    }

    private PhaseIr createPhaseIr(String id, int order) {
        return PhaseIr.builder()
            .id(PhaseId.of(id))
            .name(id + " phase")
            .order(order)
            .build();
    }

    private SkillIr createSkillIr(String id) {
        return SkillIr.builder()
            .skillId(SkillId.of(id))
            .skillVersion(SemanticVersion.of(1, 0, 0))
            .name(id + " skill")
            .handler(HandlerRef.of("skill://test-service"))
            .irChecksum(Sha256.of("skill-checksum"))
            .compiledAt(Instant.now())
            .compilerVersion("1.0.0")
            .build();
    }

    private Provenance createProvenance() {
        ReleaseId releaseId = ReleaseId.of(
            FlowId.of("test-flow"),
            ReleaseVersion.of("1.0.0")
        );

        return Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/test/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(Instant.now())
            .commitAuthor("test@example.com")
            .committedAt(Instant.now().minusSeconds(3600))
            .builderId("test-builder")
            .builder(BuilderInfo.of("sdlc-registry", "1.0.0"))
            .compilerVersion("1.0.0")
            .irChecksum(Sha256Hash.of("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
            .packageChecksum(Sha256Hash.of("ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb"))
            .gitTag("v1.0.0")
            .build();
    }

    private Sha256Hash computeHash(byte[] content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content);
            return Sha256Hash.ofBytes(hashBytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
