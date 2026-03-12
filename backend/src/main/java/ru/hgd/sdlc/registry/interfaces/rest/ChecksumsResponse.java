package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 * Checksum information for a release package.
 */
@Getter
@Builder
@Jacksonized
public final class ChecksumsResponse {

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
     * Map of file paths to their checksums.
     */
    @Singular("entry")
    @NonNull
    private final Map<String, String> files;
}
