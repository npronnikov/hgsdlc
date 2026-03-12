package ru.hgd.sdlc.registry.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.IrMetadata;
import ru.hgd.sdlc.registry.application.lockfile.Lockfile;
import ru.hgd.sdlc.registry.application.lockfile.LockfileEntry;
import ru.hgd.sdlc.registry.application.lockfile.LockfileEntryType;
import ru.hgd.sdlc.registry.application.lockfile.LockfileGenerator;
import ru.hgd.sdlc.registry.application.lockfile.LockfileVersion;
import ru.hgd.sdlc.registry.application.query.ReleaseQuery;
import ru.hgd.sdlc.registry.application.query.ReleaseQueryService;
import ru.hgd.sdlc.registry.application.resolver.PackageResolver;
import ru.hgd.sdlc.registry.application.verifier.ProvenanceVerifier;
import ru.hgd.sdlc.registry.application.verifier.VerificationIssue;
import ru.hgd.sdlc.registry.application.verifier.VerificationResult;
import ru.hgd.sdlc.registry.application.verifier.VerificationSeverity;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReleaseControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private ReleaseQueryService queryService;

    @Mock
    private PackageResolver packageResolver;

    @Mock
    private LockfileGenerator lockfileGenerator;

    @Mock
    private ProvenanceVerifier provenanceVerifier;

    @InjectMocks
    private ReleaseController releaseController;

    private ReleasePackage testPackage;
    private ReleaseId testReleaseId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        mockMvc = MockMvcBuilders.standaloneSetup(releaseController)
                .setControllerAdvice(new ReleaseExceptionHandler())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new ByteArrayHttpMessageConverter()
                )
                .build();

        testReleaseId = ReleaseId.of(FlowId.of("test-flow"), ReleaseVersion.of("1.0.0"));
        testPackage = createTestPackage(testReleaseId);
    }

    @Nested
    @DisplayName("GET /api/v1/releases/{flowId}")
    class ListReleases {

        @Test
        @DisplayName("should return list of releases")
        void shouldReturnListOfReleases() throws Exception {
            List<ReleaseMetadata> versions = List.of(
                    createTestMetadata("v1.0.0"),
                    createTestMetadata("v0.9.0")
            );
            when(queryService.listVersions("test-flow")).thenReturn(versions);

            mockMvc.perform(get("/api/v1/releases/test-flow")
                            .param("offset", "0")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.offset").value(0))
                    .andExpect(jsonPath("$.limit").value(10))
                    .andExpect(jsonPath("$.releases").isArray());
        }

        @Test
        @DisplayName("should return empty list for unknown flow")
        void shouldReturnEmptyListForUnknownFlow() throws Exception {
            when(queryService.listVersions("unknown-flow")).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/releases/unknown-flow"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0))
                    .andExpect(jsonPath("$.releases").isEmpty());
        }

        @Test
        @DisplayName("should apply pagination")
        void shouldApplyPagination() throws Exception {
            List<ReleaseMetadata> versions = List.of(
                    createTestMetadata("v1.0.0"),
                    createTestMetadata("v0.9.0"),
                    createTestMetadata("v0.8.0")
            );
            when(queryService.listVersions("test-flow")).thenReturn(versions);

            mockMvc.perform(get("/api/v1/releases/test-flow")
                            .param("offset", "1")
                            .param("limit", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(3));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/releases/{flowId}/latest")
    class GetLatestRelease {

        @Test
        @DisplayName("should return latest release")
        void shouldReturnLatestRelease() throws Exception {
            when(queryService.findLatest("test-flow")).thenReturn(Optional.of(testPackage));

            mockMvc.perform(get("/api/v1/releases/test-flow/latest"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.releaseId").value("test-flow@1.0.0"))
                    .andExpect(jsonPath("$.metadata.displayName").value("Test Flow"))
                    .andExpect(jsonPath("$.provenance.signed").value(false))
                    .andExpect(jsonPath("$.phaseCount").value(0))
                    .andExpect(jsonPath("$.skillCount").value(0));
        }

        @Test
        @DisplayName("should return 404 when no releases found")
        void shouldReturn404WhenNoReleasesFound() throws Exception {
            when(queryService.findLatest("unknown-flow")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/releases/unknown-flow/latest"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("RELEASE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/releases/{flowId}/{version}")
    class GetReleaseByVersion {

        @Test
        @DisplayName("should return specific release version")
        void shouldReturnSpecificReleaseVersion() throws Exception {
            when(queryService.findById(any(ReleaseId.class))).thenReturn(Optional.of(testPackage));

            mockMvc.perform(get("/api/v1/releases/test-flow/1.0.0"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.releaseId").value("test-flow@1.0.0"))
                    .andExpect(jsonPath("$.metadata.author").value("test@example.com"));
        }

        @Test
        @DisplayName("should return 404 for unknown release")
        void shouldReturn404ForUnknownRelease() throws Exception {
            when(queryService.findById(any(ReleaseId.class))).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/releases/unknown-flow/1.0.0"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("RELEASE_NOT_FOUND"));
        }

        @Test
        @DisplayName("should return 400 for invalid version format")
        void shouldReturn400ForInvalidVersionFormat() throws Exception {
            mockMvc.perform(get("/api/v1/releases/test-flow/invalid-version"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/releases/{flowId}/{version}/download")
    class DownloadRelease {

        @Test
        @DisplayName("should download release package")
        void shouldDownloadReleasePackage() throws Exception {
            when(queryService.findById(any(ReleaseId.class))).thenReturn(Optional.of(testPackage));

            mockMvc.perform(get("/api/v1/releases/test-flow/1.0.0/download"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"test-flow-1.0.0.zip\""))
                    .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
        }

        @Test
        @DisplayName("should return 404 for unknown release download")
        void shouldReturn404ForUnknownReleaseDownload() throws Exception {
            when(queryService.findById(any(ReleaseId.class))).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/releases/unknown-flow/1.0.0/download"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/releases/{flowId}/{version}/lockfile")
    class GetLockfile {

        @Test
        @DisplayName("should return lockfile for release")
        void shouldReturnLockfileForRelease() throws Exception {
            Lockfile lockfile = createTestLockfile();
            when(packageResolver.resolve(any(ReleaseId.class))).thenReturn(List.of(testPackage));
            when(lockfileGenerator.generate(any())).thenReturn(lockfile);

            mockMvc.perform(get("/api/v1/releases/test-flow/1.0.0/lockfile"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.flowId").value("test-flow"))
                    .andExpect(jsonPath("$.flowVersion").value("1.0.0"))
                    .andExpect(jsonPath("$.entries").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/releases/{flowId}/{version}/provenance")
    class GetProvenance {

        @Test
        @DisplayName("should return provenance for release")
        void shouldReturnProvenanceForRelease() throws Exception {
            when(queryService.findById(any(ReleaseId.class))).thenReturn(Optional.of(testPackage));

            mockMvc.perform(get("/api/v1/releases/test-flow/1.0.0/provenance"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.releaseId").value("test-flow@1.0.0"))
                    .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/test/test"))
                    .andExpect(jsonPath("$.commitSha").value("0123456789abcdef0123456789abcdef01234567"))
                    .andExpect(jsonPath("$.signed").value(false));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/releases/{flowId}/{version}/verify")
    class VerifyRelease {

        @Test
        @DisplayName("should return valid verification result")
        void shouldReturnValidVerificationResult() throws Exception {
            VerificationResult validResult = VerificationResult.valid();
            when(queryService.findById(any(ReleaseId.class))).thenReturn(Optional.of(testPackage));
            when(provenanceVerifier.verify(any(ReleasePackage.class))).thenReturn(validResult);

            mockMvc.perform(get("/api/v1/releases/test-flow/1.0.0/verify"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.issues").isArray());
        }

        @Test
        @DisplayName("should return issues when verification fails")
        void shouldReturnIssuesWhenVerificationFails() throws Exception {
            VerificationResult invalidResult = VerificationResult.invalid(
                    List.of(VerificationIssue.error("INVALID_CHECKSUM", "Checksum mismatch"))
            );
            when(queryService.findById(any(ReleaseId.class))).thenReturn(Optional.of(testPackage));
            when(provenanceVerifier.verify(any(ReleasePackage.class))).thenReturn(invalidResult);

            mockMvc.perform(get("/api/v1/releases/test-flow/1.0.0/verify"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.issues[0].severity").value("ERROR"))
                    .andExpect(jsonPath("$.issues[0].code").value("INVALID_CHECKSUM"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/releases/search")
    class SearchReleases {

        @Test
        @DisplayName("should search releases with query")
        void shouldSearchReleasesWithQuery() throws Exception {
            List<ReleaseMetadata> results = List.of(createTestMetadata("v1.0.0"));
            when(queryService.search(any(ReleaseQuery.class))).thenReturn(results);

            mockMvc.perform(get("/api/v1/releases/search")
                            .param("q", "test")
                            .param("offset", "0")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.results").isArray())
                    .andExpect(jsonPath("$.offset").value(0))
                    .andExpect(jsonPath("$.limit").value(10));
        }

        @Test
        @DisplayName("should search releases by author")
        void shouldSearchReleasesByAuthor() throws Exception {
            List<ReleaseMetadata> results = List.of(createTestMetadata("v1.0.0"));
            when(queryService.search(any(ReleaseQuery.class))).thenReturn(results);

            mockMvc.perform(get("/api/v1/releases/search")
                            .param("author", "test@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results").isArray());
        }

        @Test
        @DisplayName("should return empty results for no matches")
        void shouldReturnEmptyResultsForNoMatches() throws Exception {
            when(queryService.search(any(ReleaseQuery.class))).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/releases/search")
                            .param("q", "nonexistent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results").isEmpty())
                    .andExpect(jsonPath("$.total").value(0));
        }
    }

    // --- Test fixtures ---

    private ReleasePackage createTestPackage(ReleaseId releaseId) {
        Provenance provenance = Provenance.builder()
                .releaseId(releaseId)
                .repositoryUrl("https://github.com/test/test")
                .commitSha("0123456789abcdef0123456789abcdef01234567")
                .buildTimestamp(Instant.now())
                .commitAuthor("test@example.com")
                .committedAt(Instant.now())
                .builderId("test-builder")
                .builder(BuilderInfo.of("sdlc-registry", "1.0.0"))
                .compilerVersion("1.0.0")
                .irChecksum(Sha256Hash.of("a".repeat(64)))
                .packageChecksum(Sha256Hash.of("b".repeat(64)))
                .build();

        ReleaseMetadata metadata = ReleaseMetadata.builder()
                .displayName("Test Flow")
                .description("A test flow for unit testing")
                .author("test@example.com")
                .createdAt(Instant.now())
                .gitCommit("0123456789abcdef0123456789abcdef01234567")
                .build();

        ChecksumManifest checksums = ChecksumManifest.builder()
                .build();

        FlowIr flowIr = createTestFlowIr(releaseId);

        return ReleasePackage.builder()
                .id(releaseId)
                .metadata(metadata)
                .flowIr(flowIr)
                .provenance(provenance)
                .checksums(checksums)
                .build();
    }

    private FlowIr createTestFlowIr(ReleaseId releaseId) {
        Sha256 packageChecksum = Sha256.of("test-package-content");
        Sha256 irChecksum = Sha256.of("test-ir-content");

        IrMetadata irMetadata = IrMetadata.builder()
                .packageChecksum(packageChecksum)
                .irChecksum(irChecksum)
                .compiledAt(Instant.now())
                .compilerVersion("1.0.0")
                .build();

        return FlowIr.builder()
                .flowId(releaseId.flowId())
                .flowVersion(SemanticVersion.of(releaseId.version().formatted()))
                .metadata(irMetadata)
                .build();
    }

    private ReleaseMetadata createTestMetadata(String version) {
        return ReleaseMetadata.builder()
                .displayName("Test Flow " + version)
                .description("Test description")
                .author("test@example.com")
                .createdAt(Instant.now())
                .gitCommit("0123456789abcdef0123456789abcdef01234567")
                .gitTag(version)
                .build();
    }

    private Lockfile createTestLockfile() {
        LockfileEntry entry = LockfileEntry.builder()
                .releaseId("test-flow@1.0.0")
                .type(LockfileEntryType.FLOW)
                .irChecksum("a".repeat(64))
                .packageChecksum("b".repeat(64))
                .build();

        return Lockfile.builder()
                .version(LockfileVersion.V1)
                .generatedAt(Instant.now())
                .flowId("test-flow")
                .flowVersion("1.0.0")
                .entry(entry)
                .checksum("c".repeat(64))
                .build();
    }
}
