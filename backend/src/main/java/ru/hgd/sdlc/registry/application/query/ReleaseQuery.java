package ru.hgd.sdlc.registry.application.query;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

/**
 * Query parameters for searching releases.
 * Supports full-text search, filtering, and pagination.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class ReleaseQuery {

    /**
     * Full-text search query for matching release names and descriptions.
     */
    private final String text;

    /**
     * Filter by author (exact match).
     */
    private final String author;

    /**
     * Filter for releases created after this timestamp.
     */
    private final Instant after;

    /**
     * Filter for releases created before this timestamp.
     */
    private final Instant before;

    /**
     * Filter by tags (releases matching any tag).
     */
    @Builder.Default
    private final List<String> tags = List.of();

    /**
     * Maximum number of results to return.
     */
    @Builder.Default
    private final int limit = 100;

    /**
     * Pagination offset (0-based).
     */
    @Builder.Default
    private final int offset = 0;

    /**
     * Creates an empty query with default pagination.
     *
     * @return an empty query with limit=100 and offset=0
     */
    public static ReleaseQuery empty() {
        return builder().limit(100).offset(0).build();
    }

    /**
     * Checks if this query has a text filter.
     *
     * @return true if text filter is set
     */
    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    /**
     * Checks if this query has an author filter.
     *
     * @return true if author filter is set
     */
    public boolean hasAuthor() {
        return author != null && !author.isBlank();
    }

    /**
     * Checks if this query has a date range filter.
     *
     * @return true if after or before is set
     */
    public boolean hasDateRange() {
        return after != null || before != null;
    }

    /**
     * Checks if this query has tag filters.
     *
     * @return true if tags are set and non-empty
     */
    public boolean hasTags() {
        return tags != null && !tags.isEmpty();
    }

    /**
     * Checks if pagination is applied (offset > 0).
     *
     * @return true if offset > 0
     */
    public boolean isPaginated() {
        return offset > 0;
    }
}
