package ru.hgd.sdlc.registry.domain.package_;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable manifest for a release package.
 * Contains metadata about the package contents and their checksums.
 *
 * <p>This manifest is stored as release-manifest.json in the package root
 * and provides a complete index of all files in the package.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class PackageManifest {

    /**
     * Version of the manifest format.
     */
    @Builder.Default private final int formatVersion = PackageFormat.FORMAT_VERSION;

    /**
     * Unique identifier for this release (flowId + version).
     */
    @NonNull private final ReleaseId releaseId;

    /**
     * Timestamp when the package was created.
     */
    @NonNull private final Instant createdAt;

    /**
     * Map of file paths (relative to package root) to their SHA-256 hashes.
     */
    @Singular("file")
    @NonNull private final Map<String, String> files;

    /**
     * Returns an unmodifiable view of the files map.
     */
    public Map<String, String> files() {
        return Collections.unmodifiableMap(files);
    }

    /**
     * Returns the checksum for a given file path.
     *
     * @param path the file path
     * @return the SHA-256 hash if present
     */
    public Optional<String> checksumFor(String path) {
        return Optional.ofNullable(files.get(path));
    }

    /**
     * Checks if the manifest contains a given file.
     *
     * @param path the file path
     * @return true if the file is in the manifest
     */
    public boolean containsFile(String path) {
        return files.containsKey(path);
    }

    /**
     * Returns the number of files in the manifest.
     */
    public int fileCount() {
        return files.size();
    }

    /**
     * Checks if the manifest contains all required files.
     *
     * @return true if all required files are present
     */
    public boolean hasRequiredFiles() {
        return containsFile(PackageFormat.FILE_FLOW_IR)
            && containsFile(PackageFormat.FILE_PROVENANCE)
            && containsFile(PackageFormat.FILE_CHECKSUMS);
    }

    /**
     * Returns the flow IR checksum.
     */
    public Optional<String> flowIrChecksum() {
        return checksumFor(PackageFormat.FILE_FLOW_IR);
    }

    /**
     * Returns the provenance checksum.
     */
    public Optional<String> provenanceChecksum() {
        return checksumFor(PackageFormat.FILE_PROVENANCE);
    }
}
