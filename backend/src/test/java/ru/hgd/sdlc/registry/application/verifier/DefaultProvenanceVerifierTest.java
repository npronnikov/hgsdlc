package ru.hgd.sdlc.registry.application.verifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.IrMetadata;
import ru.hgd.sdlc.registry.domain.model.provenance.BuilderInfo;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.provenance.ProvenanceSignature;
import ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair;
import ru.hgd.sdlc.registry.domain.model.release.ChecksumManifest;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseMetadata;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultProvenanceVerifier.
 */
class DefaultProvenanceVerifierTest {

    private DefaultProvenanceVerifier verifier;
    private static final String EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @BeforeEach
    void setUp() {
        verifier = new DefaultProvenanceVerifier();
    }

    @Test
    @DisplayName("Should verify valid provenance")
    void shouldVerifyValidProvenance() {
        ReleasePackage pkg = createValidReleasePackage(false);

        VerificationResult result = verifier.verify(pkg);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("Should verify valid signature")
    void shouldVerifyValidSignature() {
        ReleasePackage pkg = createValidReleasePackage(true);

        VerificationResult result = verifier.verify(pkg);

        assertTrue(result.isValid());
        assertTrue(result.getInfos().stream()
            .anyMatch(i -> i.getCode().equals("SIGNATURE_VALID")));
    }

    @Test
    @DisplayName("Should detect invalid signature")
    void shouldDetectInvalidSignature() {
        ReleasePackage pkg = createReleasePackageWithInvalidSignature();

        VerificationResult result = verifier.verify(pkg);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(i -> i.getCode().equals("INVALID_SIGNATURE")));
    }

