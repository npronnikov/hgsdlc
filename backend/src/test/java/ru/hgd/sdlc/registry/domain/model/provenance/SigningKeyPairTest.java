package ru.hgd.sdlc.registry.domain.model.provenance;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SigningKeyPair.
 */
class SigningKeyPairTest {

    @Test
    void shouldGenerateKeyPair() {
        SigningKeyPair keyPair = SigningKeyPair.generate();

        assertNotNull(keyPair);
        assertEquals(32, keyPair.publicKeyBytes().length);
        assertEquals(32, keyPair.privateKeyBytes().length);
        assertNotNull(keyPair.publicKeyBase64());
    }

    @Test
    void shouldGenerateDifferentKeyPairs() {
        SigningKeyPair keyPair1 = SigningKeyPair.generate();
        SigningKeyPair keyPair2 = SigningKeyPair.generate();

        assertFalse(Arrays.equals(keyPair1.publicKeyBytes(), keyPair2.publicKeyBytes()));
        assertFalse(Arrays.equals(keyPair1.privateKeyBytes(), keyPair2.privateKeyBytes()));
    }

    @Test
    void shouldSignAndVerifyData() {
        SigningKeyPair keyPair = SigningKeyPair.generate();
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);

        byte[] signature = keyPair.sign(data);

        assertNotNull(signature);
        assertTrue(signature.length > 0);
        assertTrue(keyPair.verify(data, signature));
    }

    @Test
    void shouldSignAndVerifyString() {
        SigningKeyPair keyPair = SigningKeyPair.generate();
        String message = "Test message to sign";

        byte[] signature = keyPair.sign(message);

        assertNotNull(signature);
        assertTrue(keyPair.verify(message.getBytes(StandardCharsets.UTF_8), signature));
    }

    @Test
    void shouldRejectInvalidSignature() {
        SigningKeyPair keyPair = SigningKeyPair.generate();
        byte[] data = "Original data".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedData = "Tampered data".getBytes(StandardCharsets.UTF_8);

        byte[] signature = keyPair.sign(data);

        // Tampered data should fail verification
        assertFalse(keyPair.verify(tamperedData, signature));
    }

    @Test
    void shouldRejectSignatureFromDifferentKey() {
        SigningKeyPair keyPair1 = SigningKeyPair.generate();
        SigningKeyPair keyPair2 = SigningKeyPair.generate();
        byte[] data = "Test data".getBytes(StandardCharsets.UTF_8);

        byte[] signature1 = keyPair1.sign(data);

        // Signature from key1 should not verify with key2
        assertFalse(keyPair2.verify(data, signature1));
    }

    @Test
    void shouldCreateFromExistingKeyBytes() {
        SigningKeyPair original = SigningKeyPair.generate();
        byte[] privateKey = original.privateKeyBytes();
        byte[] publicKey = original.publicKeyBytes();

        SigningKeyPair recreated = SigningKeyPair.of(privateKey, publicKey);

        assertArrayEquals(publicKey, recreated.publicKeyBytes());
        assertArrayEquals(privateKey, recreated.privateKeyBytes());
    }

    @Test
    void shouldSignWithRecreatedKeyPair() {
        SigningKeyPair original = SigningKeyPair.generate();
        byte[] privateKey = original.privateKeyBytes();
        byte[] publicKey = original.publicKeyBytes();

        SigningKeyPair recreated = SigningKeyPair.of(privateKey, publicKey);
        byte[] data = "Test data".getBytes(StandardCharsets.UTF_8);
        byte[] signature = recreated.sign(data);

        // Both key pairs should verify the same signature
        assertTrue(recreated.verify(data, signature));
        assertTrue(original.verify(data, signature));
    }

    @Test
    void shouldRejectInvalidPrivateKeySize() {
        assertThrows(IllegalArgumentException.class, () ->
            SigningKeyPair.of(new byte[16], new byte[32])
        );
    }

    @Test
    void shouldRejectInvalidPublicKeySize() {
        SigningKeyPair original = SigningKeyPair.generate();
        assertThrows(IllegalArgumentException.class, () ->
            SigningKeyPair.of(original.privateKeyBytes(), new byte[16])
        );
    }

    @Test
    void shouldRejectNullPrivateKey() {
        assertThrows(IllegalArgumentException.class, () ->
            SigningKeyPair.of(null, new byte[32])
        );
    }

    @Test
    void shouldRejectNullPublicKey() {
        SigningKeyPair original = SigningKeyPair.generate();
        assertThrows(IllegalArgumentException.class, () ->
            SigningKeyPair.of(original.privateKeyBytes(), null)
        );
    }

    @Test
    void shouldReturnPublicKeyBase64() {
        SigningKeyPair keyPair = SigningKeyPair.generate();

        String base64 = keyPair.publicKeyBase64();

        assertNotNull(base64);
        byte[] decoded = Base64.getDecoder().decode(base64);
        assertArrayEquals(keyPair.publicKeyBytes(), decoded);
    }

    @Test
    void shouldReturnPrivateKeyBase64() {
        SigningKeyPair keyPair = SigningKeyPair.generate();

        String base64 = keyPair.privateKeyBase64();

        assertNotNull(base64);
        byte[] decoded = Base64.getDecoder().decode(base64);
        assertArrayEquals(keyPair.privateKeyBytes(), decoded);
    }

    @Test
    void shouldReturnCopiesOfKeyBytes() {
        SigningKeyPair keyPair = SigningKeyPair.generate();

        byte[] publicKey1 = keyPair.publicKeyBytes();
        byte[] publicKey2 = keyPair.publicKeyBytes();
        byte[] privateKey1 = keyPair.privateKeyBytes();
        byte[] privateKey2 = keyPair.privateKeyBytes();

        // Should be equal but not the same array
        assertArrayEquals(publicKey1, publicKey2);
        assertArrayEquals(privateKey1, privateKey2);
        assertNotSame(publicKey1, publicKey2);
        assertNotSame(privateKey1, privateKey2);
    }

    @Test
    void shouldHaveToString() {
        SigningKeyPair keyPair = SigningKeyPair.generate();

        String str = keyPair.toString();

        assertTrue(str.contains("SigningKeyPair"));
        assertTrue(str.contains(keyPair.publicKeyBase64()));
        assertFalse(str.contains(keyPair.privateKeyBase64())); // Don't leak private key
    }

    @Test
    void shouldSignLargeData() {
        SigningKeyPair keyPair = SigningKeyPair.generate();
        byte[] largeData = new byte[10000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        byte[] signature = keyPair.sign(largeData);

        assertTrue(keyPair.verify(largeData, signature));
    }

    @Test
    void shouldSignEmptyData() {
        SigningKeyPair keyPair = SigningKeyPair.generate();
        byte[] emptyData = new byte[0];

        byte[] signature = keyPair.sign(emptyData);

        assertTrue(keyPair.verify(emptyData, signature));
    }

    @Test
    void signaturesShouldBeDeterministicForSameData() {
        SigningKeyPair keyPair = SigningKeyPair.generate();
        byte[] data = "Same data".getBytes(StandardCharsets.UTF_8);

        byte[] signature1 = keyPair.sign(data);
        byte[] signature2 = keyPair.sign(data);

        // Ed25519 signatures are deterministic
        assertArrayEquals(signature1, signature2);
    }
}
