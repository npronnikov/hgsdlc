package ru.hgd.sdlc.registry.application.signing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SigningException.
 */
class SigningExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        SigningException ex = new SigningException("Test message");

        assertEquals("Test message", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void shouldCreateWithMessageAndCause() {
        Exception cause = new RuntimeException("Root cause");
        SigningException ex = new SigningException("Test message", cause);

        assertEquals("Test message", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void shouldCreateWithCause() {
        Exception cause = new RuntimeException("Root cause");
        SigningException ex = new SigningException(cause);

        assertSame(cause, ex.getCause());
        assertEquals("java.lang.RuntimeException: Root cause", ex.getMessage());
    }

    @Test
    void shouldBeRuntimeException() {
        SigningException ex = new SigningException("Test");

        assertTrue(ex instanceof RuntimeException);
    }
}
