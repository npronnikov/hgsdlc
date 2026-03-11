package ru.hgd.sdlc.compiler.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FixtureResourceLoader.
 */
class FixtureResourceLoaderTest {

    @Nested
    @DisplayName("loadFixture()")
    class LoadFixtureTests {

        @Test
        @DisplayName("should load simple-flow.md")
        void shouldLoadSimpleFlow() {
            String content = FixtureResourceLoader.simpleFlow();

            assertNotNull(content);
            assertTrue(content.startsWith("---"));
            assertTrue(content.contains("id: simple-flow"));
        }

        @Test
        @DisplayName("should load multi-phase-flow.md")
        void shouldLoadMultiPhaseFlow() {
            String content = FixtureResourceLoader.multiPhaseFlow();

            assertNotNull(content);
            assertTrue(content.contains("id: multi-phase-flow"));
            assertTrue(content.contains("development"));
            assertTrue(content.contains("review"));
            assertTrue(content.contains("deployment"));
        }

        @Test
        @DisplayName("should load simple-skill.md")
        void shouldLoadSimpleSkill() {
            String content = FixtureResourceLoader.simpleSkill();

            assertNotNull(content);
            assertTrue(content.contains("id: code-generator"));
            assertTrue(content.contains("handler: skill://code-generator"));
        }

        @Test
        @DisplayName("should load complex-flow.md")
        void shouldLoadComplexFlow() {
            String content = FixtureResourceLoader.complexFlow();

            assertNotNull(content);
            assertTrue(content.contains("id: complex-flow"));
            assertTrue(content.contains("resumePolicy: FROM_CHECKPOINT"));
        }

        @Test
        @DisplayName("should throw for non-existent file")
        void shouldThrowForNonExistentFile() {
            assertThrows(IllegalArgumentException.class, () ->
                FixtureResourceLoader.loadFixture("non-existent-file.md")
            );
        }
    }

    @Nested
    @DisplayName("exists()")
    class ExistsTests {

        @Test
        @DisplayName("should return true for existing file")
        void shouldReturnTrueForExistingFile() {
            assertTrue(FixtureResourceLoader.exists("simple-flow.md"));
        }

        @Test
        @DisplayName("should return false for non-existing file")
        void shouldReturnFalseForNonExistingFile() {
            assertFalse(FixtureResourceLoader.exists("non-existent.md"));
        }
    }
}
