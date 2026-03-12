package ru.hgd.sdlc.registry.domain.model.release;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReleaseVersion")
class ReleaseVersionTest {

    @Nested
    @DisplayName("of(String)")
    class OfString {

        @Test
        @DisplayName("should parse simple version")
        void shouldParseSimpleVersion() {
            ReleaseVersion version = ReleaseVersion.of("1.2.3");

            assertEquals(1, version.major());
            assertEquals(2, version.minor());
            assertEquals(3, version.patch());
            assertNull(version.prerelease());
            assertFalse(version.isPrerelease());
        }

        @Test
        @DisplayName("should parse version with prerelease")
        void shouldParseVersionWithPrerelease() {
            ReleaseVersion version = ReleaseVersion.of("1.2.3-alpha");

            assertEquals(1, version.major());
            assertEquals(2, version.minor());
            assertEquals(3, version.patch());
            assertEquals("alpha", version.prerelease());
            assertTrue(version.isPrerelease());
        }

        @Test
        @DisplayName("should parse version with complex prerelease")
        void shouldParseVersionWithComplexPrerelease() {
            ReleaseVersion version = ReleaseVersion.of("2.0.0-rc.1");

            assertEquals(2, version.major());
            assertEquals(0, version.minor());
            assertEquals(0, version.patch());
            assertEquals("rc.1", version.prerelease());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.0.0", "0.0.1", "0.1.0", "1.0.0", "99.99.99"})
        @DisplayName("should accept valid versions")
        void shouldAcceptValidVersions(String versionStr) {
            assertDoesNotThrow(() -> ReleaseVersion.of(versionStr));
        }

        @Test
        @DisplayName("should reject null")
        void shouldRejectNull() {
            assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.of(null));
        }

        @Test
        @DisplayName("should reject blank")
        void shouldRejectBlank() {
            assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.of("   "));
        }

