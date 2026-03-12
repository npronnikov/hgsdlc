package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * A single entry in the lockfile.
 */
@Getter
@Builder
@Jacksonized
public final class LockfileEntryResponse {

    /**
     * Canonical release ID (flowId@version).
     */
    @NonNull
    private final String releaseId;

    /**
     * Entry type (FLOW or SKILL).
     */
    @NonNull
    private final String type;

    /**
     * SHA-256 checksum of the compiled IR.
     */
    @NonNull
    private final String irChecksum;

    /**
     * SHA-256 checksum of the entire package.
     */
    @NonNull
    private final String packageChecksum;

    /**
     * List of direct dependency release IDs.
     */
    @Singular("dependency")
    @NonNull
    private final List<String> dependencies;
}
