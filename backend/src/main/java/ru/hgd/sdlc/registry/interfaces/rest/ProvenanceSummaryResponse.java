package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Summary of provenance information for a release.
 */
@Getter
@Builder
@Jacksonized
public final class ProvenanceSummaryResponse {

    /**
     * Whether the release has been signed.
     */
    private final boolean signed;

    /**
     * Git commit SHA the release was built from.
     */
    @NonNull
    private final String commitSha;

    /**
     * Timestamp when the release was built.
     */
    @NonNull
    private final Instant buildTimestamp;

    /**
     * Author of the commit.
     */
    @NonNull
    private final String commitAuthor;

    /**
     * Builder identifier.
     */
    @NonNull
    private final String builderId;
}
