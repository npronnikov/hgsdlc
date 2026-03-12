package ru.hgd.sdlc.compiler.domain.model.authored;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value type representing a Flow identifier.
 * Must be non-empty and contain only alphanumeric characters, hyphens, and underscores.
 */
public final class FlowId {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final String value;

    private FlowId(String value) {
        this.value = value;
    }

    @JsonCreator
    public static FlowId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FlowId cannot be null or blank");
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "FlowId must contain only alphanumeric characters, hyphens, and underscores: " + value);
        }
        return new FlowId(value);
    }

    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlowId flowId = (FlowId) o;
        return value.equals(flowId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "FlowId{" + value + "}";
    }
}
