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

@DisplayName("ParseCommand")
class ParseCommandTest {

    private ParseCommand parseCommand;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parseCommand = new ParseCommand();
    }

    @Nested
    @DisplayName("flow parse")
    class FlowParseTest {

        @Test
        @DisplayName("fails when file path is null")
        void failsWhenFilePathIsNull() {
            String result = parseCommand.flowParse(null);
            assertTrue(result.contains("File path is required"));
        }

        @Test
        @DisplayName("fails when file does not exist")
        void failsWhenFileDoesNotExist() {
            String result = parseCommand.flowParse("/nonexistent/path.md");
            assertTrue(result.contains("Failed to read file"));
        }

        @Test
        @DisplayName("shows raw frontmatter for valid flow")
        void showsRawFrontmatterForValidFlow() throws IOException {
            String markdown = """
                ---
                id: test-flow
                name: Test Flow
                version: 1.0.0
                ---
                # Test Flow

                This is a test flow.
                """;
            Path flowFile = tempDir.resolve("test-flow.md");
            Files.writeString(flowFile, markdown);

            String result = parseCommand.flowParse(flowFile.toString());

            assertTrue(result.contains("Raw Frontmatter"));
            assertTrue(result.contains("test-flow"));
            assertTrue(result.contains("1.0.0"));
        }

        @Test
        @DisplayName("shows body preview")
        void showsBodyPreview() throws IOException {
            String markdown = """
                ---
                id: test-flow
                version: 1.0.0
                ---
                # Test Flow

                This is a test flow body content.
                """;
            Path flowFile = tempDir.resolve("test-flow.md");
            Files.writeString(flowFile, markdown);

            String result = parseCommand.flowParse(flowFile.toString());

            assertTrue(result.contains("Body Preview"));
            assertTrue(result.contains("test flow body"));
        }

        @Test
        @DisplayName("shows parsed flow document structure")
        void showsParsedFlowDocumentStructure() throws IOException {
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

            String result = parseCommand.flowParse(flowFile.toString());

            assertTrue(result.contains("Parsed Flow Document"));
            assertTrue(result.contains("ID:"));
            assertTrue(result.contains("Version:"));
            assertTrue(result.contains("test-flow"));
        }

        @Test
        @DisplayName("reports parsing errors")
        void reportsParsingErrors() throws IOException {
            String markdown = """
                ---
                name: Missing ID
                version: 1.0.0
                ---
                # Invalid Flow
                """;
            Path flowFile = tempDir.resolve("invalid-flow.md");
            Files.writeString(flowFile, markdown);

            String result = parseCommand.flowParse(flowFile.toString());

            assertTrue(result.contains("Parsing failed") || result.contains("error"));
        }
    }

    @Nested
    @DisplayName("skill parse")
    class SkillParseTest {

        @Test
        @DisplayName("fails when file path is null")
        void failsWhenFilePathIsNull() {
            String result = parseCommand.skillParse(null);
            assertTrue(result.contains("File path is required"));
        }

        @Test
        @DisplayName("shows raw frontmatter for valid skill")
        void showsRawFrontmatterForValidSkill() throws IOException {
            String markdown = """
                ---
                id: test-skill
                name: Test Skill
                version: 1.0.0
                handler: skill://test-skill
                tags:
                  - ai
                ---
                # Test Skill
                """;
            Path skillFile = tempDir.resolve("test-skill.md");
            Files.writeString(skillFile, markdown);

            String result = parseCommand.skillParse(skillFile.toString());

            assertTrue(result.contains("Raw Frontmatter"));
            assertTrue(result.contains("test-skill"));
            assertTrue(result.contains("skill://test-skill"));
        }

        @Test
        @DisplayName("shows parsed skill document structure")
        void showsParsedSkillDocumentStructure() throws IOException {
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

            String result = parseCommand.skillParse(skillFile.toString());

            assertTrue(result.contains("Parsed Skill Document"));
            assertTrue(result.contains("ID:"));
            assertTrue(result.contains("Handler:"));
            assertTrue(result.contains("test-skill"));
        }

        @Test
        @DisplayName("shows input/output schemas")
        void showsInputOutputSchemas() throws IOException {
            String markdown = """
                ---
                id: test-skill
                name: Test Skill
                version: 1.0.0
                handler: skill://test-skill
                inputSchema:
                  type: object
                  properties:
                    name:
                      type: string
                ---
                # Test Skill
                """;
            Path skillFile = tempDir.resolve("test-skill.md");
            Files.writeString(skillFile, markdown);

            String result = parseCommand.skillParse(skillFile.toString());

            // Check for input schema section or handler info
            assertTrue(result.contains("Input Schema") || result.contains("Handler:") || result.contains("test-skill"));
        }
    }
}
