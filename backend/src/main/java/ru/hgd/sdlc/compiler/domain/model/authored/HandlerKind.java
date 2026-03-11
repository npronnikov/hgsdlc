package ru.hgd.sdlc.compiler.domain.model.authored;

/**
 * Kind of handler reference.
 */
public enum HandlerKind {

    /**
     * Skill handler (references a skill by ID).
     */
    SKILL,

    /**
     * Built-in platform capability.
     */
    BUILTIN,

    /**
     * Custom script.
     */
    SCRIPT
}
