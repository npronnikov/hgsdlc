package ru.hgd.sdlc.registry.application.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReleaseNotFoundException")
class ReleaseNotFoundExceptionTest {

    private static ReleaseId releaseId(String flowId, String version) {
        return ReleaseId.of(FlowId.of(flowId), ReleaseVersion.of(version));
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should create with release ID and message")
        void shouldCreateWithReleaseIdAndMessage() {
            ReleaseId id = releaseId("test-flow", "1.0.0");
            String message = "Release not found in registry";

            ReleaseNotFoundException exception = new ReleaseNotFoundException(id, message);

            assertEquals(id, exception.releaseId());
            assertEquals(message, exception.getMessage());
        }

        @Test
        @DisplayName("should create with release ID only")
        void shouldCreateWithReleaseIdOnly() {
            ReleaseId id = releaseId("test-flow", "1.0.0");

            ReleaseNotFoundException exception = new ReleaseNotFoundException(id);

            assertEquals(id, exception.releaseId());
            assertTrue(exception.getMessage().contains("test-flow@1.0.0"));
        }

        @Test
        @DisplayName("should handle null release ID")
        void shouldHandleNullReleaseId() {
            ReleaseNotFoundException exception = new ReleaseNotFoundException(null);

            assertNull(exception.releaseId());
            assertTrue(exception.getMessage().contains("null"));
        }
    }
}
