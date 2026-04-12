package ru.hgd.sdlc.runtime.domain;

import java.util.Arrays;

public enum ReworkSessionPolicy {
    RESUME_PREVIOUS_SESSION("resume_previous_session"),
    NEW_SESSION("new_session");

    private final String apiValue;

    ReworkSessionPolicy(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static ReworkSessionPolicy fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("session_policy is required");
        }
        return Arrays.stream(values())
                .filter((policy) -> policy.apiValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported session_policy: " + value));
    }
}
