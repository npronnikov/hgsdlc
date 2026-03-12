package ru.hgd.sdlc.registry.domain.model.provenance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProvenanceSignature.
 */
class ProvenanceSignatureTest {

    @Test
    void shouldCreateSignatureWithAllFields() {
        Instant signedAt = Instant.now();
        String publicKeyBase64 = Base64.getEncoder().encodeToString(new byte[32]);
        String signatureBase64 = Base64.getEncoder().encodeToString(new byte[64]);

        ProvenanceSignature sig = ProvenanceSignature.builder()
            .algorithm("Ed25519")
            .keyId("key-2026-01")
            .publicKey(publicKeyBase64)
            .value(signatureBase64)
            .signedAt(signedAt)
            .build();

        assertEquals("Ed25519", sig.algorithm());
        assertEquals("key-2026-01", sig.keyId());
        assertEquals(publicKeyBase64, sig.publicKey());
        assertEquals(signatureBase64, sig.value());
        assertEquals(signedAt, sig.signedAt());
    }

    @Test
    void shouldCreateSignatureFromBytes() {
        byte[] publicKey = new byte[32];
        publicKey[0] = 1; // Some non-zero data
        byte[] signature = new byte[64];
        signature[0] = 2; // Some non-zero data
        Instant signedAt = Instant.now();

        ProvenanceSignature sig = ProvenanceSignature.of(
            "Ed25519",
            "key-1",
            publicKey,
            signature,
            signedAt
        );

        assertEquals("Ed25519", sig.algorithm());
        assertEquals("key-1", sig.keyId());

        // Verify base64 encoding
        assertArrayEquals(publicKey, sig.publicKeyBytes());
        assertArrayEquals(signature, sig.valueBytes());
    }

    @Test
    void shouldDecodePublicKeyBytes() {
        byte[] originalKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            originalKey[i] = (byte) i;
        }

        ProvenanceSignature sig = ProvenanceSignature.builder()
            .algorithm("Ed25519")
            .keyId("key-1")
            .publicKey(Base64.getEncoder().encodeToString(originalKey))
            .value("c2lnbmF0dXJl")
            .signedAt(Instant.now())
            .build();

        assertArrayEquals(originalKey, sig.publicKeyBytes());
    }

    @Test
    void shouldDecodeSignatureValueBytes() {
        byte[] originalSignature = new byte[64];
        for (int i = 0; i < 64; i++) {
            originalSignature[i] = (byte) (i % 256);
        }

        ProvenanceSignature sig = ProvenanceSignature.builder()
            .algorithm("Ed25519")
            .keyId("key-1")
            .publicKey("cHVibGljLWtleQ==")
            .value(Base64.getEncoder().encodeToString(originalSignature))
            .signedAt(Instant.now())
            .build();

        assertArrayEquals(originalSignature, sig.valueBytes());
    }

    @Test
    void shouldRejectNullAlgorithm() {
        assertThrows(NullPointerException.class, () ->
            ProvenanceSignature.builder()
                .keyId("key-1")
                .publicKey("cHVibGljS2V5")
                .value("c2lnbmF0dXJl")
                .signedAt(Instant.now())
                .build()
        );
    }

    @Test
    void shouldRejectNullKeyId() {
        assertThrows(NullPointerException.class, () ->
            ProvenanceSignature.builder()
                .algorithm("Ed25519")
                .publicKey("cHVibGljS2V5")
                .value("c2lnbmF0dXJl")
                .signedAt(Instant.now())
                .build()
        );
    }

    @Test
    void shouldRejectNullPublicKey() {
        assertThrows(NullPointerException.class, () ->
            ProvenanceSignature.builder()
                .algorithm("Ed25519")
                .keyId("key-1")
                .value("c2lnbmF0dXJl")
                .signedAt(Instant.now())
                .build()
        );
    }

    @Test
    void shouldRejectNullSignatureValue() {
        assertThrows(NullPointerException.class, () ->
            ProvenanceSignature.builder()
                .algorithm("Ed25519")
                .keyId("key-1")
                .publicKey("cHVibGljS2V5")
                .signedAt(Instant.now())
                .build()
        );
    }

    @Test
    void shouldRejectNullSignedAt() {
        assertThrows(NullPointerException.class, () ->
            ProvenanceSignature.builder()
                .algorithm("Ed25519")
                .keyId("key-1")
                .publicKey("cHVibGljS2V5")
                .value("c2lnbmF0dXJl")
                .build()
        );
    }

    @Test
    void shouldSerializeAndDeserialize() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        ProvenanceSignature original = ProvenanceSignature.builder()
            .algorithm("Ed25519")
            .keyId("key-2026-01")
            .publicKey("cHVibGljS2V5QmFzZTY0")
            .value("c2lnbmF0dXJlVmFsdWVCYXNlNjQ=")
            .signedAt(Instant.parse("2026-03-12T10:30:00Z"))
            .build();

        String json = mapper.writeValueAsString(original);
        ProvenanceSignature deserialized = mapper.readValue(json, ProvenanceSignature.class);

        assertEquals(original.algorithm(), deserialized.algorithm());
        assertEquals(original.keyId(), deserialized.keyId());
        assertEquals(original.publicKey(), deserialized.publicKey());
        assertEquals(original.value(), deserialized.value());
        assertEquals(original.signedAt(), deserialized.signedAt());
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        Instant signedAt = Instant.now();
        ProvenanceSignature sig1 = ProvenanceSignature.builder()
            .algorithm("Ed25519")
            .keyId("key-1")
            .publicKey("cHVibGljS2V5")
            .value("c2lnbmF0dXJl")
            .signedAt(signedAt)
            .build();

        ProvenanceSignature sig2 = ProvenanceSignature.builder()
            .algorithm("Ed25519")
            .keyId("key-1")
            .publicKey("cHVibGljS2V5")
            .value("c2lnbmF0dXJl")
            .signedAt(signedAt)
            .build();

        ProvenanceSignature sig3 = ProvenanceSignature.builder()
            .algorithm("Ed25519")
            .keyId("key-2") // Different
            .publicKey("cHVibGljS2V5")
            .value("c2lnbmF0dXJl")
            .signedAt(signedAt)
            .build();

        assertEquals(sig1, sig2);
        assertEquals(sig1.hashCode(), sig2.hashCode());
        assertNotEquals(sig1, sig3);
    }

    @Test
    void shouldCreateModifiedCopy() {
        ProvenanceSignature original = ProvenanceSignature.builder()
            .algorithm("Ed25519")
            .keyId("key-1")
            .publicKey("cHVibGljS2V5")
            .value("c2lnbmF0dXJl")
            .signedAt(Instant.now())
            .build();

        ProvenanceSignature modified = original.toBuilder()
            .keyId("key-2")
            .build();

        assertEquals("key-1", original.keyId());
        assertEquals("key-2", modified.keyId());
    }
}
