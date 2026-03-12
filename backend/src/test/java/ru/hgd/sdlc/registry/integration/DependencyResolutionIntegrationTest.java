package ru.hgd.sdlc.registry.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.registry.application.builder.DefaultReleaseBuilder;
import ru.hgd.sdlc.registry.application.builder.ReleaseBuilder;
import ru.hgd.sdlc.registry.application.builder.SourceInfo;
import ru.hgd.sdlc.registry.application.lockfile.DefaultLockfileGenerator;
import ru.hgd.sdlc.registry.application.lockfile.Lockfile;
import ru.hgd.sdlc.registry.application.lockfile.LockfileEntry;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for dependency resolution.
 * Tests how flows with skill dependencies are resolved and captured in lockfiles.
 */
@DisplayName("Dependency Resolution Integration Test")
class DependencyResolutionIntegrationTest {

    private ReleaseBuilder releaseBuilder;
    private DefaultLockfileGenerator lockfileGenerator;

    @BeforeEach
    void setUp() {
        releaseBuilder = new DefaultReleaseBuilder();
        lockfileGenerator = new DefaultLockfileGenerator();
    }

    @Nested
    @DisplayName("Flow with Skill Dependencies")
    class FlowWithSkillDependencies {

        @Test
        @DisplayName("should build flow with bundled skills")
        void shouldBuildFlowWithBundledSkills() {
            // Given: A flow that depends on skill X, and skill X is bundled
            FlowIr flowIr = ReleaseTestFixtures.createCompleteFlowIr("dep-flow", "1.0.0", 2, 1);
            Map<SkillId, SkillIr> bundledSkills = ReleaseTestFixtures.createSkillsMap("skill-0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, Map.of(), bundledSkills, sourceInfo);

            // Then
            assertEquals(1, pkg.skillCount());
            assertTrue(pkg.getSkill(SkillId.of("skill-0")).isPresent());
        }

        @Test
        @DisplayName("should include skill dependencies in resolved skills map")
        void shouldIncludeSkillDependenciesInResolvedSkillsMap() {
            // Given
            SkillId skillId = SkillId.of("required-skill");
            Sha256 skillChecksum = Sha256.of("skill-checksum-123");

            Map<SkillId, Sha256> resolvedSkills = new HashMap<>();
            resolvedSkills.put(skillId, skillChecksum);

            FlowIr flowIr = FlowIr.builder()
                .flowId(ru.hgd.sdlc.compiler.domain.model.authored.FlowId.of("flow-with-deps"))
                .flowVersion(ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion.of(1, 0, 0))
                .metadata(ru.hgd.sdlc.compiler.domain.model.ir.IrMetadata.create(
                    Sha256.of("pkg-checksum"),
                    Sha256.of("ir-checksum"),
                    "1.0.0"
                ))
                .resolvedSkills(resolvedSkills)
                .build();

            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Then
            assertTrue(pkg.flowIr().resolvedSkills().containsKey(skillId));
            assertEquals(skillChecksum, pkg.flowIr().resolvedSkills().get(skillId));
        }

        @Test
        @DisplayName("should handle multiple skill dependencies")
        void shouldHandleMultipleSkillDependencies() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createCompleteFlowIr("multi-dep-flow", "1.0.0", 1, 3);
            Map<SkillId, SkillIr> skills = new HashMap<>();
            skills.put(SkillId.of("skill-0"), ReleaseTestFixtures.createSimpleSkillIr("skill-0", "1.0.0"));
            skills.put(SkillId.of("skill-1"), ReleaseTestFixtures.createSimpleSkillIr("skill-1", "1.0.0"));
            skills.put(SkillId.of("skill-2"), ReleaseTestFixtures.createSimpleSkillIr("skill-2", "1.0.0"));

            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, Map.of(), skills, sourceInfo);

