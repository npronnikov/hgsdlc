package ru.hgd.sdlc.registry.application.lockfile;

/**
 * Exception thrown when lockfile serialization or deserialization fails.
 */
public class LockfileSerializationException extends RuntimeException {

    /**
     * Creates a new LockfileSerializationException with a message.
     *
     * @param message the error message
     */
    public LockfileSerializationException(String message) {
        super(message);
    }

    /**
     * Creates a new LockfileSerializationException with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public LockfileSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
