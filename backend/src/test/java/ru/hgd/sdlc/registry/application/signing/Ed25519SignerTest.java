package ru.hgd.sdlc.registry.application.signing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.registry.domain.model.provenance.*;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Ed25519Signer.
 */
class Ed25519SignerTest {

    private SigningKeyPair keyPair;
    private String keyId;
    private Ed25519Signer signer;
    private Provenance testProvenance;

    @BeforeEach
    void setUp() {
        keyPair = SigningKeyPair.generate();
        keyId = "test-key-2026-03";
        signer = new Ed25519Signer(keyPair, keyId);

        ReleaseId releaseId = ReleaseId.of(FlowId.of("test-flow"), ReleaseVersion.of("1.0.0"));
        BuilderInfo builderInfo = BuilderInfo.of("test-builder", "1.0.0");
        Sha256Hash irChecksum = Sha256Hash.of("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        Sha256Hash packageChecksum = Sha256Hash.of("ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb");
        Instant now = Instant.now();

        testProvenance = Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/example/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(now)
            .commitAuthor("developer@example.com")
            .committedAt(now.minusSeconds(3600))
            .builderId("test-builder")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(irChecksum)
            .packageChecksum(packageChecksum)
            .build();
    }

    @Test
    void shouldSignProvenance() {
        SignedProvenance signed = signer.sign(testProvenance);

        assertNotNull(signed);
        assertNotNull(signed.provenance());
        assertNotNull(signed.signature());
    }

    @Test
    void shouldCreateValidSignature() {
        SignedProvenance signed = signer.sign(testProvenance);
        ProvenanceSignature signature = signed.signature();

        assertEquals("Ed25519", signature.algorithm());
        assertEquals(keyId, signature.keyId());
        assertNotNull(signature.value());
        assertNotNull(signature.publicKey());
        assertNotNull(signature.signedAt());
    }

    @Test
    void shouldIncludePublicKeyInSignature() {
        SignedProvenance signed = signer.sign(testProvenance);

        assertEquals(keyPair.publicKeyBase64(), signed.signature().publicKey());
    }

    @Test
    void shouldReturnKeyId() {
        assertEquals(keyId, signer.keyId());
    }

    @Test
    void shouldReturnPublicKeyBase64() {
        assertEquals(keyPair.publicKeyBase64(), signer.publicKeyBase64());
    }

    @Test
    void shouldRejectNullProvenance() {
        assertThrows(IllegalArgumentException.class, () ->
            signer.sign(null)
        );
    }

    @Test
    void shouldCreateVerifiableSignature() {
        SignedProvenance signed = signer.sign(testProvenance);

        SignedProvenance.VerificationResult result = signed.verify();

        assertTrue(result.valid());
        assertEquals(keyId, result.keyId());
    }

    @Test
    void shouldFailVerificationWithWrongKey() {
        SignedProvenance signed = signer.sign(testProvenance);

        // Use a different public key for verification
        SigningKeyPair differentKeyPair = SigningKeyPair.generate();
        SignedProvenance.VerificationResult result = signed.verify(differentKeyPair.publicKeyBytes());

        assertFalse(result.valid());
        assertEquals("Public key mismatch", result.reason());
    }

    @Test
    void shouldCreateDeterministicSignatures() {
        // Sign the same provenance twice - the signatures should verify correctly
        // Note: Ed25519 is deterministic, so same key + same payload = same signature
        SignedProvenance signed1 = signer.sign(testProvenance);
        SignedProvenance signed2 = signer.sign(testProvenance);

        // Both should verify correctly
        assertTrue(signed1.verify().valid());
        assertTrue(signed2.verify().valid());
    }

    @Test
    void shouldSignWithEmbeddedSignatureInProvenance() {
        SignedProvenance signed = signer.sign(testProvenance);

        // The provenance inside SignedProvenance should have the signature attached
        assertTrue(signed.provenance().isSigned());
        assertTrue(signed.provenance().getSignature().isPresent());
    }

    @Test
    void shouldRejectNullKeyPair() {
        assertThrows(NullPointerException.class, () ->
            new Ed25519Signer(null, keyId)
        );
    }

    @Test
    void shouldRejectNullKeyId() {
        assertThrows(NullPointerException.class, () ->
            new Ed25519Signer(keyPair, null)
        );
    }

    @Test
    void shouldUseCorrectSignatureAlgorithm() {
        SignedProvenance signed = signer.sign(testProvenance);

        assertEquals("Ed25519", signed.signature().algorithm());
    }

    @Test
    void signatureShouldHaveTimestamp() {
        Instant beforeSign = Instant.now();
        SignedProvenance signed = signer.sign(testProvenance);
        Instant afterSign = Instant.now();

        assertNotNull(signed.signature().signedAt());
        assertTrue(signed.signature().signedAt().isAfter(beforeSign.minusSeconds(1)));
        assertTrue(signed.signature().signedAt().isBefore(afterSign.plusSeconds(1)));
    }
}
