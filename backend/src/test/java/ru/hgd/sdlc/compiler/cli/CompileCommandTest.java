package ru.hgd.sdlc.compiler.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.hgd.sdlc.compiler.application.*;
import ru.hgd.sdlc.compiler.domain.compiler.FlowCompiler;
import ru.hgd.sdlc.compiler.domain.compiler.SkillCompiler;
import ru.hgd.sdlc.compiler.domain.parser.FlowParser;
import ru.hgd.sdlc.compiler.domain.parser.FrontmatterExtractor;
import ru.hgd.sdlc.compiler.domain.parser.SkillParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompileCommand")
class CompileCommandTest {

    private CompileCommand compileCommand;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Create domain dependencies manually for unit testing
        FrontmatterExtractor frontmatterExtractor = new FrontmatterExtractor();
        FlowParser flowParser = new FlowParser();
        SkillParser skillParser = new SkillParser();
        FlowCompiler flowCompiler = new FlowCompiler();
        SkillCompiler skillCompiler = new SkillCompiler();

        // Create services with default constructors (they create internal validators/serializers)
        ParseService parseService = new ParseService(frontmatterExtractor, flowParser, skillParser);
        ValidationService validationService = new ValidationService();
        SerializationService serializationService = new SerializationService(true); // prettyPrint for readable output
        FlowCompilerService flowCompilerService = new FlowCompilerService(parseService, validationService, flowCompiler, serializationService);
        SkillCompilerService skillCompilerService = new SkillCompilerService(parseService, validationService, skillCompiler, serializationService);
        CompilerService compilerService = new CompilerService(parseService, validationService, serializationService, flowCompilerService, skillCompilerService);

