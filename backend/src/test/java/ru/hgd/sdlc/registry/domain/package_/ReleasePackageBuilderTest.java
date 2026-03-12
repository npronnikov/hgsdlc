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
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReleasePackageBuilder")
class ReleasePackageBuilderTest {

    private ReleasePackageBuilder builder;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        builder = new ReleasePackageBuilder();
    }

    @Nested
    @DisplayName("fromSource()")
    class FromSource {

        @Test
        @DisplayName("should build package with required components")
        void shouldBuildPackageWithRequiredComponents() throws Exception {
            FlowIr flowIr = createFlowIr();
            Provenance provenance = createProvenance();
            Map<PhaseId, PhaseIr> phases = Map.of(
                PhaseId.of("setup"), createPhaseIr("setup")
            );
            Map<SkillId, SkillIr> skills = Map.of(
                SkillId.of("code-gen"), createSkillIr("code-gen")
            );

            var pkg = builder.fromSource(flowIr, phases, skills, provenance);

            assertNotNull(pkg);
            assertEquals("test-flow@1.0.0", pkg.id().canonicalId());
            assertEquals(1, pkg.phaseCount());
            assertEquals(1, pkg.skillCount());
        }

        @Test
        @DisplayName("should throw when flow IR is null")
        void shouldThrowWhenFlowIrNull() {
            Provenance provenance = createProvenance();

            assertThrows(ReleasePackageBuilder.PackageBuildException.class,
                () -> builder.fromSource(null, Map.of(), Map.of(), provenance));
        }

        @Test
        @DisplayName("should throw when provenance is null")
        void shouldThrowWhenProvenanceNull() {
            FlowIr flowIr = createFlowIr();

            assertThrows(ReleasePackageBuilder.PackageBuildException.class,
                () -> builder.fromSource(flowIr, Map.of(), Map.of(), null));
        }

        @Test
        @DisplayName("should build package with empty phases and skills")
        void shouldBuildWithEmptyPhasesAndSkills() throws Exception {
            FlowIr flowIr = createFlowIr();
            Provenance provenance = createProvenance();

            var pkg = builder.fromSource(flowIr, Map.of(), Map.of(), provenance);

            assertNotNull(pkg);
            assertEquals(0, pkg.phaseCount());
            assertEquals(0, pkg.skillCount());
        }
    }

    @Nested
    @DisplayName("toByteArray()")
    class ToByteArray {

        @Test
        @DisplayName("should create valid ZIP bytes")
        void shouldCreateValidZipBytes() throws Exception {
            var pkg = createFullPackage();

            byte[] zipBytes = builder.toByteArray(pkg);

            assertNotNull(zipBytes);
            assertTrue(zipBytes.length > 0);

            // Verify it's a valid ZIP
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                int count = 0;
                while ((entry = zis.getNextEntry()) != null) {
                    count++;
                    zis.closeEntry();
                }
                assertTrue(count >= 4); // At least manifest, flow IR, provenance, checksums
            }
        }
    }

    @Nested
    @DisplayName("writeToZip()")
    class WriteToZip {

        @Test
        @DisplayName("should write valid ZIP file")
        void shouldWriteValidZipFile() throws Exception {
            var pkg = createFullPackage();
            Path zipPath = tempDir.resolve("test-release.zip");

            builder.writeToZip(pkg, zipPath);

            assertTrue(Files.exists(zipPath));
            assertTrue(Files.size(zipPath) > 0);
        }

        @Test
        @DisplayName("should include all required files in ZIP")
        void shouldIncludeAllRequiredFiles() throws Exception {
            var pkg = createFullPackage();
            Path zipPath = tempDir.resolve("test-release.zip");

            builder.writeToZip(pkg, zipPath);

            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
                Map<String, Boolean> found = new HashMap<>();
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    found.put(entry.getName(), true);
                    zis.closeEntry();
                }

                assertTrue(found.containsKey(PackageFormat.FILE_MANIFEST));
                assertTrue(found.containsKey(PackageFormat.FILE_FLOW_IR));
                assertTrue(found.containsKey(PackageFormat.FILE_PROVENANCE));
                assertTrue(found.containsKey(PackageFormat.FILE_CHECKSUMS));
            }
        }

        @Test
        @DisplayName("should include phase IR files")
        void shouldIncludePhaseIrFiles() throws Exception {
            FlowIr flowIr = createFlowIr();
            Provenance provenance = createProvenance();
            Map<PhaseId, PhaseIr> phases = Map.of(
                PhaseId.of("setup"), createPhaseIr("setup"),
                PhaseId.of("develop"), createPhaseIr("develop")
            );

            var pkg = builder.fromSource(flowIr, phases, Map.of(), provenance);
            Path zipPath = tempDir.resolve("test-release.zip");

            builder.writeToZip(pkg, zipPath);

            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
                boolean foundSetup = false;
                boolean foundDevelop = false;
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("phases/setup.ir.json")) foundSetup = true;
                    if (entry.getName().equals("phases/develop.ir.json")) foundDevelop = true;
                    zis.closeEntry();
                }
                assertTrue(foundSetup);
                assertTrue(foundDevelop);
            }
        }

        @Test
        @DisplayName("should include skill IR files")
        void shouldIncludeSkillIrFiles() throws Exception {
            FlowIr flowIr = createFlowIr();
            Provenance provenance = createProvenance();
            Map<SkillId, SkillIr> skills = Map.of(
                SkillId.of("code-gen"), createSkillIr("code-gen")
            );

            var pkg = builder.fromSource(flowIr, Map.of(), skills, provenance);
            Path zipPath = tempDir.resolve("test-release.zip");

            builder.writeToZip(pkg, zipPath);

            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
                boolean foundSkill = false;
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("skills/code-gen.ir.json")) foundSkill = true;
                    zis.closeEntry();
                }
                assertTrue(foundSkill);
            }
        }
    }

    // Helper methods

    private ru.hgd.sdlc.registry.domain.model.release.ReleasePackage createFullPackage() throws Exception {
        FlowIr flowIr = createFlowIr();
        Provenance provenance = createProvenance();
        Map<PhaseId, PhaseIr> phases = Map.of(
            PhaseId.of("setup"), createPhaseIr("setup")
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

    private PhaseIr createPhaseIr(String id) {
        return PhaseIr.builder()
            .id(PhaseId.of(id))
            .name(id + " phase")
            .order(0)
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
}
