package ru.hgd.sdlc.registry.application.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VersionConflictException")
class VersionConflictExceptionTest {

    private static ReleaseId releaseId(String flowId, String version) {
        return ReleaseId.of(FlowId.of(flowId), ReleaseVersion.of(version));
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should create with conflicting releases and message")
        void shouldCreateWithConflictingReleasesAndMessage() {
            Set<ReleaseId> conflicts = new HashSet<>();
            conflicts.add(releaseId("skill-x", "1.0.0"));
            conflicts.add(releaseId("skill-x", "2.0.0"));

            String message = "Skill skill-x has conflicting versions";

            VersionConflictException exception = new VersionConflictException(conflicts, message);

            assertEquals(conflicts, exception.conflictingReleases());
            assertEquals(message, exception.getMessage());
        }

        @Test
        @DisplayName("should create with conflicting releases only")
        void shouldCreateWithConflictingReleasesOnly() {
            Set<ReleaseId> conflicts = new HashSet<>();
            conflicts.add(releaseId("skill-x", "1.0.0"));
            conflicts.add(releaseId("skill-x", "2.0.0"));

            VersionConflictException exception = new VersionConflictException(conflicts);

            assertEquals(conflicts, exception.conflictingReleases());
            assertTrue(exception.getMessage().contains("Version conflict"));
            assertTrue(exception.getMessage().contains("skill-x@1.0.0"));
            assertTrue(exception.getMessage().contains("skill-x@2.0.0"));
        }

        @Test
        @DisplayName("should handle empty conflict set")
        void shouldHandleEmptyConflictSet() {
            VersionConflictException exception = new VersionConflictException(Set.of());

            assertTrue(exception.conflictingReleases().isEmpty());
            assertTrue(exception.getMessage().contains("Version conflict"));
        }

        @Test
        @DisplayName("should handle null conflict set")
        void shouldHandleNullConflictSet() {
            VersionConflictException exception = new VersionConflictException(null);

            assertTrue(exception.conflictingReleases().isEmpty());
        }

        @Test
        @DisplayName("should return unmodifiable set")
        void shouldReturnUnmodifiableSet() {
            Set<ReleaseId> conflicts = new HashSet<>();
            conflicts.add(releaseId("skill-x", "1.0.0"));

            VersionConflictException exception = new VersionConflictException(conflicts);

            assertThrows(UnsupportedOperationException.class, () ->
                exception.conflictingReleases().add(releaseId("skill-y", "1.0.0")));
        }
    }
}
