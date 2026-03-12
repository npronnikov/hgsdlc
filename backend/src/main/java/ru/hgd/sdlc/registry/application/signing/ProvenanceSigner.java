package ru.hgd.sdlc.registry.application.signing;

import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.provenance.SignedProvenance;

/**
 * Interface for signing provenance records.
 * Implementations provide cryptographic signing capabilities
 * for release provenance using Ed25519 or similar algorithms.
 */
public interface ProvenanceSigner {

    /**
     * Signs a provenance record with the default key.
     *
     * @param provenance the provenance record to sign
     * @return the signed provenance
     * @throws SigningException if signing fails
     * @throws IllegalArgumentException if provenance is null
     */
    SignedProvenance sign(Provenance provenance) throws SigningException;

    /**
     * Returns the key ID being used for signing.
     *
     * @return the key identifier
     */
    String keyId();

    /**
     * Returns the public key for verification as Base64-encoded string.
     *
     * @return Base64-encoded public key
     */
    String publicKeyBase64();
}
