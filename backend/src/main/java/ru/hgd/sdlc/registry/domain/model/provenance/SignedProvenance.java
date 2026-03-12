package ru.hgd.sdlc.registry.domain.model.provenance;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Complete signed provenance containing the provenance record and its signature.
 * Provides verification capabilities to validate provenance authenticity.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class SignedProvenance {

    /**
     * The provenance record being signed.
     */
    @NonNull private final Provenance provenance;

    /**
     * The signature for the provenance.
     */
    @NonNull private final ProvenanceSignature signature;

    /**
     * Verifies the signature against the provided public key.
     * Uses Ed25519 signature verification.
     *
     * @param publicKeyBytes the Ed25519 public key bytes to verify against
     * @return VerificationResult indicating success or failure with reason
     */
    public VerificationResult verify(byte[] publicKeyBytes) {
        if (publicKeyBytes == null || publicKeyBytes.length == 0) {
            return VerificationResult.failure("Public key cannot be null or empty");
        }

        // Verify the public key matches the one in the signature
        byte[] signaturePublicKey = signature.publicKeyBytes();
        if (!java.util.Arrays.equals(publicKeyBytes, signaturePublicKey)) {
            return VerificationResult.failure("Public key mismatch");
        }

        // Verify the algorithm is Ed25519
        if (!"Ed25519".equals(signature.algorithm())) {
            return VerificationResult.failure("Unsupported signature algorithm: " + signature.algorithm());
        }

        try {
            // Get the signable payload (canonical JSON without signature)
            String payload = provenance.toSignablePayload();
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

            // Verify using Ed25519
            boolean valid = verifyEd25519(
                publicKeyBytes,
                signature.valueBytes(),
                payloadBytes
            );

            if (valid) {
                return VerificationResult.success(signature.keyId());
            } else {
                return VerificationResult.failure("Signature verification failed");
            }
        } catch (Exception e) {
            return VerificationResult.failure("Verification error: " + e.getMessage());
        }
    }

    /**
     * Verifies the signature against the public key from the signature itself.
     *
     * @return VerificationResult indicating success or failure with reason
     */
    public VerificationResult verify() {
        return verify(signature.publicKeyBytes());
    }

    /**
     * Internal Ed25519 verification using Java's security API.
     */
    private boolean verifyEd25519(byte[] publicKey, byte[] signatureBytes, byte[] message) {
        try {
            // Use Ed25519 via java.security.eddsa (available in Java 15+)
            java.security.spec.EdECPoint point = decodeEd25519PublicKey(publicKey);
            java.security.spec.EdECPublicKeySpec spec = new java.security.spec.EdECPublicKeySpec(
                new java.security.spec.NamedParameterSpec("Ed25519"), point);
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("Ed25519");
            java.security.PublicKey pubKey = keyFactory.generatePublic(spec);

            java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
            verifier.initVerify(pubKey);
            verifier.update(message);
            return verifier.verify(signatureBytes);
        } catch (Exception e) {
            // Fallback for environments where Ed25519 might not be fully available
            throw new RuntimeException("Ed25519 verification failed", e);
        }
    }

    /**
     * Decodes a raw Ed25519 public key (32 bytes) into an EdECPoint.
     */
    private java.security.spec.EdECPoint decodeEd25519PublicKey(byte[] publicKey) {
        if (publicKey.length != 32) {
            throw new IllegalArgumentException("Ed25519 public key must be 32 bytes");
        }

        // Copy and prepare for conversion
        byte[] keyBytes = Arrays.copyOf(publicKey, 32);

        // The high bit indicates the sign of X
        boolean xOdd = (keyBytes[31] & 0x80) != 0;

        // Clear the high bit for Y coordinate interpretation
        keyBytes[31] &= 0x7f;

        // Convert little-endian to big-endian for BigInteger
        reverse(keyBytes);

        java.math.BigInteger y = new java.math.BigInteger(1, keyBytes);
        return new java.security.spec.EdECPoint(xOdd, y);
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

    /**
     * Result of a signature verification operation.
     */
    @Getter
    @Accessors(fluent = true)
    @Builder
    public static final class VerificationResult {
        private final boolean valid;
        private final String keyId;
        private final String reason;

        /**
         * Creates a successful verification result.
         */
        public static VerificationResult success(String keyId) {
            return VerificationResult.builder()
                .valid(true)
                .keyId(keyId)
                .build();
        }

        /**
         * Creates a failed verification result.
         */
        public static VerificationResult failure(String reason) {
            return VerificationResult.builder()
                .valid(false)
                .reason(reason)
                .build();
        }
    }
}
