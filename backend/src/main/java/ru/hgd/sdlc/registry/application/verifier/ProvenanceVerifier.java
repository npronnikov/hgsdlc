package ru.hgd.sdlc.registry.application.verifier;

import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;

/**
 * Service interface for verifying the provenance of release packages.
 * Validates signatures, checksums, and required metadata fields.
 */
public interface ProvenanceVerifier {

    /**
     * Verifies the provenance of a release package.
     * Performs comprehensive verification including:
     * <ul>
     *   <li>Provenance presence check</li>
     *   <li>Required field validation</li>
     *   <li>Checksum verification</li>
     *   <li>Signature verification (if present)</li>
     *   <li>Builder info validation</li>
     *   <li>Git commit SHA format validation</li>
     * </ul>
     *
     * @param pkg the release package to verify
     * @return verification result indicating success or listing issues found
     * @throws IllegalArgumentException if pkg is null
     */
    VerificationResult verify(ReleasePackage pkg);

    /**
     * Verifies the signature on provenance.
     * Validates that the signature is valid for the given provenance
     * using the public key embedded in the signature itself.
     *
     * @param provenance the provenance with signature to verify
     * @return verification result indicating success or listing issues found
     * @throws IllegalArgumentException if provenance is null
     */
    VerificationResult verifySignature(Provenance provenance);
}
