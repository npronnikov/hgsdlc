package ru.hgd.sdlc.registry.application.resolver;

import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;

import java.util.Collections;
import java.util.Set;

/**
 * Exception thrown when version conflicts are detected during dependency resolution.
 * This occurs when different flows require different versions of the same skill.
 */
public final class VersionConflictException extends RuntimeException {

    private final Set<ReleaseId> conflictingReleases;

    /**
     * Creates a new VersionConflictException.
     *
     * @param conflictingReleases the set of conflicting release IDs
     * @param message the error message
     */
    public VersionConflictException(Set<ReleaseId> conflictingReleases, String message) {
        super(message);
        this.conflictingReleases = conflictingReleases != null
            ? Collections.unmodifiableSet(conflictingReleases)
            : Set.of();
    }

    /**
     * Creates a new VersionConflictException with a generated message.
     *
     * @param conflictingReleases the set of conflicting release IDs
     */
    public VersionConflictException(Set<ReleaseId> conflictingReleases) {
        this(conflictingReleases, buildDefaultMessage(conflictingReleases));
    }

    private static String buildDefaultMessage(Set<ReleaseId> conflictingReleases) {
        if (conflictingReleases == null || conflictingReleases.isEmpty()) {
            return "Version conflict detected";
        }
        StringBuilder sb = new StringBuilder("Version conflict detected between releases: ");
        boolean first = true;
        for (ReleaseId id : conflictingReleases) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(id.canonicalId());
            first = false;
        }
        return sb.toString();
    }

    /**
     * Returns the set of conflicting release IDs.
     */
    public Set<ReleaseId> conflictingReleases() {
        return conflictingReleases;
    }
}
