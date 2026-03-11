package ru.hgd.sdlc.compiler.domain.compiler;

/**
 * Interface representing compiled IR output.
 * Per ADR-002: Runtime executes compiled IR, never raw Markdown.
 *
 * <p>The IR is the canonical executable representation that the runtime consumes.
 * It contains all resolved references, normalized structures, and content hashes
 * for deterministic execution.
 */
public interface CompiledIR {

    /**
     * Returns the unique identifier for this compiled IR.
     */
    String irId();

    /**
     * Returns the version of the source document.
     */
    String sourceVersion();

    /**
     * Returns the checksum of this compiled IR.
     */
    String checksum();
}
