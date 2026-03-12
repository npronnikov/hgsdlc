package ru.hgd.sdlc.registry.domain.model.release;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing a SHA-256 hash.
 * Validates that the hash is exactly 64 lowercase hexadecimal characters.
 */
public final class Sha256Hash {

    private static final Pattern HEX_PATTERN = Pattern.compile("^[a-f0-9]{64}$");

    private final String hex;

    private Sha256Hash(String hex) {
        this.hex = hex;
    }

    /**
     * Creates a Sha256Hash from a hex string.
     *
     * @param hex the hex string (64 lowercase hex characters)
     * @return a new Sha256Hash instance
     * @throws IllegalArgumentException if the hex string is invalid
     */
    @JsonCreator
    public static Sha256Hash of(String hex) {
        if (hex == null || hex.isBlank()) {
            throw new IllegalArgumentException("SHA-256 hash cannot be null or blank");
        }
        String normalized = hex.toLowerCase().trim();
        if (!HEX_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "SHA-256 hash must be exactly 64 lowercase hexadecimal characters: " + hex);
        }
        return new Sha256Hash(normalized);
    }

    /**
     * Creates a Sha256Hash from a byte array.
     *
     * @param bytes the 32-byte array
     * @return a new Sha256Hash instance
     * @throws IllegalArgumentException if the byte array is not 32 bytes
     */
    public static Sha256Hash ofBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 32) {
            throw new IllegalArgumentException("SHA-256 hash must be exactly 32 bytes");
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return new Sha256Hash(sb.toString());
    }

    /**
     * Returns the lowercase hex string representation.
     *
     * @return 64-character lowercase hex string
     */
    @JsonValue
    public String hex() {
        return hex;
    }

    /**
     * Returns the hash as a byte array.
     *
     * @return 32-byte array
     */
    public byte[] toBytes() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sha256Hash that = (Sha256Hash) o;
        return hex.equals(that.hex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hex);
    }

    @Override
    public String toString() {
        return "Sha256Hash{" + hex + "}";
    }
}
