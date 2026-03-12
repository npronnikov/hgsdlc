package ru.hgd.sdlc.registry.domain.model.provenance;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Ed25519 key pair wrapper for signing and verification.
 * Provides a simple API for generating keys and signing data.
 */
public final class SigningKeyPair {

    private static final String ALGORITHM = "Ed25519";
    private static final int PUBLIC_KEY_SIZE = 32;
    private static final int PRIVATE_KEY_SIZE = 32;

    private final byte[] privateKeyBytes;
    private final byte[] publicKeyBytes;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    private SigningKeyPair(byte[] privateKeyBytes, byte[] publicKeyBytes,
                          PrivateKey privateKey, PublicKey publicKey) {
        this.privateKeyBytes = Arrays.copyOf(privateKeyBytes, privateKeyBytes.length);
        this.publicKeyBytes = Arrays.copyOf(publicKeyBytes, publicKeyBytes.length);
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    /**
     * Generates a new Ed25519 key pair.
     *
     * @return a new SigningKeyPair instance
     * @throws IllegalStateException if key generation fails
     */
    public static SigningKeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            KeyPair keyPair = generator.generateKeyPair();

            // Extract raw bytes from keys
            byte[] publicKeyBytes = extractRawPublicKey(keyPair.getPublic());
            byte[] privateKeyBytes = extractRawPrivateKey(keyPair.getPrivate());

            return new SigningKeyPair(
                privateKeyBytes,
                publicKeyBytes,
                keyPair.getPrivate(),
                keyPair.getPublic()
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Ed25519 algorithm not available", e);
        }
    }

    /**
     * Creates a SigningKeyPair from existing key bytes.
     *
     * @param privateKeyBytes the 32-byte private key
     * @param publicKeyBytes the 32-byte public key
     * @return a new SigningKeyPair instance
     * @throws IllegalArgumentException if keys are invalid
     */
    public static SigningKeyPair of(byte[] privateKeyBytes, byte[] publicKeyBytes) {
        if (privateKeyBytes == null || privateKeyBytes.length != PRIVATE_KEY_SIZE) {
            throw new IllegalArgumentException(
                "Private key must be " + PRIVATE_KEY_SIZE + " bytes");
        }
        if (publicKeyBytes == null || publicKeyBytes.length != PUBLIC_KEY_SIZE) {
            throw new IllegalArgumentException(
                "Public key must be " + PUBLIC_KEY_SIZE + " bytes");
        }

        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);

