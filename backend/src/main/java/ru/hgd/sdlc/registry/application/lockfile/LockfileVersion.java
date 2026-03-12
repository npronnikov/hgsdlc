package ru.hgd.sdlc.registry.application.lockfile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lockfile schema version for evolution.
 */
public enum LockfileVersion {
    V1("1.0");

    private final String version;

    LockfileVersion(String version) {
        this.version = version;
    }

    /**
     * Returns the version string.
     *
     * @return the version string
     */
    @JsonValue
    public String version() {
        return version;
    }

    /**
     * Parses a version string to a LockfileVersion.
     *
     * @param version the version string
     * @return the corresponding LockfileVersion
     * @throws IllegalArgumentException if the version is not recognized
     */
    @JsonCreator
    public static LockfileVersion fromString(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Version cannot be null or blank");
        }
        for (LockfileVersion v : values()) {
            if (v.version.equals(version)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unknown lockfile version: " + version);
    }

    /**
     * Returns the current (latest) lockfile version.
     *
     * @return the current lockfile version
     */
    public static LockfileVersion current() {
        return V1;
    }
}