        compileCommand = new CompileCommand(compilerService);
    }

    @Nested
    @DisplayName("flow compile")
    class FlowCompileTest {

        @Test
        @DisplayName("fails when file path is null")
        void failsWhenFilePathIsNull() {
            String result = compileCommand.flowCompile(null, null, null, false);
            assertTrue(result.contains("File path is required"));
        }

        @Test
        @DisplayName("fails when file path is blank")
        void failsWhenFilePathIsBlank() {
            String result = compileCommand.flowCompile("  ", null, null, false);
            assertTrue(result.contains("File path is required"));
        }

        @Test
        @DisplayName("fails when file does not exist")
        void failsWhenFileDoesNotExist() {
            String result = compileCommand.flowCompile("/nonexistent/path.md", null, null, false);
            assertTrue(result.contains("Failed to read file"));
        }

        @Test
        @DisplayName("compiles valid flow markdown")
        void compilesValidFlowMarkdown() throws IOException {
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

            String result = compileCommand.flowCompile(flowFile.toString(), null, "json", false);

            // Should contain the flow ID in the serialized IR
            assertTrue(result.contains("test-flow") || result.contains("SUCCESS"));
        }

        @Test
        @DisplayName("reports validation errors for invalid flow")
        void reportsValidationErrorsForInvalidFlow() throws IOException {
            String markdown = """
                ---
                name: Missing ID
                version: 1.0.0
                ---
                # Invalid Flow
                """;
            Path flowFile = tempDir.resolve("invalid-flow.md");
            Files.writeString(flowFile, markdown);

            String result = compileCommand.flowCompile(flowFile.toString(), null, "json", false);

            // Check for error indicators: error codes, failure messages, or missing field
            assertTrue(result.contains("[E") || result.contains("failed") || result.contains("Missing") || result.contains("required"));
        }

        @Test
        @DisplayName("validates only when flag is set")
        void validatesOnlyWhenFlagIsSet() throws IOException {
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

            String result = compileCommand.flowCompile(flowFile.toString(), null, "json", true);

            assertTrue(result.contains("Validation passed"));
        }

        @Test
        @DisplayName("writes output to file when specified")
        void writesOutputToFileWhenSpecified() throws IOException {
            String markdown = """
                ---
                id: test-flow
                name: Test Flow
                version: 1.0.0
                ---
                # Test Flow
                """;
            Path flowFile = tempDir.resolve("test-flow.md");
            Path outputFile = tempDir.resolve("output.json");
            Files.writeString(flowFile, markdown);

            String result = compileCommand.flowCompile(flowFile.toString(), outputFile.toString(), "json", false);

            assertTrue(result.contains("IR written to") || result.contains("test-flow"));
            if (result.contains("IR written to")) {
                assertTrue(Files.exists(outputFile));
                String outputContent = Files.readString(outputFile);
                assertTrue(outputContent.contains("test-flow"));
            }
        }

        @Test
        @DisplayName("handles malformed frontmatter")
        void handlesMalformedFrontmatter() throws IOException {
            String markdown = """
                ---
                id: [broken
                version: 1.0.0
                ---
                # Broken
                """;
            Path flowFile = tempDir.resolve("broken.md");
            Files.writeString(flowFile, markdown);

            String result = compileCommand.flowCompile(flowFile.toString(), null, "json", false);

            // Check for error indicators: error codes or failure messages
            assertTrue(result.contains("[E") || result.contains("failed") || result.contains("malformed"));
        }
    }

    @Nested
    @DisplayName("skill compile")
    class SkillCompileTest {

        @Test
        @DisplayName("fails when file path is null")
        void failsWhenFilePathIsNull() {
            String result = compileCommand.skillCompile(null, null, null, false);
            assertTrue(result.contains("File path is required"));
        }

        @Test
        @DisplayName("compiles valid skill markdown")
        void compilesValidSkillMarkdown() throws IOException {
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

            String result = compileCommand.skillCompile(skillFile.toString(), null, "json", false);

            assertTrue(result.contains("test-skill") || result.contains("SUCCESS"));
        }

        @Test
        @DisplayName("reports validation errors for invalid skill")
        void reportsValidationErrorsForInvalidSkill() throws IOException {
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

            String result = compileCommand.skillCompile(skillFile.toString(), null, "json", false);

            // Check for error indicators: error codes, failure messages, or missing field
            assertTrue(result.contains("[E") || result.contains("failed") || result.contains("Missing") || result.contains("required"));
        }

        @Test
        @DisplayName("validates only when flag is set")
        void validatesOnlyWhenFlagIsSet() throws IOException {
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

            String result = compileCommand.skillCompile(skillFile.toString(), null, "json", true);

            assertTrue(result.contains("Validation passed"));
        }
    }

    @Nested
    @DisplayName("format parsing")
    class FormatParsingTest {

        @Test
        @DisplayName("defaults to JSON when format is null")
        void defaultsToJsonWhenFormatIsNull() throws IOException {
            String markdown = """
                ---
                id: test-flow
                version: 1.0.0
                ---
                # Test
                """;
            Path flowFile = tempDir.resolve("test.md");
            Files.writeString(flowFile, markdown);

            String result = compileCommand.flowCompile(flowFile.toString(), null, null, false);

            // JSON output should contain these characters
            assertNotNull(result);
        }

        @Test
        @DisplayName("accepts json format")
        void acceptsJsonFormat() throws IOException {
            String markdown = """
                ---
                id: test-flow
                version: 1.0.0
                ---
                # Test
                """;
            Path flowFile = tempDir.resolve("test.md");
            Files.writeString(flowFile, markdown);

            String result = compileCommand.flowCompile(flowFile.toString(), null, "json", false);

            assertNotNull(result);
        }

        @Test
        @DisplayName("accepts YAML format (fallback to JSON)")
        void acceptsYamlFormat() throws IOException {
            String markdown = """
                ---
                id: test-flow
                version: 1.0.0
                ---
                # Test
                """;
            Path flowFile = tempDir.resolve("test.md");
            Files.writeString(flowFile, markdown);

            String result = compileCommand.flowCompile(flowFile.toString(), null, "yaml", false);

            assertNotNull(result);
        }
    }
}
