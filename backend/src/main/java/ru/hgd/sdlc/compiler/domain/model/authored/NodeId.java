package ru.hgd.sdlc.compiler.domain.model.authored;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value type representing a Node identifier.
 * Must be non-empty and contain only alphanumeric characters, hyphens, and underscores.
 */
public final class NodeId {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final String value;

    private NodeId(String value) {
        this.value = value;
    }

    public static NodeId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("NodeId cannot be null or blank");
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "NodeId must contain only alphanumeric characters, hyphens, and underscores: " + value);
        }
        return new NodeId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeId nodeId = (NodeId) o;
        return value.equals(nodeId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "NodeId{" + value + "}";
    }
}
