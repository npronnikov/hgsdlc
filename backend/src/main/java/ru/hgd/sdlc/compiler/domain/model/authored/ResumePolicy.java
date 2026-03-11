package ru.hgd.sdlc.compiler.domain.model.authored;

/**
 * Policy for resuming a flow after interruption.
 */
public enum ResumePolicy {

    /**
     * Resume from the last successful checkpoint.
     */
    FROM_CHECKPOINT,

    /**
     * Restart from the beginning of the current phase.
     */
    RESTART_PHASE,

    /**
     * Restart the entire flow from the beginning.
     */
    RESTART_FLOW,

    /**
     * Resume is not allowed; flow must be restarted manually.
     */
    MANUAL
}
