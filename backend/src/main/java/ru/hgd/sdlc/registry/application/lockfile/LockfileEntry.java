package ru.hgd.sdlc.registry.application.lockfile;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.util.Collections;
import java.util.List;

/**
 * A single entry in the lockfile representing a resolved package.
 *
 * <p>Each entry captures:
 * <ul>
 *   <li>The release ID (flow-id@version)</li>
 *   <li>The type (FLOW or SKILL)</li>
 *   <li>Checksums for IR and package integrity</li>
 *   <li>Direct dependencies</li>
 * </ul>
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class LockfileEntry {

    /**
     * The release identifier in canonical form (e.g., "flow-id@1.0.0").
     */
    @NonNull private final String releaseId;

    /**
     * The type of this entry.
     */
    @NonNull private final LockfileEntryType type;

    /**
     * SHA-256 checksum of the compiled IR.
     */
    @NonNull private final String irChecksum;

    /**
     * SHA-256 checksum of the entire package.
     */
    @NonNull private final String packageChecksum;

    /**
     * IDs of direct dependencies (release IDs in canonical form).
     * Dependencies are listed in the order they should be loaded.
     */
    @Singular
    @NonNull private final List<String> dependencies;

    /**
     * Returns an unmodifiable view of the dependencies.
     */
    public List<String> dependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    /**
     * Checks if this entry has any dependencies.
     *
     * @return true if there are dependencies
     */
    public boolean hasDependencies() {
        return !dependencies.isEmpty();
    }

    /**
     * Returns the number of dependencies.
     *
     * @return the dependency count
     */
    public int dependencyCount() {
        return dependencies.size();
    }
}
