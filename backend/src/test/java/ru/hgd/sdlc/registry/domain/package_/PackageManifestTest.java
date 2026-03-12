package ru.hgd.sdlc.registry.domain.package_;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PackageManifest")
class PackageManifestTest {

    private static final ReleaseId RELEASE_ID = ReleaseId.of(
        FlowId.of("test-flow"),
        ReleaseVersion.of("1.0.0")
    );

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("should build manifest with all fields")
        void shouldBuildWithAllFields() {
            Instant now = Instant.now();

            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(now)
                .file("flow.ir.json", "abc123")
                .file("provenance.json", "def456")
                .build();

            assertEquals(RELEASE_ID, manifest.releaseId());
            assertEquals(now, manifest.createdAt());
            assertEquals(1, PackageFormat.FORMAT_VERSION); // Default
            assertEquals(2, manifest.fileCount());
        }

        @Test
        @DisplayName("should build manifest with files map")
        void shouldBuildWithFilesMap() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .files(Map.of("file1.txt", "hash1", "file2.txt", "hash2"))
                .build();

            assertEquals(2, manifest.fileCount());
        }
    }

    @Nested
    @DisplayName("checksumFor(String)")
    class ChecksumFor {

        @Test
        @DisplayName("should return checksum for existing file")
        void shouldReturnForExistingFile() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .file("flow.ir.json", "abc123def456")
                .build();

            assertTrue(manifest.checksumFor("flow.ir.json").isPresent());
            assertEquals("abc123def456", manifest.checksumFor("flow.ir.json").get());
        }

        @Test
        @DisplayName("should return empty for non-existing file")
        void shouldReturnEmptyForNonExisting() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .build();

            assertTrue(manifest.checksumFor("nonexistent").isEmpty());
        }
    }

    @Nested
    @DisplayName("containsFile(String)")
    class ContainsFile {

        @Test
        @DisplayName("should return true for existing file")
        void shouldReturnTrueForExisting() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .file("flow.ir.json", "hash")
                .build();

            assertTrue(manifest.containsFile("flow.ir.json"));
        }

        @Test
        @DisplayName("should return false for non-existing file")
        void shouldReturnFalseForNonExisting() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .build();

            assertFalse(manifest.containsFile("nonexistent"));
        }
    }

    @Nested
    @DisplayName("fileCount()")
    class FileCount {

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .file("file1.txt", "hash1")
                .file("file2.txt", "hash2")
                .file("file3.txt", "hash3")
                .build();

            assertEquals(3, manifest.fileCount());
        }

        @Test
        @DisplayName("should return 0 for empty manifest")
        void shouldReturnZeroForEmpty() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .build();

            assertEquals(0, manifest.fileCount());
        }
    }

    @Nested
    @DisplayName("hasRequiredFiles()")
    class HasRequiredFiles {

        @Test
        @DisplayName("should return true when all required files present")
        void shouldReturnTrueWhenAllPresent() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .file(PackageFormat.FILE_FLOW_IR, "hash1")
                .file(PackageFormat.FILE_PROVENANCE, "hash2")
                .file(PackageFormat.FILE_CHECKSUMS, "hash3")
                .build();

            assertTrue(manifest.hasRequiredFiles());
        }

        @Test
        @DisplayName("should return false when flow IR missing")
        void shouldReturnFalseWhenFlowIrMissing() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .file(PackageFormat.FILE_PROVENANCE, "hash2")
                .file(PackageFormat.FILE_CHECKSUMS, "hash3")
                .build();

            assertFalse(manifest.hasRequiredFiles());
        }

        @Test
        @DisplayName("should return false when provenance missing")
        void shouldReturnFalseWhenProvenanceMissing() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .file(PackageFormat.FILE_FLOW_IR, "hash1")
                .file(PackageFormat.FILE_CHECKSUMS, "hash3")
                .build();

            assertFalse(manifest.hasRequiredFiles());
        }

        @Test
        @DisplayName("should return false when checksums missing")
        void shouldReturnFalseWhenChecksumsMissing() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .file(PackageFormat.FILE_FLOW_IR, "hash1")
                .file(PackageFormat.FILE_PROVENANCE, "hash2")
                .build();

            assertFalse(manifest.hasRequiredFiles());
        }
    }

    @Nested
    @DisplayName("flowIrChecksum()")
    class FlowIrChecksum {

        @Test
        @DisplayName("should return flow IR checksum")
        void shouldReturnFlowIrChecksum() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .file(PackageFormat.FILE_FLOW_IR, "flow-hash")
                .build();

            assertTrue(manifest.flowIrChecksum().isPresent());
            assertEquals("flow-hash", manifest.flowIrChecksum().get());
        }
    }

    @Nested
    @DisplayName("provenanceChecksum()")
    class ProvenanceChecksum {

        @Test
        @DisplayName("should return provenance checksum")
        void shouldReturnProvenanceChecksum() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .file(PackageFormat.FILE_PROVENANCE, "provenance-hash")
                .build();

            assertTrue(manifest.provenanceChecksum().isPresent());
            assertEquals("provenance-hash", manifest.provenanceChecksum().get());
        }
    }

    @Nested
    @DisplayName("files()")
    class Files {

        @Test
        @DisplayName("should return unmodifiable map")
        void shouldReturnUnmodifiableMap() {
            PackageManifest manifest = PackageManifest.builder()
                .releaseId(RELEASE_ID)
                .createdAt(Instant.now())
                .file("file.txt", "hash")
                .build();

            assertThrows(UnsupportedOperationException.class,
                () -> manifest.files().put("new", "hash"));
        }
    }
}
