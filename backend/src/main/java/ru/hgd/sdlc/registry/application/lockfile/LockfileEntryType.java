package ru.hgd.sdlc.registry.application.lockfile;

/**
 * Type of entry in the lockfile.
 */
public enum LockfileEntryType {
    /**
     * A flow release.
     */
    FLOW,

    /**
     * A skill release.
     */
    SKILL
}
