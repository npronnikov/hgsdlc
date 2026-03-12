package ru.hgd.sdlc.registry.application.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReleaseBuildException")
class ReleaseBuildExceptionTest {

    @Test
    @DisplayName("should create exception with message")
    void shouldCreateExceptionWithMessage() {
        ReleaseBuildException ex = new ReleaseBuildException("Build failed");

        assertEquals("Build failed", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("should create exception with message and cause")
    void shouldCreateExceptionWithMessageAndCause() {
        RuntimeException cause = new RuntimeException("Underlying error");
        ReleaseBuildException ex = new ReleaseBuildException("Build failed", cause);

        assertEquals("Build failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("should create exception with cause only")
    void shouldCreateExceptionWithCauseOnly() {
        RuntimeException cause = new RuntimeException("Underlying error");
        ReleaseBuildException ex = new ReleaseBuildException(cause);

        assertEquals(cause, ex.getCause());
        assertTrue(ex.getMessage().contains("Underlying error"));
    }
}
