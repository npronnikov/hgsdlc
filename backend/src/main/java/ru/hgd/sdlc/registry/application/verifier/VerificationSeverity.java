package ru.hgd.sdlc.registry.application.verifier;

/**
 * Severity level for verification issues.
 */
public enum VerificationSeverity {
    /**
     * Critical error that makes the package invalid.
     */
    ERROR,

    /**
     * Warning that should be reviewed but doesn't invalidate the package.
     */
    WARNING,

    /**
     * Informational message about the verification process.
     */
    INFO
}
