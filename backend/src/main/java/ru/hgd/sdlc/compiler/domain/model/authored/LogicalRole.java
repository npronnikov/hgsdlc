package ru.hgd.sdlc.compiler.domain.model.authored;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value type representing the logical role of an artifact.
 * Examples: "source-code", "documentation", "config", "test-results".
 */
public final class LogicalRole {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");

    private final String value;

    private LogicalRole(String value) {
        this.value = value;
    }

    public static LogicalRole of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LogicalRole cannot be null or blank");
        }
        String normalized = value.toLowerCase().trim();
        if (!VALID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "LogicalRole must start with lowercase letter and contain only lowercase letters, numbers, and hyphens: " + value);
        }
        return new LogicalRole(normalized);
    }

    // Common logical roles
    public static LogicalRole sourceCode() {
        return new LogicalRole("source-code");
    }

    public static LogicalRole documentation() {
        return new LogicalRole("documentation");
    }

    public static LogicalRole config() {
        return new LogicalRole("config");
    }

    public static LogicalRole testResults() {
        return new LogicalRole("test-results");
    }

    public static LogicalRole artifacts() {
        return new LogicalRole("artifacts");
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogicalRole that = (LogicalRole) o;
        return value.equals(that.value);
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
