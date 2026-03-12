package ru.hgd.sdlc.registry.application.signing;

/**
 * Exception thrown when a signing operation fails.
 * This includes key generation, loading, or signing failures.
 */
public final class SigningException extends RuntimeException {

    /**
     * Creates a signing exception with a message.
     *
     * @param message the error message
     */
    public SigningException(String message) {
        super(message);
    }

    /**
     * Creates a signing exception with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public SigningException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a signing exception with a cause.
     *
     * @param cause the underlying cause
     */
    public SigningException(Throwable cause) {
        super(cause);
    }
}
