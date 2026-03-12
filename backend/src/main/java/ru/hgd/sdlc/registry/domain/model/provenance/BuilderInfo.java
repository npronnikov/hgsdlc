package ru.hgd.sdlc.registry.domain.model.provenance;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.util.Optional;

/**
 * Information about the build system that produced a release.
 * Captures the builder identity for provenance tracking.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public class BuilderInfo {

    /**
     * Name of the builder (e.g., "sdlc-registry", "ci-pipeline").
     */
    @NonNull private final String name;

    /**
     * Version of the builder.
     */
    @NonNull private final String version;

    /**
     * Hostname where the build was executed (optional for security).
     */
    private final String hostname;

    /**
     * Returns the hostname as an Optional.
     *
     * @return Optional containing the hostname if present
     */
    public Optional<String> hostnameOptional() {
        return Optional.ofNullable(hostname);
    }

    /**
     * Creates a BuilderInfo with required fields.
     *
     * @param name the builder name
     * @param version the builder version
     * @return a new BuilderInfo
     */
    public static BuilderInfo of(String name, String version) {
        return BuilderInfo.builder()
            .name(name)
            .version(version)
            .build();
    }

    /**
     * Creates a BuilderInfo with all fields including hostname.
     *
     * @param name the builder name
     * @param version the builder version
     * @param hostname the build hostname
     * @return a new BuilderInfo
     */
    public static BuilderInfo of(String name, String version, String hostname) {
        return BuilderInfo.builder()
            .name(name)
            .version(version)
            .hostname(hostname)
            .build();
    }
}
