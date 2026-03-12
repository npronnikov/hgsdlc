package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

/**
 * A single issue found during release verification.
 */
@Getter
@Builder
@Jacksonized
public final class VerificationIssueResponse {

    /**
     * Severity level (ERROR, WARNING, INFO).
     */
    @NonNull
    private final String severity;

    /**
     * Machine-readable issue code.
     */
    @NonNull
    private final String code;

    /**
     * Human-readable description.
     */
    @NonNull
    private final String message;

    /**
     * Additional details (may be null).
     */
    private final String details;
}
