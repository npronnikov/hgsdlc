package ru.hgd.sdlc.shared.hashing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hashing utility for content addressing.
 */
public final class Sha256 {

    private final String hexValue;

    private Sha256(String hexValue) {
        this.hexValue = hexValue;
    }

    public static Sha256 of(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return new Sha256(bytesToHex(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static Sha256 of(String content) {
        return of(content.getBytes(StandardCharsets.UTF_8));
    }

    public String hexValue() {
        return hexValue;
    }

    @Override
    public String toString() {
        return hexValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sha256 sha256 = (Sha256) o;
        return hexValue.equals(sha256.hexValue);
    }

    @Override
    public int hashCode() {
        return hexValue.hashCode();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
