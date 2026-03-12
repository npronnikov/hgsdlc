package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Optional;

/**
 * Detailed metadata about a release.
 */
@Getter
@Builder
@Jacksonized
public final class ReleaseMetadataResponse {

    /**
     * Human-readable display name.
     */
    @NonNull
    private final String displayName;

    /**
     * Description of the release (may be null).
     */
    private final String description;

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
     * Git commit SHA this release was built from.
     */
    @NonNull
    private final String gitCommit;

    /**
     * Git tag for this release (may be null).
     */
    private final String gitTag;
}
