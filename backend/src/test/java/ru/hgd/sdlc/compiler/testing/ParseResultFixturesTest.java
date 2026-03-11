package ru.hgd.sdlc.compiler.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.compiler.domain.parser.ParseError;
import ru.hgd.sdlc.compiler.domain.parser.ParseResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ParseResultFixtures.
 */
class ParseResultFixturesTest {

    @Nested
    @DisplayName("Successful parse results")
    class SuccessfulParseResultTests {

        @Test
        @DisplayName("successfulParseResult should return success")
        void successfulParseResultShouldReturnSuccess() {
            ParseResult<FlowDocument> result = ParseResultFixtures.successfulFlowParseResult();

            assertTrue(result.isSuccess());
            assertNotNull(result.getDocument());
        }

        @Test
        @DisplayName("successfulSkillParseResult should return skill")
        void successfulSkillParseResultShouldReturnSkill() {
            ParseResult<SkillDocument> result = ParseResultFixtures.successfulSkillParseResult();

            assertTrue(result.isSuccess());
            assertNotNull(result.getDocument());
            assertEquals("simple-skill", result.getDocument().id().value());
        }

        @Test
        @DisplayName("successfulParseResult with document should contain that document")
        void successfulParseResultWithDocumentShouldContainThatDocument() {
            FlowDocument customFlow = FlowDocumentFixtures.multiPhaseFlow();
            ParseResult<FlowDocument> result = ParseResultFixtures.successfulParseResult(customFlow);

            assertTrue(result.isSuccess());
            assertSame(customFlow, result.getDocument());
        }
    }

    @Nested
    @DisplayName("Failed parse results")
    class FailedParseResultTests {

        @Test
        @DisplayName("failedParseResult should return failure")
        void failedParseResultShouldReturnFailure() {
            ParseResult<FlowDocument> result = ParseResultFixtures.failedParseResult();

            assertTrue(result.isFailure());
            assertNull(result.getDocument());
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("failedParseResultWithMultipleErrors should have multiple errors")
        void failedParseResultWithMultipleErrorsShouldHaveMultipleErrors() {
            ParseResult<FlowDocument> result = ParseResultFixtures.failedParseResultWithMultipleErrors();

            assertTrue(result.isFailure());
            assertTrue(result.getErrors().size() >= 2);
        }

        @Test
        @DisplayName("malformedFrontmatterResult should have E1002 code")
        void malformedFrontmatterResultShouldHaveE1002Code() {
            ParseResult<FlowDocument> result = ParseResultFixtures.malformedFrontmatterResult();

            assertTrue(result.isFailure());
            assertEquals("E1002", result.getFirstError().code());
        }

        @Test
        @DisplayName("unclosedFrontmatterResult should have E1003 code")
        void unclosedFrontmatterResultShouldHaveE1003Code() {
            ParseResult<FlowDocument> result = ParseResultFixtures.unclosedFrontmatterResult();

            assertTrue(result.isFailure());
            assertEquals("E1003", result.getFirstError().code());
        }

        @Test
        @DisplayName("missingFieldResult should have E1004 code")
        void missingFieldResultShouldHaveE1004Code() {
            ParseResult<FlowDocument> result = ParseResultFixtures.missingFieldResult("id");

            assertTrue(result.isFailure());
            assertEquals("E1004", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("id"));
        }

        @Test
        @DisplayName("invalidFieldResult should have E1005 code")
        void invalidFieldResultShouldHaveE1005Code() {
            ParseResult<FlowDocument> result = ParseResultFixtures.invalidFieldResult("version", "not a valid version");

            assertTrue(result.isFailure());
            assertEquals("E1005", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("version"));
        }
    }

    @Nested
    @DisplayName("Parse results with warnings")
    class WarningParseResultTests {

        @Test
        @DisplayName("parseResultWithWarnings should have warnings")
        void parseResultWithWarningsShouldHaveWarnings() {
            ParseResult<FlowDocument> result = ParseResultFixtures.parseResultWithWarnings();

            assertTrue(result.hasWarnings());
            assertFalse(result.isFailure());
            assertFalse(result.getWarnings().isEmpty());
        }

        @Test
        @DisplayName("warnings should have W prefix codes")
        void warningsShouldHaveWPrefixCodes() {
            ParseResult<FlowDocument> result = ParseResultFixtures.parseResultWithWarnings();

            List<ParseError> warnings = result.getWarnings();
            assertTrue(warnings.stream().allMatch(ParseError::isWarning));
        }
    }

    @Nested
    @DisplayName("Custom error creation")
    class CustomErrorTests {

        @Test
        @DisplayName("customError should create error with code and message")
        void customErrorShouldCreateErrorWithCodeAndMessage() {
            ParseError error = ParseResultFixtures.customError("E9999", "Custom error message");

            assertEquals("E9999", error.code());
            assertEquals("Custom error message", error.message());
            assertTrue(error.isError());
        }

        @Test
        @DisplayName("customError with location should include location")
        void customErrorWithLocationShouldIncludeLocation() {
            ParseError error = ParseResultFixtures.customError("E9999", "Error", "line:5");

            assertEquals("E9999", error.code());
            assertTrue(error.location().isPresent());
            assertEquals("line:5", error.location().get());
        }
    }

    @Nested
    @DisplayName("Generic parse result handling")
    class GenericHandlingTests {

        @Test
        @DisplayName("should work with generic type parameter")
        void shouldWorkWithGenericTypeParameter() {
            ParseResult<FlowDocument> flowResult = ParseResultFixtures.successfulParseResult(FlowDocumentFixtures.simpleFlow());
            ParseResult<SkillDocument> skillResult = ParseResultFixtures.successfulParseResult(SkillDocumentFixtures.simpleSkill());

            assertTrue(flowResult.isSuccess());
            assertTrue(skillResult.isSuccess());
            assertInstanceOf(FlowDocument.class, flowResult.getDocument());
            assertInstanceOf(SkillDocument.class, skillResult.getDocument());
        }
    }
}
