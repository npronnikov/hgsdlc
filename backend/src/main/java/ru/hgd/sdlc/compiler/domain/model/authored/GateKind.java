package ru.hgd.sdlc.compiler.domain.model.authored;

/**
 * Kind of gate for GATE nodes.
 */
public enum GateKind {

    /**
     * Requires human approval before proceeding.
     */
    APPROVAL,

    /**
     * Requires completion of a questionnaire.
     */
    QUESTIONNAIRE,

    /**
     * Conditional gate based on evaluated condition.
     */
    CONDITIONAL
}
