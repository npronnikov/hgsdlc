package ru.hgd.sdlc.compiler.domain.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Sealed interface representing the result of validation.
 * Can be either Valid (with optional warnings) or Invalid (with errors and optional warnings).
 */
public sealed interface ValidationResult {

    /**
     * Checks if validation passed (no errors).
     *
     * @return true if valid, false if invalid
     */
    boolean isValid();

    /**
     * Checks if validation has any issues (errors or warnings).
     *
     * @return true if there are issues
     */
    boolean hasIssues();

    /**
     * Returns all errors (ERROR severity only).
     *
     * @return list of errors
     */
    List<ValidationError> errors();

    /**
     * Returns all warnings (WARNING severity only).
     *
     * @return list of warnings
     */
    List<ValidationError> warnings();

    /**
     * Returns all issues (errors and warnings combined).
     *
     * @return list of all validation issues
     */
    List<ValidationError> allIssues();

    /**
     * Combines this result with another, accumulating all errors and warnings.
     *
     * @param other the other result to combine with
     * @return a new combined result
     */
    ValidationResult combine(ValidationResult other);

    /**
     * Valid result - no errors, optionally with warnings.
     */
    final class Valid implements ValidationResult {

        private final List<ValidationError> warnings;

        private Valid(List<ValidationError> warnings) {
            this.warnings = List.copyOf(warnings);
        }

        /**
         * Creates a valid result with no warnings.
         *
         * @return a valid result
         */
        public static Valid of() {
            return new Valid(List.of());
        }

        /**
         * Creates a valid result with warnings.
         *
         * @param warnings the warnings
         * @return a valid result with warnings
         */
        public static Valid of(List<ValidationError> warnings) {
            Objects.requireNonNull(warnings, "Warnings cannot be null");
            // Verify all are actually warnings
            for (ValidationError issue : warnings) {
                if (issue.isError()) {
                    throw new IllegalArgumentException("Valid result cannot contain errors: " + issue);
                }
            }
            return new Valid(warnings);
        }

