package ru.hgd.sdlc.registry.domain.model.provenance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Immutable provenance record for a release package.
 * Contains information about where and how the package was built,
 * enabling verification of supply chain integrity.
 */
@Getter
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Provenance {

    /**
     * Pattern for valid Git commit SHA-1 hash (40 hex characters).
     */
    private static final Pattern GIT_SHA_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$");

    /**
     * Shared ObjectMapper for canonical JSON serialization.
     */
    private static final ObjectMapper CANONICAL_MAPPER = createCanonicalMapper();

    private static ObjectMapper createCanonicalMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new Jdk8Module());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return mapper;
    }

    /**
     * Version of the provenance format.
     */
    @Builder.Default private final int provenanceVersion = 1;

    /**
     * Unique identifier of the release this provenance belongs to.
     */
    @NonNull private final ReleaseId releaseId;

    /**
     * Git repository URL where the source resides.
     */
    @NonNull private final String repositoryUrl;

    /**
     * Git commit SHA that this release was built from (40-char hex string).
     */
    @NonNull private final String commitSha;

    /**
     * Timestamp when the release was built.
     */
    @NonNull private final Instant buildTimestamp;

    /**
     * Git tag for this release (may be null).
     */
    private final String gitTag;

    /**
     * Git branch name (may be null).
     */
    private final String branch;

    /**
     * Commit message (may be null).
     */
    private final String commitMessage;

    /**
     * Author of the commit (e.g., email).
     */
    @NonNull private final String commitAuthor;

    /**
     * Timestamp when the commit was made.
     */
    @NonNull private final Instant committedAt;

    /**
     * Identity of the builder (e.g., "ci-pipeline", "developer@localhost").
     */
    @NonNull private final String builderId;

    /**
     * Information about the builder that produced this release.
     */
    @NonNull private final BuilderInfo builder;

    /**
     * Version of the compiler used to build this release.
     */
    @NonNull private final String compilerVersion;

    /**
     * SHA-256 hash of the compiled IR.
     */
    @NonNull private final Sha256Hash irChecksum;

    /**
     * SHA-256 hash of the entire package.
     */
    @NonNull private final Sha256Hash packageChecksum;

    /**
     * Map of input file paths to their SHA-256 hashes.
     */
    @Singular("input") private final Map<String, String> inputs;

    /**
     * Map of build environment information (e.g., Java version, OS).
     */
    @Singular("environmentEntry") private final Map<String, String> environment;

    /**
     * Optional signature (added after signing).
     */
    private final ProvenanceSignature signature;

    /**
     * Returns the git tag if present.
     */
    public Optional<String> getGitTag() {
        return Optional.ofNullable(gitTag);
    }

    /**
     * Returns the branch name if present.
     */
    public Optional<String> getBranch() {
        return Optional.ofNullable(branch);
    }

    /**
     * Returns the commit message if present.
     */
    public Optional<String> getCommitMessage() {
        return Optional.ofNullable(commitMessage);
    }

    /**
     * Returns the signature if present.
     */
    public Optional<ProvenanceSignature> getSignature() {
        return Optional.ofNullable(signature);
    }

    /**
     * Checks if this provenance has been signed.
     */
    public boolean isSigned() {
        return signature != null;
    }

    /**
     * Creates a copy of this provenance with the given signature.
     *
     * @param signature the signature to add
     * @return a new Provenance with the signature
     */
    public Provenance withSignature(ProvenanceSignature signature) {
        return this.toBuilder().signature(signature).build();
    }

    /**
     * Generates the canonical JSON representation for signing.
     * The canonical form ensures consistent serialization for signature verification.
     * Note: This serializes the provenance WITHOUT the signature field for signing.
     *
     * @return canonical JSON string
     * @throws IllegalStateException if serialization fails
     */
    public String toSignablePayload() {
        try {
            // Create a copy without the signature for signing
            Provenance unsigned = signature != null
                ? this.toBuilder().signature(null).build()
                : this;
            return CANONICAL_MAPPER.writeValueAsString(unsigned);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize provenance for signing", e);
        }
    }

    /**
     * Parses a Provenance from its JSON representation.
     *
     * @param json the JSON string
     * @return the parsed Provenance
     * @throws IllegalArgumentException if parsing fails
     */
    public static Provenance fromJson(String json) {
        try {
            return CANONICAL_MAPPER.readValue(json, Provenance.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse provenance from JSON", e);
        }
    }

    /**
     * Validates a git commit SHA.
     *
     * @param commitSha the commit SHA to validate
     * @return the normalized (lowercase) commit SHA
     * @throws IllegalArgumentException if the commit SHA is invalid
     */
    public static String validateGitCommit(String commitSha) {
        if (commitSha == null || commitSha.isBlank()) {
            throw new IllegalArgumentException("Git commit cannot be null or blank");
        }
        if (!GIT_SHA_PATTERN.matcher(commitSha).matches()) {
            throw new IllegalArgumentException(
                "Git commit must be a valid 40-character SHA-1 hash: " + commitSha);
        }
        return commitSha.toLowerCase();
    }
}
