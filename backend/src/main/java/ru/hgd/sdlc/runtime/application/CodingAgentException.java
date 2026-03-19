package ru.hgd.sdlc.runtime.application;

import java.util.Map;

public class CodingAgentException extends Exception {
    private final String errorCode;
    private final Map<String, Object> details;

    public CodingAgentException(String errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public CodingAgentException(String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : details;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
