package ru.hgd.sdlc.compiler.domain.model.authored;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value type representing an Artifact Template identifier.
 * Must be non-empty and contain only alphanumeric characters, hyphens, and underscores.
 */
public final class ArtifactTemplateId {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final String value;

    private ArtifactTemplateId(String value) {
        this.value = value;
    }

    public static ArtifactTemplateId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ArtifactTemplateId cannot be null or blank");
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "ArtifactTemplateId must contain only alphanumeric characters, hyphens, and underscores: " + value);
        }
        return new ArtifactTemplateId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtifactTemplateId that = (ArtifactTemplateId) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "ArtifactTemplateId{" + value + "}";
    }
}
