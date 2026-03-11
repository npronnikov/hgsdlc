package ru.hgd.sdlc.compiler.domain.model.authored;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value type representing a Phase identifier.
 * Must be non-empty and contain only alphanumeric characters, hyphens, and underscores.
 */
public final class PhaseId {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final String value;

    private PhaseId(String value) {
        this.value = value;
    }

    public static PhaseId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PhaseId cannot be null or blank");
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "PhaseId must contain only alphanumeric characters, hyphens, and underscores: " + value);
        }
        return new PhaseId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhaseId phaseId = (PhaseId) o;
        return value.equals(phaseId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "PhaseId{" + value + "}";
    }
}
