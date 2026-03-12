package ru.hgd.sdlc.registry.application.builder;

/**
 * Exception thrown when a release build operation fails.
 * This includes validation failures, provenance generation errors,
 * or any other issue during the release build process.
 */
public final class ReleaseBuildException extends RuntimeException {

    /**
     * Creates a release build exception with a message.
     *
     * @param message the error message
     */
    public ReleaseBuildException(String message) {
        super(message);
    }

    /**
     * Creates a release build exception with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public ReleaseBuildException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a release build exception with a cause.
     *
     * @param cause the underlying cause
     */
    public ReleaseBuildException(Throwable cause) {
        super(cause);
    }
}
