package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

/**
 * Full details of a release package.
 */
@Getter
@Builder
@Jacksonized
public final class ReleasePackageResponse {

    /**
     * Canonical release ID in format "flowId@version".
     */
    @NonNull
    private final String releaseId;

    /**
     * Detailed metadata about the release.
     */
    @NonNull
    private final ReleaseMetadataResponse metadata;

    /**
     * Summary of provenance information.
     */
    @NonNull
    private final ProvenanceSummaryResponse provenance;

    /**
     * Checksum information.
     */
    @NonNull
    private final ChecksumsResponse checksums;

    /**
     * Number of phases in the flow.
     */
    private final int phaseCount;

    /**
     * Number of bundled skills.
     */
    private final int skillCount;
}
