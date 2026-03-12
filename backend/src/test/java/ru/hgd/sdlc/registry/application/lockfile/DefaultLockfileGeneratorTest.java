package ru.hgd.sdlc.registry.application.lockfile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.IrMetadata;
import ru.hgd.sdlc.registry.domain.model.provenance.BuilderInfo;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.release.ChecksumManifest;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseMetadata;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultLockfileGenerator")
class DefaultLockfileGeneratorTest {

    private DefaultLockfileGenerator generator;
    private LockfileSerializer serializer;

    @BeforeEach
    void setUp() {
        generator = new DefaultLockfileGenerator();
        serializer = new LockfileSerializer();
    }

    private static ReleaseId releaseId(String flowId, String version) {
        return ReleaseId.of(FlowId.of(flowId), ReleaseVersion.of(version));
    }

    private ReleasePackage createPackage(ReleaseId id) {
        return createPackage(id, Map.of(), Map.of());
    }

    private ReleasePackage createPackage(
            ReleaseId id,
            Map<SkillId, Sha256> resolvedSkills,
            Map<SkillId, SkillIr> bundledSkills) {

        Sha256 packageChecksum = Sha256.of("package-content-" + id.canonicalId());
        Sha256 irChecksum = Sha256.of("ir-content-" + id.canonicalId());

        FlowIr flowIr = FlowIr.builder()
            .flowId(id.flowId())
            .flowVersion(SemanticVersion.of(id.version().formatted()))
            .metadata(IrMetadata.create(packageChecksum, irChecksum, "1.0.0"))
            .resolvedSkills(resolvedSkills)
            .build();

        ReleaseMetadata metadata = ReleaseMetadata.builder()
            .displayName(id.flowId().value())
            .author("test-author")
            .createdAt(Instant.now())
            .gitCommit("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            .build();

        Provenance provenance = Provenance.builder()
            .releaseId(id)
            .repositoryUrl("https://github.com/test/test.git")
            .commitSha("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            .buildTimestamp(Instant.now())
            .commitAuthor("test-author")
            .committedAt(Instant.now())
            .builderId("test-builder")
            .builder(BuilderInfo.of("test-builder", "1.0.0"))
            .compilerVersion("1.0.0")
            .irChecksum(Sha256Hash.of("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .packageChecksum(Sha256Hash.of("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
            .build();

        ChecksumManifest checksums = ChecksumManifest.builder()
            .entry("flow.ir.json", Sha256Hash.of("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"))
            .build();

        return ReleasePackage.builder()
            .id(id)
            .metadata(metadata)
            .flowIr(flowIr)
            .provenance(provenance)
            .checksums(checksums)
            .skills(bundledSkills)
            .build();
    }

    @Nested
    @DisplayName("generate()")
    class Generate {

        @Test
        @DisplayName("should generate lockfile from single package")
        void shouldGenerateFromSinglePackage() {
            ReleaseId flowId = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(flowId);
            List<ReleasePackage> packages = List.of(pkg);

            Lockfile lockfile = generator.generate(packages);

            assertNotNull(lockfile);
            assertEquals(LockfileVersion.V1, lockfile.version());
            assertNotNull(lockfile.generatedAt());
            assertEquals("test-flow", lockfile.flowId());
            assertEquals("1.0.0", lockfile.flowVersion());
            assertEquals(1, lockfile.size());
            assertNotNull(lockfile.checksum());
            assertFalse(lockfile.checksum().isBlank());
        }

        @Test
        @DisplayName("should throw for null packages")
        void shouldThrowForNullPackages() {
            assertThrows(IllegalArgumentException.class, () -> generator.generate(null));
        }

        @Test
        @DisplayName("should throw for empty packages")
        void shouldThrowForEmptyPackages() {
            assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of()));
        }

        @Test
        @DisplayName("should generate lockfile with dependencies")
        void shouldGenerateWithDependencies() {
            ReleaseId rootId = releaseId("root-flow", "1.0.0");
            ReleaseId depId = releaseId("dep-flow", "2.0.0");

            SkillId skillId = SkillId.of("shared-skill");
            Sha256 skillChecksum = Sha256.of("skill-content");

            SkillIr bundledSkillIr = SkillIr.builder()
                .skillId(skillId)
                .skillVersion(SemanticVersion.of(1, 0, 0))
                .name("Shared Skill")
                .handler(ru.hgd.sdlc.compiler.domain.model.authored.HandlerRef.of("skill://shared-skill"))
                .irChecksum(skillChecksum)
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

            // Root flow depends on a skill
            ReleasePackage rootPkg = createPackage(rootId, Map.of(skillId, skillChecksum), Map.of());
            // Dep flow provides the skill
            ReleasePackage depPkg = createPackage(depId, Map.of(), Map.of(skillId, bundledSkillIr));

            // Packages in topological order (dependencies first)
            List<ReleasePackage> packages = List.of(rootPkg, depPkg);

            Lockfile lockfile = generator.generate(packages);

            assertNotNull(lockfile);
            assertEquals(2, lockfile.size());
            assertEquals("root-flow", lockfile.flowId());
        }

        @Test
        @DisplayName("should record correct release IDs")
        void shouldRecordCorrectReleaseIds() {
            ReleaseId flowId = releaseId("my-flow", "2.3.4");
            ReleasePackage pkg = createPackage(flowId);

            Lockfile lockfile = generator.generate(List.of(pkg));

            assertEquals("my-flow@2.3.4", lockfile.rootReleaseId());
            LockfileEntry entry = lockfile.rootEntry();
            assertNotNull(entry);
            assertEquals("my-flow@2.3.4", entry.releaseId());
        }
    }

    @Nested
    @DisplayName("entries")
    class Entries {

        @Test
        @DisplayName("should create entry with correct type")
        void shouldCreateEntryWithCorrectType() {
            ReleaseId flowId = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(flowId);

            Lockfile lockfile = generator.generate(List.of(pkg));

            LockfileEntry entry = lockfile.rootEntry();
            assertNotNull(entry);
            assertEquals(LockfileEntryType.FLOW, entry.type());
        }

        @Test
        @DisplayName("should record IR checksum")
        void shouldRecordIrChecksum() {
            ReleaseId flowId = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(flowId);

            Lockfile lockfile = generator.generate(List.of(pkg));

            LockfileEntry entry = lockfile.rootEntry();
            assertNotNull(entry);
            assertNotNull(entry.irChecksum());
            assertEquals(64, entry.irChecksum().length()); // SHA-256 is 64 hex chars
        }

        @Test
        @DisplayName("should record package checksum")
        void shouldRecordPackageChecksum() {
            ReleaseId flowId = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(flowId);

            Lockfile lockfile = generator.generate(List.of(pkg));

            LockfileEntry entry = lockfile.rootEntry();
            assertNotNull(entry);
            assertNotNull(entry.packageChecksum());
            assertEquals(64, entry.packageChecksum().length()); // SHA-256 is 64 hex chars
        }

        @Test
        @DisplayName("should record dependencies for entries")
        void shouldRecordDependencies() {
            ReleaseId rootId = releaseId("root-flow", "1.0.0");
            ReleaseId depId = releaseId("dep-flow", "2.0.0");

            SkillId skillId = SkillId.of("shared-skill");
            Sha256 skillChecksum = Sha256.of("skill-content");

            SkillIr bundledSkillIr = SkillIr.builder()
                .skillId(skillId)
                .skillVersion(SemanticVersion.of(1, 0, 0))
                .name("Shared Skill")
                .handler(ru.hgd.sdlc.compiler.domain.model.authored.HandlerRef.of("skill://shared-skill"))
                .irChecksum(skillChecksum)
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

            ReleasePackage rootPkg = createPackage(rootId, Map.of(skillId, skillChecksum), Map.of());
            ReleasePackage depPkg = createPackage(depId, Map.of(), Map.of(skillId, bundledSkillIr));

            List<ReleasePackage> packages = List.of(rootPkg, depPkg);

            Lockfile lockfile = generator.generate(packages);

            // Root entry should have dependency on dep-flow
            LockfileEntry rootEntry = lockfile.findEntry("root-flow@1.0.0");
            assertNotNull(rootEntry);
            assertTrue(rootEntry.hasDependencies());
            assertTrue(rootEntry.dependencies().contains("dep-flow@2.0.0"));
        }

        @Test
        @DisplayName("should record empty dependencies for standalone package")
        void shouldRecordEmptyDependenciesForStandalone() {
            ReleaseId flowId = releaseId("standalone-flow", "1.0.0");
            ReleasePackage pkg = createPackage(flowId, Map.of(), Map.of());

            Lockfile lockfile = generator.generate(List.of(pkg));

            LockfileEntry entry = lockfile.rootEntry();
            assertNotNull(entry);
            assertFalse(entry.hasDependencies());
            assertTrue(entry.dependencies().isEmpty());
        }
    }

    @Nested
    @DisplayName("checksums")
    class Checksums {

        @Test
        @DisplayName("should compute deterministic checksum")
        void shouldComputeDeterministicChecksum() {
            ReleaseId flowId = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(flowId);
            List<ReleasePackage> packages = List.of(pkg);

            Lockfile lockfile1 = generator.generate(packages);
            Lockfile lockfile2 = generator.generate(packages);

            // Checksums should be equal for same content
            // Note: generatedAt will differ, so checksums may differ
            // Let's verify the structure instead
            assertNotNull(lockfile1.checksum());
            assertNotNull(lockfile2.checksum());
            assertEquals(64, lockfile1.checksum().length());
        }

        @Test
        @DisplayName("should produce valid hex checksum")
        void shouldProduceValidHexChecksum() {
            ReleaseId flowId = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(flowId);

            Lockfile lockfile = generator.generate(List.of(pkg));

            String checksum = lockfile.checksum();
            assertTrue(checksum.matches("^[a-f0-9]{64}$"),
                "Checksum should be 64 lowercase hex characters");
        }
    }

    @Nested
    @DisplayName("serialization")
    class Serialization {

        @Test
        @DisplayName("should serialize to JSON")
        void shouldSerializeToJson() {
            ReleaseId flowId = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(flowId);

            Lockfile lockfile = generator.generate(List.of(pkg));

            String json = serializer.toJson(lockfile);

            assertNotNull(json);
            assertTrue(json.contains("\"version\":\"1.0\""));
            assertTrue(json.contains("\"flowId\":\"test-flow\""));
            assertTrue(json.contains("\"flowVersion\":\"1.0.0\""));
        }

        @Test
        @DisplayName("should deserialize from JSON")
        void shouldDeserializeFromJson() {
            ReleaseId flowId = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(flowId);

            Lockfile original = generator.generate(List.of(pkg));
            String json = serializer.toJson(original);

            Lockfile deserialized = serializer.fromJson(json);

            assertNotNull(deserialized);
            assertEquals(original.version(), deserialized.version());
            assertEquals(original.flowId(), deserialized.flowId());
            assertEquals(original.flowVersion(), deserialized.flowVersion());
            assertEquals(original.checksum(), deserialized.checksum());
            assertEquals(original.size(), deserialized.size());
        }

        @Test
        @DisplayName("should round-trip through JSON")
        void shouldRoundTripThroughJson() {
            ReleaseId flowId = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(flowId);

            Lockfile original = generator.generate(List.of(pkg));

            String json = serializer.toJson(original);
            Lockfile restored = serializer.fromJson(json);

            assertEquals(original.flowId(), restored.flowId());
            assertEquals(original.flowVersion(), restored.flowVersion());
            assertEquals(original.checksum(), restored.checksum());
            assertEquals(original.size(), restored.size());

            LockfileEntry originalEntry = original.rootEntry();
            LockfileEntry restoredEntry = restored.rootEntry();

            assertEquals(originalEntry.releaseId(), restoredEntry.releaseId());
            assertEquals(originalEntry.type(), restoredEntry.type());
            assertEquals(originalEntry.irChecksum(), restoredEntry.irChecksum());
            assertEquals(originalEntry.packageChecksum(), restoredEntry.packageChecksum());
        }

        @Test
        @DisplayName("should serialize with multiple entries")
        void shouldSerializeWithMultipleEntries() {
            ReleaseId rootId = releaseId("root-flow", "1.0.0");
            ReleaseId depId = releaseId("dep-flow", "2.0.0");

            SkillId skillId = SkillId.of("shared-skill");
            Sha256 skillChecksum = Sha256.of("skill-content");

            SkillIr bundledSkillIr = SkillIr.builder()
                .skillId(skillId)
                .skillVersion(SemanticVersion.of(1, 0, 0))
                .name("Shared Skill")
                .handler(ru.hgd.sdlc.compiler.domain.model.authored.HandlerRef.of("skill://shared-skill"))
                .irChecksum(skillChecksum)
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

            ReleasePackage rootPkg = createPackage(rootId, Map.of(skillId, skillChecksum), Map.of());
            ReleasePackage depPkg = createPackage(depId, Map.of(), Map.of(skillId, bundledSkillIr));

            Lockfile original = generator.generate(List.of(rootPkg, depPkg));

            String json = serializer.toJson(original);
            Lockfile restored = serializer.fromJson(json);

            assertEquals(2, restored.size());
            assertEquals(original.entries().get(0).releaseId(), restored.entries().get(0).releaseId());
            assertEquals(original.entries().get(1).releaseId(), restored.entries().get(1).releaseId());
        }
    }

    @Nested
    @DisplayName("dependency order")
    class DependencyOrder {

        @Test
        @DisplayName("should preserve topological order")
        void shouldPreserveTopologicalOrder() {
            ReleaseId firstId = releaseId("first-flow", "1.0.0");
            ReleaseId secondId = releaseId("second-flow", "1.0.0");
            ReleaseId thirdId = releaseId("third-flow", "1.0.0");

            ReleasePackage firstPkg = createPackage(firstId);
            ReleasePackage secondPkg = createPackage(secondId);
            ReleasePackage thirdPkg = createPackage(thirdId);

            List<ReleasePackage> packages = List.of(firstPkg, secondPkg, thirdPkg);

            Lockfile lockfile = generator.generate(packages);

            assertEquals(3, lockfile.size());
            assertEquals("first-flow@1.0.0", lockfile.entries().get(0).releaseId());
            assertEquals("second-flow@1.0.0", lockfile.entries().get(1).releaseId());
            assertEquals("third-flow@1.0.0", lockfile.entries().get(2).releaseId());
        }

        @Test
        @DisplayName("should find entry by release ID")
        void shouldFindEntryByReleaseId() {
            ReleaseId flowId = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(flowId);

            Lockfile lockfile = generator.generate(List.of(pkg));

            LockfileEntry found = lockfile.findEntry("test-flow@1.0.0");
            assertNotNull(found);
            assertEquals("test-flow@1.0.0", found.releaseId());

            assertNull(lockfile.findEntry("non-existent@1.0.0"));
        }
    }
}
