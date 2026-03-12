package ru.hgd.sdlc.registry.application.signing;

import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.provenance.SignedProvenance;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProvenanceSigner interface contract.
 */
class ProvenanceSignerTest {

    @Test
    void ed25519SignerShouldImplementProvenanceSigner() {
        Ed25519Signer signer = new Ed25519Signer(
            ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair.generate(),
            "test-key"
        );

        assertTrue(signer instanceof ProvenanceSigner);
    }

    @Test
    void ed25519SignerShouldProvideKeyId() {
        ProvenanceSigner signer = new Ed25519Signer(
            ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair.generate(),
            "my-key-id"
        );

        assertEquals("my-key-id", signer.keyId());
    }

    @Test
    void ed25519SignerShouldProvidePublicKey() {
        var keyPair = ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair.generate();
        ProvenanceSigner signer = new Ed25519Signer(keyPair, "key-id");

        assertEquals(keyPair.publicKeyBase64(), signer.publicKeyBase64());
    }

    @Test
    void ed25519SignerShouldSignProvenance() {
        ProvenanceSigner signer = new Ed25519Signer(
            ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair.generate(),
            "key-id"
        );

        // Create minimal provenance for testing
        var releaseId = ru.hgd.sdlc.registry.domain.model.release.ReleaseId.of(
            ru.hgd.sdlc.compiler.domain.model.authored.FlowId.of("test"),
            ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion.of("1.0.0")
        );
        var builderInfo = ru.hgd.sdlc.registry.domain.model.provenance.BuilderInfo.of("test", "1.0");
        var checksum = ru.hgd.sdlc.registry.domain.model.release.Sha256Hash.of(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );
        var now = java.time.Instant.now();

        Provenance provenance = ru.hgd.sdlc.registry.domain.model.provenance.Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl("https://github.com/test/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .buildTimestamp(now)
            .commitAuthor("test@example.com")
            .committedAt(now)
            .builderId("test")
            .builder(builderInfo)
            .compilerVersion("1.0.0")
            .irChecksum(checksum)
            .packageChecksum(checksum)
            .build();

        SignedProvenance signed = signer.sign(provenance);

        assertNotNull(signed);
        assertNotNull(signed.signature());
        assertEquals("key-id", signed.signature().keyId());
    }
}
