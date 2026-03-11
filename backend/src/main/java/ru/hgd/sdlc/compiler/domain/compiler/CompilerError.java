package ru.hgd.sdlc.compiler.domain.compiler;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a compilation error.
 * Contains structured information about what went wrong during compilation.
 */
public final class CompilerError {

    private final String code;
    private final String message;
    private final String location;
    private final Throwable cause;

    private CompilerError(String code, String message, String location, Throwable cause) {
        this.code = code;
        this.message = message;
        this.location = location;
        this.cause = cause;
    }

    /**
     * Creates a compiler error with code and message.
     */
    public static CompilerError of(String code, String message) {
        return new CompilerError(code, message, null, null);
    }

    /**
     * Creates a compiler error with code, message, and location.
     */
    public static CompilerError of(String code, String message, String location) {
        return new CompilerError(code, message, location, null);
    }

    /**
     * Creates a compiler error with code, message, location, and cause.
     */
    public static CompilerError of(String code, String message, String location, Throwable cause) {
        return new CompilerError(code, message, location, cause);
    }

    // Factory methods for common errors

    /**
     * Creates an error for a missing required field.
     */
    public static CompilerError missingField(String fieldName, String location) {
        return of("E2001", "Missing required field: " + fieldName, location);
    }

    /**
     * Creates an error for an invalid reference.
     */
    public static CompilerError invalidReference(String refType, String refId, String location) {
        return of("E2002", "Invalid " + refType + " reference: " + refId, location);
    }

    /**
     * Creates an error for a circular dependency.
     */
    public static CompilerError circularDependency(String nodeId, String location) {
        return of("E2003", "Circular dependency detected at node: " + nodeId, location);
    }

    /**
     * Creates an error for an unresolved skill.
     */
    public static CompilerError unresolvedSkill(String skillId, String location) {
        return of("E2004", "Unresolved skill reference: " + skillId, location);
    }

    /**
     * Creates an error for an unresolved artifact.
     */
    public static CompilerError unresolvedArtifact(String artifactId, String location) {
        return of("E2005", "Unresolved artifact reference: " + artifactId, location);
    }

    /**
     * Creates an error for invalid phase order.
     */
    public static CompilerError invalidPhaseOrder(String reason, String location) {
        return of("E2006", "Invalid phase order: " + reason, location);
    }

    /**
     * Creates an error for an invalid transition.
     */
    public static CompilerError invalidTransition(String fromNode, String toNode, String reason) {
        return of("E2007", "Invalid transition from " + fromNode + " to " + toNode + ": " + reason, null);
    }

    /**
     * Creates an error for serialization failure.
     */
    public static CompilerError serializationFailure(String reason, Throwable cause) {
        return of("E2008", "Serialization failed: " + reason, null, cause);
    }

    /**
     * Creates an error for validation failure.
     */
    public static CompilerError validationFailure(String reason, String location) {
        return of("E2009", "Validation failed: " + reason, location);
    }

    /**
     * Creates an error for a node not found in phase.
     */
    public static CompilerError nodeNotFoundInPhase(String nodeId, String phaseId) {
        return of("E2010", "Node " + nodeId + " not found in phase " + phaseId, null);
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
        CompilerError that = (CompilerError) o;
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
        return "CompilerError{" + code + ": " + message + loc + "}";
    }
}
