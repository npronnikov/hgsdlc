package ru.hgd.sdlc.registry.application.query;

import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseMetadata;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;

import java.util.List;
import java.util.Optional;

/**
 * Query service for release information.
 * Provides read-only access to releases and their dependencies.
 *
 * <p>This service is optimized for query operations and returns
 * projections suitable for UI display and API responses.
 */
public interface ReleaseQueryService {

    /**
     * Finds a release by exact version.
     *
     * @param id the release ID (flowId + version)
     * @return the release package if found
     */
    Optional<ReleasePackage> findById(ReleaseId id);

    /**
     * Finds the latest version of a flow.
     *
     * @param flowId the flow ID (without version)
     * @return the latest release package if any
     */
    Optional<ReleasePackage> findLatest(String flowId);

    /**
     * Lists all versions of a flow.
     *
     * @param flowId the flow ID (without version)
     * @return list of release metadata, sorted by version descending
     */
    List<ReleaseMetadata> listVersions(String flowId);

    /**
     * Searches for flows matching a query.
     *
     * @param query the search query
     * @return list of matching release metadata
     */
    List<ReleaseMetadata> search(ReleaseQuery query);

    /**
     * Gets the dependency tree for a release.
     *
     * @param id the release ID
     * @return the dependency tree if the release exists
     */
    Optional<DependencyTree> getDependencyTree(ReleaseId id);
}
