package ru.hgd.sdlc.compiler.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConsoleOutput")
class ConsoleOutputTest {

    private ConsoleOutput colorEnabled;
    private ConsoleOutput colorDisabled;

    @BeforeEach
    void setUp() {
        colorEnabled = new ConsoleOutput(true);
        colorDisabled = new ConsoleOutput(false);
    }

    @Nested
    @DisplayName("with colors enabled")
    class ColorEnabledTest {

        @Test
        @DisplayName("formats error text with red color")
        void formatsErrorWithRedColor() {
            String result = colorEnabled.error("test error");
            assertTrue(result.contains("test error"));
            assertTrue(result.contains("\u001B[31m")); // Red
            assertTrue(result.contains("\u001B[0m"));  // Reset
        }

        @Test
        @DisplayName("formats success text with green color")
        void formatsSuccessWithGreenColor() {
            String result = colorEnabled.success("test success");
            assertTrue(result.contains("test success"));
            assertTrue(result.contains("\u001B[32m")); // Green
        }

        @Test
        @DisplayName("formats warning text with yellow color")
        void formatsWarningWithYellowColor() {
            String result = colorEnabled.warning("test warning");
            assertTrue(result.contains("test warning"));
            assertTrue(result.contains("\u001B[33m")); // Yellow
        }

        @Test
        @DisplayName("formats info text with cyan color")
        void formatsInfoWithCyanColor() {
            String result = colorEnabled.info("test info");
            assertTrue(result.contains("test info"));
            assertTrue(result.contains("\u001B[36m")); // Cyan
        }

        @Test
        @DisplayName("formats bold text")
        void formatsBoldText() {
            String result = colorEnabled.bold("test bold");
            assertTrue(result.contains("test bold"));
            assertTrue(result.contains("\u001B[1m")); // Bold
        }
    }

    @Nested
    @DisplayName("with colors disabled")
    class ColorDisabledTest {

        @Test
        @DisplayName("returns plain text without ANSI codes")
        void returnsPlainTextWithoutAnsiCodes() {
            assertEquals("test error", colorDisabled.error("test error"));
            assertEquals("test success", colorDisabled.success("test success"));
            assertEquals("test warning", colorDisabled.warning("test warning"));
            assertEquals("test info", colorDisabled.info("test info"));
            assertEquals("test bold", colorDisabled.bold("test bold"));
        }
    }
}
