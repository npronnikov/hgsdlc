package ru.hgd.sdlc.registry.domain.release;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;

import java.time.Instant;

/**
 * Persistence entity representing a release record in the registry.
 * Maps to the releases table and serves as the database representation.
 *
 * <p>This is distinct from ReleasePackage which contains the full IR.
 * ReleaseRecord contains metadata needed for dependency resolution and lookups.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class ReleaseRecord {

    /**
     * Database ID (auto-generated).
     */
    private final Long id;

    /**
     * Unique identifier for this release (flowId + version).
     */
    @NonNull private final ReleaseId releaseId;

    /**
     * SHA-256 hash of the package archive.
     */
    @NonNull private final Sha256Hash packageHash;

    /**
     * JSON representation of the provenance for this release.
     */
    @NonNull private final String provenanceJson;

    /**
     * Timestamp when this record was created.
     */
    @NonNull private final Instant createdAt;

    /**
     * Creates a new ReleaseRecord with the current timestamp.
     *
     * @param releaseId the release ID
     * @param packageHash the package hash
     * @param provenanceJson the provenance JSON
     * @return a new ReleaseRecord
     */
    public static ReleaseRecord create(ReleaseId releaseId, Sha256Hash packageHash, String provenanceJson) {
        return ReleaseRecord.builder()
            .releaseId(releaseId)
            .packageHash(packageHash)
            .provenanceJson(provenanceJson)
            .createdAt(Instant.now())
            .build();
    }
}
