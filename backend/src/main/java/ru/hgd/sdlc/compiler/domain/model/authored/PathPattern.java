package ru.hgd.sdlc.compiler.domain.model.authored;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value type representing a path pattern for artifacts.
 * Supports glob-like patterns: *, **, {a,b}.
 */
public final class PathPattern {

    private final String pattern;

    private PathPattern(String pattern) {
        this.pattern = pattern;
    }

    public static PathPattern of(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("PathPattern cannot be null or blank");
        }
        // Basic validation - pattern should be a valid path
        String trimmed = pattern.trim();
        if (trimmed.contains("..")) {
            throw new IllegalArgumentException("PathPattern cannot contain '..': " + pattern);
        }
        return new PathPattern(trimmed);
    }

    public static PathPattern exact(String path) {
        return new PathPattern(path);
    }

    public static PathPattern glob(String pattern) {
        return new PathPattern(pattern);
    }

    public String pattern() {
        return pattern;
    }

    public boolean isGlob() {
        return pattern.contains("*") || pattern.contains("{") || pattern.contains("?");
    }

    /**
     * Converts this path pattern to a regex Pattern.
     * Note: This is a simple implementation for basic glob patterns.
     */
    public Pattern toRegex() {
        String regex = pattern
            .replace(".", "\\.")
            .replace("**", ".*")
            .replace("*", "[^/]*")
            .replace("?", "[^/]");
        return Pattern.compile("^" + regex + "$");
    }

    /**
     * Checks if the given path matches this pattern.
     *
     * @param path the path to check
     * @return true if the path matches
     */
    public boolean matches(String path) {
        return toRegex().matcher(path).matches();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathPattern that = (PathPattern) o;
        return pattern.equals(that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern);
    }

    @Override
    public String toString() {
        return pattern;
    }
}
