package ru.hgd.sdlc.compiler.domain.model.ir;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.compiler.domain.model.authored.ArtifactTemplateId;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.util.Optional;

/**
 * Resolved artifact binding with schema hash.
 * This is the compiled form of ArtifactBinding with resolved schema information.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public class ResolvedArtifactBinding {

    @NonNull private final ArtifactTemplateId artifactId;

    /**
     * Optional binding name (alias used in the node).
     */
    private final String bindingName;

    /**
     * Whether this binding is required.
     */
    @Builder.Default private final boolean required = true;

    /**
     * Hash of the resolved schema for this artifact.
     */
    @NonNull private final Sha256 schemaHash;

    /**
     * Returns the binding name if present.
     */
    public Optional<String> bindingName() {
        return Optional.ofNullable(bindingName);
    }

    /**
     * Creates a required binding.
     */
    public static ResolvedArtifactBinding required(ArtifactTemplateId artifactId, Sha256 schemaHash) {
        return ResolvedArtifactBinding.builder()
            .artifactId(artifactId)
            .schemaHash(schemaHash)
            .required(true)
            .build();
    }

    /**
     * Creates an optional binding.
     */
    public static ResolvedArtifactBinding optional(ArtifactTemplateId artifactId, Sha256 schemaHash) {
        return ResolvedArtifactBinding.builder()
            .artifactId(artifactId)
            .schemaHash(schemaHash)
            .required(false)
            .build();
    }
}
