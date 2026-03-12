package ru.hgd.sdlc.registry.application.verifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VerificationSeverity enum.
 */
class VerificationSeverityTest {

    @Test
    void shouldHaveErrorSeverity() {
        assertEquals("ERROR", VerificationSeverity.ERROR.name());
    }

    @Test
    void shouldHaveWarningSeverity() {
        assertEquals("WARNING", VerificationSeverity.WARNING.name());
    }

    @Test
    void shouldHaveInfoSeverity() {
        assertEquals("INFO", VerificationSeverity.INFO.name());
    }

    @Test
    void shouldHaveExactlyThreeValues() {
        VerificationSeverity[] values = VerificationSeverity.values();
        assertEquals(3, values.length);
    }
}