            // Reconstruct public key
            EdECPoint point = decodeEd25519PublicKey(publicKeyBytes);
            EdECPublicKeySpec publicKeySpec = new EdECPublicKeySpec(
                new NamedParameterSpec(ALGORITHM), point);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            // For private key, we need to use PKCS8 encoding
            // Ed25519 private key in PKCS8 format is 48 bytes
            byte[] pkcs8PrivateKey = wrapPrivateKeyToPKCS8(privateKeyBytes);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(pkcs8PrivateKey);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            return new SigningKeyPair(
                Arrays.copyOf(privateKeyBytes, privateKeyBytes.length),
                Arrays.copyOf(publicKeyBytes, publicKeyBytes.length),
                privateKey,
                publicKey
            );
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Failed to reconstruct keys", e);
        }
    }

    /**
     * Signs the given data with the private key.
     *
     * @param data the data to sign
     * @return the signature bytes
     * @throws IllegalStateException if signing fails
     */
    public byte[] sign(byte[] data) {
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey);
            signer.update(data);
            return signer.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | java.security.SignatureException e) {
            throw new IllegalStateException("Signing failed", e);
        }
    }

    /**
     * Signs the given string with the private key.
     *
     * @param data the string to sign (UTF-8 encoded)
     * @return the signature bytes
     * @throws IllegalStateException if signing fails
     */
    public byte[] sign(String data) {
        return sign(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Verifies a signature against the given data.
     *
     * @param data the original data
     * @param signature the signature to verify
     * @return true if the signature is valid
     * @throws IllegalStateException if verification fails
     */
    public boolean verify(byte[] data, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException | java.security.SignatureException e) {
            throw new IllegalStateException("Verification failed", e);
        }
    }

    /**
     * Returns the raw private key bytes (32 bytes).
     * CAUTION: Handle with care - this is sensitive data.
     *
     * @return copy of the private key bytes
     */
    public byte[] privateKeyBytes() {
        return Arrays.copyOf(privateKeyBytes, privateKeyBytes.length);
    }

    /**
     * Returns the raw public key bytes (32 bytes).
     *
     * @return copy of the public key bytes
     */
    public byte[] publicKeyBytes() {
        return Arrays.copyOf(publicKeyBytes, publicKeyBytes.length);
    }

    /**
     * Returns the public key as a Base64-encoded string.
     *
     * @return Base64-encoded public key
     */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKeyBytes);
    }

    /**
     * Returns the private key as a Base64-encoded string.
     * CAUTION: Handle with care - this is sensitive data.
     *
     * @return Base64-encoded private key
     */
    public String privateKeyBase64() {
        return Base64.getEncoder().encodeToString(privateKeyBytes);
    }

    /**
     * Extracts the raw 32-byte public key from a PublicKey object.
     */
    private static byte[] extractRawPublicKey(PublicKey publicKey) {
        // X509 encoded key for Ed25519 has a 12-byte prefix
        byte[] encoded = publicKey.getEncoded();
        if (encoded.length == 44) {
            // Standard X509 format: 12-byte prefix + 32-byte key
            return Arrays.copyOfRange(encoded, 12, 44);
        }
        throw new IllegalArgumentException("Unexpected public key format: " + encoded.length + " bytes");
    }

    /**
     * Extracts the raw 32-byte private key from a PrivateKey object.
     */
    private static byte[] extractRawPrivateKey(PrivateKey privateKey) {
        // PKCS8 encoded key for Ed25519 has additional prefix
        byte[] encoded = privateKey.getEncoded();
        if (encoded.length == 48) {
            // Standard PKCS8 format: 16-byte prefix + 32-byte key
            return Arrays.copyOfRange(encoded, 16, 48);
        }
        throw new IllegalArgumentException("Unexpected private key format: " + encoded.length + " bytes");
    }

    /**
     * Wraps a raw 32-byte private key into PKCS8 format.
     */
    private static byte[] wrapPrivateKeyToPKCS8(byte[] rawPrivateKey) {
        // Ed25519 OID: 1.3.101.112
        // PKCS8 format for Ed25519:
        // 30 2e                                          -- SEQUENCE (46 bytes)
        //    02 01 00                                    -- version INTEGER 0
        //    30 05                                       -- algorithmIdentifier SEQUENCE
        //       06 03 2b 65 70                          -- OID for Ed25519
        //    04 22                                       -- OCTET STRING (34 bytes)
        //       04 20                                    -- OCTET STRING wrapper (32 bytes)
        //          [32 bytes of private key]
        byte[] pkcs8 = new byte[48];
        pkcs8[0] = 0x30; pkcs8[1] = 0x2e;  // SEQUENCE
        pkcs8[2] = 0x02; pkcs8[3] = 0x01; pkcs8[4] = 0x00;  // version
        pkcs8[5] = 0x30; pkcs8[6] = 0x05;  // algorithmIdentifier
        pkcs8[7] = 0x06; pkcs8[8] = 0x03;  // OID
        pkcs8[9] = 0x2b; pkcs8[10] = 0x65; pkcs8[11] = 0x70;  // Ed25519 OID
        pkcs8[12] = 0x04; pkcs8[13] = 0x22;  // OCTET STRING
        pkcs8[14] = 0x04; pkcs8[15] = 0x20;  // inner OCTET STRING
        System.arraycopy(rawPrivateKey, 0, pkcs8, 16, 32);
        return pkcs8;
    }

    /**
     * Decodes a raw Ed25519 public key (32 bytes) into an EdECPoint.
     */
    private static EdECPoint decodeEd25519PublicKey(byte[] publicKey) {
        if (publicKey.length != PUBLIC_KEY_SIZE) {
            throw new IllegalArgumentException("Ed25519 public key must be " + PUBLIC_KEY_SIZE + " bytes");
        }

        // Copy and prepare for conversion
        byte[] keyBytes = Arrays.copyOf(publicKey, PUBLIC_KEY_SIZE);

        // The high bit indicates the sign of X
        boolean xOdd = (keyBytes[31] & 0x80) != 0;

        // Clear the high bit for Y coordinate interpretation
        keyBytes[31] &= 0x7f;

        // Convert little-endian to big-endian for BigInteger
        reverse(keyBytes);

        java.math.BigInteger y = new java.math.BigInteger(1, keyBytes);
        return new EdECPoint(xOdd, y);
    }

    /**
     * Reverses a byte array in place.
     */
    private static void reverse(byte[] array) {
        for (int i = 0; i < array.length / 2; i++) {
            byte temp = array[i];
            array[i] = array[array.length - 1 - i];
            array[array.length - 1 - i] = temp;
        }
    }

    @Override
    public String toString() {
        return "SigningKeyPair{publicKey=" + publicKeyBase64() + "}";
    }
}