            // Then
            assertEquals(3, pkg.skillCount());
        }
    }

    @Nested
    @DisplayName("Lockfile Dependency Tracking")
    class LockfileDependencyTracking {

        @Test
        @DisplayName("should generate lockfile with dependency entries")
        void shouldGenerateLockfileWithDependencyEntries() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createCompleteFlowIr("lockfile-dep-flow", "1.0.0", 2, 2);
            Map<SkillId, SkillIr> skills = ReleaseTestFixtures.createSkillsMap("skill-0", "skill-1");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, Map.of(), skills, sourceInfo);

            // When
            Lockfile lockfile = lockfileGenerator.generate(List.of(pkg));

            // Then
            assertNotNull(lockfile);
            assertEquals(1, lockfile.entries().size());

            LockfileEntry entry = lockfile.entries().get(0);
            assertNotNull(entry.releaseId());
            assertNotNull(entry.irChecksum());
            assertNotNull(entry.packageChecksum());
        }

        @Test
        @DisplayName("should track flow dependencies in lockfile")
        void shouldTrackFlowDependenciesInLockfile() {
            // Given: Two packages where one depends on the other
            FlowIr flowA = ReleaseTestFixtures.createSimpleFlowIr("flow-a", "1.0.0");
            FlowIr flowB = ReleaseTestFixtures.createSimpleFlowIr("flow-b", "1.0.0");

            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            ReleasePackage pkgA = releaseBuilder.build(flowA, sourceInfo);
            ReleasePackage pkgB = releaseBuilder.build(flowB, sourceInfo);

            // When
            Lockfile lockfile = lockfileGenerator.generate(List.of(pkgA, pkgB));

            // Then
            assertEquals(2, lockfile.entries().size());
        }

        @Test
        @DisplayName("should capture IR checksums for integrity verification")
        void shouldCaptureIrChecksumsForIntegrityVerification() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("checksum-flow", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // When
            Lockfile lockfile = lockfileGenerator.generate(List.of(pkg));

            // Then
            LockfileEntry entry = lockfile.entries().get(0);
            String expectedChecksum = pkg.flowIr().metadata().irChecksum().hexValue();
            assertEquals(expectedChecksum, entry.irChecksum());
        }
    }

    @Nested
    @DisplayName("Skill Resolution")
    class SkillResolution {

        @Test
        @DisplayName("should resolve skill by ID")
        void shouldResolveSkillById() {
            // Given
            String skillName = "resolved-skill";
            SkillIr skill = ReleaseTestFixtures.createSimpleSkillIr(skillName, "1.0.0");
            Map<SkillId, SkillIr> skills = Map.of(SkillId.of(skillName), skill);

            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("skill-resolution-flow", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, Map.of(), skills, sourceInfo);

            // Then
            assertTrue(pkg.getSkill(SkillId.of(skillName)).isPresent());
            assertEquals(skill, pkg.getSkill(SkillId.of(skillName)).get());
        }

        @Test
        @DisplayName("should handle missing skill gracefully")
        void shouldHandleMissingSkillGracefully() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("no-skill-flow", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Then
            assertEquals(0, pkg.skillCount());
            assertTrue(pkg.getSkill(SkillId.of("nonexistent")).isEmpty());
        }

        @Test
        @DisplayName("should preserve skill version in package")
        void shouldPreserveSkillVersionInPackage() {
            // Given
            SkillIr skill = ReleaseTestFixtures.createSimpleSkillIr("versioned-skill", "2.1.0");
            Map<SkillId, SkillIr> skills = Map.of(SkillId.of("versioned-skill"), skill);

            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("skill-version-flow", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, Map.of(), skills, sourceInfo);

            // Then
            SkillIr packagedSkill = pkg.getSkill(SkillId.of("versioned-skill")).orElseThrow();
            assertEquals("2.1.0", packagedSkill.skillVersion().toString());
        }
    }

    @Nested
    @DisplayName("Version Constraints")
    class VersionConstraints {

        @Test
        @DisplayName("should preserve semantic version in release ID")
        void shouldPreserveSemanticVersionInReleaseId() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("versioned-flow", "2.3.4");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Then
            assertEquals("versioned-flow@2.3.4", pkg.id().canonicalId());
            assertEquals(2, pkg.version().major());
            assertEquals(3, pkg.version().minor());
            assertEquals(4, pkg.version().patch());
        }

        @Test
        @DisplayName("should handle pre-release versions")
        void shouldHandlePreReleaseVersions() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("prerelease-flow", "1.0.0-alpha.1");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Then
            assertTrue(pkg.id().canonicalId().contains("1.0.0-alpha.1"));
        }
    }

    @Nested
    @DisplayName("Dependency Graph")
    class DependencyGraph {

        @Test
        @DisplayName("should create dependency graph from package")
        void shouldCreateDependencyGraphFromPackage() {
            // Given - create a graph with root flow-a
            var rootId = ru.hgd.sdlc.registry.domain.model.release.ReleaseId.parse("flow-a@1.0.0");
            ru.hgd.sdlc.registry.domain.model.dependency.DependencyGraph graph =
                ru.hgd.sdlc.registry.domain.model.dependency.DependencyGraph.of(rootId, java.util.Map.of());

            // When
            var skillId = ru.hgd.sdlc.registry.domain.model.release.ReleaseId.parse("skill-x@1.0.0");
            graph = graph.addNode(skillId);
            graph = graph.addDependency(rootId, skillId);

            // Then
            assertEquals(2, graph.nodeCount());
            assertEquals(1, graph.edgeCount());
        }

        @Test
        @DisplayName("should detect circular dependencies")
        void shouldDetectCircularDependencies() {
            // Given - create a graph with root nodeA
            var nodeA = ru.hgd.sdlc.registry.domain.model.release.ReleaseId.parse("a@1.0.0");
            var nodeB = ru.hgd.sdlc.registry.domain.model.release.ReleaseId.parse("b@1.0.0");
            ru.hgd.sdlc.registry.domain.model.dependency.DependencyGraph graph =
                ru.hgd.sdlc.registry.domain.model.dependency.DependencyGraph.of(nodeA, java.util.Map.of());

            // When
            graph = graph.addNode(nodeB);
            graph = graph.addDependency(nodeA, nodeB);
            graph = graph.addDependency(nodeB, nodeA);

            // Then
            assertTrue(graph.hasCycle());
        }

        @Test
        @DisplayName("should handle empty dependency graph")
        void shouldHandleEmptyDependencyGraph() {
            // Given - create an empty graph (only root, no dependencies)
            var rootId = ru.hgd.sdlc.registry.domain.model.release.ReleaseId.parse("empty-flow@0.0.0");
            ru.hgd.sdlc.registry.domain.model.dependency.DependencyGraph graph =
                ru.hgd.sdlc.registry.domain.model.dependency.DependencyGraph.of(rootId, java.util.Map.of());

            // Then - has 1 node (root) and 0 edges
            assertEquals(1, graph.nodeCount());
            assertEquals(0, graph.edgeCount());
            assertFalse(graph.hasCycle());
            assertTrue(graph.isEmpty());
        }
    }
}
