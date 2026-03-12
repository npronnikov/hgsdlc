package ru.hgd.sdlc.registry.integration;

import ru.hgd.sdlc.registry.application.builder.SourceInfo;
import ru.hgd.sdlc.registry.domain.model.provenance.BuilderInfo;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Test fixtures for creating test provenance records.
 * Used in integration tests to create consistent provenance data.
 */
public final class ProvenanceTestFixtures {

    private static final String DEFAULT_REPO_URL = "https://github.com/test/repo.git";
    private static final String DEFAULT_COMMIT_SHA = "abc123def456789012345678901234567890abcd";
    private static final String DEFAULT_AUTHOR = "test@example.com";

    private ProvenanceTestFixtures() {
        // Utility class
    }

    /**
     * Creates a SourceInfo with default values.
     *
     * @return a new SourceInfo instance
     */
    public static SourceInfo createDefaultSourceInfo() {
        return SourceInfo.builder()
            .repositoryUrl(DEFAULT_REPO_URL)
            .commitSha(DEFAULT_COMMIT_SHA)
            .commitAuthor(DEFAULT_AUTHOR)
            .committedAt(Instant.now().minusSeconds(3600))
            .branch("main")
            .tag("v1.0.0")
            .build();
    }

    /**
     * Creates a SourceInfo with custom values.
     *
     * @param repoUrl the repository URL
     * @param commitSha the commit SHA
     * @param branch the branch name
     * @param tag the tag
     * @return a new SourceInfo instance
     */
    public static SourceInfo createSourceInfo(String repoUrl, String commitSha, String branch, String tag) {
        return SourceInfo.builder()
            .repositoryUrl(repoUrl)
            .commitSha(commitSha)
            .commitAuthor(DEFAULT_AUTHOR)
            .committedAt(Instant.now().minusSeconds(3600))
            .branch(branch)
            .tag(tag)
            .build();
    }

    /**
     * Creates a SourceInfo with input checksums.
     *
     * @param inputChecksums map of file paths to checksums
     * @return a new SourceInfo instance
     */
    public static SourceInfo createSourceInfoWithChecksums(Map<String, String> inputChecksums) {
        SourceInfo.SourceInfoBuilder builder = SourceInfo.builder()
            .repositoryUrl(DEFAULT_REPO_URL)
            .commitSha(DEFAULT_COMMIT_SHA)
            .commitAuthor(DEFAULT_AUTHOR)
            .committedAt(Instant.now().minusSeconds(3600))
            .branch("main");

        inputChecksums.forEach(builder::inputChecksum);

        return builder.build();
    }

    /**
     * Creates a minimal provenance record for testing.
     *
     * @param releaseId the release ID
     * @return a new Provenance instance
     */
    public static Provenance createMinimalProvenance(ReleaseId releaseId) {
        return Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl(DEFAULT_REPO_URL)
            .commitSha(DEFAULT_COMMIT_SHA)
            .buildTimestamp(Instant.now())
            .commitAuthor(DEFAULT_AUTHOR)
            .committedAt(Instant.now().minusSeconds(3600))
            .builderId("test-builder")
            .builder(BuilderInfo.of("test-builder", "1.0.0"))
            .compilerVersion("1.0.0")
            .irChecksum(Sha256Hash.of("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
            .packageChecksum(Sha256Hash.of("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592"))
            .build();
    }

    /**
     * Creates a full provenance record with all fields.
     *
     * @param releaseId the release ID
     * @param branch the branch name
     * @param tag the git tag
     * @return a new Provenance instance
     */
    public static Provenance createFullProvenance(ReleaseId releaseId, String branch, String tag) {
        return Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl(DEFAULT_REPO_URL)
            .commitSha(DEFAULT_COMMIT_SHA)
            .buildTimestamp(Instant.now())
            .branch(branch)
            .gitTag(tag)
            .commitAuthor(DEFAULT_AUTHOR)
            .committedAt(Instant.now().minusSeconds(3600))
            .builderId("test-builder")
            .builder(BuilderInfo.of("test-builder", "1.0.0"))
            .compilerVersion("1.0.0")
            .irChecksum(Sha256Hash.of("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
            .packageChecksum(Sha256Hash.of("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592"))
            .input("flow.md", "abc123")
            .input("phase1.md", "def456")
            .environmentEntry("java.version", System.getProperty("java.version", "unknown"))
            .environmentEntry("os.name", System.getProperty("os.name", "unknown"))
            .build();
    }

    /**
     * Creates a ReleaseId for testing.
     *
     * @param flowId the flow ID
     * @param version the version
     * @return a new ReleaseId instance
     */
    public static ReleaseId createReleaseId(String flowId, String version) {
        return ReleaseId.of(
            ru.hgd.sdlc.compiler.domain.model.authored.FlowId.of(flowId),
            ReleaseVersion.of(version)
        );
    }

    /**
     * Creates a map of sample input checksums.
     *
     * @return map of file paths to checksums
     */
    public static Map<String, String> createSampleInputChecksums() {
        Map<String, String> checksums = new HashMap<>();
        checksums.put("flows/main-flow.md", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        checksums.put("phases/setup.md", "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592");
        checksums.put("skills/code-gen.md", "a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a");
        return checksums;
    }

    /**
     * Creates a valid commit SHA for testing.
     *
     * @param seed a seed string to generate a unique SHA
     * @return a 40-character hex string
     */
    public static String createCommitSha(String seed) {
        // Create a deterministic but valid-looking SHA based on seed
        String hash = Sha256Hash.of(seed).hex();
        return hash.substring(0, 40);
    }

    /**
     * Creates a provenance for a signed release.
     *
     * @param releaseId the release ID
     * @return a new Provenance instance ready for signing
     */
    public static Provenance createProvenanceForSigning(ReleaseId releaseId) {
        return Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl(DEFAULT_REPO_URL)
            .commitSha(DEFAULT_COMMIT_SHA)
            .buildTimestamp(Instant.now())
            .commitAuthor(DEFAULT_AUTHOR)
            .committedAt(Instant.now().minusSeconds(3600))
            .builderId("signing-test-builder")
            .builder(BuilderInfo.of("signing-test-builder", "1.0.0"))
            .compilerVersion("1.0.0")
            .irChecksum(Sha256Hash.of("a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a"))
            .packageChecksum(Sha256Hash.of("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"))
            .build();
    }
}
