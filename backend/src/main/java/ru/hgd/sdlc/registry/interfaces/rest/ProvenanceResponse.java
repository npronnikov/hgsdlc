package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Map;

/**
 * Full provenance details for a release.
 */
@Getter
@Builder
@Jacksonized
public final class ProvenanceResponse {

    /**
     * Canonical release ID.
     */
    @NonNull
    private final String releaseId;

    /**
     * Git repository URL.
     */
    @NonNull
    private final String repositoryUrl;

    /**
     * Git commit SHA.
     */
    @NonNull
    private final String commitSha;

    /**
     * Timestamp when the release was built.
     */
    @NonNull
    private final Instant buildTimestamp;

    /**
     * Author of the commit.
     */
    @NonNull
    private final String commitAuthor;

    /**
     * Builder identifier.
     */
    @NonNull
    private final String builderId;

    /**
     * Whether the provenance has been signed.
     */
    private final boolean signed;

    /**
     * Git tag (if any).
     */
    private final String gitTag;

    /**
     * Branch name (if any).
     */
    private final String branch;

    /**
     * Commit message (if any).
     */
    private final String commitMessage;

    /**
     * Compiler version used to build.
     */
    @NonNull
    private final String compilerVersion;

    /**
     * SHA-256 hash of the compiled IR.
     */
    @NonNull
    private final String irChecksum;

    /**
     * SHA-256 hash of the entire package.
     */
    @NonNull
    private final String packageChecksum;

    /**
     * Map of input file paths to their checksums.
     */
    @Singular("input")
    private final Map<String, String> inputs;

    /**
     * Map of build environment information.
     */
    @Singular("environmentEntry")
    private final Map<String, String> environment;

    /**
     * Signature algorithm (if signed).
     */
    private final String signatureAlgorithm;

    /**
     * Signature timestamp (if signed).
     */
    private final Instant signatureTimestamp;

    /**
     * Public key used for signing (if signed).
     */
    private final String signPublicKey;
}
