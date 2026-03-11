package ru.hgd.sdlc.compiler.domain.validation;

/**
 * Functional interface for validating documents.
 *
 * @param <T> the type of document to validate
 */
@FunctionalInterface
public interface Validator<T> {

    /**
     * Validates the given input.
     *
     * @param input   the document to validate
     * @param context the validation context for accumulating errors/warnings
     * @return the validation result
     */
    ValidationResult validate(T input, ValidationContext context);

    /**
     * Creates a validator that combines this validator with another.
     * Both validators will be applied, accumulating all errors and warnings.
     *
     * @param other the other validator
     * @return a composite validator
     */
    default Validator<T> and(Validator<T> other) {
        return CompositeValidator.of(this, other);
    }

    /**
     * Creates a validator that always returns valid.
     *
     * @param <T> the document type
     * @return a validator that always passes
     */
    static <T> Validator<T> alwaysValid() {
        return (input, context) -> ValidationResult.valid();
    }

    /**
     * Creates a validator from a simple function that returns a result.
     *
     * @param validator the validator function
     * @param <T>       the document type
     * @return a new validator
     */
    static <T> Validator<T> from(java.util.function.BiFunction<T, ValidationContext, ValidationResult> validator) {
        return validator::apply;
    }
}
