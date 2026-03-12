package ru.hgd.sdlc.registry.application.query;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;

/**
 * Represents a directed edge in a dependency tree.
 * An edge from A to B means "A depends on B".
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class DependencyEdge {

    /**
     * The source release ID (the one that has the dependency).
     */
    @NonNull
    private final ReleaseId from;

    /**
     * The target release ID (the dependency).
     */
    @NonNull
    private final ReleaseId to;

    /**
     * Creates a new dependency edge.
     *
     * @param from the source release
     * @param to the target release (dependency)
     * @return a new DependencyEdge
     */
    public static DependencyEdge of(ReleaseId from, ReleaseId to) {
        return builder()
            .from(from)
            .to(to)
            .build();
    }
}
