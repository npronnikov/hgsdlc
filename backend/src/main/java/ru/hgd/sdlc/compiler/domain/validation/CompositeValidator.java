package ru.hgd.sdlc.compiler.domain.validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A validator that combines multiple validators.
 * All validators are applied, and results are accumulated.
 *
 * @param <T> the type of document to validate
 */
public final class CompositeValidator<T> implements Validator<T> {

    private final List<Validator<T>> validators;

    private CompositeValidator(List<Validator<T>> validators) {
        this.validators = List.copyOf(validators);
    }

    /**
     * Creates a composite validator from multiple validators.
     *
     * @param validators the validators to combine
     * @param <T>        the document type
     * @return a composite validator
     */
    @SafeVarargs
    public static <T> CompositeValidator<T> of(Validator<T>... validators) {
        Objects.requireNonNull(validators, "Validators cannot be null");
        return new CompositeValidator<>(Arrays.asList(validators));
    }

    /**
     * Creates a composite validator from a list of validators.
     *
     * @param validators the validators to combine
     * @param <T>        the document type
     * @return a composite validator
     */
    public static <T> CompositeValidator<T> of(List<Validator<T>> validators) {
        Objects.requireNonNull(validators, "Validators cannot be null");
        return new CompositeValidator<>(validators);
    }

    /**
     * Creates an empty composite validator that always passes.
     *
     * @param <T> the document type
     * @return an empty composite validator
     */
    public static <T> CompositeValidator<T> empty() {
        return new CompositeValidator<>(List.of());
    }

    /**
     * Adds a validator to this composite.
     *
     * @param validator the validator to add
     * @return a new composite validator with the added validator
     */
    public CompositeValidator<T> add(Validator<T> validator) {
        Objects.requireNonNull(validator, "Validator cannot be null");
        List<Validator<T>> newValidators = new ArrayList<>(validators);
        newValidators.add(validator);
        return new CompositeValidator<>(newValidators);
    }

    @Override
    public ValidationResult validate(T input, ValidationContext context) {
        if (validators.isEmpty()) {
            return ValidationResult.valid();
        }

        ValidationResult result = ValidationResult.valid();
        for (Validator<T> validator : validators) {
            ValidationResult current = validator.validate(input, context);
            result = result.combine(current);
        }
        return result;
    }

    /**
     * Returns the number of validators in this composite.
     *
     * @return the validator count
     */
    public int size() {
        return validators.size();
    }

    /**
     * Checks if this composite has no validators.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return validators.isEmpty();
    }

    @Override
    public String toString() {
        return "CompositeValidator{" + validators.size() + " validators}";
    }
}