        @ParameterizedTest
        @ValueSource(strings = {"1", "1.2", "1.2.3.4", "v1.2.3", "1.2.3+", "1.2.x"})
        @DisplayName("should reject invalid formats")
        void shouldRejectInvalidFormats(String versionStr) {
            assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.of(versionStr));
        }
    }

    @Nested
    @DisplayName("of(int, int, int)")
    class OfInts {

        @Test
        @DisplayName("should create version from components")
        void shouldCreateFromComponents() {
            ReleaseVersion version = ReleaseVersion.of(1, 2, 3);

            assertEquals(1, version.major());
            assertEquals(2, version.minor());
            assertEquals(3, version.patch());
            assertFalse(version.isPrerelease());
        }

        @Test
        @DisplayName("should reject negative major")
        void shouldRejectNegativeMajor() {
            assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.of(-1, 0, 0));
        }

        @Test
        @DisplayName("should reject negative minor")
        void shouldRejectNegativeMinor() {
            assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.of(0, -1, 0));
        }

        @Test
        @DisplayName("should reject negative patch")
        void shouldRejectNegativePatch() {
            assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.of(0, 0, -1));
        }
    }

    @Nested
    @DisplayName("of(int, int, int, String)")
    class OfIntsWithPrerelease {

        @Test
        @DisplayName("should create version with prerelease")
        void shouldCreateWithPrerelease() {
            ReleaseVersion version = ReleaseVersion.of(1, 2, 3, "beta");

            assertEquals(1, version.major());
            assertEquals(2, version.minor());
            assertEquals(3, version.patch());
            assertEquals("beta", version.prerelease());
            assertTrue(version.isPrerelease());
        }
    }

    @Nested
    @DisplayName("formatted()")
    class Formatted {

        @Test
        @DisplayName("should format simple version")
        void shouldFormatSimpleVersion() {
            ReleaseVersion version = ReleaseVersion.of(1, 2, 3);

            assertEquals("1.2.3", version.formatted());
        }

        @Test
        @DisplayName("should format version with prerelease")
        void shouldFormatWithPrerelease() {
            ReleaseVersion version = ReleaseVersion.of(1, 2, 3, "alpha");

            assertEquals("1.2.3-alpha", version.formatted());
        }

        @Test
        @DisplayName("toString should return formatted")
        void toStringShouldReturnFormatted() {
            ReleaseVersion version = ReleaseVersion.of(1, 2, 3);

            assertEquals("1.2.3", version.toString());
        }
    }

    @Nested
    @DisplayName("compareTo")
    class CompareTo {

        @Test
        @DisplayName("should compare by major version")
        void shouldCompareByMajor() {
            ReleaseVersion v1 = ReleaseVersion.of(1, 0, 0);
            ReleaseVersion v2 = ReleaseVersion.of(2, 0, 0);

            assertTrue(v1.compareTo(v2) < 0);
            assertTrue(v2.compareTo(v1) > 0);
        }

        @Test
        @DisplayName("should compare by minor version when major is equal")
        void shouldCompareByMinor() {
            ReleaseVersion v1 = ReleaseVersion.of(1, 0, 0);
            ReleaseVersion v2 = ReleaseVersion.of(1, 1, 0);

            assertTrue(v1.compareTo(v2) < 0);
            assertTrue(v2.compareTo(v1) > 0);
        }

        @Test
        @DisplayName("should compare by patch version when major and minor are equal")
        void shouldCompareByPatch() {
            ReleaseVersion v1 = ReleaseVersion.of(1, 0, 0);
            ReleaseVersion v2 = ReleaseVersion.of(1, 0, 1);

            assertTrue(v1.compareTo(v2) < 0);
            assertTrue(v2.compareTo(v1) > 0);
        }

        @Test
        @DisplayName("should consider prerelease as lower than release")
        void shouldConsiderPrereleaseLower() {
            ReleaseVersion v1 = ReleaseVersion.of(1, 0, 0, "alpha");
            ReleaseVersion v2 = ReleaseVersion.of(1, 0, 0);

            assertTrue(v1.compareTo(v2) < 0);
            assertTrue(v2.compareTo(v1) > 0);
        }

        @Test
        @DisplayName("should compare prerelease identifiers")
        void shouldComparePrereleaseIdentifiers() {
            ReleaseVersion v1 = ReleaseVersion.of(1, 0, 0, "alpha");
            ReleaseVersion v2 = ReleaseVersion.of(1, 0, 0, "beta");

            assertTrue(v1.compareTo(v2) < 0);
            assertTrue(v2.compareTo(v1) > 0);
        }

        @Test
        @DisplayName("should compare numeric prerelease identifiers")
        void shouldCompareNumericPrereleaseIdentifiers() {
            ReleaseVersion v1 = ReleaseVersion.of(1, 0, 0, "rc.1");
            ReleaseVersion v2 = ReleaseVersion.of(1, 0, 0, "rc.2");

            assertTrue(v1.compareTo(v2) < 0);
            assertTrue(v2.compareTo(v1) > 0);
        }

        @Test
        @DisplayName("should consider equal versions as equal")
        void shouldConsiderEqualVersionsEqual() {
            ReleaseVersion v1 = ReleaseVersion.of(1, 2, 3);
            ReleaseVersion v2 = ReleaseVersion.of(1, 2, 3);

            assertEquals(0, v1.compareTo(v2));
        }

        @Test
        @DisplayName("should reject null comparison")
        void shouldRejectNullComparison() {
            ReleaseVersion version = ReleaseVersion.of(1, 0, 0);

            assertThrows(NullPointerException.class, () -> version.compareTo(null));
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("should be equal for same components")
        void shouldBeEqualForSameComponents() {
            ReleaseVersion v1 = ReleaseVersion.of(1, 2, 3);
            ReleaseVersion v2 = ReleaseVersion.of(1, 2, 3);

            assertEquals(v1, v2);
            assertEquals(v1.hashCode(), v2.hashCode());
        }

        @Test
        @DisplayName("should be equal for same components including prerelease")
        void shouldBeEqualForSameComponentsWithPrerelease() {
            ReleaseVersion v1 = ReleaseVersion.of("1.2.3-alpha");
            ReleaseVersion v2 = ReleaseVersion.of("1.2.3-alpha");

            assertEquals(v1, v2);
            assertEquals(v1.hashCode(), v2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different major")
        void shouldNotBeEqualForDifferentMajor() {
            ReleaseVersion v1 = ReleaseVersion.of(1, 0, 0);
            ReleaseVersion v2 = ReleaseVersion.of(2, 0, 0);

            assertNotEquals(v1, v2);
        }

        @Test
        @DisplayName("should not be equal for different prerelease")
        void shouldNotBeEqualForDifferentPrerelease() {
            ReleaseVersion v1 = ReleaseVersion.of("1.0.0-alpha");
            ReleaseVersion v2 = ReleaseVersion.of("1.0.0-beta");

            assertNotEquals(v1, v2);
        }
    }
}
