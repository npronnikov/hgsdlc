package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Result of release verification.
 */
@Getter
@Builder
@Jacksonized
public final class VerificationResultResponse {

    /**
     * Whether the release is valid (no ERROR-level issues).
     */
    private final boolean valid;

    /**
     * List of issues found during verification.
     */
    @Singular("issue")
    @NonNull
    private final List<VerificationIssueResponse> issues;
}
