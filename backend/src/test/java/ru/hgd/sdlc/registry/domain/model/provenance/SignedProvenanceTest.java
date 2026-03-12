package ru.hgd.sdlc.registry.domain.model.provenance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SignedProvenance.
 */
class SignedProvenanceTest {

    private Provenance provenance;
    private SigningKeyPair keyPair;

    @BeforeEach
    void setUp() {
        ReleaseId releaseId = ReleaseId.of(FlowId.of("test-flow"), ReleaseVersion.of("1.0.0"));
        BuilderInfo builderInfo = BuilderInfo.of("sdlc-registry", "1.0.0");
        Sha256Hash irChecksum = Sha256Hash.of("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        Sha256Hash packageChecksum = Sha256Hash.of("ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb");
        Instant now = Instant.now();

        provenance = Provenance.builder()
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

        keyPair = SigningKeyPair.generate();
    }

    @Test
    void shouldCreateSignedProvenance() {
        byte[] signature = keyPair.sign(provenance.toSignablePayload());
        ProvenanceSignature provSig = ProvenanceSignature.of(
            "Ed25519",
            "key-1",
            keyPair.publicKeyBytes(),
            signature,
            Instant.now()
        );

        SignedProvenance signed = SignedProvenance.builder()
            .provenance(provenance)
            .signature(provSig)
            .build();

        assertEquals(provenance, signed.provenance());
        assertEquals(provSig, signed.signature());
    }

    @Test
    void shouldVerifyValidSignature() {
        byte[] signature = keyPair.sign(provenance.toSignablePayload());
        ProvenanceSignature provSig = ProvenanceSignature.of(
            "Ed25519",
            "key-1",
            keyPair.publicKeyBytes(),
            signature,
            Instant.now()
        );

        SignedProvenance signed = SignedProvenance.builder()
            .provenance(provenance)
            .signature(provSig)
            .build();

        SignedProvenance.VerificationResult result = signed.verify();

        assertTrue(result.valid());
        assertEquals("key-1", result.keyId());
        assertNull(result.reason());
    }

    @Test
    void shouldVerifyWithProvidedPublicKey() {
        byte[] signature = keyPair.sign(provenance.toSignablePayload());
        ProvenanceSignature provSig = ProvenanceSignature.of(
            "Ed25519",
            "key-1",
            keyPair.publicKeyBytes(),
            signature,
            Instant.now()
        );

        SignedProvenance signed = SignedProvenance.builder()
            .provenance(provenance)
            .signature(provSig)
            .build();

        SignedProvenance.VerificationResult result = signed.verify(keyPair.publicKeyBytes());

        assertTrue(result.valid());
        assertEquals("key-1", result.keyId());
    }

    @Test
    void shouldRejectMismatchedPublicKey() {
        byte[] signature = keyPair.sign(provenance.toSignablePayload());
        ProvenanceSignature provSig = ProvenanceSignature.of(
            "Ed25519",
            "key-1",
            keyPair.publicKeyBytes(),
            signature,
            Instant.now()
        );

        SignedProvenance signed = SignedProvenance.builder()
            .provenance(provenance)
            .signature(provSig)
            .build();

        // Use a different key
        SigningKeyPair differentKey = SigningKeyPair.generate();
        SignedProvenance.VerificationResult result = signed.verify(differentKey.publicKeyBytes());

        assertFalse(result.valid());
        assertEquals("Public key mismatch", result.reason());
    }

    @Test
    void shouldRejectNullPublicKey() {
        byte[] signature = keyPair.sign(provenance.toSignablePayload());
        ProvenanceSignature provSig = ProvenanceSignature.of(
            "Ed25519",
            "key-1",
            keyPair.publicKeyBytes(),
            signature,
            Instant.now()
        );

        SignedProvenance signed = SignedProvenance.builder()
            .provenance(provenance)
            .signature(provSig)
            .build();

        SignedProvenance.VerificationResult result = signed.verify((byte[]) null);

        assertFalse(result.valid());
        assertEquals("Public key cannot be null or empty", result.reason());
    }

    @Test
    void shouldRejectEmptyPublicKey() {
        byte[] signature = keyPair.sign(provenance.toSignablePayload());
        ProvenanceSignature provSig = ProvenanceSignature.of(
            "Ed25519",
            "key-1",
            keyPair.publicKeyBytes(),
            signature,
            Instant.now()
        );

        SignedProvenance signed = SignedProvenance.builder()
            .provenance(provenance)
            .signature(provSig)
            .build();

        SignedProvenance.VerificationResult result = signed.verify(new byte[0]);

        assertFalse(result.valid());
        assertEquals("Public key cannot be null or empty", result.reason());
    }

    @Test
    void shouldRejectTamperedProvenance() {
        byte[] signature = keyPair.sign(provenance.toSignablePayload());
        ProvenanceSignature provSig = ProvenanceSignature.of(
            "Ed25519",
            "key-1",
            keyPair.publicKeyBytes(),
            signature,
            Instant.now()
        );

        // Modify the provenance after signing
        Provenance tampered = provenance.toBuilder()
            .commitAuthor("attacker@example.com")
            .build();

        SignedProvenance signed = SignedProvenance.builder()
            .provenance(tampered)
            .signature(provSig)
            .build();

        SignedProvenance.VerificationResult result = signed.verify();

        assertFalse(result.valid());
        assertEquals("Signature verification failed", result.reason());
    }

    @Test
    void shouldRejectUnsupportedAlgorithm() {
        byte[] signature = keyPair.sign(provenance.toSignablePayload());
        ProvenanceSignature provSig = ProvenanceSignature.of(
            "RSA-4096", // Unsupported algorithm
            "key-1",
            keyPair.publicKeyBytes(),
            signature,
            Instant.now()
        );

        SignedProvenance signed = SignedProvenance.builder()
            .provenance(provenance)
            .signature(provSig)
            .build();

        SignedProvenance.VerificationResult result = signed.verify();

        assertFalse(result.valid());
        assertTrue(result.reason().contains("Unsupported signature algorithm"));
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        byte[] signature = keyPair.sign(provenance.toSignablePayload());
        ProvenanceSignature provSig = ProvenanceSignature.of(
            "Ed25519",
            "key-1",
            keyPair.publicKeyBytes(),
            signature,
            Instant.now()
        );

        SignedProvenance signed1 = SignedProvenance.builder()
            .provenance(provenance)
            .signature(provSig)
            .build();

        SignedProvenance signed2 = SignedProvenance.builder()
            .provenance(provenance)
            .signature(provSig)
            .build();

        assertEquals(signed1, signed2);
        assertEquals(signed1.hashCode(), signed2.hashCode());
    }

    @Test
    void shouldRejectNullProvenance() {
        assertThrows(NullPointerException.class, () ->
            SignedProvenance.builder()
                .signature(ProvenanceSignature.of(
                    "Ed25519",
                    "key-1",
                    new byte[32],
                    new byte[64],
                    Instant.now()
                ))
                .build()
        );
    }

    @Test
    void shouldRejectNullSignature() {
        assertThrows(NullPointerException.class, () ->
            SignedProvenance.builder()
                .provenance(provenance)
                .build()
        );
    }
}
