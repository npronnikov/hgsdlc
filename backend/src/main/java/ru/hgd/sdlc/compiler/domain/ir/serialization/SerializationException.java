package ru.hgd.sdlc.compiler.domain.ir.serialization;

/**
 * Exception thrown when IR serialization or deserialization fails.
 */
public class SerializationException extends Exception {

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
