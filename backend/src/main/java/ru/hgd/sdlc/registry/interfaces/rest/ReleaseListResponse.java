package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Paginated list of releases.
 */
@Getter
@Builder
@Jacksonized
public final class ReleaseListResponse {

    /**
     * List of release summaries.
     */
    @Singular("release")
    @NonNull
    private final List<ReleaseSummaryResponse> releases;

    /**
     * Total number of matching releases.
     */
    private final int total;

    /**
     * Pagination offset.
     */
    private final int offset;

    /**
     * Page size limit.
     */
    private final int limit;
}
