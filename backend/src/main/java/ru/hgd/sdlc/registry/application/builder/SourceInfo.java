package ru.hgd.sdlc.registry.application.builder;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Source provenance information for a release build.
 * Contains Git information and input file checksums.
 *
 * <p>This record captures where the source came from, including:
 * <ul>
 *   <li>Git repository URL and commit information</li>
 *   <li>Branch and tag (if applicable)</li>
 *   <li>Input file checksums for reproducibility</li>
 * </ul>
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class SourceInfo {

    /**
     * Git repository URL where the source resides.
     */
    @NonNull private final String repositoryUrl;

    /**
     * Git commit SHA that this release was built from (40-char hex string).
     */
    @NonNull private final String commitSha;

    /**
     * Author of the commit (e.g., email).
     */
    @NonNull private final String commitAuthor;

    /**
     * Timestamp when the commit was made.
     */
    @NonNull private final Instant committedAt;

    /**
     * Git branch name (may be null).
     */
    private final String branch;

    /**
     * Git tag for this release (may be null).
     */
    private final String tag;

    /**
     * Map of input file paths to their SHA-256 checksums.
     */
    @Singular("inputChecksum")
    @NonNull private final Map<String, String> inputChecksums;

    /**
     * Returns the branch name if present.
     */
    public Optional<String> branch() {
        return Optional.ofNullable(branch);
    }

    /**
     * Returns the git tag if present.
     */
    public Optional<String> tag() {
        return Optional.ofNullable(tag);
    }

    /**
     * Returns an unmodifiable view of input checksums.
     */
    public Map<String, String> inputChecksums() {
        return Collections.unmodifiableMap(inputChecksums);
    }

    /**
     * Checks if this source info has input checksums.
     */
    public boolean hasInputChecksums() {
        return !inputChecksums.isEmpty();
    }

    /**
     * Creates a SourceInfo with required fields only.
     *
     * @param repositoryUrl the repository URL
     * @param commitSha the commit SHA
     * @param commitAuthor the commit author
     * @param committedAt the commit timestamp
     * @return a new SourceInfo
     */
    public static SourceInfo of(String repositoryUrl, String commitSha,
                                 String commitAuthor, Instant committedAt) {
        return SourceInfo.builder()
            .repositoryUrl(repositoryUrl)
            .commitSha(commitSha)
            .commitAuthor(commitAuthor)
            .committedAt(committedAt)
            .build();
    }
}