        /**
         * Creates a valid result with a single warning.
         *
         * @param warning the warning
         * @return a valid result with the warning
         */
        public static Valid withWarning(ValidationError warning) {
            Objects.requireNonNull(warning, "Warning cannot be null");
            if (warning.isError()) {
                throw new IllegalArgumentException("Valid result cannot contain errors: " + warning);
            }
            return new Valid(List.of(warning));
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public boolean hasIssues() {
            return !warnings.isEmpty();
        }

        @Override
        public List<ValidationError> errors() {
            return List.of();
        }

        @Override
        public List<ValidationError> warnings() {
            return warnings;
        }

        @Override
        public List<ValidationError> allIssues() {
            return warnings;
        }

        @Override
        public ValidationResult combine(ValidationResult other) {
            if (other.isValid()) {
                List<ValidationError> combined = new ArrayList<>(warnings);
                combined.addAll(other.warnings());
                return Valid.of(combined);
            } else {
                List<ValidationError> combinedWarnings = new ArrayList<>(warnings);
                combinedWarnings.addAll(other.warnings());
                List<ValidationError> combinedErrors = new ArrayList<>(other.errors());
                return Invalid.of(combinedErrors, combinedWarnings);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Valid valid = (Valid) o;
            return warnings.equals(valid.warnings);
        }

        @Override
        public int hashCode() {
            return Objects.hash(warnings);
        }

        @Override
        public String toString() {
            if (warnings.isEmpty()) {
                return "Valid";
            }
            return "Valid with " + warnings.size() + " warning(s)";
        }
    }

    /**
     * Invalid result - contains errors and optionally warnings.
     */
    final class Invalid implements ValidationResult {

        private final List<ValidationError> errors;
        private final List<ValidationError> warnings;

        private Invalid(List<ValidationError> errors, List<ValidationError> warnings) {
            this.errors = List.copyOf(errors);
            this.warnings = List.copyOf(warnings);
        }

        /**
         * Creates an invalid result with errors.
         *
         * @param errors the errors
         * @return an invalid result
         */
        public static Invalid of(List<ValidationError> errors) {
            return of(errors, List.of());
        }

        /**
         * Creates an invalid result with errors and warnings.
         *
         * @param errors   the errors
         * @param warnings the warnings
         * @return an invalid result
         */
        public static Invalid of(List<ValidationError> errors, List<ValidationError> warnings) {
            Objects.requireNonNull(errors, "Errors cannot be null");
            Objects.requireNonNull(warnings, "Warnings cannot be null");
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("Invalid result must have at least one error");
            }
            // Verify errors are actually errors
            for (ValidationError issue : errors) {
                if (issue.isWarning()) {
                    throw new IllegalArgumentException("Invalid result errors must be ERROR severity: " + issue);
                }
            }
            // Verify warnings are actually warnings
            for (ValidationError issue : warnings) {
                if (issue.isError()) {
                    throw new IllegalArgumentException("Invalid result warnings must be WARNING severity: " + issue);
                }
            }
            return new Invalid(errors, warnings);
        }

        /**
         * Creates an invalid result with a single error.
         *
         * @param error the error
         * @return an invalid result
         */
        public static Invalid of(ValidationError error) {
            Objects.requireNonNull(error, "Error cannot be null");
            if (error.isWarning()) {
                throw new IllegalArgumentException("Error must be ERROR severity: " + error);
            }
            return new Invalid(List.of(error), List.of());
        }

        /**
         * Creates an invalid result with a single error and warnings.
         *
         * @param error    the error
         * @param warnings the warnings
         * @return an invalid result
         */
        public static Invalid of(ValidationError error, List<ValidationError> warnings) {
            Objects.requireNonNull(error, "Error cannot be null");
            if (error.isWarning()) {
                throw new IllegalArgumentException("Error must be ERROR severity: " + error);
            }
            return Invalid.of(List.of(error), warnings);
        }

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public boolean hasIssues() {
            return true;
        }

        @Override
        public List<ValidationError> errors() {
            return errors;
        }

        @Override
        public List<ValidationError> warnings() {
            return warnings;
        }

        @Override
        public List<ValidationError> allIssues() {
            List<ValidationError> all = new ArrayList<>(errors);
            all.addAll(warnings);
            return Collections.unmodifiableList(all);
        }

        @Override
        public ValidationResult combine(ValidationResult other) {
            List<ValidationError> combinedErrors = new ArrayList<>(errors);
            List<ValidationError> combinedWarnings = new ArrayList<>(warnings);

            combinedErrors.addAll(other.errors());
            combinedWarnings.addAll(other.warnings());

            return Invalid.of(combinedErrors, combinedWarnings);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Invalid invalid = (Invalid) o;
            return errors.equals(invalid.errors) && warnings.equals(invalid.warnings);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errors, warnings);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Invalid: ").append(errors.size()).append(" error(s)");
            if (!warnings.isEmpty()) {
                sb.append(", ").append(warnings.size()).append(" warning(s)");
            }
            return sb.toString();
        }
    }

    // Static factory methods

    /**
     * Creates a valid result with no warnings.
     *
     * @return a valid result
     */
    static ValidationResult valid() {
        return Valid.of();
    }

    /**
     * Creates a valid result with warnings.
     *
     * @param warnings the warnings
     * @return a valid result with warnings
     */
    static ValidationResult valid(List<ValidationError> warnings) {
        return Valid.of(warnings);
    }

    /**
     * Creates an invalid result with errors.
     *
     * @param errors the errors
     * @return an invalid result
     */
    static ValidationResult invalid(List<ValidationError> errors) {
        return Invalid.of(errors);
    }

    /**
     * Creates an invalid result with errors and warnings.
     *
     * @param errors   the errors
     * @param warnings the warnings
     * @return an invalid result
     */
    static ValidationResult invalid(List<ValidationError> errors, List<ValidationError> warnings) {
        return Invalid.of(errors, warnings);
    }
}
