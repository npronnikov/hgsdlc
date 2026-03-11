package ru.hgd.sdlc.compiler.testing;

import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.compiler.domain.parser.ParseError;
import ru.hgd.sdlc.compiler.domain.parser.ParseResult;

import java.util.List;

/**
 * Factory for creating test ParseResult instances.
 * Provides pre-configured parse results for various testing scenarios.
 */
public final class ParseResultFixtures implements TestFixture {

    private ParseResultFixtures() {
        // Utility class - no instantiation
    }

    /**
     * Creates a successful parse result with a simple flow.
     *
     * @param <T> the document type
     * @return a successful parse result
     */
    @SuppressWarnings("unchecked")
    public static <T> ParseResult<T> successfulParseResult() {
        return (ParseResult<T>) ParseResult.success(FlowDocumentFixtures.simpleFlow());
    }

    /**
     * Creates a successful parse result with the given document.
     *
     * @param document the parsed document
     * @param <T> the document type
     * @return a successful parse result
     */
    public static <T> ParseResult<T> successfulParseResult(T document) {
        return ParseResult.success(document);
    }

    /**
     * Creates a successful parse result for a flow.
     *
     * @return a successful parse result with a flow document
     */
    public static ParseResult<FlowDocument> successfulFlowParseResult() {
        return ParseResult.success(FlowDocumentFixtures.simpleFlow());
    }

    /**
     * Creates a successful parse result for a skill.
     *
     * @return a successful parse result with a skill document
     */
    public static ParseResult<SkillDocument> successfulSkillParseResult() {
        return ParseResult.success(SkillDocumentFixtures.simpleSkill());
    }

    /**
     * Creates a failed parse result with a single error.
     *
     * @param <T> the document type
     * @return a failed parse result
     */
    public static <T> ParseResult<T> failedParseResult() {
        return ParseResult.failure(ParseError.missingFrontmatter());
    }

    /**
     * Creates a failed parse result with the given error.
     *
     * @param error the parse error
     * @param <T> the document type
     * @return a failed parse result
     */
    public static <T> ParseResult<T> failedParseResult(ParseError error) {
        return ParseResult.failure(error);
    }

    /**
     * Creates a failed parse result with multiple errors.
     *
     * @param <T> the document type
     * @return a failed parse result with multiple errors
     */
    public static <T> ParseResult<T> failedParseResultWithMultipleErrors() {
        return ParseResult.failure(List.of(
            ParseError.missingField("id", "frontmatter"),
            ParseError.missingField("version", "frontmatter"),
            ParseError.invalidField("startRoles", "must be a non-empty array", "frontmatter")
        ));
    }

    /**
     * Creates a failed parse result for malformed frontmatter.
     *
     * @param <T> the document type
     * @return a failed parse result
     */
    public static <T> ParseResult<T> malformedFrontmatterResult() {
        return ParseResult.failure(ParseError.malformedFrontmatter(
            "Unexpected character at line 5",
            new RuntimeException("YAML parsing failed")
        ));
    }

    /**
     * Creates a failed parse result for unclosed frontmatter.
     *
     * @param <T> the document type
     * @return a failed parse result
     */
    public static <T> ParseResult<T> unclosedFrontmatterResult() {
        return ParseResult.failure(ParseError.unclosedFrontmatter());
    }

    /**
     * Creates a failed parse result for a missing required field.
     *
     * @param fieldName the name of the missing field
     * @param <T> the document type
     * @return a failed parse result
     */
    public static <T> ParseResult<T> missingFieldResult(String fieldName) {
        return ParseResult.failure(ParseError.missingField(fieldName, "frontmatter"));
    }

    /**
     * Creates a failed parse result for an invalid field value.
     *
     * @param fieldName the name of the invalid field
     * @param reason the reason for invalidity
     * @param <T> the document type
     * @return a failed parse result
     */
    public static <T> ParseResult<T> invalidFieldResult(String fieldName, String reason) {
        return ParseResult.failure(ParseError.invalidField(fieldName, reason, "frontmatter"));
    }

    /**
     * Creates a parse result with warnings (successful but with warnings).
     *
     * @param <T> the document type
     * @return a parse result with warnings
     */
    @SuppressWarnings("unchecked")
    public static <T> ParseResult<T> parseResultWithWarnings() {
        return (ParseResult<T>) ParseResult.withWarnings(
            FlowDocumentFixtures.simpleFlow(),
            List.of(
                ParseError.unknownField("deprecatedField", "frontmatter"),
                ParseError.unknownField("legacyOption", "frontmatter")
            )
        );
    }

    /**
     * Creates a parse result with warnings for the given document.
     *
     * @param document the parsed document
     * @param warnings the warning list
     * @param <T> the document type
     * @return a parse result with warnings
     */
    public static <T> ParseResult<T> parseResultWithWarnings(T document, List<ParseError> warnings) {
        return ParseResult.withWarnings(document, warnings);
    }

    /**
     * Creates a custom parse error for testing.
     *
     * @param code the error code
     * @param message the error message
     * @return a parse error
     */
    public static ParseError customError(String code, String message) {
        return ParseError.of(code, message);
    }

    /**
     * Creates a custom parse error with location for testing.
     *
     * @param code the error code
     * @param message the error message
     * @param location the error location
     * @return a parse error
     */
    public static ParseError customError(String code, String message, String location) {
        return ParseError.of(code, message, location);
    }
}
