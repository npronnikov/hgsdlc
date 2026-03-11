package ru.hgd.sdlc.compiler.domain.model.ir;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.time.Instant;

/**
 * Metadata for compiled IR.
 * Contains versioning, checksums, and compilation timestamps.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public class IrMetadata {

    /**
     * Current IR schema version for evolution.
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Hash of all source files in the package.
     */
    @NonNull private final Sha256 packageChecksum;

    /**
     * Hash of this IR (computed after compilation).
     */
    @NonNull private final Sha256 irChecksum;

    /**
     * Timestamp when this IR was compiled.
     */
    @NonNull private final Instant compiledAt;

    /**
     * Version of the compiler that produced this IR.
     */
    @NonNull private final String compilerVersion;

    /**
     * Schema version of this IR.
     */
    @Builder.Default private final int schemaVersion = CURRENT_SCHEMA_VERSION;

    /**
     * Creates metadata with current timestamp and default schema version.
     */
    public static IrMetadata create(Sha256 packageChecksum, Sha256 irChecksum, String compilerVersion) {
        return IrMetadata.builder()
            .packageChecksum(packageChecksum)
            .irChecksum(irChecksum)
            .compiledAt(Instant.now())
            .compilerVersion(compilerVersion)
            .build();
    }
}
