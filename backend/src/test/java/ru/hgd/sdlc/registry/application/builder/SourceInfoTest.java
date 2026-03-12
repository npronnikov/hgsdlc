package ru.hgd.sdlc.registry.application.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SourceInfo")
class SourceInfoTest {

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("should build SourceInfo with required fields")
        void shouldBuildWithRequiredFields() {
            Instant now = Instant.now();

            SourceInfo info = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("abc123def456789012345678901234567890abcd")
                .commitAuthor("test@example.com")
                .committedAt(now)
                .build();

            assertEquals("https://github.com/test/repo.git", info.repositoryUrl());
            assertEquals("abc123def456789012345678901234567890abcd", info.commitSha());
            assertEquals("test@example.com", info.commitAuthor());
            assertEquals(now, info.committedAt());
            assertTrue(info.branch().isEmpty());
            assertTrue(info.tag().isEmpty());
            assertTrue(info.inputChecksums().isEmpty());
        }

        @Test
        @DisplayName("should build SourceInfo with all fields")
        void shouldBuildWithAllFields() {
            Instant now = Instant.now();

            SourceInfo info = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("abc123def456789012345678901234567890abcd")
                .commitAuthor("test@example.com")
                .committedAt(now)
                .branch("main")
                .tag("v1.0.0")
                .inputChecksum("flow.md", "abc123")
                .inputChecksum("phase.md", "def456")
                .build();

            assertEquals("https://github.com/test/repo.git", info.repositoryUrl());
            assertEquals("abc123def456789012345678901234567890abcd", info.commitSha());
            assertEquals("test@example.com", info.commitAuthor());
            assertEquals(now, info.committedAt());
            assertTrue(info.branch().isPresent());
            assertEquals("main", info.branch().get());
            assertTrue(info.tag().isPresent());
            assertEquals("v1.0.0", info.tag().get());
            assertEquals(2, info.inputChecksums().size());
            assertEquals("abc123", info.inputChecksums().get("flow.md"));
            assertEquals("def456", info.inputChecksums().get("phase.md"));
        }
    }

    @Nested
    @DisplayName("accessors")
    class Accessors {

        @Test
        @DisplayName("should return empty optional for null branch")
        void shouldReturnEmptyOptionalForNullBranch() {
            SourceInfo info = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("abc123def456789012345678901234567890abcd")
                .commitAuthor("test@example.com")
                .committedAt(Instant.now())
                .build();

            assertTrue(info.branch().isEmpty());
        }

        @Test
        @DisplayName("should return empty optional for null tag")
        void shouldReturnEmptyOptionalForNullTag() {
            SourceInfo info = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("abc123def456789012345678901234567890abcd")
                .commitAuthor("test@example.com")
                .committedAt(Instant.now())
                .build();

            assertTrue(info.tag().isEmpty());
        }

        @Test
        @DisplayName("should return unmodifiable input checksums")
        void shouldReturnUnmodifiableInputChecksums() {
            SourceInfo info = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("abc123def456789012345678901234567890abcd")
                .commitAuthor("test@example.com")
                .committedAt(Instant.now())
                .inputChecksum("file.md", "hash123")
                .build();

            Map<String, String> checksums = info.inputChecksums();
            assertThrows(UnsupportedOperationException.class, () ->
                checksums.put("new.md", "newhash"));
        }

        @Test
        @DisplayName("should check if has input checksums")
        void shouldCheckIfHasInputChecksums() {
            SourceInfo withChecksums = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("abc123def456789012345678901234567890abcd")
                .commitAuthor("test@example.com")
                .committedAt(Instant.now())
                .inputChecksum("file.md", "hash123")
                .build();

            SourceInfo withoutChecksums = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("abc123def456789012345678901234567890abcd")
                .commitAuthor("test@example.com")
                .committedAt(Instant.now())
                .build();

            assertTrue(withChecksums.hasInputChecksums());
            assertFalse(withoutChecksums.hasInputChecksums());
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("should create SourceInfo with of() method")
        void shouldCreateWithOfMethod() {
            Instant now = Instant.now();

            SourceInfo info = SourceInfo.of(
                "https://github.com/test/repo.git",
                "abc123def456789012345678901234567890abcd",
                "test@example.com",
                now
            );

            assertEquals("https://github.com/test/repo.git", info.repositoryUrl());
            assertEquals("abc123def456789012345678901234567890abcd", info.commitSha());
            assertEquals("test@example.com", info.commitAuthor());
            assertEquals(now, info.committedAt());
            assertTrue(info.inputChecksums().isEmpty());
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("should be equal for same values")
        void shouldBeEqualForSameValues() {
            Instant now = Instant.now();

            SourceInfo info1 = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("abc123def456789012345678901234567890abcd")
                .commitAuthor("test@example.com")
                .committedAt(now)
                .branch("main")
                .build();

            SourceInfo info2 = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("abc123def456789012345678901234567890abcd")
                .commitAuthor("test@example.com")
                .committedAt(now)
                .branch("main")
                .build();

            assertEquals(info1, info2);
            assertEquals(info1.hashCode(), info2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different values")
        void shouldNotBeEqualForDifferentValues() {
            Instant now = Instant.now();

            SourceInfo info1 = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("abc123def456789012345678901234567890abcd")
                .commitAuthor("test@example.com")
                .committedAt(now)
                .build();

            SourceInfo info2 = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/other.git")
                .commitSha("abc123def456789012345678901234567890abcd")
                .commitAuthor("test@example.com")
                .committedAt(now)
                .build();

            assertNotEquals(info1, info2);
        }
    }
}
