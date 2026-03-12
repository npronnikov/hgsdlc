package ru.hgd.sdlc.registry.domain.model.provenance;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Base64;

/**
 * Immutable signature for a provenance record.
 * Contains the cryptographic signature and key information
 * for verification.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class ProvenanceSignature {

    /**
     * Signature algorithm (e.g., "Ed25519").
     */
    @NonNull private final String algorithm;

    /**
     * Identifier for the signing key.
     */
    @NonNull private final String keyId;

    /**
     * Public key used for verification (Base64 encoded).
     */
    @NonNull private final String publicKey;

    /**
     * Signature value (Base64 encoded).
     */
    @NonNull private final String value;

    /**
     * Timestamp when the signature was created.
     */
    @NonNull private final Instant signedAt;

    /**
     * Returns the public key bytes.
     *
     * @return decoded public key bytes
     */
    public byte[] publicKeyBytes() {
        return Base64.getDecoder().decode(publicKey);
    }

    /**
     * Returns the signature bytes.
     *
     * @return decoded signature bytes
     */
    public byte[] valueBytes() {
        return Base64.getDecoder().decode(value);
    }

    /**
     * Creates a signature from raw bytes.
     *
     * @param algorithm the signature algorithm
     * @param keyId the key identifier
     * @param publicKey the public key bytes
     * @param value the signature bytes
     * @param signedAt the timestamp
     * @return a new ProvenanceSignature
     */
    public static ProvenanceSignature of(
            String algorithm,
            String keyId,
            byte[] publicKey,
            byte[] value,
            Instant signedAt) {
        return ProvenanceSignature.builder()
            .algorithm(algorithm)
            .keyId(keyId)
            .publicKey(Base64.getEncoder().encodeToString(publicKey))
            .value(Base64.getEncoder().encodeToString(value))
            .signedAt(signedAt)
            .build();
    }
}
