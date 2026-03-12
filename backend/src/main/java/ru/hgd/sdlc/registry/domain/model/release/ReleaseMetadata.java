package ru.hgd.sdlc.registry.domain.model.release;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Optional;

/**
 * Immutable release metadata.
 * Contains human-readable information about a release.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class ReleaseMetadata {

    /**
     * Human-readable display name for the release.
     */
    @NonNull private final String displayName;

    /**
     * Description of the release (may be null).
     */
    private final String description;

    /**
     * Author of the release (e.g., email or username).
     */
    @NonNull private final String author;

    /**
     * Timestamp when the release was created.
     */
    @NonNull private final Instant createdAt;

    /**
     * Git commit SHA that this release was built from.
     */
    @NonNull private final String gitCommit;

    /**
     * Git tag for this release (may be null for dev builds).
     */
    private final String gitTag;

    /**
     * Returns the description if present.
     */
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the git tag if present.
     */
    public Optional<String> gitTag() {
        return Optional.ofNullable(gitTag);
    }
}
