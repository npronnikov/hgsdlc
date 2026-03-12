package ru.hgd.sdlc.registry.domain.model.release;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable checksum manifest for a release package.
 * Maps file paths to their SHA-256 hashes for integrity verification.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class ChecksumManifest {

    /**
     * Map of file paths (relative to package root) to their SHA-256 hashes.
     */
    @Singular("entry")
    @NonNull private final Map<String, Sha256Hash> entries;

    /**
     * Returns an unmodifiable view of the entries.
     */
    public Map<String, Sha256Hash> entries() {
        return Collections.unmodifiableMap(entries);
    }

    /**
     * Returns the checksum for a given path.
     *
     * @param path the file path
     * @return the checksum if present
     */
    public Optional<Sha256Hash> checksumFor(String path) {
        return Optional.ofNullable(entries.get(path));
    }

    /**
     * Checks if the manifest contains a checksum for the given path.
     *
     * @param path the file path
     * @return true if a checksum exists
     */
    public boolean contains(String path) {
        return entries.containsKey(path);
    }

    /**
     * Returns the number of entries in the manifest.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Verifies that a file's content matches its expected checksum.
     *
     * @param path the file path
     * @param content the file content
     * @return verification result
     */
    public VerificationResult verify(String path, byte[] content) {
        Sha256Hash expected = entries.get(path);
        if (expected == null) {
            return VerificationResult.notFound(path);
        }

        Sha256Hash actual = Sha256Hash.ofBytes(hashBytes(content));
        if (expected.equals(actual)) {
            return VerificationResult.valid(path);
        } else {
            return VerificationResult.mismatch(path, expected, actual);
        }
    }

    /**
     * Verifies all entries against provided content.
     *
     * @param contents map of path to content
     * @return verification result for all entries
     */
    public BulkVerificationResult verifyAll(Map<String, byte[]> contents) {
        java.util.List<VerificationResult> results = new java.util.ArrayList<>();
        boolean allValid = true;

        for (Map.Entry<String, Sha256Hash> entry : entries.entrySet()) {
            String path = entry.getKey();
            byte[] content = contents.get(path);

            if (content == null) {
                results.add(VerificationResult.notFound(path));
                allValid = false;
            } else {
                VerificationResult result = verify(path, content);
                results.add(result);
                if (!result.valid()) {
                    allValid = false;
                }
            }
        }

        return new BulkVerificationResult(allValid, Collections.unmodifiableList(results));
    }

    private byte[] hashBytes(byte[] content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return digest.digest(content);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Result of a single checksum verification.
     */
    public static final class VerificationResult {
        private final boolean valid;
        private final String path;
        private final String reason;
        private final Sha256Hash expected;
        private final Sha256Hash actual;

        private VerificationResult(boolean valid, String path, String reason,
                                   Sha256Hash expected, Sha256Hash actual) {
            this.valid = valid;
            this.path = path;
            this.reason = reason;
            this.expected = expected;
            this.actual = actual;
        }

        public static VerificationResult valid(String path) {
            return new VerificationResult(true, path, "OK", null, null);
        }

        public static VerificationResult notFound(String path) {
            return new VerificationResult(false, path, "No checksum found for path", null, null);
        }

        public static VerificationResult mismatch(String path, Sha256Hash expected, Sha256Hash actual) {
            return new VerificationResult(false, path, "Checksum mismatch", expected, actual);
        }

        public boolean valid() { return valid; }
        public String path() { return path; }
        public String reason() { return reason; }
        public Optional<Sha256Hash> expected() { return Optional.ofNullable(expected); }
        public Optional<Sha256Hash> actual() { return Optional.ofNullable(actual); }
    }

    /**
     * Result of bulk checksum verification.
     */
    @Getter
    @Accessors(fluent = true)
    @EqualsAndHashCode
    public static final class BulkVerificationResult {
        private final boolean allValid;
        private final java.util.List<VerificationResult> results;

        private BulkVerificationResult(boolean allValid, java.util.List<VerificationResult> results) {
            this.allValid = allValid;
            this.results = results;
        }

        public java.util.List<VerificationResult> results() {
            return Collections.unmodifiableList(results);
        }
    }
}
