package ru.hgd.sdlc.compiler.domain.ir.serialization;

import ru.hgd.sdlc.compiler.domain.compiler.CompiledIR;
import ru.hgd.sdlc.shared.hashing.Sha256;

/**
 * Content-addressed checksum utility for compiled IR.
 * Used for IR identity and integrity verification.
 *
 * <p>Per ADR-002: IR is content-addressed for deterministic identity.
 * The checksum is computed from the serialized IR representation.
 */
public final class IRChecksum {

    private final IRSerializer serializer;

    /**
     * Creates a new IRChecksum with the default JSON serializer.
     */
    public IRChecksum() {
        this.serializer = new JsonIRSerializer();
    }

    /**
     * Creates a new IRChecksum with a custom serializer.
     *
     * @param serializer the serializer to use for computing checksums
     */
    public IRChecksum(IRSerializer serializer) {
        this.serializer = serializer;
    }

    /**
     * Computes the SHA-256 checksum of a compiled IR.
     * The checksum is computed from the serialized representation.
     *
     * @param ir the compiled IR
     * @return the SHA-256 checksum
     */
    public Sha256 compute(CompiledIR ir) {
        try {
            String serialized = serializer.serialize(ir);
            return Sha256.of(serialized);
        } catch (SerializationException e) {
            throw new RuntimeException("Failed to compute IR checksum: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies that a compiled IR matches an expected checksum.
     *
     * @param ir the compiled IR
     * @param expectedChecksum the expected checksum
     * @return true if the checksums match
     */
    public boolean verify(CompiledIR ir, Sha256 expectedChecksum) {
        Sha256 actualChecksum = compute(ir);
        return actualChecksum.equals(expectedChecksum);
    }

    /**
     * Verifies that a compiled IR matches an expected checksum hex value.
     *
     * @param ir the compiled IR
     * @param expectedHex the expected checksum as hex string
     * @return true if the checksums match
     */
    public boolean verify(CompiledIR ir, String expectedHex) {
        Sha256 actualChecksum = compute(ir);
        return actualChecksum.hexValue().equals(expectedHex);
    }

    /**
     * Computes the checksum of serialized IR data.
     *
     * @param serializedData the serialized IR data
     * @return the SHA-256 checksum
     */
    public static Sha256 ofSerialized(String serializedData) {
        return Sha256.of(serializedData);
    }
}
