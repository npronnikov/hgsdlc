package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.registry.application.lockfile.Lockfile;
import ru.hgd.sdlc.registry.application.lockfile.LockfileEntry;
import ru.hgd.sdlc.registry.application.lockfile.LockfileGenerator;
import ru.hgd.sdlc.registry.application.query.ReleaseQuery;
import ru.hgd.sdlc.registry.application.query.ReleaseQueryService;
import ru.hgd.sdlc.registry.application.resolver.PackageResolver;
import ru.hgd.sdlc.registry.application.resolver.ReleaseNotFoundException;
import ru.hgd.sdlc.registry.application.verifier.ProvenanceVerifier;
import ru.hgd.sdlc.registry.application.verifier.VerificationIssue;
import ru.hgd.sdlc.registry.application.verifier.VerificationResult;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.provenance.ProvenanceSignature;
import ru.hgd.sdlc.registry.domain.model.release.ChecksumManifest;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseMetadata;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for release operations.
 * Provides endpoints for querying, downloading, and verifying releases.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/releases")
@RequiredArgsConstructor
@ConditionalOnBean({ReleaseQueryService.class, PackageResolver.class, LockfileGenerator.class, ProvenanceVerifier.class})
public class ReleaseController {

    private final ReleaseQueryService queryService;
    private final PackageResolver packageResolver;
    private final LockfileGenerator lockfileGenerator;
    private final ProvenanceVerifier provenanceVerifier;

