package ru.hgd.sdlc.registry.application.lockfile;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Immutable lockfile representing a fully resolved dependency tree.
 *
 * <p>The lockfile captures:
 * <ul>
 *   <li>The root flow ID and version</li>
 *   <li>All transitive dependencies in topological order</li>
 *   <li>Checksums for integrity verification</li>
 *   <li>Timestamp of generation</li>
 * </ul>
 *
 * <p>This is the definitive record of what packages should be used
 * for a flow execution. The lockfile ensures reproducibility across
 * different environments and executions.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class Lockfile {

    /**
     * Lockfile schema version.
     */
    @NonNull private final LockfileVersion version;

    /**
     * Timestamp when this lockfile was generated.
     */
    @NonNull private final Instant generatedAt;

    /**
     * The root flow ID (without version).
     */
    @NonNull private final String flowId;

    /**
     * The root flow version.
     */
    @NonNull private final String flowVersion;

    /**
     * All entries in topological order (dependencies first).
     * The first entry is always the root flow.
     */
    @Singular
    @NonNull private final List<LockfileEntry> entries;

    /**
     * SHA-256 checksum of the lockfile content for integrity.
     */
    @NonNull private final String checksum;

    /**
     * Returns an unmodifiable view of the entries.
     */
    public List<LockfileEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Returns the number of entries in the lockfile.
     *
     * @return the entry count
     */
    public int size() {
        return entries.size();
    }

    /**
     * Checks if the lockfile is empty.
     *
     * @return true if there are no entries
     */
    @JsonIgnore
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Returns the root flow entry (first entry in topological order).
     *
     * @return the root entry, or null if empty
     */
    public LockfileEntry rootEntry() {
        return entries.isEmpty() ? null : entries.get(0);
    }

    /**
     * Finds an entry by release ID.
     *
     * @param releaseId the release ID to find
     * @return the entry if found, or null
     */
    public LockfileEntry findEntry(String releaseId) {
        return entries.stream()
            .filter(e -> e.releaseId().equals(releaseId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns the canonical release ID for the root flow.
     *
     * @return the root release ID (flowId@flowVersion)
     */
    public String rootReleaseId() {
        return flowId + "@" + flowVersion;
    }
}
