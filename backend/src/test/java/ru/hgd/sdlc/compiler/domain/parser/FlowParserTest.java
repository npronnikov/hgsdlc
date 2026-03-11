package ru.hgd.sdlc.compiler.domain.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.shared.kernel.Result;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FlowParser")
class FlowParserTest {

    private FrontmatterExtractor extractor;
    private FlowParser parser;

    @BeforeEach
    void setUp() {
        extractor = new FrontmatterExtractor();
        parser = new FlowParser();
    }

    @Nested
    @DisplayName("valid input")
    class ValidInputTest {

        @Test
        @DisplayName("parses minimal valid flow")
        void parsesMinimalValidFlow() {
            String markdown = """
                ---
                id: minimal-flow
                version: 1.0.0
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isSuccess());

            FlowDocument flow = result.getDocument();
            assertEquals("minimal-flow", flow.id().value());
            assertEquals("1.0.0", flow.version().toString());
        }

        @Test
        @DisplayName("parses complete flow")
        void parsesCompleteFlow() {
            String markdown = """
                ---
                id: full-flow
                name: Full Flow
                version: 2.1.0
                phase_order:
                  - setup
                  - develop
                  - review
                start_roles: [developer, architect]
                resume_policy: from-checkpoint
                author: John Doe
                ---

                # Full Flow Description

                This is a complete flow with all fields.
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isSuccess());

            FlowDocument flow = result.getDocument();
            assertEquals("full-flow", flow.id().value());
            assertEquals("Full Flow", flow.name());
            assertEquals("2.1.0", flow.version().toString());
            assertEquals(3, flow.phaseOrder().size());
            assertEquals(PhaseId.of("setup"), flow.phaseOrder().get(0));
            assertEquals(2, flow.startRoles().size());
            assertTrue(flow.startRoles().contains(Role.of("developer")));
            assertEquals(ResumePolicy.FROM_CHECKPOINT, flow.resumePolicy());
            assertEquals("John Doe", flow.author());
            assertTrue(flow.description().content().contains("Full Flow Description"));
        }

        @Test
        @DisplayName("parses flow with optional fields omitted")
        void parsesFlowWithOptionalFieldsOmitted() {
            String markdown = """
                ---
                id: simple
                version: 0.1.0
                ---

                Simple body.
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isSuccess());

            FlowDocument flow = result.getDocument();
            assertNull(flow.name());
            assertTrue(flow.phaseOrder().isEmpty());
            assertTrue(flow.startRoles().isEmpty());
            assertNull(flow.author());
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
                version: 1.0.0
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());
            assertEquals("E1004", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("id"));
        }

        @Test
        @DisplayName("fails when version is missing")
        void failsWhenVersionIsMissing() {
            String markdown = """
                ---
                id: test-flow
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());
            assertEquals("E1004", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("version"));
        }

        @Test
        @DisplayName("fails when id is empty")
        void failsWhenIdIsEmpty() {
            String markdown = """
                ---
                id: ""
                version: 1.0.0
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());
            assertEquals("E1004", result.getFirstError().code());
        }
    }

    @Nested
    @DisplayName("field type validation")
    class FieldTypeValidationTest {

        @Test
        @DisplayName("fails when version is invalid semantic version")
        void failsWhenVersionIsInvalidSemanticVersion() {
            String markdown = """
                ---
                id: test-flow
                version: not-a-version
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());
            assertEquals("E1005", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("version"));
        }

        @Test
        @DisplayName("fails when phase_order is not a list")
        void failsWhenPhaseOrderIsNotAList() {
            String markdown = """
                ---
                id: test-flow
                version: 1.0.0
                phase_order: not-a-list
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());
            assertEquals("E1005", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("phase_order"));
        }

        @Test
        @DisplayName("fails when start_roles contains non-strings")
        void failsWhenStartRolesContainsNonStrings() {
            String markdown = """
                ---
                id: test-flow
                version: 1.0.0
                start_roles: [developer, 123]
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());
            assertEquals("E1005", result.getFirstError().code());
        }
    }

    @Nested
    @DisplayName("resume policy parsing")
    class ResumePolicyParsingTest {

        @Test
        @DisplayName("parses from-checkpoint policy")
        void parsesFromCheckpointPolicy() {
            String markdown = """
                ---
                id: test
                version: 1.0.0
                resume_policy: from-checkpoint
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());

            assertTrue(result.isSuccess());
            assertEquals(ResumePolicy.FROM_CHECKPOINT, result.getDocument().resumePolicy());
        }

        @Test
        @DisplayName("defaults to from-checkpoint when not specified")
        void defaultsToFromCheckpointWhenNotSpecified() {
            String markdown = """
                ---
                id: test
                version: 1.0.0
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());

            assertTrue(result.isSuccess());
            assertEquals(ResumePolicy.FROM_CHECKPOINT, result.getDocument().resumePolicy());
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
                phase_order: not-a-list
                start_roles: [123]
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());

            // Should have errors for: id (missing), version (invalid), phase_order (invalid), start_roles (invalid)
            assertEquals(4, result.getErrors().size());

            // Verify all error codes
            assertTrue(result.getErrors().stream().anyMatch(e -> e.message().contains("id")));
            assertTrue(result.getErrors().stream().anyMatch(e -> e.message().contains("version")));
            assertTrue(result.getErrors().stream().anyMatch(e -> e.message().contains("phase_order")));
            assertTrue(result.getErrors().stream().anyMatch(e -> e.message().contains("start_roles")));
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

            ParseResult<FlowDocument> result = parser.parse(extracted.getValue());
            assertTrue(result.isFailure());

            // Should have errors for: id (missing), version (missing)
            assertEquals(2, result.getErrors().size());
            assertTrue(result.getErrors().stream().allMatch(e -> "E1004".equals(e.code())));
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

            Result<FlowDocument, ParseError> result = parser.parseLegacy(extracted.getValue());
            assertTrue(result.isFailure());
            assertNotNull(result.getError());
        }

        @Test
        @DisplayName("parseLegacy returns success on valid input")
        void parseLegacyReturnsSuccessOnValidInput() {
            String markdown = """
                ---
                id: test
                version: 1.0.0
                ---
                """;

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            Result<FlowDocument, ParseError> result = parser.parseLegacy(extracted.getValue());
            assertTrue(result.isSuccess());
            assertEquals("test", result.getValue().id().value());
        }
    }
}
