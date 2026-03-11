package com.example.sdlc.shared.errors;

/**
 * Base exception for domain errors.
 */
public class DomainException extends RuntimeException {

    private final String code;
    private final String details;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
        this.details = null;
    }

    public DomainException(String code, String message, String details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public DomainException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = null;
    }

    public String getCode() {
        return code;
    }

    public String getDetails() {
        return details;
    }
}
