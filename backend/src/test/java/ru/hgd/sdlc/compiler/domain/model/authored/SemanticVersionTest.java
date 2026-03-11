package ru.hgd.sdlc.compiler.domain.model.authored;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SemanticVersion")
class SemanticVersionTest {

    @Nested
    @DisplayName("of(String)")
    class OfString {

        @Test
        @DisplayName("parses simple version")
        void parsesSimpleVersion() {
            SemanticVersion v = SemanticVersion.of("1.2.3");

            assertEquals(1, v.major());
            assertEquals(2, v.minor());
            assertEquals(3, v.patch());
            assertEquals("1.2.3", v.toString());
        }

        @Test
        @DisplayName("parses version with pre-release")
        void parsesVersionWithPreRelease() {
            SemanticVersion v = SemanticVersion.of("1.0.0-alpha");

            assertEquals(1, v.major());
            assertTrue(v.isPreRelease());
            assertEquals("alpha", v.preRelease());
        }

        @Test
        @DisplayName("parses version with pre-release and build")
        void parsesVersionWithPreReleaseAndBuild() {
            SemanticVersion v = SemanticVersion.of("1.0.0-beta+build.123");

            assertEquals("beta", v.preRelease());
            assertEquals("build.123", v.buildMetadata());
        }

        @ParameterizedTest
        @ValueSource(strings = {"1.0.0", "0.0.1", "10.20.30", "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-0.3.7", "1.0.0-x.7.z.92", "1.0.0+build", "1.0.0-alpha+build"})
        @DisplayName("accepts valid versions")
        void acceptsValidVersions(String version) {
            assertDoesNotThrow(() -> SemanticVersion.of(version));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "1", "1.0", "1.0.0.", ".1.0.0", "1.0.0-", "v1.0.0", "1.0.0.0"})
        @DisplayName("rejects invalid versions")
        void rejectsInvalidVersions(String version) {
            assertThrows(IllegalArgumentException.class, () -> SemanticVersion.of(version));
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThrows(IllegalArgumentException.class, () -> SemanticVersion.of(null));
        }
    }

    @Nested
    @DisplayName("of(int, int, int)")
    class OfInts {

        @Test
        @DisplayName("creates from components")
        void createsFromComponents() {
            SemanticVersion v = SemanticVersion.of(2, 1, 0);

            assertEquals(2, v.major());
            assertEquals(1, v.minor());
            assertEquals(0, v.patch());
            assertEquals("2.1.0", v.toString());
        }

        @Test
        @DisplayName("rejects negative major")
        void rejectsNegativeMajor() {
            assertThrows(IllegalArgumentException.class, () -> SemanticVersion.of(-1, 0, 0));
        }

        @Test
        @DisplayName("rejects negative minor")
        void rejectsNegativeMinor() {
            assertThrows(IllegalArgumentException.class, () -> SemanticVersion.of(1, -1, 0));
        }

        @Test
        @DisplayName("rejects negative patch")
        void rejectsNegativePatch() {
            assertThrows(IllegalArgumentException.class, () -> SemanticVersion.of(1, 0, -1));
        }
    }

    @Nested
    @DisplayName("compareTo")
    class CompareTo {

        @Test
        @DisplayName("compares major version")
        void comparesMajor() {
            SemanticVersion v1 = SemanticVersion.of("1.0.0");
            SemanticVersion v2 = SemanticVersion.of("2.0.0");

            assertTrue(v1.compareTo(v2) < 0);
            assertTrue(v2.compareTo(v1) > 0);
        }

        @Test
        @DisplayName("compares minor version")
        void comparesMinor() {
            SemanticVersion v1 = SemanticVersion.of("1.0.0");
            SemanticVersion v2 = SemanticVersion.of("1.1.0");

            assertTrue(v1.compareTo(v2) < 0);
        }

        @Test
        @DisplayName("compares patch version")
        void comparesPatch() {
            SemanticVersion v1 = SemanticVersion.of("1.0.0");
            SemanticVersion v2 = SemanticVersion.of("1.0.1");

            assertTrue(v1.compareTo(v2) < 0);
        }

        @Test
        @DisplayName("pre-release has lower precedence")
        void preReleaseHasLowerPrecedence() {
            SemanticVersion v1 = SemanticVersion.of("1.0.0-alpha");
            SemanticVersion v2 = SemanticVersion.of("1.0.0");

            assertTrue(v1.compareTo(v2) < 0);
        }

        @Test
        @DisplayName("compares pre-release versions")
        void comparesPreReleaseVersions() {
            SemanticVersion v1 = SemanticVersion.of("1.0.0-alpha");
            SemanticVersion v2 = SemanticVersion.of("1.0.0-beta");

            assertTrue(v1.compareTo(v2) < 0);
        }

        @Test
        @DisplayName("numeric pre-release has lower precedence")
        void numericPreReleaseHasLowerPrecedence() {
            SemanticVersion v1 = SemanticVersion.of("1.0.0-1");
            SemanticVersion v2 = SemanticVersion.of("1.0.0-alpha");

            assertTrue(v1.compareTo(v2) < 0);
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("equal for same version")
        void equalForSameVersion() {
            SemanticVersion v1 = SemanticVersion.of("1.0.0");
            SemanticVersion v2 = SemanticVersion.of("1.0.0");

            assertEquals(v1, v2);
            assertEquals(v1.hashCode(), v2.hashCode());
        }

        @Test
        @DisplayName("equal ignoring build metadata")
        void equalIgnoringBuildMetadata() {
            SemanticVersion v1 = SemanticVersion.of("1.0.0+build1");
            SemanticVersion v2 = SemanticVersion.of("1.0.0+build2");

            assertEquals(v1, v2);
        }

        @Test
        @DisplayName("not equal for different pre-release")
        void notEqualForDifferentPreRelease() {
            SemanticVersion v1 = SemanticVersion.of("1.0.0-alpha");
            SemanticVersion v2 = SemanticVersion.of("1.0.0-beta");

            assertNotEquals(v1, v2);
        }
    }
}
