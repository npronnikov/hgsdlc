package ru.hgd.sdlc.registry.application.signing;

import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.provenance.ProvenanceSignature;
import ru.hgd.sdlc.registry.domain.model.provenance.SignedProvenance;
import ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair;

import java.time.Instant;

/**
 * Ed25519-based implementation of ProvenanceSigner.
 * Uses the SigningKeyPair to sign provenance records.
 */
@Getter
@Accessors(fluent = true)
public final class Ed25519Signer implements ProvenanceSigner {

    private static final String ALGORITHM = "Ed25519";

    @NonNull private final SigningKeyPair keyPair;
    @NonNull private final String keyId;

    /**
     * Creates an Ed25519Signer with the given key pair and key ID.
     *
     * @param keyPair the signing key pair
     * @param keyId the identifier for this key
     * @throws NullPointerException if keyPair or keyId is null
     */
    public Ed25519Signer(@NonNull SigningKeyPair keyPair, @NonNull String keyId) {
        this.keyPair = keyPair;
        this.keyId = keyId;
    }

    @Override
    public SignedProvenance sign(Provenance provenance) throws SigningException {
        if (provenance == null) {
            throw new IllegalArgumentException("Provenance cannot be null");
        }

        try {
            // Get the signable payload (canonical JSON without signature)
            String payload = provenance.toSignablePayload();

            // Sign the payload
            byte[] signature = keyPair.sign(payload);

            // Create the signature wrapper
            ProvenanceSignature provSig = ProvenanceSignature.of(
                ALGORITHM,
                keyId,
                keyPair.publicKeyBytes(),
                signature,
                Instant.now()
            );

            // Create the signed provenance with signature attached
            Provenance signedProvenanceRecord = provenance.withSignature(provSig);

            return SignedProvenance.builder()
                .provenance(signedProvenanceRecord)
                .signature(provSig)
                .build();
        } catch (IllegalStateException e) {
            throw new SigningException("Failed to sign provenance", e);
        }
    }

    @Override
    public String publicKeyBase64() {
        return keyPair.publicKeyBase64();
    }
}
