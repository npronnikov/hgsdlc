package ru.hgd.sdlc.compiler.domain.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.shared.kernel.Result;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillParser")
class SkillParserTest {

    private FrontmatterExtractor extractor;
    private SkillParser parser;

    @BeforeEach
    void setUp() {
        extractor = new FrontmatterExtractor();
        parser = new SkillParser();
    }

    @Nested
    @DisplayName("valid input")
    class ValidInputTest {

        @Test
        @DisplayName("parses minimal valid skill")
        void parsesMinimalValidSkill() {
            String markdown = """
                ---
                id: generate-code
                name: Code Generator
                version: 1.0.0
                handler: skill://qwen-coder
                ---

                Generates code based on instructions.
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<SkillDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isSuccess());

            SkillDocument skill = result.getDocument();
            assertEquals("generate-code", skill.id().value());
            assertEquals("Code Generator", skill.name());
            assertEquals("1.0.0", skill.version().toString());
            assertEquals("skill://qwen-coder", skill.handler().toString());
        }

        @Test
        @DisplayName("parses skill with all fields")
        void parsesSkillWithAllFields() {
            String markdown = """
                ---
                id: review-code
                name: Code Reviewer
                version: 2.0.0
                handler: builtin://review
                input_schema:
                  type: object
                  properties:
                    code:
                      type: string
                output_schema:
                  type: object
                  properties:
                    issues:
                      type: array
                tags: [review, quality]
                author: Jane Doe
                ---

                # Code Review Skill

                Reviews code for quality issues.
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<SkillDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isSuccess());

            SkillDocument skill = result.getDocument();
            assertEquals("review-code", skill.id().value());
            assertEquals("Code Reviewer", skill.name());
            assertEquals("2.0.0", skill.version().toString());
            assertEquals("builtin://review", skill.handler().toString());
            assertFalse(skill.inputSchema().isEmpty());
            assertFalse(skill.outputSchema().isEmpty());
            assertEquals(2, skill.tags().size());
            assertTrue(skill.hasTag("review"));
            assertEquals("Jane Doe", skill.author());
            assertTrue(skill.description().content().contains("Code Review Skill"));
        }

        @Test
        @DisplayName("parses skill with script handler")
        void parsesSkillWithScriptHandler() {
            String markdown = """
                ---
                id: custom-script
                name: Custom Script
                version: 1.0.0
                handler: script://scripts/custom.sh
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<SkillDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isSuccess());

            SkillDocument skill = result.getDocument();
            assertEquals("script://scripts/custom.sh", skill.handler().toString());
            assertEquals(HandlerKind.SCRIPT, skill.handler().kind());
        }
    }

    @Nested
    @DisplayName("required field validation")
    class RequiredFieldValidationTest {

        @Test
        @DisplayName("fails when id is missing")
        void failsWhenIdIsMissing() {
            String markdown = """
                ---
                name: Test
                version: 1.0.0
                handler: skill://test
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<SkillDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());
            assertEquals("E1004", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("id"));
        }

        @Test
        @DisplayName("fails when name is missing")
        void failsWhenNameIsMissing() {
            String markdown = """
                ---
                id: test-skill
                version: 1.0.0
                handler: skill://test
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<SkillDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());
            assertEquals("E1004", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("name"));
        }

        @Test
        @DisplayName("fails when version is missing")
        void failsWhenVersionIsMissing() {
            String markdown = """
                ---
                id: test-skill
                name: Test Skill
                handler: skill://test
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<SkillDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());
            assertEquals("E1004", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("version"));
        }

        @Test
        @DisplayName("fails when handler is missing")
        void failsWhenHandlerIsMissing() {
            String markdown = """
                ---
                id: test-skill
                name: Test Skill
                version: 1.0.0
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<SkillDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());
            assertEquals("E1004", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("handler"));
        }
    }

    @Nested
    @DisplayName("handler validation")
    class HandlerValidationTest {

        @Test
        @DisplayName("fails when handler format is invalid")
        void failsWhenHandlerFormatIsInvalid() {
            String markdown = """
                ---
                id: test-skill
                name: Test Skill
                version: 1.0.0
                handler: invalid-handler-format
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<SkillDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());
            assertEquals("E1005", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("handler"));
        }
    }

    @Nested
    @DisplayName("multiple errors")
    class MultipleErrorsTest {

        @Test
        @DisplayName("returns all errors when multiple fields are invalid")
        void returnsAllErrorsWhenMultipleFieldsInvalid() {
            String markdown = """
                ---
                version: not-a-version
                handler: invalid-format
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<SkillDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());

            // Should have errors for: id (missing), name (missing), version (invalid), handler (invalid)
            assertEquals(4, result.getErrors().size());

            // Verify all error codes
            assertTrue(result.getErrors().stream().anyMatch(e -> e.message().contains("id")));
            assertTrue(result.getErrors().stream().anyMatch(e -> e.message().contains("name")));
            assertTrue(result.getErrors().stream().anyMatch(e -> e.message().contains("version")));
            assertTrue(result.getErrors().stream().anyMatch(e -> e.message().contains("handler")));
        }

        @Test
        @DisplayName("returns all missing required field errors")
        void returnsAllMissingRequiredFieldErrors() {
            String markdown = """
                ---
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<SkillDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());

            // Should have errors for: id, name, version, handler (all missing)
            assertEquals(4, result.getErrors().size());
            assertTrue(result.getErrors().stream().allMatch(e -> "E1004".equals(e.code())));
        }
    }

    @Nested
    @DisplayName("toRef")
    class ToRefTest {

        @Test
        @DisplayName("creates correct HandlerRef from skill")
        void createsCorrectHandlerRefFromSkill() {
            String markdown = """
                ---
                id: my-skill
                name: My Skill
                version: 1.0.0
                handler: skill://my-skill
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            ParseResult<SkillDocument> result = parser.parse(extracted.getValue());

            assertTrue(result.isSuccess());
            SkillDocument skill = result.getDocument();

            HandlerRef ref = skill.toRef();
            assertEquals("skill://my-skill", ref.toString());
            assertEquals(HandlerKind.SKILL, ref.kind());
        }
    }

    @Nested
    @DisplayName("legacy API")
    class LegacyApiTest {

        @Test
        @DisplayName("parseLegacy returns Result with first error")
        void parseLegacyReturnsResultWithFirstError() {
            String markdown = """
                ---
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            Result<SkillDocument, ParseError> result = parser.parseLegacy(extracted.getValue());
            assertTrue(result.isFailure());
            assertNotNull(result.getError());
        }

        @Test
        @DisplayName("parseLegacy returns success on valid input")
        void parseLegacyReturnsSuccessOnValidInput() {
            String markdown = """
                ---
                id: test
                name: Test
                version: 1.0.0
                handler: skill://test
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            Result<SkillDocument, ParseError> result = parser.parseLegacy(extracted.getValue());
            assertTrue(result.isSuccess());
            assertEquals("test", result.getValue().id().value());
        }
    }
}
