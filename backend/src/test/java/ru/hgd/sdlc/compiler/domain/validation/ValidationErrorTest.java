package ru.hgd.sdlc.compiler.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidationError")
class ValidationErrorTest {

    @Test
    @DisplayName("creates error with all fields")
    void createsErrorWithAllFields() {
        SourceLocation location = SourceLocation.of("test.md", 10, 5);
        ValidationError error = ValidationError.of("E001", "Test error", location, Severity.ERROR);

        assertEquals("E001", error.code());
        assertEquals("Test error", error.message());
        assertEquals(location, error.location());
        assertEquals(Severity.ERROR, error.severity());
        assertTrue(error.isError());
        assertFalse(error.isWarning());
    }

    @Test
    @DisplayName("creates warning with factory method")
    void createsWarningWithFactoryMethod() {
        SourceLocation location = SourceLocation.of("test.md", 1);
        ValidationError warning = ValidationError.warning("W001", "Test warning", location);

        assertEquals("W001", warning.code());
        assertEquals("Test warning", warning.message());
        assertEquals(Severity.WARNING, warning.severity());
        assertTrue(warning.isWarning());
        assertFalse(warning.isError());
    }

    @Test
    @DisplayName("creates error with factory method")
    void createsErrorWithFactoryMethod() {
        SourceLocation location = SourceLocation.of("test.md", 1);
        ValidationError error = ValidationError.error("E001", "Test error", location);

        assertEquals(Severity.ERROR, error.severity());
        assertTrue(error.isError());
    }

    @Test
    @DisplayName("throws when code is null")
    void throwsWhenCodeIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationError.error(null, "message", SourceLocation.unknown()));
    }

    @Test
    @DisplayName("throws when message is null")
    void throwsWhenMessageIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
            ValidationError.error("E001", null, SourceLocation.unknown()));
    }

    @Test
    @DisplayName("creates error at different location")
    void createsErrorAtDifferentLocation() {
        SourceLocation location1 = SourceLocation.of("test.md", 1);
        SourceLocation location2 = SourceLocation.of("test.md", 5);

        ValidationError error = ValidationError.error("E001", "Test error", location1);
        ValidationError atNewLocation = error.atLocation(location2);

        assertEquals(location2, atNewLocation.location());
        assertEquals(error.code(), atNewLocation.code());
        assertEquals(error.message(), atNewLocation.message());
    }

    @Test
    @DisplayName("formats toString correctly")
    void formatsToStringCorrectly() {
        SourceLocation location = SourceLocation.of("test.md", 10, 5);
        ValidationError error = ValidationError.error("E001", "Test error", location);

        String str = error.toString();
        assertTrue(str.contains("Error"));
        assertTrue(str.contains("E001"));
        assertTrue(str.contains("Test error"));
        assertTrue(str.contains("test.md"));
    }
}
