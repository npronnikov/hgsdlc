package ru.hgd.sdlc.compiler.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidateCommand")
class ValidateCommandTest {

    private ValidateCommand validateCommand;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        validateCommand = new ValidateCommand();
    }

    @Nested
    @DisplayName("flow validate")
    class FlowValidateTest {

        @Test
        @DisplayName("fails when file path is null")
        void failsWhenFilePathIsNull() {
            String result = validateCommand.flowValidate(null);
            assertTrue(result.contains("File path is required"));
        }

        @Test
        @DisplayName("fails when file does not exist")
        void failsWhenFileDoesNotExist() {
            String result = validateCommand.flowValidate("/nonexistent/path.md");
            assertTrue(result.contains("Failed to read file"));
        }

        @Test
        @DisplayName("validates valid flow markdown")
        void validatesValidFlowMarkdown() throws IOException {
            String markdown = """
                ---
                id: test-flow
                name: Test Flow
                version: 1.0.0
                ---
                # Test Flow
                """;
            Path flowFile = tempDir.resolve("test-flow.md");
            Files.writeString(flowFile, markdown);

            String result = validateCommand.flowValidate(flowFile.toString());

            assertTrue(result.contains("Validation passed"));
            assertTrue(result.contains("test-flow"));
        }

        @Test
        @DisplayName("reports errors for invalid flow")
        void reportsErrorsForInvalidFlow() throws IOException {
            String markdown = """
                ---
                name: Missing ID
                version: 1.0.0
                ---
                # Invalid Flow
                """;
            Path flowFile = tempDir.resolve("invalid-flow.md");
            Files.writeString(flowFile, markdown);

            String result = validateCommand.flowValidate(flowFile.toString());

            assertTrue(result.contains("Validation failed") || result.contains("error"));
        }

        @Test
        @DisplayName("shows flow statistics on success")
        void showsFlowStatisticsOnSuccess() throws IOException {
            String markdown = """
                ---
                id: test-flow
                name: Test Flow
                version: 1.0.0
                ---
                # Test Flow
                """;
            Path flowFile = tempDir.resolve("test-flow.md");
            Files.writeString(flowFile, markdown);

            String result = validateCommand.flowValidate(flowFile.toString());

            assertTrue(result.contains("Flow ID: test-flow"));
            assertTrue(result.contains("Version: 1.0.0"));
        }
    }

    @Nested
    @DisplayName("skill validate")
    class SkillValidateTest {

        @Test
        @DisplayName("fails when file path is null")
        void failsWhenFilePathIsNull() {
            String result = validateCommand.skillValidate(null);
            assertTrue(result.contains("File path is required"));
        }

        @Test
        @DisplayName("validates valid skill markdown")
        void validatesValidSkillMarkdown() throws IOException {
            String markdown = """
                ---
                id: test-skill
                name: Test Skill
                version: 1.0.0
                handler: skill://test-skill
                ---
                # Test Skill
                """;
            Path skillFile = tempDir.resolve("test-skill.md");
            Files.writeString(skillFile, markdown);

            String result = validateCommand.skillValidate(skillFile.toString());

            assertTrue(result.contains("Validation passed"));
            assertTrue(result.contains("test-skill"));
        }

        @Test
        @DisplayName("reports errors for invalid skill")
        void reportsErrorsForInvalidSkill() throws IOException {
            String markdown = """
                ---
                name: Missing ID
                version: 1.0.0
                handler: skill://test
                ---
                # Invalid Skill
                """;
            Path skillFile = tempDir.resolve("invalid-skill.md");
            Files.writeString(skillFile, markdown);

            String result = validateCommand.skillValidate(skillFile.toString());

            assertTrue(result.contains("Validation failed") || result.contains("error"));
        }

        @Test
        @DisplayName("shows skill statistics on success")
        void showsSkillStatisticsOnSuccess() throws IOException {
            String markdown = """
                ---
                id: test-skill
                name: Test Skill
                version: 1.0.0
                handler: skill://test-skill
                tags:
                  - ai
                  - code-gen
                ---
                # Test Skill
                """;
            Path skillFile = tempDir.resolve("test-skill.md");
            Files.writeString(skillFile, markdown);

            String result = validateCommand.skillValidate(skillFile.toString());

            assertTrue(result.contains("Skill ID: test-skill"));
            assertTrue(result.contains("Version: 1.0.0"));
            assertTrue(result.contains("Handler:"));
        }
    }
}
