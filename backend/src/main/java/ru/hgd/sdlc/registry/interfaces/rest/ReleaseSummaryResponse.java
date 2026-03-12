package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Summary information about a release for list views.
 */
@Getter
@Builder
@Jacksonized
public final class ReleaseSummaryResponse {

    /**
     * The flow ID (without version).
     */
    @NonNull
    private final String flowId;

    /**
     * The version string (e.g., "1.0.0").
     */
    @NonNull
    private final String version;

    /**
     * Human-readable display name.
     */
    @NonNull
    private final String displayName;

    /**
     * Author of the release.
     */
    @NonNull
    private final String author;

    /**
     * Timestamp when the release was created.
     */
    @NonNull
    private final Instant createdAt;

    /**
     * Canonical release ID in format "flowId@version".
     */
    @NonNull
    private final String releaseId;
}