    @Test
    @DisplayName("Should detect invalid git SHA format")
    void shouldDetectInvalidGitShaFormat() {
        ReleasePackage pkg = createReleasePackageWithInvalidGitSha();

        VerificationResult result = verifier.verify(pkg);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(i -> i.getCode().equals("INVALID_GIT_SHA_FORMAT")));
    }

    @Test
    @DisplayName("Should warn on future build timestamp")
    void shouldWarnOnFutureBuildTimestamp() {
        ReleasePackage pkg = createReleasePackageWithFutureTimestamp();

        VerificationResult result = verifier.verify(pkg);

        assertTrue(result.isValid());
        assertTrue(result.getWarnings().stream()
            .anyMatch(i -> i.getCode().equals("FUTURE_BUILD_TIMESTAMP")));
    }

    @Test
    @DisplayName("Should return warning for unsigned provenance")
    void shouldReturnWarningForUnsignedProvenance() {
        Provenance provenance = createValidProvenance();

        VerificationResult result = verifier.verifySignature(provenance);

        assertTrue(result.isValid());
        assertTrue(result.getWarnings().stream()
            .anyMatch(i -> i.getCode().equals("NO_SIGNATURE")));
    }

    @Test
    @DisplayName("Should verify signature on signed provenance")
    void shouldVerifySignatureOnSignedProvenance() {
        SigningKeyPair keyPair = SigningKeyPair.generate();
        Provenance provenance = createValidProvenance();

        byte[] signature = keyPair.sign(provenance.toSignablePayload().getBytes(StandardCharsets.UTF_8));
        ProvenanceSignature provSig = ProvenanceSignature.of(
            "Ed25519",
            "test-key",
            keyPair.publicKeyBytes(),
            signature,
            Instant.now()
        );
        Provenance signedProvenance = provenance.withSignature(provSig);

        VerificationResult result = verifier.verifySignature(signedProvenance);

        assertTrue(result.isValid());
    }

    // Helper methods

    private ReleasePackage createValidReleasePackage(boolean withSignature) {
        Provenance provenance = createValidProvenance();

        if (withSignature) {
            SigningKeyPair keyPair = SigningKeyPair.generate();
            byte[] signature = keyPair.sign(provenance.toSignablePayload().getBytes(StandardCharsets.UTF_8));
            ProvenanceSignature provSig = ProvenanceSignature.of(
                "Ed25519",
                "test-key",
                keyPair.publicKeyBytes(),
                signature,
                Instant.now()
            );
            provenance = provenance.withSignature(provSig);
        }

        return createReleasePackage(provenance);
    }

    private ReleasePackage createReleasePackageWithInvalidSignature() {
        SigningKeyPair keyPair = SigningKeyPair.generate();
        Provenance provenance = createValidProvenance();

        // Create invalid signature: sign a different content with the same key
        byte[] wrongSignature = keyPair.sign("wrong content".getBytes(StandardCharsets.UTF_8));

        ProvenanceSignature invalidSig = ProvenanceSignature.of(
            "Ed25519",
            "test-key",
            keyPair.publicKeyBytes(),
            wrongSignature,
            Instant.now()
        );

        Provenance signedProvenance = provenance.withSignature(invalidSig);

        return createReleasePackage(signedProvenance);
    }

    private ReleasePackage createReleasePackageWithInvalidGitSha() {
        ReleaseId releaseId = ReleaseId.of(FlowId.of("test-flow"), ReleaseVersion.of("1.0.0"));
        BuilderInfo builderInfo = BuilderInfo.of("test-builder", "1.0.0");
        Sha256Hash checksum = Sha256Hash.of(EMPTY_HASH);

        Instant now = Instant.now();

        Provenance provenance = Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/test/repo.git")
            .commitSha("invalid-sha-format")
            .buildTimestamp(now)
            .commitAuthor("test@example.com")
            .committedAt(now)
            .builderId("test-builder")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(checksum)
            .packageChecksum(checksum)
            .build();

        return createReleasePackage(provenance);
    }

    private ReleasePackage createReleasePackageWithFutureTimestamp() {
        Instant future = Instant.now().plusSeconds(86400);
        ReleaseId releaseId = ReleaseId.of(FlowId.of("test-flow"), ReleaseVersion.of("1.0.0"));
        BuilderInfo builderInfo = BuilderInfo.of("test-builder", "1.0.0");
        Sha256Hash checksum = Sha256Hash.of(EMPTY_HASH);

        Provenance provenance = Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/test/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(future)
            .commitAuthor("test@example.com")
            .committedAt(Instant.now())
            .builderId("test-builder")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(checksum)
            .packageChecksum(checksum)
            .build();

        return createReleasePackage(provenance);
    }

    private Provenance createValidProvenance() {
        ReleaseId releaseId = ReleaseId.of(FlowId.of("test-flow"), ReleaseVersion.of("1.0.0"));
        BuilderInfo builderInfo = BuilderInfo.of("test-builder", "1.0.0");
        Sha256Hash checksum = Sha256Hash.of(EMPTY_HASH);
        Instant now = Instant.now();

        return Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/test/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(now)
            .commitAuthor("test@example.com")
            .committedAt(now)
            .builderId("test-builder")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(checksum)
            .packageChecksum(checksum)
            .build();
    }

    private ReleasePackage createReleasePackage(Provenance provenance) {
        ReleaseId releaseId = ReleaseId.of(FlowId.of("test-flow"), ReleaseVersion.of("1.0.0"));
        ReleaseMetadata metadata = ReleaseMetadata.builder()
            .displayName("Test Release")
            .author("test@example.com")
            .createdAt(Instant.now())
            .gitCommit("abc123def456789012345678901234567890abcd")
            .build();

        Sha256 irChecksum = Sha256.fromHex(EMPTY_HASH);
        IrMetadata irMetadata = IrMetadata.create(irChecksum, irChecksum, "1.0.0");

        FlowIr flowIr = FlowIr.builder()
            .flowId(ru.hgd.sdlc.compiler.domain.model.authored.FlowId.of("test-flow"))
            .flowVersion(SemanticVersion.of("1.0.0"))
            .metadata(irMetadata)
            .phases(Collections.emptyList())
            .build();

        ChecksumManifest checksums = ChecksumManifest.builder()
            .entry("flow.json", Sha256Hash.of(EMPTY_HASH))
            .build();

        return ReleasePackage.builder()
            .id(releaseId)
            .metadata(metadata)
            .flowIr(flowIr)
            .provenance(provenance)
            .checksums(checksums)
            .build();
    }
}
