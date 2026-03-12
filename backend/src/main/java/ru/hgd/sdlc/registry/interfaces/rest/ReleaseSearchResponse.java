package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Paginated search results for releases.
 */
@Getter
@Builder
@Jacksonized
public final class ReleaseSearchResponse {

    /**
     * List of matching releases.
     */
    @Singular("result")
    @NonNull
    private final List<ReleaseSummaryResponse> results;

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
