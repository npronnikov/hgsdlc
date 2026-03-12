package ru.hgd.sdlc.registry.application.verifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProvenanceVerifier interface contract.
 */
class ProvenanceVerifierTest {

    @Test
    void defaultProvenanceVerifierShouldImplementInterface() {
        ProvenanceVerifier verifier = new DefaultProvenanceVerifier();

        assertTrue(verifier instanceof ProvenanceVerifier);
    }

    @Test
    void shouldThrowOnNullPackage() {
        ProvenanceVerifier verifier = new DefaultProvenanceVerifier();

        assertThrows(NullPointerException.class, () -> verifier.verify(null));
    }

    @Test
    void shouldThrowOnNullProvenanceForSignature() {
        ProvenanceVerifier verifier = new DefaultProvenanceVerifier();

        assertThrows(NullPointerException.class, () -> verifier.verifySignature(null));
    }
}
