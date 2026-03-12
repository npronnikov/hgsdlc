package ru.hgd.sdlc.registry.application.verifier;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.util.Optional;

/**
 * Represents a single issue found during provenance verification.
 * Contains severity, code, message, and optional details.
 */
@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
public final class VerificationIssue {

    /**
     * Severity level of this issue.
     */
    @NonNull private final VerificationSeverity severity;

    /**
     * Machine-readable code identifying the issue type.
     * Examples: "MISSING_PROVENANCE", "INVALID_CHECKSUM", "INVALID_SIGNATURE"
     */
    @NonNull private final String code;

    /**
     * Human-readable description of the issue.
     */
    @NonNull private final String message;

    /**
     * Additional details about the issue (optional).
     */
    private final String details;

    /**
     * Returns the details as an Optional.
     *
     * @return Optional containing details if present
     */
    public Optional<String> getDetailsOptional() {
        return Optional.ofNullable(details);
    }

    /**
     * Creates an ERROR-level verification issue.
     *
     * @param code the issue code
     * @param message the human-readable message
     * @return a new VerificationIssue with ERROR severity
     */
    public static VerificationIssue error(String code, String message) {
        return VerificationIssue.builder()
            .severity(VerificationSeverity.ERROR)
            .code(code)
            .message(message)
            .build();
    }

    /**
     * Creates an ERROR-level verification issue with details.
     *
     * @param code the issue code
     * @param message the human-readable message
     * @param details additional details
     * @return a new VerificationIssue with ERROR severity
     */
    public static VerificationIssue error(String code, String message, String details) {
        return VerificationIssue.builder()
            .severity(VerificationSeverity.ERROR)
            .code(code)
            .message(message)
            .details(details)
            .build();
    }

    /**
     * Creates a WARNING-level verification issue.
     *
     * @param code the issue code
     * @param message the human-readable message
     * @return a new VerificationIssue with WARNING severity
     */
    public static VerificationIssue warning(String code, String message) {
        return VerificationIssue.builder()
            .severity(VerificationSeverity.WARNING)
            .code(code)
            .message(message)
            .build();
    }

    /**
     * Creates a WARNING-level verification issue with details.
     *
     * @param code the issue code
     * @param message the human-readable message
     * @param details additional details
     * @return a new VerificationIssue with WARNING severity
     */
    public static VerificationIssue warning(String code, String message, String details) {
        return VerificationIssue.builder()
            .severity(VerificationSeverity.WARNING)
            .code(code)
            .message(message)
            .details(details)
            .build();
    }

    /**
     * Creates an INFO-level verification issue.
     *
     * @param code the issue code
     * @param message the human-readable message
     * @return a new VerificationIssue with INFO severity
     */
    public static VerificationIssue info(String code, String message) {
        return VerificationIssue.builder()
            .severity(VerificationSeverity.INFO)
            .code(code)
            .message(message)
            .build();
    }

    /**
     * Creates an INFO-level verification issue with details.
     *
     * @param code the issue code
     * @param message the human-readable message
     * @param details additional details
     * @return a new VerificationIssue with INFO severity
     */
    public static VerificationIssue info(String code, String message, String details) {
        return VerificationIssue.builder()
            .severity(VerificationSeverity.INFO)
            .code(code)
            .message(message)
            .details(details)
            .build();
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s%s",
            severity, code, message,
            details != null ? " (" + details + ")" : "");
    }
}
