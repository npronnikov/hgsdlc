package ru.hgd.sdlc.registry.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.registry.application.resolver.ReleaseNotFoundException;
import ru.hgd.sdlc.registry.application.resolver.VersionConflictException;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReleaseExceptionHandlerTest {

    private ReleaseExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new ReleaseExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/releases/test-flow/1.0.0");
    }

    @Nested
    @DisplayName("handleNotFound")
    class HandleNotFound {

        @Test
        @DisplayName("should return 404 with error response")
        void shouldReturn404WithErrorResponse() {
            ReleaseId releaseId = ReleaseId.of(FlowId.of("test-flow"), ReleaseVersion.of("1.0.0"));
            ReleaseNotFoundException ex = new ReleaseNotFoundException(releaseId);

            ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex, request);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("RELEASE_NOT_FOUND", response.getBody().getError());
            assertTrue(response.getBody().getMessage().contains("test-flow@1.0.0"));
            assertEquals("/api/v1/releases/test-flow/1.0.0", response.getBody().getPath());
            assertNotNull(response.getBody().getTimestamp());
        }

        @Test
        @DisplayName("should handle null release ID")
        void shouldHandleNullReleaseId() {
            ReleaseNotFoundException ex = new ReleaseNotFoundException(null);

            ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex, request);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals("RELEASE_NOT_FOUND", response.getBody().getError());
        }
    }

    @Nested
    @DisplayName("handleConflict")
    class HandleConflict {

        @Test
        @DisplayName("should return 409 with error response")
        void shouldReturn409WithErrorResponse() {
            ReleaseId releaseId1 = ReleaseId.of(FlowId.of("flow1"), ReleaseVersion.of("1.0.0"));
            ReleaseId releaseId2 = ReleaseId.of(FlowId.of("flow2"), ReleaseVersion.of("2.0.0"));
            VersionConflictException ex = new VersionConflictException(Set.of(releaseId1, releaseId2));

            ResponseEntity<ErrorResponse> response = handler.handleConflict(ex, request);

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("VERSION_CONFLICT", response.getBody().getError());
            assertTrue(response.getBody().getMessage().contains("Version conflict"));
        }
    }

    @Nested
    @DisplayName("handleBadRequest")
    class HandleBadRequest {

        @Test
        @DisplayName("should return 400 with error response")
        void shouldReturn400WithErrorResponse() {
            IllegalArgumentException ex = new IllegalArgumentException("Invalid version format");

            ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex, request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("BAD_REQUEST", response.getBody().getError());
            assertEquals("Invalid version format", response.getBody().getMessage());
        }
    }

    @Nested
    @DisplayName("handleGenericError")
    class HandleGenericError {

        @Test
        @DisplayName("should return 500 with generic error response")
        void shouldReturn500WithGenericErrorResponse() {
            RuntimeException ex = new RuntimeException("Unexpected error");

            ResponseEntity<ErrorResponse> response = handler.handleGenericError(ex, request);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("INTERNAL_ERROR", response.getBody().getError());
            assertEquals("An unexpected error occurred", response.getBody().getMessage());
        }
    }
}
