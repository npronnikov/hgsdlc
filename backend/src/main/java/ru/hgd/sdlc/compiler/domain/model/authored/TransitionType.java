package ru.hgd.sdlc.compiler.domain.model.authored;

/**
 * Type of transition between nodes.
 */
public enum TransitionType {

    /**
     * Normal forward transition.
     */
    FORWARD,

    /**
     * Backward transition for rework (returns to earlier node).
     */
    REWORK,

    /**
     * Skip transition (conditionally skips nodes).
     */
    SKIP
}
