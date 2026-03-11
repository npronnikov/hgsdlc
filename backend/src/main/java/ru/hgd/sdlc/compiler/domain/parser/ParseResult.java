package ru.hgd.sdlc.compiler.domain.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Result of parsing operation that can contain multiple errors.
 * Collects all validation errors instead of failing on the first one.
 *
 * @param <T> the type of the parsed document
 */
public final class ParseResult<T> {

    private final T document;
    private final List<ParseError> errors;

    private ParseResult(T document, List<ParseError> errors) {
        this.document = document;
        this.errors = List.copyOf(errors);
    }

    /**
     * Creates a successful parse result.
     */
    public static <T> ParseResult<T> success(T document) {
        Objects.requireNonNull(document, "document cannot be null");
        return new ParseResult<>(document, List.of());
    }

    /**
     * Creates a failed parse result with errors.
     */
    public static <T> ParseResult<T> failure(List<ParseError> errors) {
        Objects.requireNonNull(errors, "errors cannot be null");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("errors cannot be empty for failure");
        }
        return new ParseResult<>(null, errors);
    }

    /**
     * Creates a failed parse result with a single error.
     */
    public static <T> ParseResult<T> failure(ParseError error) {
        Objects.requireNonNull(error, "error cannot be null");
        return new ParseResult<>(null, List.of(error));
    }

    /**
     * Creates a result with both a document and warnings.
     * Document is present but has non-fatal warnings.
     */
    public static <T> ParseResult<T> withWarnings(T document, List<ParseError> warnings) {
        Objects.requireNonNull(document, "document cannot be null");
        Objects.requireNonNull(warnings, "warnings cannot be null");
        // Verify all are warnings
        for (ParseError w : warnings) {
            if (!w.isWarning()) {
                throw new IllegalArgumentException("All errors in withWarnings must be warnings (code starting with W)");
            }
        }
        return new ParseResult<>(document, warnings);
    }

    /**
     * Returns true if parsing succeeded without errors.
     */
    public boolean isSuccess() {
        return document != null && errors.isEmpty();
    }

    /**
     * Returns true if parsing succeeded but has warnings.
     */
    public boolean hasWarnings() {
        return document != null && !errors.isEmpty() && errors.stream().allMatch(ParseError::isWarning);
    }

    /**
     * Returns true if parsing failed with errors.
     */
    public boolean isFailure() {
        return document == null && !errors.isEmpty();
    }

    /**
     * Returns the parsed document, or null if parsing failed.
     */
    public T getDocument() {
        return document;
    }

    /**
     * Returns all errors (both fatal errors and warnings).
     */
    public List<ParseError> getErrors() {
        return errors;
    }

    /**
     * Returns only fatal errors (code starting with E).
     */
    public List<ParseError> getFatalErrors() {
        return errors.stream()
            .filter(ParseError::isError)
            .toList();
    }

    /**
     * Returns only warnings (code starting with W).
     */
    public List<ParseError> getWarnings() {
        return errors.stream()
            .filter(ParseError::isWarning)
            .toList();
    }

    /**
     * Returns the first error, or null if no errors.
     */
    public ParseError getFirstError() {
        return errors.isEmpty() ? null : errors.get(0);
    }

    /**
     * Maps the document to another type if successful.
     */
    public <U> ParseResult<U> map(Function<T, U> mapper) {
        if (document == null) {
            return new ParseResult<>(null, errors);
        }
        return new ParseResult<>(mapper.apply(document), errors);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParseResult<?> that = (ParseResult<?>) o;
        return Objects.equals(document, that.document) && errors.equals(that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(document, errors);
    }

    @Override
    public String toString() {
        if (isSuccess()) {
            return "ParseResult.success(" + document + ")";
        }
        if (hasWarnings()) {
            return "ParseResult.withWarnings(" + document + ", " + errors.size() + " warnings)";
        }
        return "ParseResult.failure(" + errors.size() + " errors)";
    }
}
