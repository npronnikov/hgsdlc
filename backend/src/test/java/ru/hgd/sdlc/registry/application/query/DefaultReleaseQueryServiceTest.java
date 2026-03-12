package ru.hgd.sdlc.registry.application.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.IrMetadata;
import ru.hgd.sdlc.registry.application.resolver.PackageResolver;
import ru.hgd.sdlc.registry.application.resolver.ReleaseNotFoundException;
import ru.hgd.sdlc.registry.domain.model.provenance.BuilderInfo;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.release.ChecksumManifest;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseMetadata;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;
import ru.hgd.sdlc.registry.domain.release.ReleaseRecord;
import ru.hgd.sdlc.registry.domain.repository.ReleaseRepository;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultReleaseQueryService")
class DefaultReleaseQueryServiceTest {

    @Mock
    private ReleaseRepository releaseRepository;

    @Mock
    private PackageResolver packageResolver;

    private DefaultReleaseQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new DefaultReleaseQueryService(releaseRepository, packageResolver);
    }

    private static ReleaseId releaseId(String flowId, String version) {
        return ReleaseId.of(FlowId.of(flowId), ReleaseVersion.of(version));
    }

    private ReleasePackage createPackage(ReleaseId id) {
        Sha256 packageChecksum = Sha256.of("package-content");
        Sha256 irChecksum = Sha256.of("ir-content");

        FlowIr flowIr = FlowIr.builder()
            .flowId(id.flowId())
            .flowVersion(SemanticVersion.of(id.version().formatted()))
            .metadata(IrMetadata.create(packageChecksum, irChecksum, "1.0.0"))
            .build();

        ReleaseMetadata metadata = ReleaseMetadata.builder()
            .displayName("Test Flow " + id.flowId().value())
            .description("Test flow description")
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
            .entry("flow.ir.json", Sha256Hash.of("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .build();

        return ReleasePackage.builder()
            .id(id)
            .metadata(metadata)
            .flowIr(flowIr)
            .provenance(provenance)
            .checksums(checksums)
            .build();
    }

    private ReleaseRecord createRecord(ReleaseId id) {
        return ReleaseRecord.builder()
            .id(1L)
            .releaseId(id)
            .packageHash(Sha256Hash.of("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .provenanceJson("{}")
            .createdAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("should find release by ID")
        void shouldFindReleaseById() {
            ReleaseId id = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(id);

            when(packageResolver.resolveExact(id)).thenReturn(pkg);

            Optional<ReleasePackage> result = queryService.findById(id);

            assertTrue(result.isPresent());
            assertEquals(pkg, result.get());
        }

        @Test
        @DisplayName("should return empty when release not found")
        void shouldReturnEmptyWhenNotFound() {
            ReleaseId id = releaseId("test-flow", "1.0.0");

            when(packageResolver.resolveExact(id)).thenThrow(new ReleaseNotFoundException(id));

            Optional<ReleasePackage> result = queryService.findById(id);

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("findLatest()")
    class FindLatest {

        @Test
        @DisplayName("should find latest version of a flow")
        void shouldFindLatestVersion() {
            ReleaseId id = releaseId("test-flow", "2.0.0");
            ReleasePackage pkg = createPackage(id);
            ReleaseRecord record = createRecord(id);

            when(releaseRepository.findLatestVersion("test-flow")).thenReturn(Optional.of(record));
            when(packageResolver.resolveExact(id)).thenReturn(pkg);

            Optional<ReleasePackage> result = queryService.findLatest("test-flow");

            assertTrue(result.isPresent());
            assertEquals(pkg, result.get());
        }

        @Test
        @DisplayName("should return empty when no versions exist")
        void shouldReturnEmptyWhenNoVersions() {
            when(releaseRepository.findLatestVersion("test-flow")).thenReturn(Optional.empty());

            Optional<ReleasePackage> result = queryService.findLatest("test-flow");

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("listVersions()")
    class ListVersions {

        @Test
        @DisplayName("should list all versions of a flow")
        void shouldListAllVersions() {
            ReleaseId id1 = releaseId("test-flow", "1.0.0");
            ReleaseId id2 = releaseId("test-flow", "2.0.0");
            ReleasePackage pkg1 = createPackage(id1);
            ReleasePackage pkg2 = createPackage(id2);
            ReleaseRecord record1 = createRecord(id1);
            ReleaseRecord record2 = createRecord(id2);

            when(releaseRepository.listVersions("test-flow")).thenReturn(List.of(record2, record1));
            when(packageResolver.resolveExact(id1)).thenReturn(pkg1);
            when(packageResolver.resolveExact(id2)).thenReturn(pkg2);

            List<ReleaseMetadata> result = queryService.listVersions("test-flow");

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should return empty list when no versions exist")
        void shouldReturnEmptyWhenNoVersions() {
            when(releaseRepository.listVersions("test-flow")).thenReturn(List.of());

            List<ReleaseMetadata> result = queryService.listVersions("test-flow");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("search()")
    class Search {

        @Test
        @DisplayName("should throw UnsupportedOperationException until repository supports search")
        void shouldThrowUnsupportedOperationExceptionForTextQuery() {
            ReleaseQuery query = ReleaseQuery.builder()
                .text("test")
                .limit(10)
                .offset(0)
                .build();

            // Search is not yet supported - requires repository implementation
            assertThrows(UnsupportedOperationException.class, () ->
                queryService.search(query)
            );
        }

        @Test
        @DisplayName("should throw UnsupportedOperationException for pagination query")
        void shouldThrowUnsupportedOperationExceptionForPagination() {
            ReleaseQuery query = ReleaseQuery.builder()
                .limit(5)
                .offset(10)
                .build();

            // Search is not yet supported - requires repository implementation
            assertThrows(UnsupportedOperationException.class, () ->
                queryService.search(query)
            );
        }

        @Test
        @DisplayName("should throw UnsupportedOperationException for offset beyond data")
        void shouldThrowUnsupportedOperationExceptionForOffsetBeyondData() {
            ReleaseQuery query = ReleaseQuery.builder()
                .limit(10)
                .offset(1000)
                .build();

            // Search is not yet supported - requires repository implementation
            assertThrows(UnsupportedOperationException.class, () ->
                queryService.search(query)
            );
        }
    }

    @Nested
    @DisplayName("getDependencyTree()")
    class GetDependencyTree {

        @Test
        @DisplayName("should build dependency tree for release")
        void shouldBuildDependencyTree() {
            ReleaseId id = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(id);

            when(packageResolver.resolveExact(id)).thenReturn(pkg);
            when(packageResolver.resolve(id)).thenReturn(List.of(pkg));

            Optional<DependencyTree> result = queryService.getDependencyTree(id);

            assertTrue(result.isPresent());
            DependencyTree tree = result.get();
            assertEquals(id, tree.root());
            assertEquals(1, tree.nodeCount());
            assertTrue(tree.getNode(id).isPresent());
        }

        @Test
        @DisplayName("should return empty when release not found")
        void shouldReturnEmptyWhenNotFound() {
            ReleaseId id = releaseId("test-flow", "1.0.0");

            when(packageResolver.resolveExact(id)).thenThrow(new ReleaseNotFoundException(id));

            Optional<DependencyTree> result = queryService.getDependencyTree(id);

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should contain correct node information")
        void shouldContainCorrectNodeInformation() {
            ReleaseId id = releaseId("test-flow", "1.0.0");
            ReleasePackage pkg = createPackage(id);

            when(packageResolver.resolveExact(id)).thenReturn(pkg);
            when(packageResolver.resolve(id)).thenReturn(List.of(pkg));

            Optional<DependencyTree> result = queryService.getDependencyTree(id);

            assertTrue(result.isPresent());
            DependencyTree tree = result.get();

            Optional<DependencyNode> node = tree.getNode(id);
            assertTrue(node.isPresent());
            assertEquals(id, node.get().id());
            assertEquals("flow", node.get().type());
            assertEquals("Test Flow test-flow", node.get().name());
        }
    }

    @Nested
    @DisplayName("handle empty results gracefully")
    class HandleEmptyResults {

        @Test
        @DisplayName("should handle null package from resolver")
        void shouldHandleNullPackage() {
            ReleaseId id = releaseId("test-flow", "1.0.0");

            when(packageResolver.resolveExact(id)).thenReturn(null);

            Optional<ReleasePackage> result = queryService.findById(id);

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should handle empty repository results")
        void shouldHandleEmptyRepositoryResults() {
            when(releaseRepository.listVersions("non-existent")).thenReturn(List.of());

            List<ReleaseMetadata> result = queryService.listVersions("non-existent");

            assertTrue(result.isEmpty());
        }
    }
}
