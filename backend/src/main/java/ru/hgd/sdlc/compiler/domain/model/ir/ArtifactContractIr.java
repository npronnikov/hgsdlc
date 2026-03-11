package ru.hgd.sdlc.compiler.domain.model.ir;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.compiler.domain.model.authored.ArtifactTemplateId;
import ru.hgd.sdlc.compiler.domain.model.authored.LogicalRole;
import ru.hgd.sdlc.compiler.domain.model.authored.SchemaId;
import ru.hgd.sdlc.shared.hashing.Sha256;

/**
 * Compiled artifact contract in IR.
 * Represents a resolved artifact template with schema hash.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public class ArtifactContractIr {

    @NonNull private final ArtifactTemplateId id;

    /**
     * Logical role of this artifact.
     */
    @NonNull private final LogicalRole logicalRole;

    /**
     * Schema ID for validation.
     */
    @NonNull private final SchemaId schemaId;

    /**
     * Whether this artifact is required.
     */
    @Builder.Default private final boolean required = true;

    /**
     * Hash of the schema for validation.
     */
    @NonNull private final Sha256 schemaHash;

    /**
     * Creates a contract from template components.
     */
    public static ArtifactContractIr of(
        ArtifactTemplateId id,
        LogicalRole logicalRole,
        SchemaId schemaId,
        boolean required,
        Sha256 schemaHash
    ) {
        return ArtifactContractIr.builder()
            .id(id)
            .logicalRole(logicalRole)
            .schemaId(schemaId)
            .required(required)
            .schemaHash(schemaHash)
            .build();
    }
}
