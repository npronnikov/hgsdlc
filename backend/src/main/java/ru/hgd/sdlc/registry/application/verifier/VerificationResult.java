package ru.hgd.sdlc.registry.application.verifier;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of a provenance verification operation.
 * Contains validity status and any issues found during verification.
 */
@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
public final class VerificationResult {

    /**
     * Whether the verification passed (no ERROR-level issues).
     */
    private final boolean success;

    /**
     * List of issues found during verification.
     * May include errors, warnings, and informational messages.
     */
    @Singular("issue")
    @NonNull private final List<VerificationIssue> issues;

    /**
     * Returns whether the verification is valid.
     *
     * @return true if verification passed
     */
    public boolean isValid() {
        return success;
    }

    /**
     * Returns whether the verification has any errors.
     *
     * @return true if there are ERROR-level issues
     */
    public boolean hasErrors() {
        return !getErrors().isEmpty();
    }

    /**
     * Returns an unmodifiable view of all issues.
     *
     * @return unmodifiable list of issues
     */
    public List<VerificationIssue> getIssuesList() {
        return Collections.unmodifiableList(issues);
    }

    /**
     * Returns only the ERROR-level issues.
     *
     * @return list of errors
     */
    public List<VerificationIssue> getErrors() {
        return issues.stream()
            .filter(i -> i.getSeverity() == VerificationSeverity.ERROR)
            .toList();
    }

    /**
     * Returns only the WARNING-level issues.
     *
     * @return list of warnings
     */
    public List<VerificationIssue> getWarnings() {
        return issues.stream()
            .filter(i -> i.getSeverity() == VerificationSeverity.WARNING)
            .toList();
    }

    /**
     * Returns only the INFO-level issues.
     *
     * @return list of informational messages
     */
    public List<VerificationIssue> getInfos() {
        return issues.stream()
            .filter(i -> i.getSeverity() == VerificationSeverity.INFO)
            .toList();
    }

    /**
     * Creates a valid verification result with no issues.
     *
     * @return a valid VerificationResult
     */
    public static VerificationResult valid() {
        return VerificationResult.builder()
            .success(true)
            .issues(List.of())
            .build();
    }

    /**
     * Creates a valid verification result with informational issues only.
     *
     * @param issues the informational issues
     * @return a valid VerificationResult
     */
    public static VerificationResult validWithInfos(List<VerificationIssue> issues) {
        return VerificationResult.builder()
            .success(true)
            .issues(issues)
            .build();
    }

    /**
     * Creates an invalid verification result with the given issues.
     *
     * @param issues the issues that caused the failure
     * @return an invalid VerificationResult
     */
    public static VerificationResult invalid(List<VerificationIssue> issues) {
        return VerificationResult.builder()
            .success(false)
            .issues(issues)
            .build();
    }

    /**
     * Creates an invalid verification result with a single error.
     *
     * @param code the error code
     * @param message the error message
     * @return an invalid VerificationResult
     */
    public static VerificationResult invalid(String code, String message) {
        return VerificationResult.builder()
            .success(false)
            .issue(VerificationIssue.error(code, message))
            .build();
    }

    /**
     * Creates an invalid verification result with a single error and details.
     *
     * @param code the error code
     * @param message the error message
     * @param details additional details
     * @return an invalid VerificationResult
     */
    public static VerificationResult invalid(String code, String message, String details) {
        return VerificationResult.builder()
            .success(false)
            .issue(VerificationIssue.error(code, message, details))
            .build();
    }

    /**
     * Combines this result with another result.
     * The combined result is valid only if both results are valid.
     *
     * @param other the other result to combine with
     * @return a combined VerificationResult
     */
    public VerificationResult combine(VerificationResult other) {
        List<VerificationIssue> combinedIssues = new ArrayList<>(this.issues);
        combinedIssues.addAll(other.issues);

        return VerificationResult.builder()
            .success(this.success && other.success)
            .issues(combinedIssues)
            .build();
    }

    @Override
    public String toString() {
        if (success && issues.isEmpty()) {
            return "VerificationResult[valid=true, no issues]";
        }
        return String.format("VerificationResult[valid=%s, issues=%d]",
            success, issues.size());
    }
}
