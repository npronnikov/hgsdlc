package ru.hgd.sdlc.registry.domain.model.release;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChecksumManifest")
class ChecksumManifestTest {

    // SHA-256 of empty string
    private static final Sha256Hash HASH1 = Sha256Hash.of("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    // SHA-256 of "a"
    private static final Sha256Hash HASH2 = Sha256Hash.of("ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb");

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("should build manifest with entries")
        void shouldBuildWithEntries() {
            ChecksumManifest manifest = ChecksumManifest.builder()
                .entry("flow.ir.json", HASH1)
                .entry("flow.md", HASH2)
                .build();

            assertEquals(2, manifest.size());
            assertTrue(manifest.contains("flow.ir.json"));
            assertTrue(manifest.contains("flow.md"));
        }
    }

    @Nested
    @DisplayName("checksumFor(String)")
    class ChecksumFor {

        @Test
        @DisplayName("should return checksum for existing path")
        void shouldReturnForExistingPath() {
            ChecksumManifest manifest = ChecksumManifest.builder()
                .entry("flow.ir.json", HASH1)
                .build();

            assertTrue(manifest.checksumFor("flow.ir.json").isPresent());
            assertEquals(HASH1, manifest.checksumFor("flow.ir.json").get());
        }

        @Test
        @DisplayName("should return empty for non-existing path")
        void shouldReturnEmptyForNonExisting() {
            ChecksumManifest manifest = ChecksumManifest.builder().build();

            assertTrue(manifest.checksumFor("nonexistent").isEmpty());
        }
    }

    @Nested
    @DisplayName("verify(String, byte[])")
    class Verify {

        @Test
        @DisplayName("should return valid for matching content")
        void shouldReturnValidForMatchingContent() {
            // Create hash from known content
            byte[] content = "test content".getBytes();
            java.security.MessageDigest digest;
            try {
                digest = java.security.MessageDigest.getInstance("SHA-256");
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            byte[] hashBytes = digest.digest(content);
            Sha256Hash expectedHash = Sha256Hash.ofBytes(hashBytes);

            ChecksumManifest manifest = ChecksumManifest.builder()
                .entry("file.txt", expectedHash)
                .build();

            ChecksumManifest.VerificationResult result = manifest.verify("file.txt", content);

            assertTrue(result.valid());
            assertEquals("OK", result.reason());
        }

        @Test
        @DisplayName("should return mismatch for non-matching content")
        void shouldReturnMismatchForNonMatching() {
            ChecksumManifest manifest = ChecksumManifest.builder()
                .entry("file.txt", HASH1)
                .build();

            ChecksumManifest.VerificationResult result = manifest.verify("file.txt", "different content".getBytes());

            assertFalse(result.valid());
            assertEquals("Checksum mismatch", result.reason());
        }

        @Test
        @DisplayName("should return not found for missing path")
        void shouldReturnNotFoundForMissingPath() {
            ChecksumManifest manifest = ChecksumManifest.builder().build();

            ChecksumManifest.VerificationResult result = manifest.verify("missing.txt", "content".getBytes());

            assertFalse(result.valid());
            assertEquals("No checksum found for path", result.reason());
        }
    }

    @Nested
    @DisplayName("verifyAll(Map)")
    class VerifyAll {

        @Test
        @DisplayName("should verify all entries")
        void shouldVerifyAllEntries() {
            // Create hashes from known content
            byte[] content1 = "content1".getBytes();
            byte[] content2 = "content2".getBytes();
            java.security.MessageDigest digest;
            try {
                digest = java.security.MessageDigest.getInstance("SHA-256");
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            Sha256Hash hash1 = Sha256Hash.ofBytes(digest.digest(content1));
            Sha256Hash hash2 = Sha256Hash.ofBytes(digest.digest(content2));

            ChecksumManifest manifest = ChecksumManifest.builder()
                .entry("file1.txt", hash1)
                .entry("file2.txt", hash2)
                .build();

            ChecksumManifest.BulkVerificationResult result = manifest.verifyAll(
                Map.of("file1.txt", content1, "file2.txt", content2)
            );

            assertTrue(result.allValid());
            assertEquals(2, result.results().size());
        }

        @Test
        @DisplayName("should fail if content missing")
        void shouldFailIfContentMissing() {
            ChecksumManifest manifest = ChecksumManifest.builder()
                .entry("file.txt", HASH1)
                .build();

            ChecksumManifest.BulkVerificationResult result = manifest.verifyAll(Map.of());

            assertFalse(result.allValid());
            assertEquals(1, result.results().size());
            assertFalse(result.results().get(0).valid());
        }
    }

    @Nested
    @DisplayName("entries()")
    class Entries {

        @Test
        @DisplayName("should return unmodifiable map")
        void shouldReturnUnmodifiableMap() {
            ChecksumManifest manifest = ChecksumManifest.builder()
                .entry("file.txt", HASH1)
                .build();

            assertThrows(UnsupportedOperationException.class,
                () -> manifest.entries().put("new", HASH2));
        }
    }

    @Nested
    @DisplayName("size()")
    class Size {

        @Test
        @DisplayName("should return number of entries")
        void shouldReturnNumberOfEntries() {
            ChecksumManifest manifest = ChecksumManifest.builder()
                .entry("file1.txt", HASH1)
                .entry("file2.txt", HASH2)
                .build();

            assertEquals(2, manifest.size());
        }

        @Test
        @DisplayName("should return 0 for empty manifest")
        void shouldReturnZeroForEmpty() {
            ChecksumManifest manifest = ChecksumManifest.builder().build();

            assertEquals(0, manifest.size());
        }
    }
}
