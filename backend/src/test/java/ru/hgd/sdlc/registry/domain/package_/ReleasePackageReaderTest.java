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
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReleasePackageReader")
class ReleasePackageReaderTest {

    private ReleasePackageReader reader;
    private ReleasePackageBuilder builder;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reader = new ReleasePackageReader();
        builder = new ReleasePackageBuilder();
    }

    @Nested
    @DisplayName("readFromZip(Path)")
    class ReadFromZipPath {

        @Test
        @DisplayName("should read valid package")
        void shouldReadValidPackage() throws Exception {
            var originalPkg = createFullPackage();
            Path zipPath = tempDir.resolve("test-release.zip");
            builder.writeToZip(originalPkg, zipPath);

            var pkg = reader.readFromZip(zipPath);

            assertNotNull(pkg);
            assertEquals(originalPkg.id().canonicalId(), pkg.id().canonicalId());
        }

        @Test
        @DisplayName("should throw for non-existent file")
        void shouldThrowForNonExistentFile() {
            Path nonExistent = tempDir.resolve("non-existent.zip");

            assertThrows(ReleasePackageReader.PackageReadException.class,
                () -> reader.readFromZip(nonExistent));
        }
    }

    @Nested
    @DisplayName("readFromZipFile(ZipFile)")
    class ReadFromZipFile {

        @Test
        @DisplayName("should read flow IR")
        void shouldReadFlowIr() throws Exception {
            var originalPkg = createFullPackage();
            Path zipPath = tempDir.resolve("test-release.zip");
            builder.writeToZip(originalPkg, zipPath);

            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                var pkg = reader.readFromZipFile(zipFile);

                assertNotNull(pkg.flowIr());
                assertEquals("test-flow", pkg.flowIr().flowId().value());
            }
        }

        @Test
        @DisplayName("should read provenance")
        void shouldReadProvenance() throws Exception {
            var originalPkg = createFullPackage();
            Path zipPath = tempDir.resolve("test-release.zip");
            builder.writeToZip(originalPkg, zipPath);

            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                var pkg = reader.readFromZipFile(zipFile);

                assertNotNull(pkg.provenance());
                assertEquals("test-flow@1.0.0", pkg.provenance().getReleaseId().canonicalId());
            }
        }

        @Test
        @DisplayName("should read phases")
        void shouldReadPhases() throws Exception {
            FlowIr flowIr = createFlowIr();
            Provenance provenance = createProvenance();
            Map<PhaseId, PhaseIr> phases = Map.of(
                PhaseId.of("setup"), createPhaseIr("setup"),
                PhaseId.of("develop"), createPhaseIr("develop")
            );

            var originalPkg = builder.fromSource(flowIr, phases, Map.of(), provenance);
            Path zipPath = tempDir.resolve("test-release.zip");
            builder.writeToZip(originalPkg, zipPath);

            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                var pkg = reader.readFromZipFile(zipFile);

                assertEquals(2, pkg.phaseCount());
                assertTrue(pkg.getPhase(PhaseId.of("setup")).isPresent());
                assertTrue(pkg.getPhase(PhaseId.of("develop")).isPresent());
            }
        }

        @Test
        @DisplayName("should read skills")
        void shouldReadSkills() throws Exception {
            FlowIr flowIr = createFlowIr();
            Provenance provenance = createProvenance();
            Map<SkillId, SkillIr> skills = Map.of(
                SkillId.of("code-gen"), createSkillIr("code-gen")
            );

            var originalPkg = builder.fromSource(flowIr, Map.of(), skills, provenance);
            Path zipPath = tempDir.resolve("test-release.zip");
            builder.writeToZip(originalPkg, zipPath);

            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                var pkg = reader.readFromZipFile(zipFile);

                assertEquals(1, pkg.skillCount());
                assertTrue(pkg.getSkill(SkillId.of("code-gen")).isPresent());
            }
        }

        @Test
        @DisplayName("should throw when manifest missing")
        void shouldThrowWhenManifestMissing() throws Exception {
            Path zipPath = tempDir.resolve("invalid-release.zip");
            createZipWithoutManifest(zipPath);

            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                assertThrows(ReleasePackageReader.PackageReadException.class,
                    () -> reader.readFromZipFile(zipFile));
            }
        }

        @Test
        @DisplayName("should throw when flow IR missing")
        void shouldThrowWhenFlowIrMissing() throws Exception {
            Path zipPath = tempDir.resolve("invalid-release.zip");
            createZipWithoutFlowIr(zipPath);

            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                assertThrows(ReleasePackageReader.PackageReadException.class,
                    () -> reader.readFromZipFile(zipFile));
            }
        }

        @Test
        @DisplayName("should throw when provenance missing")
        void shouldThrowWhenProvenanceMissing() throws Exception {
            Path zipPath = tempDir.resolve("invalid-release.zip");
            createZipWithoutProvenance(zipPath);

            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                assertThrows(ReleasePackageReader.PackageReadException.class,
                    () -> reader.readFromZipFile(zipFile));
            }
        }

        @Test
        @DisplayName("should throw when checksums missing")
        void shouldThrowWhenChecksumsMissing() throws Exception {
            Path zipPath = tempDir.resolve("invalid-release.zip");
            createZipWithoutChecksums(zipPath);

            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                assertThrows(ReleasePackageReader.PackageReadException.class,
                    () -> reader.readFromZipFile(zipFile));
            }
        }

        @Test
        @DisplayName("should throw on checksum mismatch")
        void shouldThrowOnChecksumMismatch() throws Exception {
            Path zipPath = tempDir.resolve("tampered-release.zip");
            createZipWithTamperedContent(zipPath);

            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                assertThrows(ReleasePackageReader.PackageReadException.class,
                    () -> reader.readFromZipFile(zipFile));
            }
        }
    }

    // Helper methods for creating invalid packages

    private void createZipWithoutManifest(Path path) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            addEntry(zos, PackageFormat.FILE_FLOW_IR, "{}");
            addEntry(zos, PackageFormat.FILE_PROVENANCE, "{}");
            addEntry(zos, PackageFormat.FILE_CHECKSUMS, "# checksums\n");
        }
    }

    private void createZipWithoutFlowIr(Path path) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            addEntry(zos, PackageFormat.FILE_MANIFEST, "{}");
            addEntry(zos, PackageFormat.FILE_PROVENANCE, "{}");
            addEntry(zos, PackageFormat.FILE_CHECKSUMS, "# checksums\n");
        }
    }

    private void createZipWithoutProvenance(Path path) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            addEntry(zos, PackageFormat.FILE_MANIFEST, "{}");
            addEntry(zos, PackageFormat.FILE_FLOW_IR, "{}");
            addEntry(zos, PackageFormat.FILE_CHECKSUMS, "# checksums\n");
        }
    }

    private void createZipWithoutChecksums(Path path) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            addEntry(zos, PackageFormat.FILE_MANIFEST, "{}");
            addEntry(zos, PackageFormat.FILE_FLOW_IR, "{}");
            addEntry(zos, PackageFormat.FILE_PROVENANCE, "{}");
        }
    }

    private void createZipWithTamperedContent(Path path) throws Exception {
        // Create a valid package first
        var originalPkg = createFullPackage();
        byte[] validZip = builder.toByteArray(originalPkg);

        // Create a tampered version with wrong checksum
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(PackageFormat.FILE_FLOW_IR, "{\"flowId\":\"test-flow\"}");
        entries.put(PackageFormat.FILE_PROVENANCE, "{}");
        // Wrong checksum - doesn't match the actual content
        entries.put(PackageFormat.FILE_CHECKSUMS,
            "# Release: test\n\n" +
            "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef  " +
            PackageFormat.FILE_FLOW_IR + "\n");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            // Add manifest without checksums
            addEntry(zos, PackageFormat.FILE_MANIFEST, "{\"releaseId\":\"test-flow@1.0.0\"}");
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                addEntry(zos, entry.getKey(), entry.getValue());
            }
        }
    }

    private void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    // Helper methods for creating test data

    private ReleasePackage createFullPackage() throws Exception {
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
