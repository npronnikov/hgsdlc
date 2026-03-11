package ru.hgd.sdlc.compiler.domain.model.authored;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value type representing a user role in the SDLC process.
 */
public final class Role {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-z_]+$");

    private final String value;

    private Role(String value) {
        this.value = value;
    }

    public static Role of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Role cannot be null or blank");
        }
        String normalized = value.toLowerCase().trim();
        if (!VALID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "Role must contain only lowercase letters and underscores: " + value);
        }
        return new Role(normalized);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return value.equals(role.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
