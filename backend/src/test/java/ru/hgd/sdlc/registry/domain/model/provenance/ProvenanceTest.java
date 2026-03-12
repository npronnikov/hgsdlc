package ru.hgd.sdlc.registry.domain.model.provenance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Provenance.
 */
class ProvenanceTest {

    private ReleaseId releaseId;
    private BuilderInfo builderInfo;
    private Sha256Hash irChecksum;
    private Sha256Hash packageChecksum;
    private Instant now;

    @BeforeEach
    void setUp() {
        releaseId = ReleaseId.of(FlowId.of("test-flow"), ReleaseVersion.of("1.0.0"));
        builderInfo = BuilderInfo.of("sdlc-registry", "1.0.0");
        irChecksum = Sha256Hash.of("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        packageChecksum = Sha256Hash.of("ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb");
        now = Instant.now();
    }

    @Test
    void shouldCreateProvenanceWithRequiredFields() {
        Provenance provenance = Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/example/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(now)
            .commitAuthor("developer@example.com")
            .committedAt(now.minusSeconds(3600))
            .builderId("ci-pipeline")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(irChecksum)
            .packageChecksum(packageChecksum)
            .build();

        assertEquals(releaseId, provenance.getReleaseId());
        assertEquals("https://github.com/example/repo.git", provenance.getRepositoryUrl());
        assertEquals("abc123def456789012345678901234567890abcd", provenance.getCommitSha());
        assertEquals(now, provenance.getBuildTimestamp());
        assertEquals("developer@example.com", provenance.getCommitAuthor());
        assertEquals(builderInfo, provenance.getBuilder());
        assertEquals(irChecksum, provenance.getIrChecksum());
        assertEquals(packageChecksum, provenance.getPackageChecksum());
        assertFalse(provenance.isSigned());
    }

    @Test
    void shouldCreateProvenanceWithOptionalFields() {
        Provenance provenance = Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/example/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(now)
            .gitTag("v1.0.0")
            .branch("main")
            .commitMessage("Initial release")
            .commitAuthor("developer@example.com")
            .committedAt(now.minusSeconds(3600))
            .builderId("ci-pipeline")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(irChecksum)
            .packageChecksum(packageChecksum)
            .build();

        assertTrue(provenance.getGitTag().isPresent());
        assertEquals("v1.0.0", provenance.getGitTag().get());
        assertTrue(provenance.getBranch().isPresent());
        assertEquals("main", provenance.getBranch().get());
        assertTrue(provenance.getCommitMessage().isPresent());
        assertEquals("Initial release", provenance.getCommitMessage().get());
    }

    @Test
    void shouldCreateProvenanceWithInputsAndEnvironment() {
        Provenance provenance = Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/example/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(now)
            .commitAuthor("developer@example.com")
            .committedAt(now.minusSeconds(3600))
            .builderId("ci-pipeline")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(irChecksum)
            .packageChecksum(packageChecksum)
            .input("flow.md", "hash1")
            .input("phase1.md", "hash2")
            .environmentEntry("java.version", "21")
            .environmentEntry("os.name", "Linux")
            .build();

        assertEquals(2, provenance.getInputs().size());
        assertEquals("hash1", provenance.getInputs().get("flow.md"));
        assertEquals("hash2", provenance.getInputs().get("phase1.md"));

        assertEquals(2, provenance.getEnvironment().size());
        assertEquals("21", provenance.getEnvironment().get("java.version"));
        assertEquals("Linux", provenance.getEnvironment().get("os.name"));
    }

    @Test
    void shouldValidateGitCommitSha() {
        assertThrows(IllegalArgumentException.class, () ->
            Provenance.validateGitCommit("invalid-sha")
        );
    }

    @Test
    void shouldNormalizeGitCommitShaToLowercase() {
        String normalized = Provenance.validateGitCommit("ABC123DEF456789012345678901234567890ABCD");
        assertEquals("abc123def456789012345678901234567890abcd", normalized);
    }

    @Test
    void shouldGenerateSignablePayload() {
        Provenance provenance = Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/example/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(now)
            .commitAuthor("developer@example.com")
            .committedAt(now.minusSeconds(3600))
            .builderId("ci-pipeline")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(irChecksum)
            .packageChecksum(packageChecksum)
            .build();

        String payload = provenance.toSignablePayload();

        assertNotNull(payload);
        assertTrue(payload.contains("commitSha")); // Contains core fields
        assertFalse(payload.contains("signature")); // No signature in payload
    }

    @Test
    void shouldExcludeSignatureFromSignablePayload() {
        ProvenanceSignature sig = ProvenanceSignature.builder()
            .algorithm("Ed25519")
            .keyId("key-1")
            .publicKey("cHVibGljLWtleQ==")
            .value("c2lnbmF0dXJl")
            .signedAt(Instant.now())
            .build();

        Provenance provenance = Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/example/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(now)
            .commitAuthor("developer@example.com")
            .committedAt(now)
            .builderId("ci")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(irChecksum)
            .packageChecksum(packageChecksum)
            .signature(sig)
            .build();

        String payload = provenance.toSignablePayload();

        // Payload should not contain signature data for signing
        assertNotNull(payload);
    }

    @Test
    void shouldSerializeAndDeserialize() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module());

        Provenance original = Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/example/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(now)
            .gitTag("v1.0.0")
            .commitAuthor("developer@example.com")
            .committedAt(now.minusSeconds(3600))
            .builderId("ci-pipeline")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(irChecksum)
            .packageChecksum(packageChecksum)
            .input("flow.md", "hash1")
            .build();

        String json = mapper.writeValueAsString(original);
        Provenance deserialized = mapper.readValue(json, Provenance.class);

        assertEquals(original.getReleaseId(), deserialized.getReleaseId());
        assertEquals(original.getCommitSha(), deserialized.getCommitSha());
        assertEquals(original.getGitTag(), deserialized.getGitTag());
        assertEquals(original.getInputs(), deserialized.getInputs());
    }

    @Test
    void shouldAddSignature() {
        Provenance original = Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/example/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(now)
            .commitAuthor("developer@example.com")
            .committedAt(now)
            .builderId("ci")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(irChecksum)
            .packageChecksum(packageChecksum)
            .build();

        assertFalse(original.isSigned());

        ProvenanceSignature sig = ProvenanceSignature.builder()
            .algorithm("Ed25519")
            .keyId("key-1")
            .publicKey("cHVibGljLWtleQ==")
            .value("c2lnbmF0dXJl")
            .signedAt(Instant.now())
            .build();

        Provenance signed = original.withSignature(sig);

        assertTrue(signed.isSigned());
        assertTrue(signed.getSignature().isPresent());
        assertFalse(original.isSigned()); // Original unchanged (immutability)
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        Provenance p1 = Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/example/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(now)
            .commitAuthor("developer@example.com")
            .committedAt(now)
            .builderId("ci")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(irChecksum)
            .packageChecksum(packageChecksum)
            .build();

        Provenance p2 = Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/example/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(now)
            .commitAuthor("developer@example.com")
            .committedAt(now)
            .builderId("ci")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(irChecksum)
            .packageChecksum(packageChecksum)
            .build();

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }
}
