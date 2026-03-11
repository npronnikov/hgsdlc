package ru.hgd.sdlc.compiler.testing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utility for loading fixture files from test resources.
 */
public final class FixtureResourceLoader implements TestFixture {

    private static final String FIXTURES_PATH = "/fixtures/";

    private FixtureResourceLoader() {
        // Utility class - no instantiation
    }

    /**
     * Loads a fixture file from the test resources fixtures directory.
     *
     * @param filename the fixture filename
     * @return the content of the fixture file
     * @throws IllegalArgumentException if the file cannot be found or read
     */
    public static String loadFixture(String filename) {
        String path = FIXTURES_PATH + filename;
        try (InputStream is = FixtureResourceLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Fixture file not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read fixture file: " + path, e);
        }
    }

    /**
     * Loads the simple-flow.md fixture.
     *
     * @return the content of simple-flow.md
     */
    public static String simpleFlow() {
        return loadFixture("simple-flow.md");
    }

    /**
     * Loads the multi-phase-flow.md fixture.
     *
     * @return the content of multi-phase-flow.md
     */
    public static String multiPhaseFlow() {
        return loadFixture("multi-phase-flow.md");
    }

    /**
     * Loads the simple-skill.md fixture.
     *
     * @return the content of simple-skill.md
     */
    public static String simpleSkill() {
        return loadFixture("simple-skill.md");
    }

    /**
     * Loads the complex-flow.md fixture.
     *
     * @return the content of complex-flow.md
     */
    public static String complexFlow() {
        return loadFixture("complex-flow.md");
    }

    /**
     * Checks if a fixture file exists.
     *
     * @param filename the fixture filename
     * @return true if the file exists
     */
    public static boolean exists(String filename) {
        String path = FIXTURES_PATH + filename;
        try (InputStream is = FixtureResourceLoader.class.getResourceAsStream(path)) {
            return is != null;
        } catch (IOException e) {
            return false;
        }
    }
}
