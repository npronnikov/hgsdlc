package ru.hgd.sdlc.runtime.domain;

import java.util.Arrays;

public enum AiSessionMode {
    SHARED_RUN_SESSION("shared_run_session"),
    ISOLATED_ATTEMPT_SESSIONS("isolated_attempt_sessions");

    private final String apiValue;

    AiSessionMode(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static AiSessionMode fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ai_session_mode is required");
        }
        return Arrays.stream(values())
                .filter((mode) -> mode.apiValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported ai_session_mode: " + value));
    }
}
