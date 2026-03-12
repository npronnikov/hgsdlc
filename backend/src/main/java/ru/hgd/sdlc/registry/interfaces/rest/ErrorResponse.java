package ru.hgd.sdlc.registry.interfaces.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Standard error response for REST API errors.
 */
@Getter
@Builder
@Jacksonized
public final class ErrorResponse {

    /**
     * Error type/code.
     */
    @NonNull
    private final String error;

    /**
     * Human-readable error message.
     */
    @NonNull
    private final String message;

    /**
     * Timestamp when the error occurred.
     */
    @NonNull
    private final Instant timestamp;

    /**
     * Request path that caused the error.
     */
    @NonNull
    private final String path;
}
