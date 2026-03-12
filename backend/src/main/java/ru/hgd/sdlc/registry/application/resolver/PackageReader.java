package ru.hgd.sdlc.registry.application.resolver;

import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;

/**
 * Interface for reading release packages from storage.
 */
public interface PackageReader {

    /**
     * Reads a release package by its ID.
     *
     * @param releaseId the release ID
     * @return the release package
     * @throws ReleaseNotFoundException if the package cannot be found
     */
    ReleasePackage read(ReleaseId releaseId) throws ReleaseNotFoundException;
}