    @GetMapping("/{flowId}")
    public ResponseEntity<ReleaseListResponse> listReleases(
            @PathVariable String flowId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.debug("Listing releases for flowId: {}", flowId);

        List<ReleaseMetadata> versions = queryService.listVersions(flowId);

        List<ReleaseSummaryResponse> summaries = versions.stream()
                .skip(offset)
                .limit(limit)
                .map(metadata -> toSummaryResponse(flowId, metadata))
                .toList();

        ReleaseListResponse response = ReleaseListResponse.builder()
                .releases(summaries)
                .total(versions.size())
                .offset(offset)
                .limit(limit)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{flowId}/latest")
    public ResponseEntity<ReleasePackageResponse> getLatestRelease(
            @PathVariable String flowId
    ) {
        log.debug("Getting latest release for flowId: {}", flowId);

        ReleasePackage pkg = queryService.findLatest(flowId)
                .orElseThrow(() -> new ReleaseNotFoundException(null,
                        "No releases found for flow: " + flowId));

        return ResponseEntity.ok(toPackageResponse(pkg));
    }

    @GetMapping("/{flowId}/{version}")
    public ResponseEntity<ReleasePackageResponse> getRelease(
            @PathVariable String flowId,
            @PathVariable String version
    ) {
        log.debug("Getting release: {}@{}", flowId, version);

        ReleaseId releaseId = ReleaseId.of(
                ru.hgd.sdlc.compiler.domain.model.authored.FlowId.of(flowId),
                ReleaseVersion.of(version)
        );

        ReleasePackage pkg = queryService.findById(releaseId)
                .orElseThrow(() -> new ReleaseNotFoundException(releaseId));

        return ResponseEntity.ok(toPackageResponse(pkg));
    }

    @GetMapping("/{flowId}/{version}/download")
    public ResponseEntity<byte[]> downloadRelease(
            @PathVariable String flowId,
            @PathVariable String version
    ) {
        log.debug("Downloading release: {}@{}", flowId, version);

        ReleaseId releaseId = ReleaseId.of(
                ru.hgd.sdlc.compiler.domain.model.authored.FlowId.of(flowId),
                ReleaseVersion.of(version)
        );

        ReleasePackage pkg = queryService.findById(releaseId)
                .orElseThrow(() -> new ReleaseNotFoundException(releaseId));

        // For now, return a placeholder response
        // In a real implementation, this would serialize the package to bytes
        byte[] content = serializePackage(pkg);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + flowId + "-" + version + ".zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    @GetMapping("/{flowId}/{version}/lockfile")
    public ResponseEntity<LockfileResponse> getLockfile(
            @PathVariable String flowId,
            @PathVariable String version
    ) {
        log.debug("Getting lockfile for release: {}@{}", flowId, version);

        ReleaseId releaseId = ReleaseId.of(
                ru.hgd.sdlc.compiler.domain.model.authored.FlowId.of(flowId),
                ReleaseVersion.of(version)
        );

        // Resolve the package and its dependencies
        List<ReleasePackage> packages = packageResolver.resolve(releaseId);

        // Generate lockfile
        Lockfile lockfile = lockfileGenerator.generate(packages);

        return ResponseEntity.ok(toLockfileResponse(lockfile));
    }

    @GetMapping("/{flowId}/{version}/provenance")
    public ResponseEntity<ProvenanceResponse> getProvenance(
            @PathVariable String flowId,
            @PathVariable String version
    ) {
        log.debug("Getting provenance for release: {}@{}", flowId, version);

        ReleaseId releaseId = ReleaseId.of(
                ru.hgd.sdlc.compiler.domain.model.authored.FlowId.of(flowId),
                ReleaseVersion.of(version)
        );

        ReleasePackage pkg = queryService.findById(releaseId)
                .orElseThrow(() -> new ReleaseNotFoundException(releaseId));

        return ResponseEntity.ok(toProvenanceResponse(pkg.provenance()));
    }

    @GetMapping("/{flowId}/{version}/verify")
    public ResponseEntity<VerificationResultResponse> verifyRelease(
            @PathVariable String flowId,
            @PathVariable String version
    ) {
        log.debug("Verifying release: {}@{}", flowId, version);

        ReleaseId releaseId = ReleaseId.of(
                ru.hgd.sdlc.compiler.domain.model.authored.FlowId.of(flowId),
                ReleaseVersion.of(version)
        );

        ReleasePackage pkg = queryService.findById(releaseId)
                .orElseThrow(() -> new ReleaseNotFoundException(releaseId));

        VerificationResult result = provenanceVerifier.verify(pkg);

        return ResponseEntity.ok(toVerificationResultResponse(result));
    }

    @GetMapping("/search")
    public ResponseEntity<ReleaseSearchResponse> searchReleases(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String author,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.debug("Searching releases: q={}, author={}", q, author);

        ReleaseQuery query = ReleaseQuery.builder()
                .text(q)
                .author(author)
                .offset(offset)
                .limit(limit)
                .build();

        List<ReleaseMetadata> results = queryService.search(query);

        List<ReleaseSummaryResponse> summaries = results.stream()
                .map(metadata -> {
                    // Extract flowId from git commit (placeholder logic)
                    // In real implementation, metadata would include flowId
                    String flowId = extractFlowId(metadata);
                    return toSummaryResponse(flowId, metadata);
                })
                .toList();

        ReleaseSearchResponse response = ReleaseSearchResponse.builder()
                .results(summaries)
                .total(summaries.size())
                .offset(offset)
                .limit(limit)
                .build();

        return ResponseEntity.ok(response);
    }

    // --- Helper methods ---

    private ReleaseSummaryResponse toSummaryResponse(String flowId, ReleaseMetadata metadata) {
        return ReleaseSummaryResponse.builder()
                .flowId(flowId)
                .version(extractVersion(metadata))
                .displayName(metadata.displayName())
                .author(metadata.author())
                .createdAt(metadata.createdAt())
                .releaseId(flowId + "@" + extractVersion(metadata))
                .build();
    }

    private ReleasePackageResponse toPackageResponse(ReleasePackage pkg) {
        Provenance prov = pkg.provenance();

        return ReleasePackageResponse.builder()
                .releaseId(pkg.id().canonicalId())
                .metadata(toMetadataResponse(pkg.metadata()))
                .provenance(toProvenanceSummaryResponse(prov))
                .checksums(toChecksumsResponse(pkg.checksums(), prov))
                .phaseCount(pkg.phaseCount())
                .skillCount(pkg.skillCount())
                .build();
    }

    private ReleaseMetadataResponse toMetadataResponse(ReleaseMetadata metadata) {
        return ReleaseMetadataResponse.builder()
                .displayName(metadata.displayName())
                .description(metadata.description().orElse(null))
                .author(metadata.author())
                .createdAt(metadata.createdAt())
                .gitCommit(metadata.gitCommit())
                .gitTag(metadata.gitTag().orElse(null))
                .build();
    }

    private ProvenanceSummaryResponse toProvenanceSummaryResponse(Provenance provenance) {
        return ProvenanceSummaryResponse.builder()
                .signed(provenance.isSigned())
                .commitSha(provenance.getCommitSha())
                .buildTimestamp(provenance.getBuildTimestamp())
                .commitAuthor(provenance.getCommitAuthor())
                .builderId(provenance.getBuilderId())
                .build();
    }

    private ChecksumsResponse toChecksumsResponse(ChecksumManifest checksums, Provenance provenance) {
        ChecksumsResponse.ChecksumsResponseBuilder builder = ChecksumsResponse.builder()
                .irChecksum(provenance.getIrChecksum().hex())
                .packageChecksum(provenance.getPackageChecksum().hex());

        checksums.entries().forEach((path, hash) -> builder.entry(path, hash.hex()));

        return builder.build();
    }

    private ProvenanceResponse toProvenanceResponse(Provenance prov) {
        Map<String, String> inputs = prov.getInputs() != null ? prov.getInputs() : Collections.emptyMap();
        Map<String, String> environment = prov.getEnvironment() != null ? prov.getEnvironment() : Collections.emptyMap();

        ProvenanceResponse.ProvenanceResponseBuilder builder = ProvenanceResponse.builder()
                .releaseId(prov.getReleaseId().canonicalId())
                .repositoryUrl(prov.getRepositoryUrl())
                .commitSha(prov.getCommitSha())
                .buildTimestamp(prov.getBuildTimestamp())
                .commitAuthor(prov.getCommitAuthor())
                .builderId(prov.getBuilderId())
                .signed(prov.isSigned())
                .gitTag(prov.getGitTag().orElse(null))
                .branch(prov.getBranch().orElse(null))
                .commitMessage(prov.getCommitMessage().orElse(null))
                .compilerVersion(prov.getCompilerVersion())
                .irChecksum(prov.getIrChecksum().hex())
                .packageChecksum(prov.getPackageChecksum().hex())
                .inputs(inputs)
                .environment(environment);

        if (prov.getSignature().isPresent()) {
            ProvenanceSignature sig = prov.getSignature().get();
            builder.signatureAlgorithm(sig.algorithm())
                    .signatureTimestamp(sig.signedAt())
                    .signPublicKey(sig.publicKey());
        }

        return builder.build();
    }

    private LockfileResponse toLockfileResponse(Lockfile lockfile) {
        List<LockfileEntryResponse> entries = lockfile.entries().stream()
                .map(this::toLockfileEntryResponse)
                .toList();

        return LockfileResponse.builder()
                .version(lockfile.version().version())
                .generatedAt(lockfile.generatedAt())
                .flowId(lockfile.flowId())
                .flowVersion(lockfile.flowVersion())
                .entries(entries)
                .checksum(lockfile.checksum())
                .build();
    }

    private LockfileEntryResponse toLockfileEntryResponse(LockfileEntry entry) {
        return LockfileEntryResponse.builder()
                .releaseId(entry.releaseId())
                .type(entry.type().name())
                .irChecksum(entry.irChecksum())
                .packageChecksum(entry.packageChecksum())
                .dependencies(entry.dependencies())
                .build();
    }

    private VerificationResultResponse toVerificationResultResponse(VerificationResult result) {
        List<VerificationIssueResponse> issues = result.getIssuesList().stream()
                .map(this::toVerificationIssueResponse)
                .toList();

        return VerificationResultResponse.builder()
                .valid(result.isValid())
                .issues(issues)
                .build();
    }

    private VerificationIssueResponse toVerificationIssueResponse(VerificationIssue issue) {
        return VerificationIssueResponse.builder()
                .severity(issue.getSeverity().name())
                .code(issue.getCode())
                .message(issue.getMessage())
                .details(issue.getDetailsOptional().orElse(null))
                .build();
    }

    private byte[] serializePackage(ReleasePackage pkg) {
        // Placeholder implementation
        // In a real implementation, this would serialize to ZIP or similar format
        return ("ReleasePackage: " + pkg.id().canonicalId()).getBytes();
    }

    private String extractVersion(ReleaseMetadata metadata) {
        // In a real implementation, metadata would include the version directly
        // For now, extract from git tag if available
        return metadata.gitTag().orElse("0.0.0");
    }

    private String extractFlowId(ReleaseMetadata metadata) {
        // Placeholder implementation
        // In a real implementation, the search result would include flowId
        return "unknown-flow";
    }
}
