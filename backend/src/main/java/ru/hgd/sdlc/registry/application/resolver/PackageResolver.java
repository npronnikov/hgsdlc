package ru.hgd.sdlc.registry.application.resolver;

import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;

import java.util.List;

/**
 * Interface for resolving flow and skill dependencies.
 * Resolves all transitive dependencies and returns them in topological order.
 */
public interface PackageResolver {

    /**
     * Resolves a flow and all its dependencies.
     * Returns packages in topological order (dependencies first, root last).
     *
     * @param flowId the flow release ID to resolve
     * @return list of packages in dependency order
     * @throws ReleaseNotFoundException if the flow or any dependency is not found
     * @throws VersionConflictException if version conflicts are detected
     */
    List<ReleasePackage> resolve(ReleaseId flowId)
        throws ReleaseNotFoundException, VersionConflictException;

    /**
     * Resolves a specific release without resolving dependencies.
     *
     * @param releaseId the exact release ID
     * @return the release package
     * @throws ReleaseNotFoundException if the release is not found
     */
    ReleasePackage resolveExact(ReleaseId releaseId) throws ReleaseNotFoundException;
}
