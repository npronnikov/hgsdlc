package ru.hgd.sdlc.compiler.domain.model.authored;

/**
 * Kind of executor for EXECUTOR nodes.
 */
public enum ExecutorKind {

    /**
     * Executes a skill (referenced by SkillId).
     */
    SKILL,

    /**
     * Executes a built-in platform capability.
     */
    BUILTIN,

    /**
     * Executes a custom script.
     */
    SCRIPT
}
