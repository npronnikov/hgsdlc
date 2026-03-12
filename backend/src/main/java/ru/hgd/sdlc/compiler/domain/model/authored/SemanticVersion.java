package ru.hgd.sdlc.compiler.domain.model.authored;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value type representing a semantic version (MAJOR.MINOR.PATCH).
 * Follows Semantic Versioning 2.0.0 specification.
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {

    private static final Pattern VERSION_PATTERN =
        Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z0-9.-]+))?(?:\\+([a-zA-Z0-9.-]+))?$");

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String buildMetadata;
    private final String formatted;

    private SemanticVersion(int major, int minor, int patch, String preRelease, String buildMetadata) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
        this.buildMetadata = buildMetadata;
        this.formatted = formatVersion();
    }

    /**
     * Parses a semantic version string.
     *
     * @param version the version string (e.g., "1.0.0", "2.1.0-alpha", "1.0.0-beta+build")
     * @return a new SemanticVersion instance
     * @throws IllegalArgumentException if the version string is invalid
     */
    @JsonCreator
    public static SemanticVersion of(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Version string cannot be null or blank");
        }

        Matcher matcher = VERSION_PATTERN.matcher(version.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid semantic version: " + version);
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        String preRelease = matcher.group(4);
        String buildMetadata = matcher.group(5);

        return new SemanticVersion(major, minor, patch, preRelease, buildMetadata);
    }

    /**
     * Creates a semantic version from components.
     *
     * @param major the major version (must be >= 0)
     * @param minor the minor version (must be >= 0)
     * @param patch the patch version (must be >= 0)
     * @return a new SemanticVersion instance
     */
    public static SemanticVersion of(int major, int minor, int patch) {
        validateComponent(major, "major");
        validateComponent(minor, "minor");
        validateComponent(patch, "patch");
        return new SemanticVersion(major, minor, patch, null, null);
    }

    private static void validateComponent(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " version must be >= 0: " + value);
        }
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public String preRelease() {
        return preRelease;
    }

    public String buildMetadata() {
        return buildMetadata;
    }

    public boolean isPreRelease() {
        return preRelease != null;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        Objects.requireNonNull(other, "Cannot compare to null");

        int result = Integer.compare(this.major, other.major);
        if (result != 0) return result;

        result = Integer.compare(this.minor, other.minor);
        if (result != 0) return result;

        result = Integer.compare(this.patch, other.patch);
        if (result != 0) return result;

        // Pre-release versions have lower precedence
        if (this.preRelease != null && other.preRelease == null) {
            return -1;
        }
        if (this.preRelease == null && other.preRelease != null) {
            return 1;
        }
        if (this.preRelease != null && other.preRelease != null) {
            return comparePreRelease(this.preRelease, other.preRelease);
        }

        return 0; // Build metadata does not affect precedence
    }

    private int comparePreRelease(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");

        int maxLen = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < maxLen; i++) {
            if (i >= partsA.length) return -1;
            if (i >= partsB.length) return 1;

            String partA = partsA[i];
            String partB = partsB[i];

            boolean numericA = partA.chars().allMatch(Character::isDigit);
            boolean numericB = partB.chars().allMatch(Character::isDigit);

            if (numericA && numericB) {
                int cmp = Integer.compare(Integer.parseInt(partA), Integer.parseInt(partB));
                if (cmp != 0) return cmp;
            } else if (numericA) {
                return -1; // Numeric identifiers have lower precedence
            } else if (numericB) {
                return 1;
            } else {
                int cmp = partA.compareTo(partB);
                if (cmp != 0) return cmp;
            }
        }

        return 0;
    }

    private String formatVersion() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (preRelease != null) {
            sb.append('-').append(preRelease);
        }
        if (buildMetadata != null) {
            sb.append('+').append(buildMetadata);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SemanticVersion that = (SemanticVersion) o;
        return major == that.major
            && minor == that.minor
            && patch == that.patch
            && Objects.equals(preRelease, that.preRelease);
        // Note: build metadata is intentionally excluded from equality
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease);
    }

    @Override
    @JsonValue
    public String toString() {
        return formatted;
    }
}
