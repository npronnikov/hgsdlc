package ru.hgd.sdlc.compiler.domain.model.authored;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value type representing a Skill identifier.
 * Must be non-empty and contain only alphanumeric characters, hyphens, and underscores.
 */
public final class SkillId {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final String value;

    private SkillId(String value) {
        this.value = value;
    }

    public static SkillId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SkillId cannot be null or blank");
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "SkillId must contain only alphanumeric characters, hyphens, and underscores: " + value);
        }
        return new SkillId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkillId skillId = (SkillId) o;
        return value.equals(skillId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "SkillId{" + value + "}";
    }
}
