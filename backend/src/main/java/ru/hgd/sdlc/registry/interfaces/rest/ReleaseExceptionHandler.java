package ru.hgd.sdlc.registry.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.hgd.sdlc.registry.application.resolver.ReleaseNotFoundException;
import ru.hgd.sdlc.registry.application.resolver.VersionConflictException;

import java.time.Instant;

/**
 * Exception handler for the release REST API.
 * Maps domain exceptions to appropriate HTTP error responses.
 */
@Slf4j
@RestControllerAdvice
public class ReleaseExceptionHandler {

    @ExceptionHandler(ReleaseNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ReleaseNotFoundException e,
            HttpServletRequest request
    ) {
        log.debug("Release not found: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .error("RELEASE_NOT_FOUND")
                .message(e.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            VersionConflictException e,
            HttpServletRequest request
    ) {
        log.warn("Version conflict: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .error("VERSION_CONFLICT")
                .message(e.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            IllegalArgumentException e,
            HttpServletRequest request
    ) {
        log.debug("Bad request: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .error("BAD_REQUEST")
                .message(e.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(
            Exception e,
            HttpServletRequest request
    ) {
        log.error("Unexpected error processing request to {}: {}",
                request.getRequestURI(), e.getMessage(), e);

        ErrorResponse response = ErrorResponse.builder()
                .error("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
