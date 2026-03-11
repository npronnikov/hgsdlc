package ru.hgd.sdlc.compiler.domain.model.authored;

/**
 * Type of a node in a flow.
 * Determines how the node is executed.
 */
public enum NodeType {

    /**
     * Executor node performs automated actions.
     * Examples: AI code generation, script execution, validation.
     */
    EXECUTOR,

    /**
     * Gate node pauses execution for human input.
     * Examples: approval, questionnaire, conditional check.
     */
    GATE
}
