package ru.hgd.sdlc.registry.domain.repository;

import ru.hgd.sdlc.registry.domain.release.ReleaseRecord;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for release persistence.
 * Provides access to release records stored in the registry.
 */
public interface ReleaseRepository {

    /**
     * Finds a release by its unique identifier.
     *
     * @param id the release ID
     * @return the release record if found
     */
    Optional<ReleaseRecord> findById(ReleaseId id);

    /**
     * Finds the latest published version of a flow.
     *
     * @param flowId the flow ID (without version)
     * @return the latest release record if any
     */
    Optional<ReleaseRecord> findLatestVersion(String flowId);

    /**
     * Finds all direct dependencies for a release.
     * This returns the releases that the specified release depends on.
     *
     * @param id the release ID
     * @return list of dependency release records
     */
    List<ReleaseRecord> findAllDependencies(ReleaseId id);

    /**
     * Saves a release record.
     *
     * @param record the record to save
     * @return the saved record
     */
    ReleaseRecord save(ReleaseRecord record);

    /**
     * Checks if a release exists.
     *
     * @param id the release ID
     * @return true if the release exists
     */
    boolean existsById(ReleaseId id);

    /**
     * Lists all versions of a flow.
     *
     * @param flowId the flow ID
     * @return list of release records, sorted by version descending
     */
    List<ReleaseRecord> listVersions(String flowId);
}
