package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

/**
 * Complete lockfile for a release with resolved dependencies.
 */
@Getter
@Builder
@Jacksonized
public final class LockfileResponse {

    /**
     * Lockfile schema version.
     */
    @NonNull
    private final String version;

    /**
     * Timestamp when the lockfile was generated.
     */
    @NonNull
    private final Instant generatedAt;

    /**
     * Root flow ID.
     */
    @NonNull
    private final String flowId;

    /**
     * Root flow version.
     */
    @NonNull
    private final String flowVersion;

    /**
     * All entries in topological order (dependencies first).
     */
    @Singular("entry")
    @NonNull
    private final List<LockfileEntryResponse> entries;

    /**
     * SHA-256 checksum of the lockfile content.
     */
    @NonNull
    private final String checksum;
}
