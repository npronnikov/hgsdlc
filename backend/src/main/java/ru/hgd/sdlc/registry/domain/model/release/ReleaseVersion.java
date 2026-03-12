package ru.hgd.sdlc.registry.domain.model.release;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value object representing a release version following semantic versioning.
 * Pattern: MAJOR.MINOR.PATCH[-PRERELEASE]
 *
 * <p>This is the registry-specific version format used for release packages.
 * It supports basic semver with optional pre-release tags.
 */
public final class ReleaseVersion implements Comparable<ReleaseVersion> {

    private static final Pattern VERSION_PATTERN =
        Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z0-9.-]+))?$");

    private final int major;
    private final int minor;
    private final int patch;
    private final String prerelease;
    private final String formatted;

    private ReleaseVersion(int major, int minor, int patch, String prerelease) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = prerelease;
        this.formatted = formatVersion();
    }

    /**
     * Parses a version string.
     *
     * @param version the version string (e.g., "1.0.0", "2.1.0-alpha", "1.0.0-rc.1")
     * @return a new ReleaseVersion instance
     * @throws IllegalArgumentException if the version string is invalid
     */
    @JsonCreator
    public static ReleaseVersion of(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Version string cannot be null or blank");
        }

        Matcher matcher = VERSION_PATTERN.matcher(version.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Invalid version format. Expected: MAJOR.MINOR.PATCH[-PRERELEASE]. Got: " + version);
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        String prerelease = matcher.group(4);

        validateComponent(major, "major");
        validateComponent(minor, "minor");
        validateComponent(patch, "patch");

        return new ReleaseVersion(major, minor, patch, prerelease);
    }

    /**
     * Creates a version from components (without pre-release).
     *
     * @param major the major version (must be >= 0)
     * @param minor the minor version (must be >= 0)
     * @param patch the patch version (must be >= 0)
     * @return a new ReleaseVersion instance
     */
    public static ReleaseVersion of(int major, int minor, int patch) {
        validateComponent(major, "major");
        validateComponent(minor, "minor");
        validateComponent(patch, "patch");
        return new ReleaseVersion(major, minor, patch, null);
    }

    /**
     * Creates a version with a pre-release tag.
     *
     * @param major the major version (must be >= 0)
     * @param minor the minor version (must be >= 0)
     * @param patch the patch version (must be >= 0)
     * @param prerelease the pre-release tag (e.g., "alpha", "rc.1")
     * @return a new ReleaseVersion instance
     */
    public static ReleaseVersion of(int major, int minor, int patch, String prerelease) {
        validateComponent(major, "major");
        validateComponent(minor, "minor");
        validateComponent(patch, "patch");
        return new ReleaseVersion(major, minor, patch, prerelease);
    }

    private static void validateComponent(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " version must be >= 0: " + value);
        }
    }

    /**
     * Returns the major version component.
     */
    public int major() {
        return major;
    }

    /**
     * Returns the minor version component.
     */
    public int minor() {
        return minor;
    }

    /**
     * Returns the patch version component.
     */
    public int patch() {
        return patch;
    }

    /**
     * Returns the pre-release tag, or null if this is a stable release.
     */
    public String prerelease() {
        return prerelease;
    }

    /**
     * Returns true if this is a pre-release version.
     */
    public boolean isPrerelease() {
        return prerelease != null;
    }

    /**
     * Returns the formatted version string.
     */
    @JsonValue
    public String formatted() {
        return formatted;
    }

    private String formatVersion() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (prerelease != null) {
            sb.append('-').append(prerelease);
        }
        return sb.toString();
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        Objects.requireNonNull(other, "Cannot compare to null");

        int result = Integer.compare(this.major, other.major);
        if (result != 0) return result;

        result = Integer.compare(this.minor, other.minor);
        if (result != 0) return result;

        result = Integer.compare(this.patch, other.patch);
        if (result != 0) return result;

        // Pre-release versions have lower precedence than stable versions
        if (this.prerelease != null && other.prerelease == null) {
            return -1;
        }
        if (this.prerelease == null && other.prerelease != null) {
            return 1;
        }
        if (this.prerelease != null && other.prerelease != null) {
            return comparePrerelease(this.prerelease, other.prerelease);
        }

        return 0;
    }

    private int comparePrerelease(String a, String b) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReleaseVersion that = (ReleaseVersion) o;
        return major == that.major
            && minor == that.minor
            && patch == that.patch
            && Objects.equals(prerelease, that.prerelease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, prerelease);
    }

    @Override
    public String toString() {
        return formatted;
    }
}
