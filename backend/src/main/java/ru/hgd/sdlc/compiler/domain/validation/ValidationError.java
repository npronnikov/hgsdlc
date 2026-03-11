package ru.hgd.sdlc.compiler.domain.validation;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a validation error or warning found during document validation.
 * Contains structured information about the issue for error reporting.
 */
public final class ValidationError {

    private final String code;
    private final String message;
    private final SourceLocation location;
    private final Severity severity;

    private ValidationError(String code, String message, SourceLocation location, Severity severity) {
        this.code = code;
        this.message = message;
        this.location = location;
        this.severity = severity;
    }

    /**
     * Creates a validation error with all fields.
     *
     * @param code     the error code (e.g., "V2001")
     * @param message  the human-readable error message
     * @param location the source location of the error
     * @param severity the severity level
     * @return a new ValidationError instance
     */
    public static ValidationError of(String code, String message, SourceLocation location, Severity severity) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Code cannot be null or blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message cannot be null or blank");
        }
        Objects.requireNonNull(severity, "Severity cannot be null");
        Objects.requireNonNull(location, "Location cannot be null");
        return new ValidationError(code, message, location, severity);
    }

    /**
     * Creates an error-level validation issue.
     *
     * @param code     the error code
     * @param message  the error message
     * @param location the source location
     * @return a new ValidationError with ERROR severity
     */
    public static ValidationError error(String code, String message, SourceLocation location) {
        return of(code, message, location, Severity.ERROR);
    }

    /**
     * Creates a warning-level validation issue.
     *
     * @param code     the warning code
     * @param message  the warning message
     * @param location the source location
     * @return a new ValidationError with WARNING severity
     */
    public static ValidationError warning(String code, String message, SourceLocation location) {
        return of(code, message, location, Severity.WARNING);
    }

    /**
     * Returns the error code.
     *
     * @return the error code
     */
    public String code() {
        return code;
    }

    /**
     * Returns the error message.
     *
     * @return the error message
     */
    public String message() {
        return message;
    }

    /**
     * Returns the source location.
     *
     * @return the source location
     */
    public SourceLocation location() {
        return location;
    }

    /**
     * Returns the severity level.
     *
     * @return the severity
     */
    public Severity severity() {
        return severity;
    }

    /**
     * Checks if this is an error (not a warning).
     *
     * @return true if severity is ERROR
     */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /**
     * Checks if this is a warning.
     *
     * @return true if severity is WARNING
     */
    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    /**
     * Creates a new error at a different location.
     *
     * @param newLocation the new location
     * @return a new ValidationError instance
     */
    public ValidationError atLocation(SourceLocation newLocation) {
        return of(code, message, newLocation, severity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationError that = (ValidationError) o;
        return Objects.equals(code, that.code)
            && Objects.equals(message, that.message)
            && Objects.equals(location, that.location)
            && severity == that.severity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, location, severity);
    }

    @Override
    public String toString() {
        String severityPrefix = severity == Severity.ERROR ? "Error" : "Warning";
        return severityPrefix + " [" + code + "]: " + message + " at " + location;
    }
}
