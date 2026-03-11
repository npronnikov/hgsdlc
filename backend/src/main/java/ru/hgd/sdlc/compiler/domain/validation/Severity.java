package ru.hgd.sdlc.compiler.domain.validation;

/**
 * Severity level for validation issues.
 */
public enum Severity {

    /**
     * An error that prevents the document from being valid.
     * Compilation cannot proceed when errors are present.
     */
    ERROR,

    /**
     * A warning that should be addressed but does not block compilation.
     * Warnings indicate potential issues or best practice violations.
     */
    WARNING
}
