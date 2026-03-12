package ru.hgd.sdlc.registry.application.resolver;

import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;

/**
 * Exception thrown when a release cannot be found in the registry.
 */
public final class ReleaseNotFoundException extends RuntimeException {

    private final ReleaseId releaseId;

    /**
     * Creates a new ReleaseNotFoundException.
     *
     * @param releaseId the release ID that was not found
     * @param message the error message
     */
    public ReleaseNotFoundException(ReleaseId releaseId, String message) {
        super(message);
        this.releaseId = releaseId;
    }

    /**
     * Creates a new ReleaseNotFoundException with a default message.
     *
     * @param releaseId the release ID that was not found
     */
    public ReleaseNotFoundException(ReleaseId releaseId) {
        this(releaseId, "Release not found: " + (releaseId != null ? releaseId.canonicalId() : "null"));
    }

    /**
     * Returns the release ID that was not found.
     */
    public ReleaseId releaseId() {
        return releaseId;
    }
}
