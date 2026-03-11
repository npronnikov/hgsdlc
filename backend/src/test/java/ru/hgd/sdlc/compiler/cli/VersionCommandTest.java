package ru.hgd.sdlc.compiler.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VersionCommand")
class VersionCommandTest {

    private VersionCommand versionCommand;

    @BeforeEach
    void setUp() {
        versionCommand = new VersionCommand();
    }

    @Nested
    @DisplayName("version command")
    class VersionTest {

        @Test
        @DisplayName("returns version information")
        void returnsVersionInformation() {
            String result = versionCommand.version();

            assertNotNull(result);
            assertTrue(result.contains("Human-Guided SDLC Compiler"));
            assertTrue(result.contains("Compiler Version"));
            assertTrue(result.contains("IR Schema Version"));
            assertTrue(result.contains("Java Version"));
        }

        @Test
        @DisplayName("includes current compiler version")
        void includesCurrentCompilerVersion() {
            String result = versionCommand.version();

            assertTrue(result.contains("1.0.0"));
        }

        @Test
        @DisplayName("includes Java version")
        void includesJavaVersion() {
            String result = versionCommand.version();

            String javaVersion = System.getProperty("java.version");
            assertTrue(result.contains(javaVersion));
        }
    }

    @Nested
    @DisplayName("static methods")
    class StaticMethodsTest {

        @Test
        @DisplayName("getCompilerVersion returns current version")
        void getCompilerVersionReturnsCurrentVersion() {
            assertEquals("1.0.0", VersionCommand.getCompilerVersion());
        }

        @Test
        @DisplayName("getIrSchemaVersion returns current schema version")
        void getIrSchemaVersionReturnsCurrentSchemaVersion() {
            assertEquals("1", VersionCommand.getIrSchemaVersion());
        }
    }
}
