package ru.hgd.sdlc.compiler.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidationResult")
class ValidationResultTest {

    private final SourceLocation location = SourceLocation.of("test.md", 1);

    @Nested
    @DisplayName("Valid result")
    class ValidResultTest {

        @Test
        @DisplayName("creates valid result with no warnings")
        void createsValidResultWithNoWarnings() {
            ValidationResult result = ValidationResult.valid();

            assertTrue(result.isValid());
            assertFalse(result.hasIssues());
            assertTrue(result.errors().isEmpty());
            assertTrue(result.warnings().isEmpty());
            assertTrue(result.allIssues().isEmpty());
        }

        @Test
        @DisplayName("creates valid result with warnings")
        void createsValidResultWithWarnings() {
            ValidationError warning = ValidationError.warning("W001", "Test warning", location);
            ValidationResult result = ValidationResult.valid(List.of(warning));

            assertTrue(result.isValid());
            assertTrue(result.hasIssues());
            assertTrue(result.errors().isEmpty());
            assertEquals(1, result.warnings().size());
            assertEquals(1, result.allIssues().size());
        }

        @Test
        @DisplayName("throws when creating valid result with errors")
        void throwsWhenCreatingValidResultWithErrors() {
            ValidationError error = ValidationError.error("E001", "Test error", location);

            assertThrows(IllegalArgumentException.class, () -> ValidationResult.valid(List.of(error)));
        }
    }

    @Nested
    @DisplayName("Invalid result")
    class InvalidResultTest {

        @Test
        @DisplayName("creates invalid result with errors")
        void createsInvalidResultWithErrors() {
            ValidationError error = ValidationError.error("E001", "Test error", location);
            ValidationResult result = ValidationResult.invalid(List.of(error));

            assertFalse(result.isValid());
            assertTrue(result.hasIssues());
            assertEquals(1, result.errors().size());
            assertTrue(result.warnings().isEmpty());
            assertEquals(1, result.allIssues().size());
        }

        @Test
        @DisplayName("creates invalid result with errors and warnings")
        void createsInvalidResultWithErrorsAndWarnings() {
            ValidationError error = ValidationError.error("E001", "Test error", location);
            ValidationError warning = ValidationError.warning("W001", "Test warning", location);
            ValidationResult result = ValidationResult.invalid(List.of(error), List.of(warning));

            assertFalse(result.isValid());
            assertTrue(result.hasIssues());
            assertEquals(1, result.errors().size());
            assertEquals(1, result.warnings().size());
            assertEquals(2, result.allIssues().size());
        }

        @Test
        @DisplayName("throws when creating invalid result with no errors")
        void throwsWhenCreatingInvalidResultWithNoErrors() {
            assertThrows(IllegalArgumentException.class, () -> ValidationResult.invalid(List.of()));
        }

        @Test
        @DisplayName("throws when creating invalid result with warning in errors")
        void throwsWhenCreatingInvalidResultWithWarningInErrors() {
            ValidationError warning = ValidationError.warning("W001", "Test warning", location);

            assertThrows(IllegalArgumentException.class, () -> ValidationResult.invalid(List.of(warning)));
        }
    }

    @Nested
    @DisplayName("combine")
    class CombineTest {

        @Test
        @DisplayName("combines two valid results")
        void combinesTwoValidResults() {
            ValidationError warning1 = ValidationError.warning("W001", "Warning 1", location);
            ValidationError warning2 = ValidationError.warning("W002", "Warning 2", location);

            ValidationResult result1 = ValidationResult.valid(List.of(warning1));
            ValidationResult result2 = ValidationResult.valid(List.of(warning2));

            ValidationResult combined = result1.combine(result2);

            assertTrue(combined.isValid());
            assertEquals(2, combined.warnings().size());
        }

        @Test
        @DisplayName("combines valid with invalid")
        void combinesValidWithInvalid() {
            ValidationError warning = ValidationError.warning("W001", "Warning", location);
            ValidationError error = ValidationError.error("E001", "Error", location);

            ValidationResult valid = ValidationResult.valid(List.of(warning));
            ValidationResult invalid = ValidationResult.invalid(List.of(error));

            ValidationResult combined = valid.combine(invalid);

            assertFalse(combined.isValid());
            assertEquals(1, combined.errors().size());
            assertEquals(1, combined.warnings().size());
        }

        @Test
        @DisplayName("combines two invalid results")
        void combinesTwoInvalidResults() {
            ValidationError error1 = ValidationError.error("E001", "Error 1", location);
            ValidationError error2 = ValidationError.error("E002", "Error 2", location);

            ValidationResult invalid1 = ValidationResult.invalid(List.of(error1));
            ValidationResult invalid2 = ValidationResult.invalid(List.of(error2));

            ValidationResult combined = invalid1.combine(invalid2);

            assertFalse(combined.isValid());
            assertEquals(2, combined.errors().size());
        }
    }
}
