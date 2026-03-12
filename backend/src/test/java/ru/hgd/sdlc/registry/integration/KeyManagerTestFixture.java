package ru.hgd.sdlc.registry.integration;

import ru.hgd.sdlc.registry.application.signing.Ed25519Signer;
import ru.hgd.sdlc.registry.application.signing.ProvenanceSigner;
import ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Test fixture for setting up test keys for signing.
 * Manages temporary key storage and signer creation.
 */
public final class KeyManagerTestFixture {

    private final SigningKeyPair keyPair;
    private final String keyId;
    private final Path tempDir;

    private KeyManagerTestFixture(String keyId) {
        this.keyId = keyId;
        this.keyPair = SigningKeyPair.generate();
        this.tempDir = createTempDir();
    }

    /**
     * Creates a new KeyManagerTestFixture with a random key ID.
     *
     * @return a new fixture instance
     */
    public static KeyManagerTestFixture create() {
        return new KeyManagerTestFixture("test-key-" + System.currentTimeMillis());
    }

    /**
     * Creates a new KeyManagerTestFixture with a specific key ID.
     *
     * @param keyId the key identifier
     * @return a new fixture instance
     */
    public static KeyManagerTestFixture create(String keyId) {
        return new KeyManagerTestFixture(keyId);
    }

    /**
     * Returns the signing key pair.
     *
     * @return the key pair
     */
    public SigningKeyPair keyPair() {
        return keyPair;
    }

    /**
     * Returns the key ID.
     *
     * @return the key identifier
     */
    public String keyId() {
        return keyId;
    }

    /**
     * Creates a ProvenanceSigner using this key pair.
     *
     * @return a new Ed25519Signer instance
     */
    public ProvenanceSigner createSigner() {
        return new Ed25519Signer(keyPair, keyId);
    }

    /**
     * Returns the public key bytes.
     *
     * @return the raw public key bytes
     */
    public byte[] publicKeyBytes() {
        return keyPair.publicKeyBytes();
    }

    /**
     * Returns the public key as Base64.
     *
     * @return Base64-encoded public key
     */
    public String publicKeyBase64() {
        return keyPair.publicKeyBase64();
    }

    /**
     * Returns the private key bytes.
     * CAUTION: Handle with care - this is sensitive data.
     *
     * @return the raw private key bytes
     */
    public byte[] privateKeyBytes() {
        return keyPair.privateKeyBytes();
    }

    /**
     * Returns the private key as Base64.
     * CAUTION: Handle with care - this is sensitive data.
     *
     * @return Base64-encoded private key
     */
    public String privateKeyBase64() {
        return keyPair.privateKeyBase64();
    }

    /**
     * Returns the temporary directory for key storage.
     *
     * @return the temp directory path
     */
    public Path tempDir() {
        return tempDir;
    }

    /**
     * Writes the keys to files in the temp directory.
     *
     * @return path to the directory containing the keys
     * @throws Exception if writing fails
     */
    public Path writeKeysToFiles() throws Exception {
        Path privateKeyPath = tempDir.resolve("private.key");
        Path publicKeyPath = tempDir.resolve("public.key");

        Files.writeString(privateKeyPath, privateKeyBase64());
        Files.writeString(publicKeyPath, publicKeyBase64());

        return tempDir;
    }

    /**
     * Creates a KeyPair from Base64-encoded strings.
     *
     * @param privateKeyBase64 Base64-encoded private key
     * @param publicKeyBase64 Base64-encoded public key
     * @return a new SigningKeyPair
     */
    public static SigningKeyPair fromBase64(String privateKeyBase64, String publicKeyBase64) {
        byte[] privateKey = Base64.getDecoder().decode(privateKeyBase64);
        byte[] publicKey = Base64.getDecoder().decode(publicKeyBase64);
        return SigningKeyPair.of(privateKey, publicKey);
    }

    /**
     * Cleans up temporary files.
     */
    public void cleanup() {
        if (tempDir != null) {
            try {
                Files.walk(tempDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
            } catch (Exception ignored) {
            }
        }
    }

    private Path createTempDir() {
        try {
            return Files.createTempDirectory("sdlc-test-keys-");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temp directory for keys", e);
        }
    }

    /**
     * Creates a deterministic key pair from a seed.
     * Note: This generates a new key pair - the seed is just for naming.
     *
     * @param seed the seed string
     * @return a new SigningKeyPair
     */
    public static SigningKeyPair generateFromSeed(String seed) {
        // Note: Java's Ed25519 key generation is not seedable
        // This creates a new random key pair each time
        return SigningKeyPair.generate();
    }

    /**
     * Verifies that a signature was created by this key pair.
     *
     * @param data the original data
     * @param signature the signature to verify
     * @return true if the signature is valid
     */
    public boolean verifySignature(byte[] data, byte[] signature) {
        return keyPair.verify(data, signature);
    }

    /**
     * Verifies that a signature was created by this key pair.
     *
     * @param data the original data string
     * @param signature the signature to verify
     * @return true if the signature is valid
     */
    public boolean verifySignature(String data, byte[] signature) {
        return keyPair.verify(data.getBytes(java.nio.charset.StandardCharsets.UTF_8), signature);
    }
}
