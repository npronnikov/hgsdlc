package ru.hgd.sdlc.compiler.domain.parser;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a parsing error.
 * Contains structured information about what went wrong during parsing.
 */
public final class ParseError {

    private final String code;
    private final String message;
    private final String location;
    private final Throwable cause;

    private ParseError(String code, String message, String location, Throwable cause) {
        this.code = code;
        this.message = message;
        this.location = location;
        this.cause = cause;
    }

    /**
     * Creates a parse error with code and message.
     */
    public static ParseError of(String code, String message) {
        return new ParseError(code, message, null, null);
    }

    /**
     * Creates a parse error with code, message, and location.
     */
    public static ParseError of(String code, String message, String location) {
        return new ParseError(code, message, location, null);
    }

    /**
     * Creates a parse error with code, message, location, and cause.
     */
    public static ParseError of(String code, String message, String location, Throwable cause) {
        return new ParseError(code, message, location, cause);
    }

    // Factory methods for common errors

    /**
     * Creates an error for missing frontmatter.
     */
    public static ParseError missingFrontmatter() {
        return of("E1001", "Missing YAML frontmatter. File must start with '---'");
    }

    /**
     * Creates an error for malformed frontmatter.
     */
    public static ParseError malformedFrontmatter(String detail, Throwable cause) {
        return of("E1002", "Malformed YAML frontmatter: " + detail, null, cause);
    }

    /**
     * Creates an error for unclosed frontmatter.
     */
    public static ParseError unclosedFrontmatter() {
        return of("E1003", "Unclosed frontmatter. Missing closing '---'");
    }

    /**
     * Creates an error for a missing required field.
     */
    public static ParseError missingField(String fieldName, String location) {
        return of("E1004", "Missing required field: " + fieldName, location);
    }

    /**
     * Creates an error for an invalid field value.
     */
    public static ParseError invalidField(String fieldName, String reason, String location) {
        return of("E1005", "Invalid field '" + fieldName + "': " + reason, location);
    }

    /**
     * Creates an error for an unknown field.
     */
    public static ParseError unknownField(String fieldName, String location) {
        return of("W1001", "Unknown field: " + fieldName, location);
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public Optional<String> location() {
        return Optional.ofNullable(location);
    }

    public Optional<Throwable> cause() {
        return Optional.ofNullable(cause);
    }

    /**
     * Checks if this is a warning (code starts with 'W').
     */
    public boolean isWarning() {
        return code.startsWith("W");
    }

    /**
     * Checks if this is an error (code starts with 'E').
     */
    public boolean isError() {
        return code.startsWith("E");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParseError that = (ParseError) o;
        return Objects.equals(code, that.code)
            && Objects.equals(message, that.message)
            && Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, location);
    }

    @Override
    public String toString() {
        String loc = location != null ? " at " + location : "";
        return "ParseError{" + code + ": " + message + loc + "}";
    }
}
