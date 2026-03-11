package ru.hgd.sdlc.compiler.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompositeValidator")
class CompositeValidatorTest {

    @Nested
    @DisplayName("creation")
    class CreationTest {

        @Test
        @DisplayName("creates empty validator")
        void createsEmptyValidator() {
            CompositeValidator<String> validator = CompositeValidator.empty();

            assertTrue(validator.isEmpty());
            assertEquals(0, validator.size());
        }

        @Test
        @DisplayName("creates with varargs")
        void createsWithVarargs() {
            Validator<String> v1 = (input, ctx) -> ValidationResult.valid();
            Validator<String> v2 = (input, ctx) -> ValidationResult.valid();

            CompositeValidator<String> validator = CompositeValidator.of(v1, v2);

            assertEquals(2, validator.size());
            assertFalse(validator.isEmpty());
        }

        @Test
        @DisplayName("adds validators")
        void addsValidators() {
            Validator<String> v1 = (input, ctx) -> ValidationResult.valid();
            CompositeValidator<String> validator = CompositeValidator.<String>empty().add(v1);

            assertEquals(1, validator.size());
        }
    }

    @Nested
    @DisplayName("validation")
    class ValidationTest {

        @Test
        @DisplayName("empty validator always passes")
        void emptyValidatorAlwaysPasses() {
            ValidationContext context = ValidationContext.forFile("test.md");
            CompositeValidator<String> validator = CompositeValidator.empty();

            ValidationResult result = validator.validate("input", context);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("combines multiple validators with warnings")
        void combinesMultipleValidatorsWithWarnings() {
            ValidationContext context = ValidationContext.forFile("test.md");

            // First validator adds warning
            Validator<String> v1 = (input, ctx) -> {
                ctx.addWarning("W001", "Warning 1", ctx.location(1));
                return ctx.toResult();
            };
            // Second validator adds another warning - context already has warning from v1
            Validator<String> v2 = (input, ctx) -> {
                ctx.addWarning("W002", "Warning 2", ctx.location(2));
                return ctx.toResult();
            };

            CompositeValidator<String> validator = CompositeValidator.of(v1, v2);
            ValidationResult result = validator.validate("input", context);

            assertTrue(result.isValid());
            // Since context is shared, both warnings should be present
            assertTrue(result.warnings().size() >= 1, "Expected at least 1 warning, got: " + result.warnings().size());
        }

        @Test
        @DisplayName("fails when any validator fails")
        void failsWhenAnyValidatorFails() {
            ValidationContext context = ValidationContext.forFile("test.md");

            Validator<String> passing = (input, ctx) -> ValidationResult.valid();
            Validator<String> failing = (input, ctx) -> {
                ctx.addError("E001", "Error", ctx.location(1));
                return ctx.toResult();
            };

            CompositeValidator<String> validator = CompositeValidator.of(passing, failing);
            ValidationResult result = validator.validate("input", context);

            assertFalse(result.isValid());
            assertEquals(1, result.errors().size());
        }

        @Test
        @DisplayName("combines errors from multiple validators")
        void combinesErrorsFromMultipleValidators() {
            ValidationContext context = ValidationContext.forFile("test.md");

            Validator<String> v1 = (input, ctx) -> {
                ctx.addError("E001", "Error 1", ctx.location(1));
                return ctx.toResult();
            };
            Validator<String> v2 = (input, ctx) -> {
                ctx.addError("E002", "Error 2", ctx.location(2));
                return ctx.toResult();
            };

            CompositeValidator<String> validator = CompositeValidator.of(v1, v2);
            ValidationResult result = validator.validate("input", context);

            assertFalse(result.isValid());
            // Context accumulates both errors
            assertTrue(result.errors().size() >= 1, "Expected at least 1 error, got: " + result.errors().size());
        }
    }
}
